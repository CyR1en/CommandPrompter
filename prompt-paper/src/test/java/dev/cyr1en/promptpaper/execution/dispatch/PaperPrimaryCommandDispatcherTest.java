package dev.cyr1en.promptpaper.execution.dispatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PaperPrimaryCommandDispatcher Contract Tests")
class PaperPrimaryCommandDispatcherTest extends MockBukkitTest {

    private PaperPrimaryCommandDispatcher dispatcher;

    @BeforeEach
    void setupDispatcher() {
        dispatcher = new PaperPrimaryCommandDispatcher(plugin, scheduler);
    }

    private void registerMockCommand(String name, boolean succeed) {
        Command cmd = new Command(name) {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                return succeed;
            }
        };
        server.getCommandMap().register(name, cmd);
    }

    private void registerThrowingCommand(String name) {
        Command cmd = new Command(name) {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                throw new IllegalStateException("Command error for " + name);
            }
        };
        server.getCommandMap().register(name, cmd);
    }

    @Test
    @DisplayName("PLAYER mode: executes command as player and invokes callback once with success")
    void dispatchesPlayerCommandSuccessfully() {
        var player = createPlayer("TestPlayer");
        registerMockCommand("testplayerok", true);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var callbackCount = new AtomicInteger(0);

        var request = PrimaryDispatchRequest.player(player, "/testplayerok arg1");
        dispatcher.dispatch(request, outcome -> {
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
        dispatcher.dispatch(request, outcome -> {
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
        dispatcher.dispatch(request, outcome -> {
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
    @DisplayName("CONSOLE mode: dispatches on scheduler as console sender and returns outcome to initiator")
    void dispatchesConsoleCommandSuccessfully() {
        var player = createPlayer("TestPlayer");
        registerMockCommand("testconsoleok", true);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var callbackCount = new AtomicInteger(0);

        var request = PrimaryDispatchRequest.console(player, "/testconsoleok");
        dispatcher.dispatch(request, outcome -> {
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
        dispatcher.dispatch(request, outcome -> {
            callbackCount.incrementAndGet();
            outcomeRef.set(outcome);
        });

        assertEquals(1, callbackCount.get());
        assertNotNull(outcomeRef.get());
        assertTrue(outcomeRef.get().isFailure());
        assertEquals(DispatchErrorKind.DISPATCH_RETURNED_FALSE, outcomeRef.get().error().kind());
    }

    @Test
    @DisplayName("ATTACHMENT mode: applies permissions, dispatches, and cleans up immediately when ticks=0")
    void dispatchesAttachmentCommandZeroTicksCleanup() {
        var player = createPlayer("AttachPlayer");
        var permKey = "test.perm";
        assertFalse(player.hasPermission(permKey));

        Command checkingCmd = new Command("checkperm") {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                return sender.hasPermission(permKey);
            }
        };
        server.getCommandMap().register("checkperm", checkingCmd);

        var context = PermissionAttachmentContext.of("KEY", List.of(permKey));
        var request = PrimaryDispatchRequest.attachment(player, "/checkperm", context);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var callbackCount = new AtomicInteger(0);

        dispatcher.dispatch(request, outcome -> {
            callbackCount.incrementAndGet();
            outcomeRef.set(outcome);
        });

        assertEquals(1, callbackCount.get());
        assertNotNull(outcomeRef.get());
        assertTrue(outcomeRef.get().isSuccess(), "Dispatch should succeed because permission was attached");
        assertFalse(player.hasPermission(permKey), "Permission attachment must be removed immediately when lifetime is 0");
    }

    @Test
    @DisplayName("ATTACHMENT mode: ignores legacy delay and removes permissions before completion")
    void dispatchesAttachmentCommandIgnoresLegacyDelay() {
        var player = createPlayer("AttachPlayerDelayed");
        var permKey = "test.delayed.perm";
        assertFalse(player.hasPermission(permKey));

        Command checkingCmd = new Command("checkdelayed") {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                return sender.hasPermission(permKey);
            }
        };
        server.getCommandMap().register("checkdelayed", checkingCmd);

        var context = PermissionAttachmentContext.of("KEY", List.of(permKey));
        var request = PrimaryDispatchRequest.attachment(player, "checkdelayed", context);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var callbackCount = new AtomicInteger(0);

        dispatcher.dispatch(request, outcome -> {
            callbackCount.incrementAndGet();
            outcomeRef.set(outcome);
        });

        assertEquals(1, callbackCount.get());
        assertTrue(outcomeRef.get().isSuccess());
        assertFalse(player.hasPermission(permKey),
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
        assertFalse(player.hasPermission(permKey), "Permission attachment must be removed immediately on dispatch false");
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
        assertFalse(player.hasPermission(permKey), "Permission attachment must be removed immediately on exception");
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

        PlayerExecutor retiredExecutor = (task, retired) -> {
            if (retired != null) {
                retired.run();
            }
        };

        var request = PrimaryDispatchRequest.player(player, "retiredcmd", retiredExecutor);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var callbackCount = new AtomicInteger(0);

        dispatcher.dispatch(request, outcome -> {
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
