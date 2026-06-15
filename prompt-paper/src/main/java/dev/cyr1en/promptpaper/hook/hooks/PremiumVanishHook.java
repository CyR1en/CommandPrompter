package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;

/**
 * Hook for the PremiumVanish plugin. Extends {@link SuperVanishHook} since
 * PremiumVanish is a fork that shares the same API.
 */
@TargetPlugin(pluginName = "PremiumVanish")
public class PremiumVanishHook extends SuperVanishHook {

    public PremiumVanishHook(CommandPrompter plugin) {
        super(plugin);
    }
}
