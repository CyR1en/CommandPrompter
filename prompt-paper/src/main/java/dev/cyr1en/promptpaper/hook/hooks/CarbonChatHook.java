package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import net.draycia.carbon.api.CarbonChatProvider;
import net.draycia.carbon.api.event.events.CarbonChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;

/**
 * Hook for the CarbonChat plugin. Subscribes to CarbonChat's event bus
 * to capture chat input for active screens, cancelling the event and
 * clearing recipients to prevent the message from reaching other listeners.
 */
@TargetPlugin(pluginName = "CarbonChat")
public class CarbonChatHook extends BaseHook implements ChatListenerHook {

    public CarbonChatHook(CommandPrompter plugin) {
        super(plugin);
    }

    /**
     * Subscribes to CarbonChat events at priority -100 to intercept chat before
     * any other listener. Returns {@code false} if CarbonChat's API is unavailable.
     */
    @Override
    public boolean subscribe(ScreenManager screenManager) {
        var cc = CarbonChatProvider.carbonChat();
        if (cc == null) {
            getPlugin().getPluginLogger().debug("CarbonChat not available, subscription failed");
            return false;
        }
        getPlugin().getPluginLogger().debug("Subscribing to CarbonChat events");
        cc.eventHandler().subscribe(CarbonChatEvent.class, -100, false, event -> {
            var player = Bukkit.getPlayer(event.sender().uuid());
            if (player == null || !screenManager.hasChatScreen(player)) return;
            event.cancelled(true);
            event.recipients().clear();
            var msg = PlainTextComponentSerializer.plainText().serialize(event.message());
            getPlugin().getPluginLogger().debug("CarbonChat captured: player=" + player.getName()
                    + " msg=" + msg);
            player.getScheduler().run(getPlugin(), st -> screenManager.handleChatInput(player, msg), null);
        });
        return true;
    }
}
