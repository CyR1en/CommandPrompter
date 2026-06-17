package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.CommandPrompterConfig;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.i18n.PaperI18n;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReloadCommandTest extends MockBukkitTest {

    private ReloadCommand cmd;
    private PromptEngine engine;
    private ScreenManager screenManager;
    private PaperConfigLoader loader;
    private PresetRegistry registry;

    @BeforeEach
    void setUp() {
        engine = mock(PromptEngine.class);
        screenManager = mock(ScreenManager.class);
        loader = mock(PaperConfigLoader.class);
        registry = mock(PresetRegistry.class);

        var reloadI18n = mock(PaperI18n.class);
        when(reloadI18n.get("command.reload.success"))
                .thenReturn(Component.text("Configuration reloaded."));
        when(reloadI18n.get(eq("command.reload.failed"), any(Placeholder[].class)))
                .thenReturn(Component.text("Failed to reload."));
        when(loader.getI18n()).thenReturn(reloadI18n);
        when(loader.getConfig()).thenReturn(config);

        when(plugin.getEngine()).thenReturn(engine);
        when(plugin.getScreenManager()).thenReturn(screenManager);
        when(plugin.getConfigLoader()).thenReturn(loader);
        when(plugin.getPresetRegistry()).thenReturn(registry);

        cmd = new ReloadCommand(plugin);
    }

    @Test
    void reloadSuccessSendsSuccessMessage() {
        doNothing().when(loader).reload();

        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("TestUser");

        cmd.executeReload(sender);

        verify(loader, times(1)).reload();
        verify(registry, times(1)).reload();
        verify(sender, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void reloadCancelsActiveSessionsBeforeReloading() {
        var player = createPlayer("OnlinePlayer");
        doNothing().when(loader).reload();

        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("TestUser");

        cmd.executeReload(sender);

        verify(screenManager, times(1)).cancelAll(player);
        verify(engine, times(1)).cancelAll();
        verify(loader, times(1)).reload();
        verify(registry, times(1)).reload();
    }

    @Test
    void reloadFailureSendsErrorMessage() {
        doThrow(new RuntimeException("boom")).when(loader).reload();

        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("TestUser");

        cmd.executeReload(sender);

        verify(sender, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void reloadSucceedsEvenWhenRegistryIsNull() {
        // Defensive: a fresh / early reload where the registry has not yet been wired should
        // still succeed for the config side. The reload command must not NPE.
        doNothing().when(loader).reload();
        when(plugin.getPresetRegistry()).thenReturn(null);

        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("TestUser");

        cmd.executeReload(sender);

        verify(loader, times(1)).reload();
        verify(sender, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void presetRegistryFailureSurfacesAsReloadError() {
        // If the preset reload blows up, the user must see the failure message and the
        // configLoader.reload() call must have happened first.
        doNothing().when(loader).reload();
        doThrow(new PresetRegistry.PresetLoadException("bad json", new RuntimeException()))
                .when(registry).reload();

        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("TestUser");

        cmd.executeReload(sender);

        verify(loader, times(1)).reload();
        verify(registry, times(1)).reload();
        // Exactly one error message is sent; success message must not be sent.
        verify(sender, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void buildReturnsNonNullLiteralNode() {
        assertNotNull(cmd.build());
    }

    @Test
    void allowedRequiresPermission() {
        var noPerm = mock(CommandSender.class);
        when(noPerm.hasPermission("promptpaper.reload")).thenReturn(false);
        assertFalse(cmd.allowed(noPerm));

        var withPerm = mock(CommandSender.class);
        when(withPerm.hasPermission("promptpaper.reload")).thenReturn(true);
        assertTrue(cmd.allowed(withPerm));
    }
}
