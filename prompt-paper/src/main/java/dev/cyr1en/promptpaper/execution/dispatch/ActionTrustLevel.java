package dev.cyr1en.promptpaper.execution.dispatch;

/**
 * Trust classification of an immediate action source.
 */
public enum ActionTrustLevel {
    /** Sourced from an immutable trusted preset configuration. */
    TRUSTED_PRESET,
    /** Sourced from untrusted player input or inline tags. */
    UNTRUSTED_INLINE,
    /** Sourced internally from an authorized console delegation flow. */
    CONSOLE_DELEGATED
}
