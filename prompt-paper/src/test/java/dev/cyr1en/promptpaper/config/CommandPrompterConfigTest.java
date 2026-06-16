package dev.cyr1en.promptpaper.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommandPrompterConfigTest extends MockBukkitTest {

    @Test
    void getPermissionAttachmentReturnsConfiguredKeys() {
        var rawConfig = mock(YamlDocument.class);
        when(rawConfig.getKeys("Permission-Attachment.Permissions")).thenReturn(Set.of("GAMEMODE"));
        org.mockito.Mockito.doReturn(List.of("bukkit.command.gamemode", "essentials.gamemode.survival")).when(rawConfig).getList("Permission-Attachment.Permissions.GAMEMODE");

        var cfg = new CommandPrompterConfig(
                rawConfig, "[Prompter] ", 300, "cancel", false, false,
                true, true, true, "<.*?>", List.of(), List.of(),
                true, 1, List.of("bukkit.command.gamemode"), "en_US");

        var perms = cfg.getPermissionAttachment("GAMEMODE");
        assertArrayEquals(new String[]{
                "bukkit.command.gamemode", "essentials.gamemode.survival"}, perms);
    }

    @Test
    void getPermissionKeysReturnsAvailableKeys() {
        var rawConfig = mock(YamlDocument.class);
        when(rawConfig.getKeys("Permission-Attachment.Permissions")).thenReturn(Set.of("GAMEMODE", "FLY"));

        var cfg = new CommandPrompterConfig(
                rawConfig, "[Prompter] ", 300, "cancel", false, false,
                true, true, true, "<.*?>", List.of(), List.of(),
                true, 1, List.of(), "en_US");

        var keys = cfg.getPermissionKeys();
        assertTrue(keys.length >= 2);
        assertTrue(java.util.Arrays.asList(keys).contains("GAMEMODE"));
    }

    @Test
    void getPermissionKeysReturnsNoneWhenSectionMissing() {
        var rawConfig = mock(YamlDocument.class);
        when(rawConfig.getKeys("Permission-Attachment.Permissions")).thenReturn(Set.of());

        var cfg = new CommandPrompterConfig(
                rawConfig, "[Prompter] ", 300, "cancel", false, false,
                true, true, true, "<.*?>", List.of(), List.of(),
                true, 1, List.of(), "en_US");

        var keys = cfg.getPermissionKeys();
        assertArrayEquals(new String[]{"NONE"}, keys);
    }
}
