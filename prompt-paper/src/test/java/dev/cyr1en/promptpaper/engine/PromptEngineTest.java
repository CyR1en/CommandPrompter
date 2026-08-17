package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PromptEngineTest extends MockBukkitTest {

    @Test
    void interceptWithPromptsReturnsParsedCommand() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        var result = engine.intercept(player, "/cmd <name> please");
        assertTrue(result.isPresent());
        assertTrue(result.get().hasPrompts());
    }

    @Test
    void interceptWithoutPromptsReturnsEmpty() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        var result = engine.intercept(player, "/cmd no prompts");
        assertFalse(result.isPresent());
    }

    @Test
    void submitWithoutSessionReturnsEmpty() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        var result = engine.submit(player, "answer");
        assertTrue(result.isEmpty());
    }

    @Test
    void submitWithSinglePromptReturnsResult() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        engine.intercept(player, "/cmd <test> please");
        var result = engine.submit(player, "myAnswer");
        assertTrue(result.isPresent());
        assertEquals("/cmd myAnswer please", result.get().assembledCommand());
    }

    @Test
    void submitWithMultiplePromptsReturnsEmptyUntilLast() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        engine.intercept(player, "/cmd <first> and <second>");
        assertTrue(engine.submit(player, "one").isEmpty());
        assertTrue(engine.submit(player, "two").isPresent());
    }

    @Test
    void cancelRemovesActiveSession() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        engine.intercept(player, "/cmd <test> please");
        assertTrue(engine.hasActiveSession(player));
        engine.cancel(player, CancelReason.MANUAL);
        assertFalse(engine.hasActiveSession(player));
    }

    @Test
    void hasActiveSessionReturnsFalseForNoSession() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        assertFalse(engine.hasActiveSession(player));
    }

    @Test
    void getSessionReturnsSessionForActive() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        engine.intercept(player, "/cmd <test>");
        assertTrue(engine.getSession(player).isPresent());
    }

    @Test
    void getSessionReturnsEmptyForInactive() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        assertTrue(engine.getSession(player).isEmpty());
    }

    @Test
    void interceptWithEmptyStringCreatesNoSession() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        var result = engine.intercept(player, "");
        assertFalse(result.isPresent());
    }

    @Test
    void interceptSkipsParseWhenEnablePermissionTrueAndPlayerLacksPromptPaperUse() {
        when(config.enablePermission()).thenReturn(true);
        var engine = new PromptEngine(plugin, scheduler);
        var player = mock(Player.class);
        var uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission("promptpaper.use")).thenReturn(false);

        var result = engine.intercept(player, "/cmd <name> please");

        assertTrue(result.isEmpty());
        assertFalse(engine.hasActiveSession(player));
    }

    @Test
    void interceptProceedsWhenEnablePermissionTrueAndPlayerHasPromptPaperUse() {
        when(config.enablePermission()).thenReturn(true);
        var engine = new PromptEngine(plugin, scheduler);
        var player = mock(Player.class);
        var uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission("promptpaper.use")).thenReturn(true);

        var result = engine.intercept(player, "/cmd <name> please");

        assertTrue(result.isPresent());
        assertTrue(engine.hasActiveSession(player));
    }

    @Test
    void reloadGateRejectsSessionsUntilItIsReleased() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();

        assertTrue(engine.beginReload());
        assertTrue(engine.isReloadInProgress());
        assertTrue(engine.intercept(player, "/cmd <name>").isEmpty());
        assertFalse(engine.hasActiveSession(player));

        engine.endReload();
        assertFalse(engine.isReloadInProgress());
        assertTrue(engine.intercept(player, "/cmd <name>").isPresent());
        assertTrue(engine.hasActiveSession(player));
    }

    /**
     * Issue #99 integration proof: the reload-gate feedback is sent directly to
     * the player, so it must be formatted with that player as the i18n context.
     * The mocked i18n answers differently depending on whether the player
     * argument is supplied; the recipient must see the player-context result.
     */
    @Test
    void reloadGateRejectionUsesPlayerContext() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        when(i18n.get(eq("command.reload.failed"), any(Placeholder[].class)))
                .thenReturn(Component.text("context-free"));
        when(i18n.get(eq("command.reload.failed"), same(player), any(Placeholder[].class)))
                .thenReturn(Component.text("context-aware"));

        assertTrue(engine.beginReload());
        assertTrue(engine.intercept(player, "/cmd <name>").isEmpty());

        assertEquals("context-aware", player.nextMessage(),
                "the player must see the player-context formatted message");
        verify(i18n).get(eq("command.reload.failed"), same(player), any(Placeholder[].class));
        engine.endReload();
    }

    /**
     * Issue #99: an active-session rejection message is sent directly to the
     * player, so it must be formatted with that player as the i18n context.
     */
    @Test
    void activeSessionRejectionUsesPlayerContext() {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        when(i18n.get("prompt.error.session_active"))
                .thenReturn(Component.text("context-free"));
        when(i18n.get(eq("prompt.error.session_active"), same(player)))
                .thenReturn(Component.text("context-aware"));

        engine.intercept(player, "/cmd <first>");
        assertTrue(engine.hasActiveSession(player));

        assertTrue(engine.intercept(player, "/cmd <second>").isEmpty());

        assertEquals("context-aware", player.nextMessage(),
                "the player must see the player-context formatted message");
        verify(i18n).get(eq("prompt.error.session_active"), same(player));
    }

    @Test
    void concurrentSubmitAndCancelCompleteAtMostOneTransition() throws Exception {
        var engine = new PromptEngine(plugin, scheduler);
        var player = createPlayer();
        engine.intercept(player, "/cmd <name>");

        var start = new CountDownLatch(1);
        var completed = new AtomicInteger();
        var submitter = new Thread(() -> {
            await(start);
            if (engine.submit(player, "value").isPresent()) completed.incrementAndGet();
        });
        var canceller = new Thread(() -> {
            await(start);
            engine.cancel(player, CancelReason.MANUAL);
        });
        submitter.start();
        canceller.start();
        start.countDown();
        submitter.join();
        canceller.join();

        assertTrue(completed.get() <= 1);
        assertFalse(engine.hasActiveSession(player));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
