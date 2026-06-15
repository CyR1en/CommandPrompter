package dev.cyr1en.promptpaper.validation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import dev.cyr1en.promptpaper.MockBukkitTest;

class OnlinePlayerValidatorTest extends MockBukkitTest {

    @Test
    void onlinePlayerPasses() {
        createPlayer("Notch");
        var v = new OnlinePlayerValidator("op", "Player not online", null);
        assertTrue(v.validate("Notch"));
    }

    @Test
    void offlinePlayerFails() {
        var v = new OnlinePlayerValidator("op", "Player not online", null);
        assertFalse(v.validate("NobodyIsOnlineWithThisNameXYZ"));
    }

    @Test
    void nullInputReturnsFalse() {
        var v = new OnlinePlayerValidator("op", "msg", null);
        assertFalse(v.validate(null));
    }

    @Test
    void blankInputReturnsFalse() {
        var v = new OnlinePlayerValidator("op", "msg", null);
        assertFalse(v.validate(""));
        assertFalse(v.validate("   "));
    }

    @Test
    void aliasReturnsConfiguredValue() {
        var v = new OnlinePlayerValidator("myAlias", "msg", null);
        assertEquals("myAlias", v.alias());
    }

    @Test
    void inputPlayerReturnsConfiguredValue() {
        var player = createPlayer("Viewer");
        var v = new OnlinePlayerValidator("op", "msg", player);
        assertEquals(player, v.inputPlayer());
    }
}
