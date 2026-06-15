package dev.cyr1en.promptpaper.config;

import static org.junit.jupiter.api.Assertions.*;
import dev.cyr1en.promptpaper.MockBukkitTest;
import org.junit.jupiter.api.Test;

class ConfigurationManagerTest extends MockBukkitTest {

    @Test
    void configManagerCanBeConstructed() {
        var mgr = new ConfigurationManager(plugin);
        assertNotNull(mgr);
    }

    @Test
    void getConfigThrowsOnNonAnnotatedClass() {
        var mgr = new ConfigurationManager(plugin);
        assertThrows(IllegalArgumentException.class, () ->
                mgr.getConfig(String.class));
    }
}
