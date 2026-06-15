package dev.cyr1en.promptpaper.screen.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class DialogCompletionContextTest {

    @Test
    void contextWithPlayerAndNonEmptyPartialHasCompletions() {
        var ctx = new DialogCompletionContext(mock(Player.class), "/cmd ");
        assertTrue(ctx.hasCompletions());
    }

    @Test
    void contextMissingPlayerHasNoCompletions() {
        var ctx = new DialogCompletionContext(null, "/cmd ");
        assertFalse(ctx.hasCompletions());
    }

    @Test
    void contextWithEmptyPartialHasNoCompletions() {
        var ctx = new DialogCompletionContext(mock(Player.class), "");
        assertFalse(ctx.hasCompletions());
    }

    @Test
    void contextWithNullPartialHasNoCompletions() {
        var ctx = new DialogCompletionContext(mock(Player.class), null);
        assertFalse(ctx.hasCompletions());
    }

    @Test
    void contextWithBothNullsHasNoCompletions() {
        assertFalse(new DialogCompletionContext(null, null).hasCompletions());
    }

    @Test
    void contextExposesPlayerAndPartial() {
        var player = mock(Player.class);
        var ctx = new DialogCompletionContext(player, "/give notch ");
        assertTrue(ctx.player() == player);
        assertTrue(ctx.partialCommand().equals("/give notch "));
    }

    @Test
    void recordsWithSameFieldsAreEqual() {
        var player = mock(Player.class);
        var a = new DialogCompletionContext(player, "/cmd ");
        var b = new DialogCompletionContext(player, "/cmd ");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        var c = new DialogCompletionContext(player, "/other ");
        assertNotEquals(a, c);
    }
}
