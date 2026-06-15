package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.hook.PluginHook;
import dev.cyr1en.promptpaper.screen.ScreenManager;

/**
 * Extension point for chat-input hooks that replace the default Bukkit
 * {@code AsyncChatEvent} listener (e.g. CarbonChat). When multiple hooks
 * implement this interface, the first one whose {@link #subscribe} returns
 * {@code true} wins.
 */
public interface ChatListenerHook extends PluginHook {
    /**
     * Attempts to subscribe to chat events via the hook's native API.
     *
     * @return {@code true} if subscription succeeded and this hook owns chat input
     */
    boolean subscribe(ScreenManager screenManager);
}
