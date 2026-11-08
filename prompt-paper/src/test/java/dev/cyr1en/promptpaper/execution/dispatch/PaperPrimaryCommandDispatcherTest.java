package dev.cyr1en.promptpaper.execution.dispatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PaperPrimaryCommandDispatcher Contract Tests")
class PaperPrimaryCommandDispatcherTest extends MockBukkitTest {

  private PaperPrimaryCommandDispatcher dispatcher;

  /** Handler lists are static and outlive MockBukkit, so each test must remove its listeners. */
  private final List<Listener> listeners = new ArrayList<>();

  @BeforeEach
  void setupDispatcher() {
    dispatcher = new PaperPrimaryCommandDispatcher(plugin, scheduler);
  }

  @AfterEach
  void unregisterListeners() {
    listeners.forEach(HandlerList::unregisterAll);
  }

  private void registerMockCommand(String name, boolean succeed) {
    Command cmd =
        new Command(name) {
          @Override
          public boolean execute(
              @NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            return succeed;
          }
        };
    server.getCommandMap().register(name, cmd);
  }

  private void registerThrowingCommand(String name) {
    Command cmd =
        new Command(name) {
          @Override
          public boolean execute(
              @NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            throw new IllegalStateException("Command error for " + name);
          }
        };
    server.getCommandMap().register(name, cmd);
  }

  private Command recordingCommand(String name, List<String> calls) {
    Command cmd =
        new Command(name) {
          @Override
          public boolean execute(
              @NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            calls.add(commandLabel + " " + String.join(" ", args));
            return true;
          }
        };
    server.getCommandMap().register(name, cmd);
    return cmd;
  }

  private void registerPermissionRecordingCommand(
      String name, String permission, List<Boolean> results) {
    Command cmd =
        new Command(name) {
          @Override
          public boolean execute(
              @NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            results.add(sender.hasPermission(permission));
            return true;
          }
        };
    server.getCommandMap().register(name, cmd);
  }

  private void onCommandEvent(Consumer<PlayerCommandPreprocessEvent> handler) {
    Listener listener =
        new Listener() {
          @EventHandler
          public void on(PlayerCommandPreprocessEvent e) {
            handler.accept(e);
          }
        };
    listeners.add(listener);
    server.getPluginManager().registerEvents(listener, plugin);
  }

  @Test
  @DisplayName("PLAYER mode: fires PlayerCommandPreprocessEvent with the finished command")
  void firesPreprocessEventBeforeDispatch() {
    var player = createPlayer("EventPlayer");
    var calls = new ArrayList<String>();
    recordingCommand("eventcmd", calls);
    var seen = new ArrayList<String>();
    var fired = new AtomicReference<PlayerCommandPreprocessEvent>();
    onCommandEvent(
        e -> {
          seen.add(e.getMessage());
          fired.set(e);
          assertTrue(CommandEventBridge.isBridgeEvent(e));
        });

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    dispatcher.dispatch(PrimaryDispatchRequest.player(player, "/eventcmd a b"), outcomeRef::set);

    assertEquals(List.of("/eventcmd a b"), seen);
    assertEquals(List.of("eventcmd a b"), calls);
    assertTrue(outcomeRef.get().isSuccess());
    assertFalse(CommandEventBridge.isBridgeEvent(fired.get()));
  }

  @Test
  @DisplayName("PLAYER mode: a listener can rewrite or cancel the finished command")
  void preprocessListenerCanRewriteOrCancel() {
    var player = createPlayer("RewritePlayer");
    var calls = new ArrayList<String>();
    recordingCommand("rewritten", calls);
    recordingCommand("blockedcmd", calls);
    onCommandEvent(
        e -> {
          if (e.getMessage().startsWith("/original")) {
            e.setMessage("/rewritten x");
          } else if (e.getMessage().startsWith("/blockedcmd")) {
            e.setCancelled(true);
          }
        });

    var first = new AtomicReference<DispatchOutcome>();
    dispatcher.dispatch(PrimaryDispatchRequest.player(player, "/original y"), first::set);
    var second = new AtomicReference<DispatchOutcome>();
    dispatcher.dispatch(PrimaryDispatchRequest.player(player, "/blockedcmd"), second::set);

    assertEquals(List.of("rewritten x"), calls);
    assertTrue(first.get().isSuccess());
    assertEquals("rewritten x", first.get().rewrittenCommand());
    assertTrue(second.get().isFailure(), "a cancelled command never ran, so it is not a success");
    assertTrue(second.get().isCancelledByListener());
  }

