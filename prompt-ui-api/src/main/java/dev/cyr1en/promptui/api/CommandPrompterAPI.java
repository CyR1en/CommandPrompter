package dev.cyr1en.promptui.api;

import org.bukkit.plugin.Plugin;

/**
 * Public service interface for registering custom interactive prompt screen providers with
 * CommandPrompter at runtime.
 *
 * <p>Third-party plugin developers can implement custom prompt screens (such as custom item pickers,
 * guild selectors, or specialized GUI menus) and register them under unique alphanumeric keys
 * (e.g. {@code <ecoitem:...>}, {@code <region:...>}).
 *
 * <h2>Obtaining the API</h2>
 *
 * <p>CommandPrompter registers an implementation of {@link CommandPrompterAPI} in Bukkit's
 * {@link org.bukkit.plugin.ServicesManager} when enabled. Provider plugins should retrieve it during
 * or after {@code onEnable()}:
 *
 * <pre>{@code
 * RegisteredServiceProvider<CommandPrompterAPI> rsp =
 *     getServer().getServicesManager().getRegistration(CommandPrompterAPI.class);
 * if (rsp != null) {
 *     CommandPrompterAPI api = rsp.getProvider();
 *     api.registerScreen(this, "ecoitem", (player, context) -> new EcoItemInputScreen(this, player, context));
 * }
 * }</pre>
 *
 * <h2>Dependency Declaration</h2>
 *
 * <p>Provider plugins must declare CommandPrompter as a dependency in {@code plugin.yml} or
 * {@code paper-plugin.yml} (e.g. {@code depend: [CommandPrompterPaper]} or {@code softdepend: [CommandPrompterPaper]}).
 *
 * <p><b>Important:</b> Provider plugins must compile against {@code prompt-ui-api} using {@code compileOnly}
 * and <b>MUST NOT</b> shade or relocate {@code dev.cyr1en.promptui.*} classes into their own jar.
 *
 * <h2>Key Naming &amp; Ownership Rules</h2>
 *
 * <ul>
 *   <li>Registration grammar is strictly lowercase and must match {@code ^[a-z][a-z0-9_]{0,31}$}
 *       (starts with lowercase letter, lowercase alphanumeric or underscore, maximum 32 characters).</li>
 *   <li>Runtime tag resolution in command prompts is case-insensitive (e.g. {@code <ECOITEM:...>} resolves
 *       to a registered {@code "ecoitem"} provider).</li>
 *   <li>Built-in keys (e.g. {@code ""}, {@code "a"}, {@code "anvil"}, {@code "s"}, {@code "sign"},
 *       {@code "p"}, {@code "player"}, {@code "d"}, {@code "dialog"}, {@code "c"}, {@code "confirm"},
 *       {@code "i"}, {@code "item"}, and {@code "@"} presets) are reserved and cannot be registered.</li>
 *   <li>Keys are globally unique while registered. Attempting to register an existing active key
 *       will throw {@link IllegalArgumentException}.</li>
 * </ul>
 *
 * <h2>Lifecycle &amp; Auto-Cleanup</h2>
 *
 * <p>When a provider plugin is disabled, CommandPrompter automatically unregisters all of its screen
 * factories and cleanly cancels any active prompt sessions created by that provider with {@link dev.cyr1en.promptcore.CancelReason#MANUAL}.
 * Provider plugins may also explicitly unregister their screens at any time via {@link #unregisterScreens(Plugin)}.
 *
 * <h2>Fail-Closed Security</h2>
 *
 * <p>If a command contains an unregistered or mistyped screen key, CommandPrompter <b>fails closed</b>:
 * session creation is aborted, an administrative diagnostic error is logged, and the system never
 * silently falls back to a chat prompt.
 *
 * @since 3.3.0
 */
public interface CommandPrompterAPI {

  /**
   * Registers a custom prompt screen factory under a unique alphanumeric key.
   *
   * @param plugin the owning plugin registering the provider (must be non-null and currently enabled)
   * @param key the tag key (e.g., {@code "ecoitem"}); must match lowercase grammar {@code ^[a-z][a-z0-9_]{0,31}$}
   * @param factory the factory responsible for instantiating the screen (must be non-null)
   * @throws NullPointerException if {@code plugin}, {@code key}, or {@code factory} is null
   * @throws IllegalArgumentException if {@code key} is invalid, reserved, collides with configured mappings,
   *     or is already registered by an active provider
   * @throws IllegalStateException if {@code plugin} is not enabled, CommandPrompter is shutting down,
   *     or the registry is frozen
   */
  void registerScreen(Plugin plugin, String key, PromptScreenFactory factory);

  /**
   * Unregisters all screen factories owned by the specified plugin.
   *
   * <p>If the plugin has no registered screens, this method is a safe no-op. Calling this method for
   * a plugin does not affect registrations owned by other plugins.
   *
   * @param plugin the owning plugin whose screens should be unregistered (must be non-null)
   * @throws NullPointerException if {@code plugin} is null
   */
  void unregisterScreens(Plugin plugin);
}
