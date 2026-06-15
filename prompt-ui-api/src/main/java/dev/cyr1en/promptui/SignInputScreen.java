package dev.cyr1en.promptui;

import java.util.Map;

/**
 * Sign-specific configuration hooks. The NMS implementation reads the
 * provided map to decide on sign material, etc.
 */
public interface SignInputScreen extends InputScreen {

    /**
     * Apply configuration to this sign screen.
     *
     * <p>Should be called before {@link #open()}. The default implementation is a no-op
     * so non-sign subtypes can be used interchangeably in tests.
     *
     * @param config a map of configuration key → string value
     */
    default void configure(Map<String, String> config) {
    }
}
