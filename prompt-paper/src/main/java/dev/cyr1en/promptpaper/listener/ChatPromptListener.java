package dev.cyr1en.promptpaper.listener;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptui.ComponentUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Default Bukkit chat listener that captures player chat input when a
 * chat-type screen is active. Registered as a fallback when no
 * {@link dev.cyr1en.promptpaper.hook.hooks.ChatListenerHook} (e.g. CarbonChat) is available.
 * Handles both Paper's {@link AsyncChatEvent} and the legacy {@link AsyncPlayerChatEvent}.
 */
public class ChatPromptListener implements Listener {

    private final CommandPrompter plugin;
    private final ScreenManager screenManager;

    public ChatPromptListener(CommandPrompter plugin, ScreenManager screenManager) {
        this.plugin = plugin;
        this.screenManager = screenManager;
    }

    /**
     * Intercepts chat messages when the player has an active chat screen,
     * cancels the event to prevent the message from reaching other listeners,
     * and forwards the serialized text to the screen manager on the player's scheduler.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        plugin.getPluginLogger().debug("Chat event: player=" + player.getName()
                + " cancelled=" + event.isCancelled()
                + " hasChatScreen=" + screenManager.hasChatScreen(player));
        if (event.isCancelled()) return;
        if (!screenManager.hasChatScreen(player)) return;
        event.setCancelled(true);
        var message = event.message();
        plugin.getPluginLogger().debug("Chat input captured for " + player.getName());
        player.getScheduler().run(plugin, scheduledTask -> {
            screenManager.handleChatInput(player, ComponentUtil.serialize(message));
        }, null);
    }

    /**
     * Returns the {@link EventPriority} configured for chat response listeners,
     * falling back to {@link EventPriority#LOWEST} on parse failure.
     */
    public EventPriority resolvePriority() {
        var configPriority = plugin.getConfigLoader().getPromptConfig().responseListenerPriority();
        try {
            return EventPriority.valueOf(configPriority.toUpperCase());
        } catch (Exception e) {
            return EventPriority.LOWEST;
        }
    }
}
