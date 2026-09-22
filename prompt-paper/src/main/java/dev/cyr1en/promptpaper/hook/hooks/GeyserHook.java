package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import dev.cyr1en.promptpaper.hook.geyser.BedrockAnvilItems;
import dev.cyr1en.promptpaper.hook.geyser.GeyserAnvilPatch;
import dev.cyr1en.promptui.AnvilItemPresentation;
import java.util.logging.Level;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

@TargetPlugin(pluginName = "Geyser-Spigot")
public final class GeyserHook extends BaseHook implements Listener {
  private volatile Runnable restore;
  private volatile BedrockAnvilItems items;

  public GeyserHook(CommandPrompter plugin) {
    super(plugin);
  }

  public boolean isAnvilPatchEnabled() {
    return restore != null;
  }

  public AnvilItemPresentation itemPresentation() {
    var current = items;
    if (current == null || !current.isReady()) {
      throw new IllegalStateException("Bedrock anvil item definitions are unavailable");
    }
    return current;
  }

  @Override
  public void onEnable() {
    if (!getPlugin().getConfigLoader().getConfig().geyserAnvilPatch()) return;
    try {
      // Subscribe before Geyser's NORMAL ServerLoad listener initializes custom definitions.
      items = BedrockAnvilItems.subscribe(getPlugin());
    } catch (Exception | LinkageError e) {
      getPlugin()
          .getLogger()
          .log(
              Level.WARNING,
              "Geyser-Anvil-Patch could not prepare custom items; using the Bedrock fallback",
              e);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onServerLoaded(ServerLoadEvent event) {
    // Geyser initializes its registries in a NORMAL-priority ServerLoadEvent listener.
    if (items == null || restore != null) return;
    try {
      items.completeRegistration();
      if (!items.isReady()) {
        throw new IllegalStateException(
            "Geyser custom anvil items were not registered. Enable Geyser gameplay.enable-custom-content and fully restart the server.");
      }
      restore = GeyserAnvilPatch.install();
      getPlugin()
          .getPluginLogger()
          .info("Geyser-Anvil-Patch enabled with custom prompt items (runtime only).");
    } catch (Exception | LinkageError e) {
      getPlugin()
          .getLogger()
          .log(
              Level.WARNING,
              "Geyser-Anvil-Patch could not be applied; using the Bedrock fallback",
              e);
    }
  }

  @Override
  public void onDisable() {
    if (restore != null) {
      restore.run();
      restore = null;
    }
    if (items != null) {
      items.close();
      items = null;
    }
  }
}
