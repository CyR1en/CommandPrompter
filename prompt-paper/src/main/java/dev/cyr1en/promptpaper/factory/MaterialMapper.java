package dev.cyr1en.promptpaper.factory;

import dev.cyr1en.promptpaper.util.PluginLogger;
import org.bukkit.Material;

/**
 * Resolves a {@code button_icon} string from a {@code presets.json} entry to a Bukkit
 * {@link Material}. Used by the prompt factory and (in future scopes) by the screen
 * implementations that draw anvil/player-UI buttons.
 *
 * <h2>Resolution rules</h2>
 *
 * <ol>
 *   <li>{@code null} or blank input → {@link Material#PAPER}, with a warning.
 *   <li>The {@code minecraft:} namespace prefix is stripped (case-insensitive).
 *   <li>{@link Material#matchMaterial(String)} is tried. If it returns {@code null} the
 *       fallback {@link Material#PAPER} is returned and a warning is logged.
 * </ol>
 *
 * <p>Per the spec, a missing or invalid material must <b>not</b> crash the prompt flow —
 * the warning is the only signal to the operator.
 */
public final class MaterialMapper {

  private final PluginLogger logger;

  public MaterialMapper(PluginLogger logger) {
    this.logger = logger;
  }

  /**
   * Resolves a material name to a {@link Material}, falling back to {@link Material#PAPER}
   * on any failure.
   *
   * @param name the raw value from {@code button_icon} (may be {@code null} or blank)
   * @param context a short human-readable hint of where the icon came from (e.g. the prompt
   *     id) — included in the warning so operators can locate the bad entry
   * @return a non-null {@link Material}; always {@link Material#PAPER} on failure
   */
  public Material resolveOrDefault(String name, String context) {
    if (name == null || name.isBlank()) {
      logger.warn("[MaterialMapper] Missing button_icon in " + context + ", using PAPER fallback");
      return Material.PAPER;
    }
    var normalized = name;
    if (normalized.regionMatches(true, 0, "minecraft:", 0, 10)) {
      normalized = normalized.substring(10);
    }
    var mat = Material.matchMaterial(normalized);
    if (mat == null) {
      logger.warn(
          "[MaterialMapper] Invalid button_icon '"
              + name
              + "' in "
              + context
              + ", using PAPER fallback");
      return Material.PAPER;
    }
    return mat;
  }
}