  @Test
  @DisplayName("PLAYER mode: an unchanged command reports no rewrite")
  void unchangedCommandReportsNoRewrite() {
    var player = createPlayer("NoRewritePlayer");
    registerMockCommand("plaincmd", true);

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    dispatcher.dispatch(PrimaryDispatchRequest.player(player, "/plaincmd a"), outcomeRef::set);

    assertTrue(outcomeRef.get().isSuccess());
    assertNull(outcomeRef.get().rewrittenCommand());
  }

  @Test
  @DisplayName("ATTACHMENT mode: listeners see real permissions, the command runs elevated")
  void attachmentEventFiresBeforePermissionGrant() {
    var player = createPlayer("AttachEventPlayer");
    var permKey = "test.event.perm";
    var permDuringEvent = new AtomicReference<Boolean>();
    var permDuringCommand = new ArrayList<Boolean>();
    registerPermissionRecordingCommand("attachevent", permKey, permDuringCommand);
    onCommandEvent(e -> permDuringEvent.set(e.getPlayer().hasPermission(permKey)));

    var context = PermissionAttachmentContext.of("KEY", List.of(permKey));
    var outcomeRef = new AtomicReference<DispatchOutcome>();
    dispatcher.dispatch(
        PrimaryDispatchRequest.attachment(player, "/attachevent", context), outcomeRef::set);

    assertEquals(Boolean.FALSE, permDuringEvent.get());
    assertEquals(List.of(true), permDuringCommand);
    assertTrue(outcomeRef.get().isSuccess());
    assertFalse(player.hasPermission(permKey));
  }

  @Test
  @DisplayName("ATTACHMENT mode: a rewritten command runs without the temporary permissions")
  void attachmentRewrittenCommandIsNotElevated() {
    var player = createPlayer("AttachRewritePlayer");
    var permKey = "test.rewrite.perm";
    var permDuringCommand = new ArrayList<Boolean>();
    registerPermissionRecordingCommand("attachother", permKey, permDuringCommand);
    registerMockCommand("attachoriginal", true);
    onCommandEvent(e -> e.setMessage("/attachother"));

    var context = PermissionAttachmentContext.of("KEY", List.of(permKey));
    var outcomeRef = new AtomicReference<DispatchOutcome>();
    dispatcher.dispatch(
        PrimaryDispatchRequest.attachment(player, "/attachoriginal", context), outcomeRef::set);

    assertEquals(List.of(false), permDuringCommand);
    assertTrue(outcomeRef.get().isSuccess());
    assertEquals("attachother", outcomeRef.get().rewrittenCommand());
  }

  @Test
  @DisplayName("ATTACHMENT mode: a cancelled command never runs")
  void attachmentCancelledCommandNeverRuns() {
    var player = createPlayer("AttachCancelPlayer");
    var calls = new ArrayList<String>();
    recordingCommand("attachblocked", calls);
    onCommandEvent(e -> e.setCancelled(true));

    var context = PermissionAttachmentContext.of("KEY", List.of("test.cancel.perm"));
    var outcomeRef = new AtomicReference<DispatchOutcome>();
    dispatcher.dispatch(
        PrimaryDispatchRequest.attachment(player, "/attachblocked", context), outcomeRef::set);

    assertTrue(calls.isEmpty());
    assertTrue(outcomeRef.get().isCancelledByListener());
  }

  @Test
  @DisplayName("Bridge: only its own event is skipped, and nested fires restore the outer event")
  void bridgeTracksOnlyItsOwnEvent() {
    var player = createPlayer("NestedPlayer");
    var unrelatedSkipped = new AtomicReference<Boolean>();
    var outerStillTracked = new AtomicReference<Boolean>();
    onCommandEvent(
        e -> {
          if (e.getMessage().equals("/outer")) {
            unrelatedSkipped.set(
                CommandEventBridge.isBridgeEvent(new PlayerCommandPreprocessEvent(player, "/x")));
            CommandEventBridge.fire(player, "inner");
            outerStillTracked.set(CommandEventBridge.isBridgeEvent(e));
          }
        });

    CommandEventBridge.fire(player, "outer");

    assertEquals(Boolean.FALSE, unrelatedSkipped.get());
    assertEquals(Boolean.TRUE, outerStillTracked.get());
  }

  @Test
  @DisplayName("PLAYER mode: executes command as player and invokes callback once with success")
  void dispatchesPlayerCommandSuccessfully() {
    var player = createPlayer("TestPlayer");
    registerMockCommand("testplayerok", true);

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    var callbackCount = new AtomicInteger(0);

    var request = PrimaryDispatchRequest.player(player, "/testplayerok arg1");
    dispatcher.dispatch(
        request,
        outcome -> {
          callbackCount.incrementAndGet();
          outcomeRef.set(outcome);
        });

    assertEquals(1, callbackCount.get(), "Callback must be invoked exactly once");
    assertNotNull(outcomeRef.get());
    assertTrue(outcomeRef.get().isSuccess(), "Outcome must be success");
    assertNull(outcomeRef.get().error());
  }

