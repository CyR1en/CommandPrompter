package dev.cyr1en.promptcore.config.handlers;

import dev.cyr1en.promptcore.config.YamlDocument;
import java.lang.reflect.Field;

/**
 * SPI for reading, writing, and defaulting a specific Java type in YAML configs.
 *
 * <p>Implementations handle the conversion between SnakeYAML values and the target Java type.
 * Register new types in {@link ConfigTypeHandlerFactory}.
 *
 * @param <T> the Java type this handler manages
 */
public interface ConfigTypeHandler<T> {

  /** Reads the value for {@code nodeName} from the config. */
  T getValue(YamlDocument config, String nodeName, Field field);

  /** Writes {@code value} (with optional comment lines) to the config under {@code nodeName}. */
  void setValue(YamlDocument config, String nodeName, Object value, String[] comments);

  /**
   * Returns the default value parsed from the field's {@link
   * dev.cyr1en.promptcore.config.annotations.field.NodeDefault} annotation.
   */
  T getDefault(Field field);
}
