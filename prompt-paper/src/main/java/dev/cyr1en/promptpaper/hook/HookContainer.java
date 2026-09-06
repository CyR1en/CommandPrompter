package dev.cyr1en.promptpaper.hook;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import dev.cyr1en.promptpaper.hook.hooks.CarbonChatHook;
import dev.cyr1en.promptpaper.hook.hooks.HuskTownsHook;
import dev.cyr1en.promptpaper.hook.hooks.LuckPermsHook;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import dev.cyr1en.promptpaper.hook.hooks.PremiumVanishHook;
import dev.cyr1en.promptpaper.hook.hooks.SuperVanishHook;
import dev.cyr1en.promptpaper.hook.hooks.TownyHook;
import dev.cyr1en.promptpaper.hook.hooks.VanishNoPacketHook;
import dev.cyr1en.promptpaper.hook.hooks.WorldGuardHook;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

/**
 * Registry and lifecycle manager for external plugin hooks. Each hook class must be annotated with
 * {@link TargetPlugin} and have a single-arg {@link CommandPrompter} constructor. The container
 * checks whether the target plugin is enabled before constructing the hook, and auto-registers any
 * hook that implements {@link org.bukkit.event.Listener}.
 */
public class HookContainer {

  /** Hook order is also the selection priority for hooks sharing an extension point. */
  private static final List<Class<? extends PluginHook>> HOOK_TYPES =
      List.of(
          PremiumVanishHook.class,
          SuperVanishHook.class,
          CarbonChatHook.class,
          VanishNoPacketHook.class,
          PapiHook.class,
          TownyHook.class,
          LuckPermsHook.class,
          HuskTownsHook.class,
          WorldGuardHook.class);

  private final CommandPrompter plugin;
  private final Map<Class<?>, PluginHook> hooks = new LinkedHashMap<>();
  private final Map<Class<?>, String> targetPlugins = new LinkedHashMap<>();
  private boolean initialized;

  public HookContainer(CommandPrompter plugin) {
    this.plugin = plugin;
  }

  /**
   * Constructs and enables all known hook classes. Each hook is skipped silently if its {@link
   * TargetPlugin} is not installed on the server.
   */
  public void initHooks() {
    if (initialized) {
      plugin.getPluginLogger().debug("Hooks already initialized; keeping existing registrations");
      return;
    }
    initialized = true;
    plugin.getPluginLogger().debug("Initializing hooks...");
    for (var type : HOOK_TYPES) {
      hook(type);
    }
    plugin
        .getPluginLogger()
        .debug("Hooks initialized: " + hooks.size() + "/" + HOOK_TYPES.size() + " active");
  }

  /**
   * Attempts to construct, register, and enable a single hook type. Skips silently if the target
   * plugin is missing or the annotation is absent.
   */
  private <T extends PluginHook> void hook(Class<T> type) {
    try {
      var instance = constructHook(type);
      if (instance == null) return;

      // Run optional API initialization before exposing the hook to selectors. If the
      // provider throws, only this hook is skipped and the remaining integrations continue.
      instance.onEnable();
      registerListener(type, instance);
      hooks.put(type, instance);
      var annotation = type.getAnnotation(TargetPlugin.class);
      if (annotation != null) targetPlugins.put(type, annotation.pluginName());
      plugin.getPluginLogger().info(" \u2713 " + type.getSimpleName() + " hooked");
    } catch (ServiceConfigurationError | LinkageError | Exception e) {
      logHookFailure(type, e);
    }
  }

  /**
   * Constructs a single hook, checking for {@link TargetPlugin} presence. Returns {@code null} and
   * logs a message if the target plugin is missing, the annotation is absent, or the required
   * constructor is not found.
   */
  private <T extends PluginHook> T constructHook(Class<T> type) {
    try {
      var ann = type.getAnnotation(TargetPlugin.class);
      if (ann == null) {
        plugin
            .getPluginLogger()
            .debug("Skipping " + type.getSimpleName() + ": no @TargetPlugin annotation");
        return null;
      }

      var target = ann.pluginName();
      if (!Bukkit.getPluginManager().isPluginEnabled(target)) {
        plugin
            .getPluginLogger()
            .debug("Skipping " + type.getSimpleName() + ": " + target + " not installed");
        return null;
      }

      var ctor = type.getDeclaredConstructor(CommandPrompter.class);
      return ctor.newInstance(plugin);
    } catch (NoSuchMethodException e) {
      plugin
          .getPluginLogger()
          .err(
              "Hook "
                  + type.getSimpleName()
                  + " is missing required constructor (CommandPrompter)");
      return null;
    } catch (ServiceConfigurationError | LinkageError | Exception e) {
      logHookFailure(type, e);
      return null;
    }
  }

  /** Registers a listener after successful optional-API initialization. */
  private void registerListener(Class<?> type, PluginHook hook) {
    if (!(hook instanceof Listener listener)) return;

    // PremiumVanish exposes the SuperVanish event/API and PremiumVanishHook inherits the
    // listener method. Registering both instances makes every vanish event invalidate the
    // cache twice when both plugins are installed. PremiumVanish has explicit priority above
    // SuperVanish, so retain the first listener and keep the second hook API-only.
    if (type == SuperVanishHook.class && hooks.containsKey(PremiumVanishHook.class)) {
      plugin
          .getPluginLogger()
          .debug("Skipping inherited SuperVanish listener because PremiumVanish is already hooked");
      return;
    }
    Bukkit.getPluginManager().registerEvents(listener, plugin);
  }

  private void logHookFailure(Class<?> type, Throwable failure) {
    var message = failure.getMessage();
    plugin
        .getPluginLogger()
        .err(
            "Skipping hook "
                + type.getSimpleName()
                + ": "
                + (message == null ? failure.getClass().getSimpleName() : message));
  }

  /** Returns the Bukkit plugin name that the given hook type targets, if known. */
  public Optional<String> getTargetPlugin(Class<?> hookType) {
    return Optional.ofNullable(targetPlugins.get(hookType));
  }

  /** Returns the registered hook instance of the given type, if present. */
  public <T> Optional<T> getHook(Class<T> type) {
    return Optional.ofNullable(type.cast(hooks.get(type)));
  }

  public <T extends PluginHook> Optional<T> getPluginHook(Class<T> type) {
    return getHook(type);
  }

  /**
   * Returns the first registered hook that is an instance of the given type, or empty if none
   * match.
   */
  public <T extends PluginHook> Optional<T> getFirstHooked(Class<T> baseType) {
    return hooks.values().stream().filter(baseType::isInstance).map(baseType::cast).findFirst();
  }

  /** Returns all registered hooks that implement the given interface type. */
  public <T extends PluginHook> List<T> getHooksImplementing(Class<T> baseType) {
    return hooks.values().stream().filter(baseType::isInstance).map(baseType::cast).toList();
  }

  /** Calls {@link PluginHook#onDisable()} on every registered hook. */
  public void disableAll() {
    plugin.getPluginLogger().debug("Disabling all hooks...");
    // Disable in reverse registration order so selected fallbacks remain available until
    // higher-priority integrations have released their resources.
    var registered = new ArrayList<>(hooks.values());
    for (var hook : registered.reversed()) {
      try {
        hook.onDisable();
      } catch (ServiceConfigurationError | LinkageError | Exception e) {
        logHookFailure(hook.getClass(), e);
      }
    }
  }
}
