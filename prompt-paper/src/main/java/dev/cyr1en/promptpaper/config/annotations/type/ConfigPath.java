package dev.cyr1en.promptpaper.config.annotations.type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the YAML filename for a {@link Configuration}-annotated class.
 *
 * <p>The value is a relative path within the plugin data folder (e.g. {@code "config.yml"}).
 * If absent, {@link java.lang.Class#getSimpleName()} + {@code ".yml"} is used.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigPath {
    /** Relative file path (e.g. {@code "prompt-config.yml"}). */
    String value();
}
