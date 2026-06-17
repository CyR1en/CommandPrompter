package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.command.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage for the Scope 5 dispatch path. Tests register real
 * {@link Command} instances via {@link MockBukkit}'s command map and assert
 * that the post-command template (with placeholders resolved) is actually
 * executed.
 *
 * <p>Using a registered command (rather than a Mockito spy on
 * {@code ServerMock.dispatchCommand}) is the only reliable way to verify
 * dispatch under MockBukkit — the {@code ServerMock} returned by
 * {@code MockBukkitTest} is not a Mockito mock, so {@code verify()} on it
 * throws.
 */
class PromptEnginePostCommandTest extends MockBukkitTest {

  /** Captured commands and their senders, in dispatch order. */
  record Captured(String command, org.bukkit.command.CommandSender sender) {}

  private final List<Captured> captured = new ArrayList<>();
  private PresetRegistry registry;
  private HookContainer hookContainer;
  private PromptEngine engine;

  @BeforeEach
  void setUp() {
    registry = mock(PresetRegistry.class);
    hookContainer = mock(HookContainer.class);
    when(plugin.getPresetRegistry()).thenReturn(registry);
    when(plugin.getHookContainer()).thenReturn(hookContainer);
    when(hookContainer.getHook(PapiHook.class)).thenReturn(Optional.empty());
    engine = new PromptEngine(plugin, scheduler);

    // Register a capture command for every command name we expect to be
    // dispatched by the tests below. The capture command records the
    // (command, sender) tuple for later assertion.
    registerCapturingCommand("dispatch_test");
    registerCapturingCommand("discord");
    registerCapturingCommand("eco");
    registerCapturingCommand("msg");
    registerCapturingCommand("promote");
    registerCapturingCommand("say");
    registerCapturingCommand("log");
  }

