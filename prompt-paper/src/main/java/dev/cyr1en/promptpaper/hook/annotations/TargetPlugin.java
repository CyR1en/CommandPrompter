package dev.cyr1en.promptpaper.hook.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link dev.cyr1en.promptpaper.hook.PluginHook} implementation with
 * the Bukkit plugin name it integrates with. {@link dev.cyr1en.promptpaper.hook.HookContainer}
 * uses this to skip construction when the target plugin is not installed.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TargetPlugin {
    /** The Bukkit plugin name (as returned by {@code PluginManager.getPlugin}). */
    String pluginName();
}
