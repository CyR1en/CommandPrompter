package dev.cyr1en.promptpaper.custom;

import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptui.api.CommandPrompterAPI;
import dev.cyr1en.promptui.api.PromptScreenFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.bukkit.plugin.Plugin;

/**
 * Thread-safe registry for custom screen types implemented by third-party plugins.
 *
 * <p>Manages provider lifecycle, isolation, exact plugin ownership indexing, atomic transactions,
 * and key validation.
 */
public class CustomScreenRegistry implements CommandPrompterAPI {

  private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,31}$");
  private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");

  private final AtomicLong providerIdGenerator = new AtomicLong(1);
  private final Map<String, CustomScreenRegistration> keyToRegistration = new ConcurrentHashMap<>();
  private final Map<Plugin, Set<String>> ownerToKeys = new ConcurrentHashMap<>();

  private final Object mutationLock = new Object();
  private final BooleanSupplier lifecycleActiveSupplier;
  private final Supplier<Map<String, ScreenType>> configuredMappingsSupplier;
  private final Set<String> reservedKeys;
  private final CustomScreenAuditLogger auditLogger;

  private volatile boolean frozen = false;

  /** Constructs a registry with default reserved keys and no configured mappings. */
  public CustomScreenRegistry() {
    this(() -> true, Map::of, PromptConfig.RESERVED_SCREEN_KEYS, CustomScreenAuditLogger.noop());
  }

  /**
   * Constructs a registry with injected lifecycle state, configured mappings, reserved keys, and
   * audit logger.
   *
   * @param lifecycleActiveSupplier supplier indicating whether CommandPrompter is actively running
   * @param configuredMappingsSupplier supplier providing snapshot of active configured screen
   *     mappings
   * @param reservedKeys set of reserved built-in screen keys
   * @param auditLogger consumer for registration and lifecycle audit events
   */
  public CustomScreenRegistry(
      BooleanSupplier lifecycleActiveSupplier,
      Supplier<Map<String, ScreenType>> configuredMappingsSupplier,
      Set<String> reservedKeys,
      CustomScreenAuditLogger auditLogger) {
    this.lifecycleActiveSupplier =
        Objects.requireNonNull(lifecycleActiveSupplier, "lifecycleActiveSupplier");
    this.configuredMappingsSupplier =
        Objects.requireNonNull(configuredMappingsSupplier, "configuredMappingsSupplier");
    this.reservedKeys = Set.copyOf(Objects.requireNonNull(reservedKeys, "reservedKeys"));
    this.auditLogger = auditLogger != null ? auditLogger : CustomScreenAuditLogger.noop();
  }

  /** Freezes the registry so no further registrations can be added. */
  public void freeze() {
    synchronized (mutationLock) {
      this.frozen = true;
      auditLogger.onAuditEvent(CustomScreenAuditEvent.frozen());
    }
  }

  /** Returns whether the registry is currently frozen. */
  public boolean isFrozen() {
    return frozen;
  }

  /** Returns whether the plugin lifecycle is active and the registry is not frozen. */
  public boolean isLifecycleActive() {
    return !frozen && lifecycleActiveSupplier.getAsBoolean();
  }

  @Override
  public void registerScreen(Plugin plugin, String key, PromptScreenFactory factory) {
    Objects.requireNonNull(plugin, "Owner plugin must not be null");
    Objects.requireNonNull(key, "Screen key must not be null");
    Objects.requireNonNull(factory, "Custom screen factory must not be null");

    String safeOwnerName = CustomScreenAuditLogger.sanitize(plugin.getName());

    if (!KEY_PATTERN.matcher(key).matches()) {
      String reason = describeKeyGrammarViolation(key);
      auditLogger.onAuditEvent(CustomScreenAuditEvent.rejected(key, safeOwnerName, reason));
      throw new IllegalArgumentException(reason);
    }

    String canonicalKey = key.toLowerCase(Locale.ROOT);
    if (canonicalKey.startsWith("@")
        || reservedKeys.contains(canonicalKey)
        || reservedKeys.contains(key)) {
      String reason = "Screen key '" + key + "' is reserved and cannot be registered";
      auditLogger.onAuditEvent(CustomScreenAuditEvent.rejected(key, safeOwnerName, reason));
      throw new IllegalArgumentException(reason);
    }

    synchronized (mutationLock) {
      if (!isLifecycleActive()) {
        String reason = frozen ? "Registry is frozen" : "CommandPrompter lifecycle is not active";
        auditLogger.onAuditEvent(CustomScreenAuditEvent.rejected(key, safeOwnerName, reason));
        throw new IllegalStateException(reason);
      }

      if (!plugin.isEnabled()) {
        String reason = "Owner plugin '" + safeOwnerName + "' is not enabled";
        auditLogger.onAuditEvent(CustomScreenAuditEvent.rejected(key, safeOwnerName, reason));
        throw new IllegalStateException(reason);
      }

      Map<String, ScreenType> configuredMappings = configuredMappingsSupplier.get();
      if (configuredMappings != null
          && (configuredMappings.containsKey(canonicalKey)
              || configuredMappings.containsKey(key))) {
        String reason = "Screen key '" + key + "' collides with configured screen mapping";
        auditLogger.onAuditEvent(CustomScreenAuditEvent.rejected(key, safeOwnerName, reason));
        throw new IllegalArgumentException(reason);
      }

      CustomScreenRegistration existing = keyToRegistration.get(canonicalKey);
      if (existing != null && existing.isActive()) {
        String reason =
            "Screen key '"
                + key
                + "' is already registered by plugin '"
                + existing.ownerName()
                + "'";
        auditLogger.onAuditEvent(CustomScreenAuditEvent.rejected(key, safeOwnerName, reason));
        throw new IllegalArgumentException(reason);
      }

      long providerId = providerIdGenerator.getAndIncrement();
      CustomScreenRegistration registration =
          new CustomScreenRegistration(providerId, canonicalKey, safeOwnerName, plugin, factory);

      keyToRegistration.put(canonicalKey, registration);
      ownerToKeys.computeIfAbsent(plugin, p -> new HashSet<>()).add(canonicalKey);

      auditLogger.onAuditEvent(
          CustomScreenAuditEvent.registered(canonicalKey, safeOwnerName, providerId));
    }
  }

  /**
   * Executes a transaction that validates candidate configured screen mappings against all active
   * custom screen registrations while holding the mutation lock, and then executes the publication
   * task.
   *
   * @param candidateMappings the new candidate configured screen mappings
   * @param publish the publication runnable that updates the configuration state
   * @throws IllegalStateException if any candidate mapping key collides with an active custom
   *     screen
   */
  public void validateMappingsAndPublish(
      Map<String, ScreenType> candidateMappings, Runnable publish) {
    Objects.requireNonNull(candidateMappings, "candidateMappings");
    Objects.requireNonNull(publish, "publish");
    synchronized (mutationLock) {
      for (String key : candidateMappings.keySet()) {
        if (key == null) continue;
        String canonical = key.trim().toLowerCase(Locale.ROOT);
        CustomScreenRegistration reg = keyToRegistration.get(canonical);
        if (reg != null && reg.isActive()) {
          throw new IllegalStateException(
              "Configured screen mapping '"
                  + key
                  + "' collides with active custom screen registered by plugin '"
                  + reg.ownerName()
                  + "'");
        }
      }
      publish.run();
    }
  }

  @Override
  public void unregisterScreens(Plugin plugin) {
    Objects.requireNonNull(plugin, "Owner plugin must not be null");
    unregisterScreensAndGet(plugin);
  }

  /**
   * Atomically unregisters all screens owned by the specified plugin and returns the detached
   * registrations.
   *
   * @param plugin the owner plugin
   * @return unmodifiable list of detached registrations, or empty list if none
   */
  public List<CustomScreenRegistration> unregisterScreensAndGet(Plugin plugin) {
    if (plugin == null) {
      return List.of();
    }

    synchronized (mutationLock) {
      Set<String> keys = ownerToKeys.remove(plugin);
      if (keys == null || keys.isEmpty()) {
        return List.of();
      }

      List<CustomScreenRegistration> unregistered = new ArrayList<>(keys.size());
      for (String key : keys) {
        CustomScreenRegistration registration = keyToRegistration.remove(key);
        if (registration != null && registration.ownerPlugin() == plugin) {
          registration.teardown();
          unregistered.add(registration);
          auditLogger.onAuditEvent(
              CustomScreenAuditEvent.unregistered(
                  key, registration.ownerName(), registration.providerId()));
        }
      }
      return Collections.unmodifiableList(unregistered);
    }
  }

  /** Unregisters all custom screens and clears all internal indexes. */
  public void unregisterAll() {
    unregisterAllAndGet();
  }

  /**
   * Atomically unregisters all custom screens, clears all internal indexes, and returns all
   * detached registrations.
   *
   * @return unmodifiable list of detached registrations
   */
  public List<CustomScreenRegistration> unregisterAllAndGet() {
    synchronized (mutationLock) {
      List<CustomScreenRegistration> detached = new ArrayList<>(keyToRegistration.size());
      for (Map.Entry<String, CustomScreenRegistration> entry : keyToRegistration.entrySet()) {
        CustomScreenRegistration reg = entry.getValue();
        reg.teardown();
        detached.add(reg);
        auditLogger.onAuditEvent(
            CustomScreenAuditEvent.teardown(entry.getKey(), reg.ownerName(), reg.providerId()));
      }
      keyToRegistration.clear();
      ownerToKeys.clear();
      return Collections.unmodifiableList(detached);
    }
  }

  /**
   * Checks if a custom screen key is currently registered and active.
   *
   * @param key the screen key
   * @return {@code true} if registered and active, {@code false} otherwise
   */
  public boolean isRegistered(String key) {
    if (key == null) return false;
    String canonicalKey = key.trim().toLowerCase(Locale.ROOT);
    CustomScreenRegistration reg = keyToRegistration.get(canonicalKey);
    return reg != null && reg.isActive();
  }

  /**
   * Retrieves an active registration handle for the given screen key backed by its live token.
   *
   * @param key the screen key
   * @return an optional containing the live handle token if active, or empty otherwise
   */
  public Optional<CustomScreenHandle> getRegistration(String key) {
    if (key == null) return Optional.empty();
    String canonicalKey = key.trim().toLowerCase(Locale.ROOT);
    CustomScreenRegistration reg = keyToRegistration.get(canonicalKey);
    if (reg != null && reg.isActive()) {
      return Optional.of(reg.token());
    }
    return Optional.empty();
  }

  /**
   * Checks whether an active custom screen collides with the given key.
   *
   * @param key the screen key
   * @return {@code true} if a registration is active for this key
   */
  public boolean hasActiveCollision(String key) {
    return isRegistered(key);
  }

  /** Returns an immutable snapshot map of all currently active registration handles. */
  public Map<String, CustomScreenHandle> getRegistrationsSnapshot() {
    Map<String, CustomScreenHandle> snapshot = new HashMap<>();
    for (Map.Entry<String, CustomScreenRegistration> entry : keyToRegistration.entrySet()) {
      if (entry.getValue().isActive()) {
        snapshot.put(entry.getKey(), entry.getValue().token());
      }
    }
    return Collections.unmodifiableMap(snapshot);
  }

  /** Returns an immutable set of all currently registered active screen keys. */
  public Set<String> getRegisteredKeys() {
    Set<String> keys = new HashSet<>();
    for (Map.Entry<String, CustomScreenRegistration> entry : keyToRegistration.entrySet()) {
      if (entry.getValue().isActive()) {
        keys.add(entry.getKey());
      }
    }
    return Collections.unmodifiableSet(keys);
  }

  private static String describeKeyGrammarViolation(String key) {
    if (key.isEmpty()) {
      return "Screen key must not be empty";
    }
    if (key.length() > 32) {
      return "Screen key '" + key + "' exceeds maximum length of 32 characters";
    }
    if (key.startsWith("@")) {
      return "Screen key '" + key + "' cannot start with '@' (reserved for preset namespace)";
    }
    if (UPPERCASE_PATTERN.matcher(key).find()) {
      return "Screen key '" + key + "' must be lowercase (matched ^[a-z][a-z0-9_]{0,31}$)";
    }
    if (Character.isDigit(key.charAt(0))) {
      return "Screen key '" + key + "' must start with a lowercase letter [a-z]";
    }
    if (key.contains(" ")) {
      return "Screen key '" + key + "' must not contain whitespace";
    }
    return "Screen key '" + key + "' is invalid. Must match regex ^[a-z][a-z0-9_]{0,31}$";
  }
}
