package com.cyr1en.commandprompter.config;

import com.cyr1en.kiso.mc.configuration.base.Config;
import org.bukkit.configuration.ConfigurationSection;

public interface AliasedSection {

    Config rawConfig();

    /**
     * Get a value of a key in a validation section using a different key value to check if we're in the right section.
     *
     * @param key    key to check
     * @param keyVal value of the key to check
     * @param query  key to get the value of
     * @return value of the query key
     */
    default String getInputValidationValue(String section, String key, String keyVal, String query) {
        var raw = rawConfig();
        var validations = raw.getConfigurationSection(section);

        for (var k : validations.getKeys(false)) {
            var inner = validations.getConfigurationSection(k);
            var asserted = asserted(inner, key, keyVal, query);
            if (!asserted.isEmpty() && !asserted.isBlank()) return asserted;
        }
        return "";
    }

    default String asserted(ConfigurationSection section, String key, String keyVal, String query) {
        if (section == null) return "";
        // Still check for alias because we are anchoring each input validation with an alias.
        // Therefore, the alias must always be present.
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
