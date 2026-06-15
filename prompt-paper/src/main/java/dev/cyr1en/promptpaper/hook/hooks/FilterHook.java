package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.hook.PluginHook;
import dev.cyr1en.promptpaper.screen.playerui.HeadCache;

/**
 * Extension point for hooks that register player-list filters with
 * {@link HeadCache} (e.g. Towny, LuckPerms, WorldGuard). Each filter
 * is identified by a regex key used in prompt player-selectors.
 */
public interface FilterHook extends PluginHook {
    /** Registers all supported {@link CacheFilter} instances on the given head cache. */
    void registerFilters(HeadCache headCache);
}
