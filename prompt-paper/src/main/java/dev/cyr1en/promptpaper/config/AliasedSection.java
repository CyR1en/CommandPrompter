package dev.cyr1en.promptpaper.config;

import com.cyr1en.kiso.mc.configuration.base.Config;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Shared lookup logic for alias-based config sections (e.g. Input-Validation).
 *
 * <p>Implemented by config records that need to resolve a value by matching
 * a sub-section's key against a target alias string.
 */
public interface AliasedSection {

    /** Returns the raw SnakeYAML config backing this record. */
    Config rawConfig();

    /**
     * Searches a named section for a sub-entry whose {@code key} matches {@code keyVal},
     * then returns the value of {@code query} within that entry.
     *
     * @param section top-level section name (e.g. {@code "Input-Validation"})
     * @param key     the key to match (e.g. {@code "Alias"})
     * @param keyVal  the value to match against
     * @param query   the property to retrieve from the matched entry
     * @return the matched value, or an empty string if not found
     */
    default String getInputValidationValue(String section, String key, String keyVal, String query) {
        var raw = rawConfig();
        var validations = raw.getConfigurationSection(section);
        if (validations == null) return "";
        for (var k : validations.getKeys(false)) {
            var inner = validations.getConfigurationSection(k);
            var asserted = asserted(inner, key, keyVal, query);
            if (!asserted.isEmpty() && !asserted.isBlank()) return asserted;
        }
        return "";
    }

    /**
     * Checks a single validation sub-section for an alias match and returns the queried value.
     *
     * @return the value if the alias matches, or empty
     */
    default String asserted(ConfigurationSection section, String key, String keyVal, String query) {
        if (section == null) return "";
        if (!section.getKeys(false).contains("Alias")) return "";
        var cfgAlias = section.getString(key);
        cfgAlias = cfgAlias != null ? cfgAlias : "";
        if (cfgAlias.equals(keyVal)) {
            var regex = section.getString(query);
            return regex != null ? regex : "";
        }
        return "";
    }
}
