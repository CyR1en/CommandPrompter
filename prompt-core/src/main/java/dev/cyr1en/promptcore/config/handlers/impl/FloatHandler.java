package dev.cyr1en.promptcore.config.handlers.impl;

import dev.cyr1en.promptcore.config.ConfigurationException;
import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptcore.config.annotations.field.NodeDefault;
import dev.cyr1en.promptcore.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;

/** Reads, writes, and defaults {@code float} config nodes. */
public class FloatHandler implements ConfigTypeHandler<Float> {
  @Override
  public Float getValue(YamlDocument config, String nodeName, Field field) {
    final double value;
    try {
      value = config.getDouble(nodeName);
    } catch (ConfigurationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ConfigurationException(
          "Invalid float configuration value for '" + nodeName + "'", e);
    }
    float narrowed = (float) value;
    if (!Float.isFinite(narrowed)) {
      throw new ConfigurationException(
          "Invalid float configuration value for '"
              + nodeName
              + "': value overflows a 32-bit float ("
              + value
              + ")");
    }
    return narrowed;
  }

  @Override
  public void setValue(YamlDocument config, String nodeName, Object value, String[] comments) {
    config.set(nodeName, value, comments);
  }

  @Override
  public Float getDefault(Field field) {
    var defaultAnnotation = field.getAnnotation(NodeDefault.class);
    if (defaultAnnotation != null) {
      try {
        var value = Float.parseFloat(defaultAnnotation.value());
        if (!Float.isFinite(value)) {
          throw new NumberFormatException("non-finite value");
        }
        return value;
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Invalid float default for '" + field.getName() + "': " + defaultAnnotation.value(), e);
      }
    }
    return 0.0f;
  }
}
