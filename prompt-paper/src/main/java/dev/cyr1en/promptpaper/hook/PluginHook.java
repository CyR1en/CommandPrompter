package dev.cyr1en.promptpaper.hook;

import dev.cyr1en.promptpaper.CommandPrompter;

/**
 * Base interface for all external plugin integrations. Hooks are discovered
 * by {@link HookContainer} via {@link dev.cyr1en.promptpaper.hook.annotations.TargetPlugin}
 * and instantiated only when the target plugin is present on the server.
 */
public interface PluginHook {
    /** Returns the owning CommandPrompter plugin instance. */
    CommandPrompter getPlugin();

    /** Called after the hook is successfully constructed and registered. */
    default void onEnable() {}
    /** Called when the CommandPrompter plugin is disabling. */
    default void onDisable() {}
}
