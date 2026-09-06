package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import java.util.ServiceConfigurationError;
import net.draycia.carbon.api.CarbonChat;
import net.draycia.carbon.api.CarbonChatProvider;
import net.draycia.carbon.api.event.CarbonEventSubscription;
import net.draycia.carbon.api.event.events.CarbonChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;

/**
 * Hook for the CarbonChat plugin. Subscribes to CarbonChat's event bus to capture chat input for
 * active screens, cancelling the event and clearing recipients to prevent the message from reaching
 * other listeners.
 */
@TargetPlugin(pluginName = "CarbonChat")
public class CarbonChatHook extends BaseHook implements ChatListenerHook {

  private CarbonEventSubscription<CarbonChatEvent> subscription;

  public CarbonChatHook(CommandPrompter plugin) {
    super(plugin);
  }

  /**
   * Subscribes to CarbonChat events at priority -100 to intercept chat before any other listener.
   * Returns {@code false} if CarbonChat's API is unavailable.
   */
  @Override
  public boolean subscribe(ScreenManager screenManager) {
    disposeSubscription();

    final var cc = getCarbonChat();
    if (cc == null) {
      return false;
    }

    try {
      getPlugin().getPluginLogger().debug("Subscribing to CarbonChat events");
      var registered =
          cc.eventHandler()
              .subscribe(
                  CarbonChatEvent.class,
                  -100,
                  false,
                  event -> {
                    var player = Bukkit.getPlayer(event.sender().uuid());
                    if (player == null || !screenManager.hasChatScreen(player)) return;
                    event.cancelled(true);
                    event.recipients().clear();
                    var msg = PlainTextComponentSerializer.plainText().serialize(event.message());
                    getPlugin()
                        .getPluginLogger()
                        .debug("CarbonChat captured: player=" + player.getName() + " msg=" + msg);
                    player
                        .getScheduler()
                        .run(getPlugin(), st -> screenManager.handleChatInput(player, msg), null);
                  });
      if (registered == null) {

        getPlugin().getPluginLogger().debug("CarbonChat returned no subscription handle");
        return false;
      }
      subscription = registered;

      return true;
    } catch (ServiceConfigurationError e) {
      logFailure("CarbonChat event API could not be configured", e);
    } catch (LinkageError e) {
      logFailure("CarbonChat event API is unavailable", e);
    } catch (Exception e) {
      logFailure("CarbonChat subscription failed", e);
    }
    return false;
  }

  /**
   * CarbonChat throws {@link IllegalStateException} until its provider has been initialized. Treat
   * that state as a normal optional-hook miss so the Bukkit listener can be selected.
   */
  private CarbonChat getCarbonChat() {
    try {
      var cc = CarbonChatProvider.carbonChat();
      if (cc == null) {
        logFailure("CarbonChat provider returned null", null);
        return null;
      }
      return cc;
    } catch (IllegalStateException e) {
      logFailure("CarbonChat is not initialized", e);
    } catch (ServiceConfigurationError e) {
      logFailure("CarbonChat provider could not be configured", e);
    } catch (LinkageError e) {
      logFailure("CarbonChat provider is unavailable", e);
    } catch (Exception e) {
      logFailure("CarbonChat provider lookup failed", e);
    }
    return null;
  }

  @Override
  public void onDisable() {
    disposeSubscription();
  }

  private void disposeSubscription() {
    var current = subscription;
    subscription = null;
    if (current == null) return;
    try {
      current.dispose();
    } catch (ServiceConfigurationError | LinkageError | Exception e) {
      logFailure("CarbonChat subscription disposal failed", e);
    }
  }

  private void logFailure(String message, Throwable failure) {

    if (failure == null) {
      getPlugin().getPluginLogger().debug(message);
    } else {
      getPlugin().getPluginLogger().debug(message + ": " + failure.getMessage());
    }
  }
}
