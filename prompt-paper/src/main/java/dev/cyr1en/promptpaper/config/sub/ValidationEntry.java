package dev.cyr1en.promptpaper.config.sub;

/** A single input-validation alias entry (alias, regex, error message). */
public record ValidationEntry(
        String alias,
        String regex,
        String errMessage) {
}
