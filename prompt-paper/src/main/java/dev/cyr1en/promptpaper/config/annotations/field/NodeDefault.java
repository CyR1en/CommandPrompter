package dev.cyr1en.promptpaper.config.annotations.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides the default value written to YAML when a key is absent.
 *
 * <p>The string value is parsed by the matching {@link dev.cyr1en.promptpaper.config.handlers.ConfigTypeHandler}
 * for the field's type. Only effective when the field also carries {@link ConfigNode}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeDefault {
    /** Default value as a string (parsed by the type handler). */
    String value();
}
