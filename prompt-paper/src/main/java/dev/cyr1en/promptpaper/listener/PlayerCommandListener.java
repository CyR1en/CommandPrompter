package dev.cyr1en.promptpaper.listener;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.engine.InterceptResult;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import java.util.Locale;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Intercepts player commands at {@link EventPriority#LOWEST} to detect prompt tags in the command
 * line. If the command contains prompts, it starts a session via {@link ScreenManager} and cancels
 * the original command event so the raw command is not dispatched until all prompts are answered.
 * Commands in the {@code ignored-commands} config list and the plugin's own commands are excluded
 * from interception.
 *
 * <h2>Fail-fast cancel</h2>
 *
 * <p>When a command contains a tag form and the intercept results in {@link
 * InterceptResult.Started} or {@link InterceptResult.RejectedFailClosed}, the event is
 * <b>unconditionally cancelled</b>. This keeps literal tags, unresolved screen keys, missing
 * presets, and malformed command lines out of the underlying command dispatcher.
 */
public class PlayerCommandListener implements Listener {

  private final CommandPrompter plugin;
  private final ScreenManager screenManager;
  private final PromptEngine engine;

  public PlayerCommandListener(CommandPrompter plugin, ScreenManager screenManager) {
    this(plugin, screenManager, plugin.getEngine());
  }

  public PlayerCommandListener(
      CommandPrompter plugin, ScreenManager screenManager, PromptEngine engine) {
    this.plugin = plugin;
    this.screenManager = screenManager;
    this.engine = engine;
  }

  /**
   * Checks incoming player commands for prompt tags. If the command has active prompts, starts a
   * session and cancels the event. Players with an active screen cannot run commands not in the
   * {@code allowed-while-in-prompt} list.
   */
  @EventHandler(priority = EventPriority.LOWEST)
  public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
    if (event.isCancelled()) {
      plugin.getPluginLogger().debug("Command already cancelled, skipping");
      return;
    }
    var player = event.getPlayer();
    var message = event.getMessage();
    var commandName =
        (message.startsWith("/") ? message.substring(1) : message)
            .split(" ", 2)[0].toLowerCase(Locale.ROOT);
    var hasActiveScreen = screenManager.hasActiveScreen(player);

    plugin
        .getPluginLogger()
        .debug(
            "Command received: player="
                + player.getName()
                + " cmd="
                + commandName
                + " hasScreen="
                + hasActiveScreen);

    var pluginCommand =
        commandName.startsWith("commandprompterpaper:")
            ? commandName.substring("commandprompterpaper:".length())
            : commandName;
    if (pluginCommand.equals("commandprompter")
        || pluginCommand.equals("cmdp")
        || pluginCommand.equals("commandprompter:response")) {
      plugin.getPluginLogger().debug("Command is plugin command, not intercepting");
      return;
    }

    var config = plugin.getConfigLoader().getConfig();
    var commandLine = message.startsWith("/") ? message.substring(1) : message;
    if (engine != null && engine.isReloadInProgress() && engine.commandHasTagForm(commandLine)) {
      // Do not let a tagged command fall through while ReloadCommand is still tearing down
      // other players. The engine also supplies localized feedback for the rejected session.
      event.setCancelled(true);
      engine.rejectIfReloading(player);
      return;
    }
    if (config.ignoredCommands().stream().anyMatch(c -> c.equalsIgnoreCase(commandName))) {
      plugin.getPluginLogger().debug("Command is in ignored-commands list, not intercepting");
      return;
    }
    if (hasActiveScreen
        && config.allowedWhileInPrompt().stream().noneMatch(c -> c.equalsIgnoreCase(commandName))) {
      plugin.getPluginLogger().debug("Player has active screen, cancelling event");
      event.setCancelled(true);
    }

    // Cancel the event if a session starts or the command references a preset or fail-closed tag.
    if (engine != null && engine.commandHasTagForm(commandLine)) {
      var allowedToUse = !config.enablePermission() || player.hasPermission("promptpaper.use");
      if (allowedToUse) {
        try {
          screenManager.startSession(player, commandLine);
        } catch (Throwable e) {
          screenManager.discardState(player.getUniqueId());
          plugin
              .getPluginLogger()
              .err("Prompt screen failed for " + player.getName() + ": " + e.getMessage());
          event.setCancelled(true);
          return;
        }
        var lastResultOpt = engine.lastInterceptResult(player);
        var lastResult = lastResultOpt != null ? lastResultOpt.orElse(null) : null;
        boolean shouldCancel =
            screenManager.hasActiveScreen(player)
                || engine.hasPresetReferences(commandLine)
                || engine.hasStructuralParseError(commandLine)
                || engine.isReloadInProgress()
                || (lastResult != null
                    && (lastResult.isStarted()
                        || lastResult.isRejectedFailClosed()
                        || lastResult.isRejectedActiveSession()));
        if (shouldCancel) {
          plugin.getPluginLogger().debug("Command had prompts/presets/errors, cancelling event");
          event.setCancelled(true);
        }
      } else {
        plugin
            .getPluginLogger()
            .debug(
                "Command had tag form but player "
                    + player.getName()
                    + " lacks promptpaper.use, not cancelling");
      }
    } else if (hasActiveScreen && event.isCancelled()) {
      // Handle backward compatibility when a screen is already open.
      screenManager.startSession(player, commandLine);
      if (screenManager.hasActiveScreen(player)) {
        event.setCancelled(true);
      }
    }
  }
}
