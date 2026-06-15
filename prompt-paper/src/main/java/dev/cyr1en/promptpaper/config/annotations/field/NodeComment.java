package dev.cyr1en.promptpaper.config.annotations.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches YAML comment lines above a config node.
 *
 * <p>Each array element becomes a separate comment line. Colons are sanitized
 * to prevent YAML parsing errors. Long lines are word-wrapped to 50 characters.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeComment {
    /** Comment lines written above the YAML key. */
    String[] value();
}
