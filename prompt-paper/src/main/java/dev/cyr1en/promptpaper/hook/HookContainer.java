package dev.cyr1en.promptpaper.hook;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import dev.cyr1en.promptpaper.hook.hooks.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

/**
 * Registry and lifecycle manager for external plugin hooks. Each hook class
 * must be annotated with {@link TargetPlugin} and have a single-arg
 * {@link CommandPrompter} constructor. The container checks whether the target
 * plugin is enabled before constructing the hook, and auto-registers any
 * hook that implements {@link org.bukkit.event.Listener}.
 */
public class HookContainer {

    private final CommandPrompter plugin;
    private final Map<Class<?>, PluginHook> hooks = new HashMap<>();
    private final Map<Class<?>, String> targetPlugins = new HashMap<>();

    public HookContainer(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    /**
     * Constructs and enables all known hook classes. Each hook is skipped
     * silently if its {@link TargetPlugin} is not installed on the server.
     */
    public void initHooks() {
        plugin.getPluginLogger().debug("Initializing hooks...");
        hook(PremiumVanishHook.class);
        hook(SuperVanishHook.class);
        hook(CarbonChatHook.class);
        hook(VanishNoPacketHook.class);
        hook(PapiHook.class);
        hook(TownyHook.class);
        hook(LuckPermsHook.class);
        hook(HuskTownsHook.class);
        hook(WorldGuardHook.class);
        var hookedCount = hooks.size();
        plugin.getPluginLogger().debug("Hooks initialized: " + hookedCount + "/" + hooks.size() + " active");
    }

    /**
     * Attempts to construct, register, and enable a single hook type.
     * Skips silently if the target plugin is missing or the annotation is absent.
     */
    private <T extends PluginHook> void hook(Class<T> type) {
        var instance = constructHook(type);
        if (instance == null) return;
        hooks.put(type, instance);
        plugin.getPluginLogger().info(" \u2713 " + type.getSimpleName() + " hooked");
        instance.onEnable();
    }

    /**
     * Constructs a single hook, checking for {@link TargetPlugin} presence.
     * Returns {@code null} and logs a message if the target plugin is missing,
     * the annotation is absent, or the required constructor is not found.
     */
    private <T extends PluginHook> T constructHook(Class<T> type) {
        var ann = type.getAnnotation(TargetPlugin.class);
        if (ann == null) {
            plugin.getPluginLogger().debug("Skipping " + type.getSimpleName() + ": no @TargetPlugin annotation");
            return null;
        }

        var target = ann.pluginName();
        if (!Bukkit.getPluginManager().isPluginEnabled(target)) {
            plugin.getPluginLogger().debug("Skipping " + type.getSimpleName() + ": " + target + " not installed");
            return null;
        }

        try {
            var ctor = type.getDeclaredConstructor(CommandPrompter.class);
            var instance = ctor.newInstance(plugin);
            if (instance instanceof Listener listener)
                Bukkit.getPluginManager().registerEvents(listener, plugin);
            targetPlugins.put(type, target);
            return instance;
        } catch (NoSuchMethodException e) {
            plugin.getPluginLogger().err("Hook " + type.getSimpleName()
                    + " is missing required constructor (CommandPrompter)");
            return null;
        } catch (Exception e) {
            plugin.getPluginLogger().err("Failed to construct hook " + type.getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /** Returns the Bukkit plugin name that the given hook type targets, if known. */
    public Optional<String> getTargetPlugin(Class<?> hookType) {
        return Optional.ofNullable(targetPlugins.get(hookType));
    }

    /** Returns the registered hook instance of the given type, if present. */
    public <T> Optional<T> getHook(Class<T> type) {
        return Optional.ofNullable((T) hooks.get(type));
    }

    public <T extends PluginHook> Optional<T> getPluginHook(Class<T> type) {
        return Optional.ofNullable((T) hooks.get(type));
    }

    /**
     * Returns the first registered hook that is an instance of the given type,
     * or empty if none match.
     */
    public <T extends PluginHook> Optional<T> getFirstHooked(Class<T> baseType) {
        return hooks.values().stream()
                .filter(baseType::isInstance)
                .map(baseType::cast)
                .findFirst();
    }

    /** Returns all registered hooks that implement the given interface type. */
    public <T extends PluginHook> List<T> getHooksImplementing(Class<T> baseType) {
        return hooks.values().stream()
                .filter(baseType::isInstance)
                .map(baseType::cast)
                .toList();
    }

    /** Calls {@link PluginHook#onDisable()} on every registered hook. */
    public void disableAll() {
        plugin.getPluginLogger().debug("Disabling all hooks...");
        hooks.values().forEach(PluginHook::onDisable);
    }
}
