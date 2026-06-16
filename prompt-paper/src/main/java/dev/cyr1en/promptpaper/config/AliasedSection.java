package dev.cyr1en.promptpaper.config;

import dev.cyr1en.promptcore.config.YamlDocument;
import java.util.Set;

public interface AliasedSection {

    YamlDocument rawConfig();

    default String getInputValidationValue(String section, String key, String keyVal, String query) {
        var raw = rawConfig();
        Set<String> validations = raw.getKeys(section);
        if (validations.isEmpty()) return "";
        for (var k : validations) {
            String subPath = section + "." + k;
            var asserted = asserted(raw, subPath, key, keyVal, query);
            if (!asserted.isEmpty() && !asserted.isBlank()) return asserted;
        }
        return "";
    }

    default String asserted(YamlDocument doc, String basePath, String key, String keyVal, String query) {
        Set<String> keys = doc.getKeys(basePath);
        if (!keys.contains("Alias")) return "";
        var cfgAlias = doc.getString(basePath + "." + key);
        cfgAlias = cfgAlias != null ? cfgAlias : "";
        if (cfgAlias.equals(keyVal)) {
            var regex = doc.getString(basePath + "." + query);
            return regex != null ? regex : "";
        }
        return "";
    }
}
