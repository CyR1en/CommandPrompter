package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import dev.cyr1en.promptpaper.hook.geyser.GeyserAnvilPatch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

@TargetPlugin(pluginName = "Geyser-Spigot")
public final class GeyserHook extends BaseHook implements Listener {
  private Runnable restore;
  private boolean enabledAtStartup;

  public GeyserHook(CommandPrompter plugin) {
    super(plugin);
  }

  public boolean isAnvilPatchEnabled() {
    return restore != null;
  }

  @Override
  public void onEnable() {
    enabledAtStartup = getPlugin().getConfigLoader().getConfig().geyserAnvilPatch();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onServerLoaded(ServerLoadEvent event) {
    // Geyser initializes its registries in a NORMAL-priority ServerLoadEvent listener.
    if (!enabledAtStartup || restore != null) return;
    try {
      restore = GeyserAnvilPatch.install();
      getPlugin()
          .getPluginLogger()
          .info("Geyser-Anvil-Patch enabled for Geyser 2.11.3 build 1245 (runtime only).");
    } catch (Exception | LinkageError e) {
      getPlugin()
          .getLogger()
          .log(java.util.logging.Level.SEVERE, "Geyser-Anvil-Patch was not applied", e);
    }
  }

  @Override
  public void onDisable() {
    if (restore != null) {
      restore.run();
      restore = null;
    }
  }
}
