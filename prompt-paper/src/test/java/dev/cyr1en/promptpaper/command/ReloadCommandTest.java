package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.i18n.PaperI18n;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import net.kyori.adventure.text.Component;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
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
        when(engine.beginReload()).thenReturn(true);

        cmd = new ReloadCommand(plugin);
    }

    @Test
    void reloadSuccessSendsSuccessMessage() {
        doNothing().when(loader).reload();

        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("TestUser");

        cmd.executeReload(sender);

        verify(loader, times(1)).reload();
        verify(engine, times(1)).reloadParser();
        verify(registry, times(1)).reload();
        verify(sender, times(1)).sendMessage(any(Component.class));
        verify(engine, times(1)).endReload();
    }

    @Test
    void reloadCancelsActiveSessionsBeforeReloading() {
        var player = createPlayer("OnlinePlayer");
        doNothing().when(loader).reload();

        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("TestUser");

        cmd.executeReload(sender);

        verify(screenManager, times(1)).cancelAll(player);
        verify(engine, times(1)).discardAll();
        verify(loader, times(1)).reload();
        verify(engine, times(1)).reloadParser();
        verify(registry, times(1)).reload();
        verify(engine, times(1)).endReload();
    }

    @Test
    void reloadFailureSendsErrorMessage() {
        doThrow(new RuntimeException("boom")).when(loader).reload();

        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("TestUser");

        cmd.executeReload(sender);

        verify(sender, times(1)).sendMessage(any(Component.class));
        verify(engine, never()).reloadParser();
        verify(engine, times(1)).endReload();
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
        verify(engine, times(1)).reloadParser();
        verify(sender, times(1)).sendMessage(any(Component.class));
        verify(engine, times(1)).endReload();
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
        verify(engine, times(1)).reloadParser();
        verify(registry, times(1)).reload();
        // Exactly one error message is sent; success message must not be sent.
        verify(sender, times(1)).sendMessage(any(Component.class));
        verify(engine, times(1)).endReload();
    }

    @Test
    void reloadRequestIsRejectedWhenAnotherBarrierIsActive() {
        when(engine.beginReload()).thenReturn(false);
        when(engine.isReloadInProgress()).thenReturn(true);
        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("TestUser");

        cmd.executeReload(sender);

        verify(loader, never()).reload();
        verify(engine, never()).endReload();
        verify(sender, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void realReloadGateClearsAfterFailure() {
        var realEngine = new PromptEngine(plugin, scheduler);
        when(plugin.getEngine()).thenReturn(realEngine);
        when(plugin.getScreenManager()).thenReturn(null);
        doThrow(new RuntimeException("boom")).when(loader).reload();
        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("TestUser");

        cmd.executeReload(sender);

        assertFalse(realEngine.isReloadInProgress());
    }

    @Test
    void realReloadGateClearsAfterSuccess() {
        var realEngine = new PromptEngine(plugin, scheduler);
        when(plugin.getEngine()).thenReturn(realEngine);
        when(plugin.getScreenManager()).thenReturn(null);
        doNothing().when(loader).reload();
        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("TestUser");

        cmd.executeReload(sender);

        assertFalse(realEngine.isReloadInProgress());
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

    @Test
    void nonregionalFeedbackUsesGlobalScheduler() {
        var globalScheduler = mock(dev.cyr1en.promptpaper.util.Scheduler.class);
        when(plugin.getScheduler()).thenReturn(globalScheduler);
        var sender = mock(CommandSender.class);
        var message = Component.text("reload result");
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(globalScheduler).runSync(any(Runnable.class));

        cmd.sendResult(sender, null, message);

        verify(globalScheduler, times(1)).runSync(any(Runnable.class));
        verify(sender, times(1)).sendMessage(message);
    }

    @Test
    void blockFeedbackUsesTheBlockRegionScheduler() {
        var regionScheduler = mock(RegionScheduler.class);
        var sender = mock(BlockCommandSender.class);
        var location = new Location(null, 10, 64, 10);
        var message = Component.text("reload result");
        when(regionScheduler.run(eq(plugin), eq(location), any()))
                .thenReturn(mock(ScheduledTask.class));

        try (var mockedBukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            mockedBukkit.when(Bukkit::getRegionScheduler).thenReturn(regionScheduler);

            cmd.sendResult(sender, location, message);
        }

        verify(regionScheduler, times(1)).run(eq(plugin), eq(location), any());
        verify(sender, never()).sendMessage(message);
    }
}
