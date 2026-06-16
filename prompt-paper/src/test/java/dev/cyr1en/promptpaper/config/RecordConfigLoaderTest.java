package dev.cyr1en.promptpaper.config;

import dev.cyr1en.promptcore.config.RecordConfigLoader;
import static org.junit.jupiter.api.Assertions.*;
import dev.cyr1en.promptpaper.MockBukkitTest;
import org.junit.jupiter.api.Test;

class RecordConfigLoaderTest extends MockBukkitTest {

    @Test
    void configManagerCanBeConstructed() {
        var mgr = new RecordConfigLoader(plugin.getDataFolder());
        assertNotNull(mgr);
    }

    @Test
    void getConfigThrowsOnNonAnnotatedClass() {
        var mgr = new RecordConfigLoader(plugin.getDataFolder());
        assertThrows(IllegalArgumentException.class, () ->
                mgr.getConfig(String.class));
    }
}
