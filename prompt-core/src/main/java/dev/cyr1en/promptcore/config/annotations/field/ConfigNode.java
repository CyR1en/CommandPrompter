package dev.cyr1en.promptcore.config.annotations.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record component as a persisted configuration node.
 *
 * <p>{@link dev.cyr1en.promptcore.config.RecordConfigLoader} scans for this annotation when
 * loading/reloading. The YAML key defaults to the field name; override with {@link NodeName}. Pair
 * with {@link NodeDefault} to auto-write missing keys on first load.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigNode {}