  /**
   * Registers a {@link Command} under the given name that records every
   * execution into {@link #captured}.
   */
  private void registerCapturingCommand(String name) {
    var cmd = new Command(name) {
      @Override
      public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
        var joined = args.length > 0 ? " " + String.join(" ", args) : "";
        captured.add(new Captured(commandLabel + joined, sender));
        return true;
      }
    };
    server.getCommandMap().register(plugin.getName().toLowerCase(), cmd);
  }

  // --- legacy PCMs (regression coverage for the refactor) ---

  @Test
  void legacyOnCompletePcmDispatchesOnCompletion() {
    var player = createPlayer("TestUser");
    engine.intercept(player, "/cmd <a:why> <!say done>");
    var result = engine.submit(player, "because");
    assertTrue(result.isPresent());
    engine.dispatchPCMs(player, result.get(), false);
    performTicks(20);
    assertEquals(1, captured.size());
    assertEquals("say done", captured.get(0).command());
  }

  @Test
  void legacyOnCancelPcmDispatchesOnCancellation() {
    var player = createPlayer("TestUser");
    engine.intercept(player, "/cmd <a:why> <!!log cancelled>");
    engine.cancel(player, CancelReason.MANUAL);
    performTicks(20);
    assertEquals(1, captured.size());
    assertEquals("log cancelled", captured.get(0).command());
  }

  // --- preset PCMs ---

  @Test
  void presetOnCompleteDispatchesOnSessionCompletion() {
    var def = new PostCommand(
            "log_reason",
            "discord broadcast {player} {input:1}",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            0);
    when(registry.getPostCommand("log_reason")).thenReturn(Optional.of(def));
    var player = createPlayer("TestUser");
    engine.intercept(player, "/cmd <a:why> <!@log_reason>");
    var result = engine.submit(player, "spamming");
    assertTrue(result.isPresent());

    engine.dispatchPCMs(player, result.get(), false);
    performTicks(20);
    assertEquals(1, captured.size());
    assertEquals("discord broadcast TestUser spamming", captured.get(0).command());
    // The console sender is Bukkit's ConsoleCommandSender, not the player.
    assertFalse(captured.get(0).sender() instanceof org.bukkit.entity.Player);
  }

  @Test
  void presetOnCancelDispatchesOnSessionCancellation() {
    var def = new PostCommand(
            "refund_fee",
            "eco give {player} 100",
            ExecutionPolicy.ON_CANCEL,
            ExecuteAs.CONSOLE,
            0);
    when(registry.getPostCommand("refund_fee")).thenReturn(Optional.of(def));
    var player = createPlayer("TestUser");
    engine.intercept(player, "/cmd <a:why> <!!@refund_fee>");
    engine.cancel(player, CancelReason.MANUAL);
    performTicks(20);
    assertEquals(1, captured.size());
    assertEquals("eco give TestUser 100", captured.get(0).command());
  }

  @Test
  void presetOnCompleteDoesNotFireOnCancellation() {
    // Preset says on_complete, parser hint was onCancel. Preset wins —
    // the command is NOT fired on cancel.
    var def = new PostCommand(
            "log_reason",
            "log {input:1}",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            0);
    when(registry.getPostCommand("log_reason")).thenReturn(Optional.of(def));
    var player = createPlayer("TestUser");
    engine.intercept(player, "/cmd <a:why> <!!@log_reason>");
    engine.cancel(player, CancelReason.MANUAL);
    performTicks(20);
    assertEquals(0, captured.size());
  }

  @Test
  void presetOnCancelDoesNotFireOnCompletion() {
    var def = new PostCommand(
            "refund",
            "eco give {player} 100",
            ExecutionPolicy.ON_CANCEL,
            ExecuteAs.CONSOLE,
            0);
    when(registry.getPostCommand("refund")).thenReturn(Optional.of(def));
    var player = createPlayer("TestUser");
    engine.intercept(player, "/cmd <a:why> <!@refund>");
    var result = engine.submit(player, "value");
    assertTrue(result.isPresent());
    engine.dispatchPCMs(player, result.get(), false);
    performTicks(20);
    assertEquals(0, captured.size());
  }

  @Test
  void presetDelayIsRespected() {
    var def = new PostCommand(
            "delayed",
            "say hello {player}",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            5);
    when(registry.getPostCommand("delayed")).thenReturn(Optional.of(def));
    var player = createPlayer("TestUser");
    engine.intercept(player, "/cmd <a:why> <!@delayed>");
    var result = engine.submit(player, "value");
    assertTrue(result.isPresent());

    engine.dispatchPCMs(player, result.get(), false);
    // Before 5 ticks: nothing.
    performTicks(4);
    assertEquals(0, captured.size());
    // After 5 ticks: dispatched.
    performTicks(1);
    assertEquals(1, captured.size());
    assertEquals("say hello TestUser", captured.get(0).command());
  }

  @Test
  void presetMultiInputPlaceholderResolvesAcrossAnswers() {
    var def = new PostCommand(
            "multi",
            "msg {input:1} and {input:2}",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            0);
    when(registry.getPostCommand("multi")).thenReturn(Optional.of(def));
    var player = createPlayer("TestUser");
    engine.intercept(player, "/cmd <a:first> <a:second> <!@multi>");
    engine.submit(player, "alpha");
    var result = engine.submit(player, "beta");
    assertTrue(result.isPresent());

    engine.dispatchPCMs(player, result.get(), false);
    performTicks(20);
    assertEquals(1, captured.size());
    assertEquals("msg alpha and beta", captured.get(0).command());
  }

  @Test
  void presetMissingAtDispatchTimeIsSkipped() {
    // The fail-fast check in PromptEngine.intercept blocks missing
    // presets from starting a session, so we cannot get here via
    // intercept + submit. But the PostCommandResolver also has a
    // defensive guard: if the registry changes between intercept and
    // dispatch (a hot reload), the dispatch must not NPE. We exercise
    // the defensive path by stubbing the registry to return a present
    // preset at intercept time and removing it at dispatch time.
    when(registry.getPostCommand("late_missing"))
            .thenReturn(Optional.of(new PostCommand(
                    "late_missing", "say gone",
                    ExecutionPolicy.ON_COMPLETE, ExecuteAs.CONSOLE, 0)))
            // Second call (during dispatch) returns empty.
            .thenReturn(Optional.empty());

    var player = createPlayer("TestUser");
    engine.intercept(player, "/cmd <a:why> <!@late_missing>");
    var result = engine.submit(player, "value");
    assertTrue(result.isPresent());

    engine.dispatchPCMs(player, result.get(), false);
    performTicks(20);
    assertEquals(0, captured.size(),
            "Missing presets at dispatch time must not cause a dispatch");
  }

  // --- PAPI integration ---

  @Test
  void papiPlaceholdersAreResolvedInPresetPostCommand() {
    var papi = mock(PapiHook.class);
    when(papi.setPlaceholder(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(inv -> ((String) inv.getArgument(1)).replace("%rank%", "VIP"));
    when(hookContainer.getHook(PapiHook.class)).thenReturn(Optional.of(papi));

    var def = new PostCommand(
            "promote",
            "promote {player} to %rank%",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            0);
    when(registry.getPostCommand("promote")).thenReturn(Optional.of(def));
    var player = createPlayer("TestUser");
    engine.intercept(player, "/cmd <a:why> <!@promote>");
    var result = engine.submit(player, "value");
    assertTrue(result.isPresent());

    engine.dispatchPCMs(player, result.get(), false);
    performTicks(20);
    assertEquals(1, captured.size());
    assertEquals("promote TestUser to VIP", captured.get(0).command());
  }

  // --- player-context dispatch ---

  @Test
  void presetWithPlayerExecuteAsRoutesThroughPlayerSender() {
    var def = new PostCommand(
            "ping",
            "msg hi from server",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.PLAYER,
            0);
    when(registry.getPostCommand("ping")).thenReturn(Optional.of(def));
    var player = createPlayer("TestUser");
    engine.intercept(player, "/cmd <a:why> <!@ping>");
    var result = engine.submit(player, "value");
    assertTrue(result.isPresent());

    engine.dispatchPCMs(player, result.get(), false);
    performTicks(20);
    assertEquals(1, captured.size());
    assertTrue(captured.get(0).sender() instanceof org.bukkit.entity.Player);
  }
}
