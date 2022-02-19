package com.cyr1en.commandprompter.hook;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import com.cyr1en.commandprompter.hook.hooks.VentureChatHook;
import org.bukkit.Bukkit;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

public class HookContainer extends HashMap<Class<?>, Hook<?>> {

    private final CommandPrompter plugin;

    public HookContainer(CommandPrompter plugin) {
        this.plugin = plugin;
        initHooks();
    }

    private void initHooks() {
        hook(VentureChatHook.class);
    }

    @Override
    public Hook<?> put(Class<?> key, Hook<?> value) {
        plugin.getPluginLogger().info(key.getSimpleName() + " contained.");
        return super.put(key, value);
    }

    private <T> void hook(Class<T> pluginHook) {
        var instance = constructHook(pluginHook);
        this.put(pluginHook, instance);
    }

    private <T> Hook<T> constructHook(Class<T> pluginHook) {
        plugin.getPluginLogger().debug("Constructing hook: " + pluginHook.getSimpleName());
        if (!pluginHook.isAnnotationPresent(TargetPlugin.class)) return Hook.empty();

        var targetPluginName = pluginHook.getAnnotation(TargetPlugin.class).pluginName();
        plugin.getPluginLogger().debug("Hook target plugin name: " + targetPluginName);
        var targetEnabled = Bukkit.getPluginManager().isPluginEnabled(targetPluginName);
        plugin.getPluginLogger().debug("Target enabled: " + targetEnabled);
        if (!targetEnabled)
            plugin.getPluginLogger().debug(targetPluginName + " is not enabled. Could not hook.");

        try {
            var constructor = pluginHook.getDeclaredConstructor(CommandPrompter.class);
            constructor.setAccessible(true);
            plugin.getPluginLogger().debug("Hook construct: " + constructor.getName());
            var instance = constructor.newInstance(plugin);
            plugin.getPluginLogger().debug("Hook instance: " + instance.getClass());
            return Hook.of(instance);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException | InstantiationException e) {
            plugin.getPluginLogger().debug(e.getMessage());
        }
        return Hook.empty();
    }

    public <T> Hook<T> getHook(Class<T> hookClass) {
        @SuppressWarnings("unchecked") var t = (Hook<T>) get(hookClass);
        return t;
    }
}
