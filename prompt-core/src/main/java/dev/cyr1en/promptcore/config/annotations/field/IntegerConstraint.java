package dev.cyr1en.promptcore.config.annotations.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains an integer config node to a valid range.
 *
 * <p>Applied by {@link dev.cyr1en.promptcore.config.handlers.impl.IntegerHandler}: values are
 * clamped to [{@link #min()}, {@link #max()}] after reading from YAML.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IntegerConstraint {
  /** Minimum allowed value (inclusive). */
  int min();

  /** Maximum allowed value (inclusive). */
  int max();
}
