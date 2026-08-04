package dev.cyr1en.promptcore.config;

/**
 * Thrown when a configuration document cannot be read, converted, or updated safely.
 *
 * <p>This is an {@link IllegalStateException} so callers that previously handled configuration
 * failures as state errors remain source-compatible, while the dedicated type lets callers
 * distinguish malformed configuration from unrelated programming errors.
 */
public class ConfigurationException extends IllegalStateException {

  public ConfigurationException(String message) {
    super(message);
  }

  public ConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