  @Test
  @DisplayName("PLAYER mode: returns typed failure when dispatchCommand returns false")
  void dispatchesPlayerCommandFalse() {
    var player = createPlayer("TestPlayer");
    registerMockCommand("testplayerfail", false);

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    var callbackCount = new AtomicInteger(0);

    var request = PrimaryDispatchRequest.player(player, "testplayerfail");
    dispatcher.dispatch(
        request,
        outcome -> {
          callbackCount.incrementAndGet();
          outcomeRef.set(outcome);
        });

    assertEquals(1, callbackCount.get());
    assertNotNull(outcomeRef.get());
    assertTrue(outcomeRef.get().isFailure());
    assertEquals(DispatchErrorKind.DISPATCH_RETURNED_FALSE, outcomeRef.get().error().kind());
  }

  @Test
  @DisplayName("PLAYER mode: returns typed failure when command execution throws exception")
  void dispatchesPlayerCommandException() {
    var player = createPlayer("TestPlayer");
    registerThrowingCommand("testplayerthrow");

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    var callbackCount = new AtomicInteger(0);

    var request = PrimaryDispatchRequest.player(player, "/testplayerthrow");
    dispatcher.dispatch(
        request,
        outcome -> {
          callbackCount.incrementAndGet();
          outcomeRef.set(outcome);
        });

    assertEquals(1, callbackCount.get());
    assertNotNull(outcomeRef.get());
    assertTrue(outcomeRef.get().isFailure());
    assertEquals(DispatchErrorKind.EXCEPTION_THROWN, outcomeRef.get().error().kind());
    assertNotNull(outcomeRef.get().error().cause());
  }

  @Test
  @DisplayName(
      "CONSOLE mode: dispatches on scheduler as console sender and returns outcome to initiator")
  void dispatchesConsoleCommandSuccessfully() {
    var player = createPlayer("TestPlayer");
    registerMockCommand("testconsoleok", true);

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    var callbackCount = new AtomicInteger(0);

    var request = PrimaryDispatchRequest.console(player, "/testconsoleok");
    dispatcher.dispatch(
        request,
        outcome -> {
          callbackCount.incrementAndGet();
          outcomeRef.set(outcome);
        });

    assertEquals(1, callbackCount.get());
    assertNotNull(outcomeRef.get());
    assertTrue(outcomeRef.get().isSuccess());
  }

  @Test
  @DisplayName("CONSOLE mode: returns typed failure when dispatchCommand returns false")
  void dispatchesConsoleCommandFalse() {
    var player = createPlayer("TestPlayer");
    registerMockCommand("testconsolefail", false);

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    var callbackCount = new AtomicInteger(0);

    var request = PrimaryDispatchRequest.console(player, "testconsolefail");
    dispatcher.dispatch(
        request,
        outcome -> {
          callbackCount.incrementAndGet();
          outcomeRef.set(outcome);
        });

    assertEquals(1, callbackCount.get());
    assertNotNull(outcomeRef.get());
    assertTrue(outcomeRef.get().isFailure());
    assertEquals(DispatchErrorKind.DISPATCH_RETURNED_FALSE, outcomeRef.get().error().kind());
  }

  @Test
  @DisplayName(
      "ATTACHMENT mode: applies permissions, dispatches, and cleans up immediately when ticks=0")
  void dispatchesAttachmentCommandZeroTicksCleanup() {
    var player = createPlayer("AttachPlayer");
    var permKey = "test.perm";
    assertFalse(player.hasPermission(permKey));

    Command checkingCmd =
        new Command("checkperm") {
          @Override
          public boolean execute(
              @NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            return sender.hasPermission(permKey);
          }
        };
    server.getCommandMap().register("checkperm", checkingCmd);

    var context = PermissionAttachmentContext.of("KEY", List.of(permKey));
    var request = PrimaryDispatchRequest.attachment(player, "/checkperm", context);

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    var callbackCount = new AtomicInteger(0);

    dispatcher.dispatch(
        request,
        outcome -> {
          callbackCount.incrementAndGet();
          outcomeRef.set(outcome);
        });

    assertEquals(1, callbackCount.get());
    assertNotNull(outcomeRef.get());
    assertTrue(
        outcomeRef.get().isSuccess(), "Dispatch should succeed because permission was attached");
    assertFalse(
        player.hasPermission(permKey),
        "Permission attachment must be removed immediately when lifetime is 0");
  }

