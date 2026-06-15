package dev.cyr1en.promptpaper.config;

/**
 * Identifies the screen implementation used for a prompt.
 *
 * <p>Mapped from the {@code screen-mappings} section of {@code prompt-config.yml}.
 */
public enum ScreenType {
    CHAT,
    ANVIL,
    SIGN,
    DIALOG,
    PLAYER
}
