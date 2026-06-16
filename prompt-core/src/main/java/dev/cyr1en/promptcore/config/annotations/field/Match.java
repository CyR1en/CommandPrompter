package dev.cyr1en.promptcore.config.annotations.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a string config value against a regular expression at load time.
 *
 * <p>If the YAML value does not fully match {@link #regex()}, an {@link IllegalArgumentException}
 * is thrown during {@link dev.cyr1en.promptcore.config.RecordConfigLoader#getConfig}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Match {
  /** Regex pattern the string value must fully match. */
  String regex();
}
