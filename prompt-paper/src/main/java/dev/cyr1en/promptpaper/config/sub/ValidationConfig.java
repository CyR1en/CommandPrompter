package dev.cyr1en.promptpaper.config.sub;

import dev.cyr1en.promptpaper.config.sub.ValidationEntry;

/**
 * Grouped configuration for input validation defaults.
 *
 * <p>The first two entries (integer, alpha) are the bundled samples. Servers can
 * add their own aliases under {@code Input-Validation.<Name>.{Alias,Regex,Err-Message}}
 * in the YAML.
 */
public record ValidationConfig(
        ValidationEntry integerSample,
        ValidationEntry alphaSample) {
}
