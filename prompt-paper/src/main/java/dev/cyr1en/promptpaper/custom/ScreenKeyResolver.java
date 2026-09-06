package dev.cyr1en.promptpaper.custom;

import dev.cyr1en.promptpaper.config.ScreenType;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Single resolver responsible for mapping prompt screen keys to their typed runtime resolution.
 *
 * <h2>Resolution Order</h2>
 *
 * <ol>
 *   <li><b>Preset syntax:</b> keys beginning with {@code @} resolve immediately to {@link
 *       ScreenResolution.Preset}.
 *   <li><b>Built-in / configured mappings:</b> empty keys resolve to {@link ScreenType#CHAT}.
 *       Standard built-in keys (anvil, sign, player, dialog, confirm) and configured
 *       screen-mappings take precedence over custom screens.
 *   <li><b>Active custom screen:</b> keys registered and active in {@link CustomScreenRegistry}
 *       resolve to {@link ScreenResolution.Custom}.
 *   <li><b>Unresolved:</b> any unrecognized key resolves to {@link ScreenResolution.Unresolved}.
 * </ol>
 */
public class ScreenKeyResolver {

  private final CustomScreenRegistry customRegistry;
  private final Supplier<Map<String, ScreenType>> builtInMappingsSupplier;

  public ScreenKeyResolver(
      CustomScreenRegistry customRegistry,
      Supplier<Map<String, ScreenType>> builtInMappingsSupplier) {
    this.customRegistry = Objects.requireNonNull(customRegistry, "customRegistry");
    this.builtInMappingsSupplier =
        Objects.requireNonNull(builtInMappingsSupplier, "builtInMappingsSupplier");
  }

  public ScreenKeyResolver(
      CustomScreenRegistry customRegistry, Map<String, ScreenType> staticBuiltInMappings) {
    this(customRegistry, () -> staticBuiltInMappings != null ? staticBuiltInMappings : Map.of());
  }

  /** Returns the underlying {@link CustomScreenRegistry}. */
  public CustomScreenRegistry customRegistry() {
    return customRegistry;
  }

  /**
   * Resolves a raw prompt screen key to its typed resolution.
   *
   * @param rawKey the raw prompt key string
   * @return the resolved outcome
   */
  public ScreenResolution resolve(String rawKey) {
    if (rawKey == null) {
      return new ScreenResolution.Unresolved("");
    }

    if (rawKey.startsWith("@")) {
      return new ScreenResolution.Preset(rawKey.substring(1));
    }

    String canonicalKey = rawKey.trim().toLowerCase(Locale.ROOT);

    ScreenType standardBuiltIn = ScreenType.fromBuiltInKey(canonicalKey);
    if (standardBuiltIn != null) {
      return new ScreenResolution.BuiltIn(standardBuiltIn);
    }

    Map<String, ScreenType> configuredMappings = builtInMappingsSupplier.get();
    if (configuredMappings != null && configuredMappings.containsKey(canonicalKey)) {
      ScreenType mapped = configuredMappings.get(canonicalKey);
      if (mapped != null) {
        return new ScreenResolution.BuiltIn(mapped);
      }
    }

    Optional<CustomScreenHandle> customHandle = customRegistry.getRegistration(canonicalKey);
    if (customHandle.isPresent() && customHandle.get().isActive()) {
      return new ScreenResolution.Custom(customHandle.get());
    }

    return new ScreenResolution.Unresolved(rawKey);
  }

  /**
   * Validates that candidate configured screen mappings do not collide with active custom screens.
   *
   * @param newMappings the new configured screen mappings to validate
   * @throws IllegalStateException if any key collides with an active custom screen
   */
  public void validateNoCollisions(Map<String, ScreenType> newMappings) {
    if (newMappings == null || newMappings.isEmpty()) {
      return;
    }
    customRegistry.validateMappingsAndPublish(newMappings, () -> {});
  }
}