  @Test
  @DisplayName("ATTACHMENT mode: ignores legacy delay and removes permissions before completion")
  void dispatchesAttachmentCommandIgnoresLegacyDelay() {
    var player = createPlayer("AttachPlayerDelayed");
    var permKey = "test.delayed.perm";
    assertFalse(player.hasPermission(permKey));

    Command checkingCmd =
        new Command("checkdelayed") {
          @Override
          public boolean execute(
              @NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            return sender.hasPermission(permKey);
          }
        };
    server.getCommandMap().register("checkdelayed", checkingCmd);

    var context = PermissionAttachmentContext.of("KEY", List.of(permKey));
    var request = PrimaryDispatchRequest.attachment(player, "checkdelayed", context);

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    var callbackCount = new AtomicInteger(0);

    dispatcher.dispatch(
        request,
        outcome -> {
          callbackCount.incrementAndGet();
          outcomeRef.set(outcome);
        });

    assertEquals(1, callbackCount.get());
    assertTrue(outcomeRef.get().isSuccess());
    assertFalse(
        player.hasPermission(permKey),
        "Permission attachment must be removed before completion is reported");
  }

  @Test
  @DisplayName("ATTACHMENT mode: removes attachment immediately if dispatch returns false")
  void dispatchesAttachmentCommandCleansUpOnFalseReturn() {
    var player = createPlayer("AttachPlayerFail");
    var permKey = "test.fail.perm";

    registerMockCommand("failcmd", false);

    var context = PermissionAttachmentContext.of("KEY", List.of(permKey));
    var request = PrimaryDispatchRequest.attachment(player, "failcmd", context);

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    dispatcher.dispatch(request, outcomeRef::set);

    assertNotNull(outcomeRef.get());
    assertTrue(outcomeRef.get().isFailure());
    assertFalse(
        player.hasPermission(permKey),
        "Permission attachment must be removed immediately on dispatch false");
  }

  @Test
  @DisplayName("ATTACHMENT mode: removes attachment immediately if dispatch throws")
  void dispatchesAttachmentCommandCleansUpOnException() {
    var player = createPlayer("AttachPlayerThrow");
    var permKey = "test.throw.perm";

    registerThrowingCommand("throwcmd");

    var context = PermissionAttachmentContext.of("KEY", List.of(permKey));
    var request = PrimaryDispatchRequest.attachment(player, "throwcmd", context);

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    dispatcher.dispatch(request, outcomeRef::set);

    assertNotNull(outcomeRef.get());
    assertTrue(outcomeRef.get().isFailure());
    assertFalse(
        player.hasPermission(permKey),
        "Permission attachment must be removed immediately on exception");
  }

  @Test
  @DisplayName("ATTACHMENT mode: fails with INVALID_ATTACHMENT if context is null or invalid")
  void rejectsInvalidAttachmentContext() {
    var player = createPlayer("InvalidAttachPlayer");
    var emptyContext = PermissionAttachmentContext.empty();

    var request = PrimaryDispatchRequest.attachment(player, "say hi", emptyContext);

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    dispatcher.dispatch(request, outcomeRef::set);

    assertNotNull(outcomeRef.get());
    assertTrue(outcomeRef.get().isFailure());
    assertEquals(DispatchErrorKind.INVALID_ATTACHMENT, outcomeRef.get().error().kind());
  }

  @Test
  @DisplayName("Handles PlayerExecutor retirement gracefully with SCHEDULER_RETIRED failure")
  void handlesPlayerExecutorRetirement() {
    var player = createPlayer("RetiredPlayer");
    registerMockCommand("retiredcmd", true);

    PlayerExecutor retiredExecutor =
        (task, retired) -> {
          if (retired != null) {
            retired.run();
          }
        };

    var request = PrimaryDispatchRequest.player(player, "retiredcmd", retiredExecutor);

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    var callbackCount = new AtomicInteger(0);

    dispatcher.dispatch(
        request,
        outcome -> {
          callbackCount.incrementAndGet();
          outcomeRef.set(outcome);
        });

    assertEquals(1, callbackCount.get(), "Callback must be invoked exactly once on retirement");
    assertNotNull(outcomeRef.get());
    assertTrue(outcomeRef.get().isFailure());
    assertEquals(DispatchErrorKind.SCHEDULER_RETIRED, outcomeRef.get().error().kind());
  }

  @Test
  @DisplayName("Rejects blank or null command with INVALID_REQUEST")
  void rejectsBlankCommand() {
    var player = createPlayer("BlankCmdPlayer");

    var outcomeRef = new AtomicReference<DispatchOutcome>();
    var request = PrimaryDispatchRequest.player(player, "   ");

    dispatcher.dispatch(request, outcomeRef::set);

    assertNotNull(outcomeRef.get());
    assertTrue(outcomeRef.get().isFailure());
    assertEquals(DispatchErrorKind.INVALID_REQUEST, outcomeRef.get().error().kind());
  }
}
