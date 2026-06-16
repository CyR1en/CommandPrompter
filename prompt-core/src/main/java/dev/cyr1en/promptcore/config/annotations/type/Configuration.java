package dev.cyr1en.promptcore.config.annotations.type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record as a configuration class managed by {@link
 * dev.cyr1en.promptcore.config.RecordConfigLoader}.
 *
 * <p>Must be paired with {@link ConfigPath} to specify the YAML file name. Each field intended for
 * persistence should carry {@link dev.cyr1en.promptcore.config.annotations.field.ConfigNode}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Configuration {}
