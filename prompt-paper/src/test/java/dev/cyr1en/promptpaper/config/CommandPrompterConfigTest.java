package dev.cyr1en.promptpaper.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.cyr1en.kiso.mc.configuration.base.Config;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class CommandPrompterConfigTest extends MockBukkitTest {

    @Test
    void getPermissionAttachmentReturnsConfiguredKeys() {
        var section = mock(ConfigurationSection.class);
        when(section.getKeys(false)).thenReturn(Set.of("GAMEMODE"));
        when(section.getStringList("GAMEMODE")).thenReturn(
                List.of("bukkit.command.gamemode", "essentials.gamemode.survival"));

        var rawConfig = mock(Config.class);
        when(rawConfig.getConfigurationSection("Permission-Attachment.Permissions"))
                .thenReturn(section);

        var cfg = new CommandPrompterConfig(
                rawConfig, "[Prompter] ", 300, "cancel", false, false,
                true, true, true, "<.*?>", List.of(), List.of(),
                true, 1, List.of("bukkit.command.gamemode"));

        var perms = cfg.getPermissionAttachment("GAMEMODE");
        assertArrayEquals(new String[]{
                "bukkit.command.gamemode", "essentials.gamemode.survival"}, perms);
    }

    @Test
    void getPermissionKeysReturnsAvailableKeys() {
        var section = mock(ConfigurationSection.class);
        when(section.getKeys(false)).thenReturn(Set.of("GAMEMODE", "FLY"));

        var rawConfig = mock(Config.class);
        when(rawConfig.getConfigurationSection("Permission-Attachment.Permissions"))
                .thenReturn(section);

        var cfg = new CommandPrompterConfig(
                rawConfig, "[Prompter] ", 300, "cancel", false, false,
                true, true, true, "<.*?>", List.of(), List.of(),
                true, 1, List.of());

        var keys = cfg.getPermissionKeys();
        assertTrue(keys.length >= 2);
        assertTrue(java.util.Arrays.asList(keys).contains("GAMEMODE"));
    }

    @Test
    void getPermissionKeysReturnsNoneWhenSectionMissing() {
        var rawConfig = mock(Config.class);
        when(rawConfig.getConfigurationSection("Permission-Attachment.Permissions"))
                .thenReturn(null);

        var cfg = new CommandPrompterConfig(
                rawConfig, "[Prompter] ", 300, "cancel", false, false,
                true, true, true, "<.*?>", List.of(), List.of(),
                true, 1, List.of());

        var keys = cfg.getPermissionKeys();
        assertArrayEquals(new String[]{"NONE"}, keys);
    }
}
