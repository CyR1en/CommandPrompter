package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.PluginHook;

/**
 * Abstract base for plugin hooks that need access to the {@link CommandPrompter} instance.
 * All concrete hooks should extend this class (or implement {@link PluginHook} directly
 * if they don't need the plugin reference).
 */
public abstract class BaseHook implements PluginHook {

    private final CommandPrompter plugin;

    protected BaseHook(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    @Override
    public CommandPrompter getPlugin() { return plugin; }
}
