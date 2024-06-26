package com.cyr1en.commandprompter.hook;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import com.cyr1en.commandprompter.hook.hooks.*;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.fusesource.jansi.Ansi;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

public class HookContainer extends HashMap<Class<?>, Hook<?>> {

    private final CommandPrompter plugin;

    public HookContainer(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    public void initHooks() {
        hook(SuperVanishHook.class);
        hook(CarbonChatHook.class);
        hook(VanishNoPacketHook.class);
        hook(PapiHook.class);
        hook(TownyHook.class);
        hook(LuckPermsHook.class);
        hook(HuskTownsHook.class);
    }

    @Override
    public Hook<?> put(Class<?> key, Hook<?> value) {
        var prefix = value.isHooked() ? new Ansi().fgRgb(153, 214, 90).a("✓").reset() :
                new Ansi().fgRgb(255, 96, 109).a("-").reset();
        plugin.getPluginLogger().info(prefix + " " + key.getSimpleName() + " contained");
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
        if (!targetEnabled) {
            plugin.getPluginLogger().debug(targetPluginName + " is not enabled. Could not hook.");
            return Hook.empty();
        }

        try {
            var constructor = pluginHook.getDeclaredConstructor(CommandPrompter.class);
            constructor.setAccessible(true);
            plugin.getPluginLogger().debug("Hook construct: " + constructor.getName());
            var instance = constructor.newInstance(plugin);
            if (instance instanceof Listener)
                plugin.getServer().getPluginManager().registerEvents((Listener) instance, plugin);
            plugin.getPluginLogger().debug("Hook instance: " + instance.getClass());

            var hook = Hook.of(instance);
            hook.setTargetPlugin(targetPluginName);
            return hook;
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException | InstantiationException |
                 IllegalPluginAccessException e) {
            plugin.getPluginLogger().debug(e.toString());
        }
        return Hook.empty();
    }

    @SuppressWarnings("unchecked")
    public Hook<VanishHook> getVanishHook() {
        return values().stream()
                .filter(hook -> hook.isHooked() && (hook.get() instanceof VanishHook))
                .map(hook -> (Hook<VanishHook>) hook).findFirst().orElse(Hook.of(new VanishHook(plugin)));
    }

    public <T> Hook<T> getHook(Class<T> hookClass) {
        @SuppressWarnings("unchecked") var t = (Hook<T>) get(hookClass);
        if (t == null) return Hook.empty();
        return t;
    }

    public boolean isHooked(Class<?> hookClass) {
        return getHook(hookClass).isHooked();
    }
}
