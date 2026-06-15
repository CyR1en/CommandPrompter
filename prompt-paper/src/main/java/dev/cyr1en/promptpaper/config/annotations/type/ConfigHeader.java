package dev.cyr1en.promptpaper.config.annotations.type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides header comment lines written to the top of the generated YAML file.
 *
 * <p>Each array element becomes a comment line. Long lines are word-wrapped
 * to 50 characters, and colons are sanitized to avoid YAML parsing issues.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigHeader {
    /** Comment lines written as YAML header comments. */
    String[] value();
}
