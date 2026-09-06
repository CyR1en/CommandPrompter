package dev.cyr1en.promptpaper.listener;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

/**
 * Listens for third-party plugin disable events and coordinates graceful custom screen teardown.
 *
 * <p>Intentionally avoids calling any provider methods from within the listener. Ignores
 * CommandPrompter's own disable event, which is handled by its dedicated shutdown lifecycle.
 */
public class PluginDisableListener implements Listener {

  private final CommandPrompter plugin;
  private final ScreenManager screenManager;

  public PluginDisableListener(CommandPrompter plugin, ScreenManager screenManager) {
    this.plugin = plugin;
    this.screenManager = screenManager;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPluginDisable(PluginDisableEvent event) {
    Plugin disabledPlugin = event.getPlugin();
    if (disabledPlugin == null || disabledPlugin.equals(plugin)) {
      return;
    }
    if (screenManager != null && screenManager.getProviderLifecycleCoordinator() != null) {
      screenManager.getProviderLifecycleCoordinator().onProviderDisable(disabledPlugin);
    }
  }
}
