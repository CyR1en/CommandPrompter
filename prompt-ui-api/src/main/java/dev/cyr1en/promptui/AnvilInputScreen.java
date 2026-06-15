package dev.cyr1en.promptui;

import java.util.Map;

/**
 * Anvil-specific configuration hooks. The NMS implementation reads the
 * provided map to decide on title, item display, cancel-button visibility, etc.
 *
 * <p>Keys are anvil-specific. See {@code AnvilPromptScreen#buildConfig} in
 * {@code prompt-paper} for the canonical key set.
 */
public interface AnvilInputScreen extends InputScreen {

    /**
     * Apply configuration to this anvil screen.
     *
     * <p>Should be called before {@link #open()}. The default implementation is a no-op
     * so non-anvil subtypes can be used interchangeably in tests.
     *
     * @param config a map of configuration key → string value
     */
    default void configure(Map<String, String> config) {
    }
}
