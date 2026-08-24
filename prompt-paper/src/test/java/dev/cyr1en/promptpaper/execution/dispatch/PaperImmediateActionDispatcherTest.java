package dev.cyr1en.promptpaper.execution.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PaperImmediateActionDispatcher Contract Tests")
class PaperImmediateActionDispatcherTest extends MockBukkitTest {

    private PaperImmediateActionDispatcher dispatcher;

    @BeforeEach
    void setupDispatcher() {
        dispatcher = new PaperImmediateActionDispatcher(plugin, scheduler);
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
    @DisplayName("PLAYER action: executes as player and invokes callback once with success")
    void dispatchesPlayerActionSuccessfully() {
        var player = createPlayer("PlayerAction");
        registerMockCommand("actplayerok", true);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var count = new AtomicInteger(0);

        var request = ImmediateActionRequest.player(player, "/actplayerok");
        dispatcher.dispatch(request, outcome -> {
            count.incrementAndGet();
            outcomeRef.set(outcome);
        });

        assertEquals(1, count.get());
        assertNotNull(outcomeRef.get());
        assertTrue(outcomeRef.get().isSuccess());
    }

    @Test
    @DisplayName("CONSOLE action: allows trusted preset when player holds promptpaper.pcm.console")
    void allowsTrustedPresetForAuthorizedPlayer() {
        var player = createPlayer("ConsoleAuthorized");
        player.addAttachment(plugin, "promptpaper.pcm.console", true);
        registerMockCommand("actconsoleok", true);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var count = new AtomicInteger(0);

        var request = ImmediateActionRequest.trustedPreset(player, "/actconsoleok", ExecuteAs.CONSOLE, "preset1");
        dispatcher.dispatch(request, outcome -> {
            count.incrementAndGet();
            outcomeRef.set(outcome);
        });

        assertEquals(1, count.get());
        assertNotNull(outcomeRef.get());
        assertTrue(outcomeRef.get().isSuccess());
    }

    @Test
    @DisplayName("CONSOLE action: allows trusted preset when player is op")
    void allowsTrustedPresetForOp() {
        var player = createPlayer("OpPlayer");
        player.setOp(true);
        registerMockCommand("actconsoleop", true);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var request = ImmediateActionRequest.trustedPreset(player, "actconsoleop", ExecuteAs.CONSOLE, "preset2");

        dispatcher.dispatch(request, outcomeRef::set);

        assertNotNull(outcomeRef.get());
        assertTrue(outcomeRef.get().isSuccess());
    }

    @Test
    @DisplayName("CONSOLE action: allows untrusted action when provenance is console-delegated")
    void allowsUntrustedActionWhenConsoleDelegated() {
        var player = createPlayer("DelegatedPlayer");
        assertFalse(player.isOp());
        registerMockCommand("actdelegatedok", true);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var request = ImmediateActionRequest.consoleDelegated(player, "/actdelegatedok", "delegated1");

        dispatcher.dispatch(request, outcomeRef::set);

        assertNotNull(outcomeRef.get());
        assertTrue(outcomeRef.get().isSuccess());
    }

    @Test
    @DisplayName("CONSOLE action: refuses untrusted inline action without delegation")
    void refusesUntrustedInlineActionAsConsole() {
        var player = createPlayer("UntrustedPlayer");
        player.setOp(true); // even if player is op, untrusted inline cannot elevate to console without delegation
        registerMockCommand("actuntrusted", true);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var request = new ImmediateActionRequest(
                player,
                "actuntrusted",
                ExecuteAs.CONSOLE,
                ActionProvenance.untrustedInline(false),
                null,
                null
        );

        dispatcher.dispatch(request, outcomeRef::set);

        assertNotNull(outcomeRef.get());
        assertTrue(outcomeRef.get().isFailure());
        assertEquals(DispatchErrorKind.UNAUTHORIZED_CONSOLE, outcomeRef.get().error().kind());
    }

    @Test
    @DisplayName("CONSOLE action: refuses trusted preset when player lacks permission")
    void refusesTrustedPresetWhenPlayerLacksPermission() {
        var player = createPlayer("UnprivilegedPlayer");
        assertFalse(player.isOp());
        registerMockCommand("actrefused", true);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var request = ImmediateActionRequest.trustedPreset(player, "actrefused", ExecuteAs.CONSOLE, "preset_denied");

        dispatcher.dispatch(request, outcomeRef::set);

        assertNotNull(outcomeRef.get());
        assertTrue(outcomeRef.get().isFailure());
        assertEquals(DispatchErrorKind.UNAUTHORIZED_CONSOLE, outcomeRef.get().error().kind());
    }

    @Test
    @DisplayName("PLAYER action: returns typed failure on false dispatch")
    void returnsFailureOnFalseDispatch() {
        var player = createPlayer("FailPlayer");
        registerMockCommand("actfail", false);

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var request = ImmediateActionRequest.player(player, "actfail");

        dispatcher.dispatch(request, outcomeRef::set);

        assertNotNull(outcomeRef.get());
        assertTrue(outcomeRef.get().isFailure());
        assertEquals(DispatchErrorKind.DISPATCH_RETURNED_FALSE, outcomeRef.get().error().kind());
    }

    @Test
    @DisplayName("PLAYER action: returns typed failure on exception")
    void returnsFailureOnException() {
        var player = createPlayer("ThrowPlayer");
        registerThrowingCommand("actthrow");

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var request = ImmediateActionRequest.player(player, "actthrow");

        dispatcher.dispatch(request, outcomeRef::set);

        assertNotNull(outcomeRef.get());
        assertTrue(outcomeRef.get().isFailure());
        assertEquals(DispatchErrorKind.EXCEPTION_THROWN, outcomeRef.get().error().kind());
    }

    @Test
    @DisplayName("Handles PlayerExecutor retirement gracefully")
    void handlesExecutorRetirement() {
        var player = createPlayer("RetiredActionPlayer");
        registerMockCommand("actretire", true);

        PlayerExecutor retiredExecutor = (task, retired) -> {
            if (retired != null) {
                retired.run();
            }
        };

        var request = new ImmediateActionRequest(
                player,
                "actretire",
                ExecuteAs.PLAYER,
                ActionProvenance.untrustedInline(),
                retiredExecutor,
                null
        );

        var outcomeRef = new AtomicReference<DispatchOutcome>();
        var count = new AtomicInteger(0);

        dispatcher.dispatch(request, outcome -> {
            count.incrementAndGet();
            outcomeRef.set(outcome);
        });

        assertEquals(1, count.get());
        assertNotNull(outcomeRef.get());
        assertTrue(outcomeRef.get().isFailure());
        assertEquals(DispatchErrorKind.SCHEDULER_RETIRED, outcomeRef.get().error().kind());
    }
}
