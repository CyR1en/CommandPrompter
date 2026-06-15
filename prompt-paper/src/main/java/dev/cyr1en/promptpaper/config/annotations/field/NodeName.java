package dev.cyr1en.promptpaper.config.annotations.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the YAML key for a {@link ConfigNode}-annotated field.
 *
 * <p>Supports dotted paths (e.g. {@code "PlayerUI.Size"}) for nested YAML sections.
 * Without this, the Java field name is used as-is.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeName {
    /** The YAML key (may include dots for nested sections). */
    String value();
}
