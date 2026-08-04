package dev.cyr1en.promptcore.config.handlers.impl;

import dev.cyr1en.promptcore.config.ConfigurationException;
import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptcore.config.annotations.field.NodeDefault;
import dev.cyr1en.promptcore.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;

/** Reads, writes, and defaults {@code double} config nodes. */
public class DoubleHandler implements ConfigTypeHandler<Double> {
  @Override
  public Double getValue(YamlDocument config, String nodeName, Field field) {
    try {
      return config.getDouble(nodeName);
    } catch (ConfigurationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ConfigurationException(
          "Invalid double configuration value for '" + nodeName + "'", e);
    }
  }

  @Override
  public void setValue(YamlDocument config, String nodeName, Object value, String[] comments) {
    config.set(nodeName, value, comments);
  }

  @Override
  public Double getDefault(Field field) {
    var defaultAnnotation = field.getAnnotation(NodeDefault.class);
    if (defaultAnnotation != null) {
      try {
        var value = Double.parseDouble(defaultAnnotation.value());
        if (!Double.isFinite(value)) {
          throw new NumberFormatException("non-finite value");
        }
        return value;
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Invalid double default for '" + field.getName() + "': " + defaultAnnotation.value(),
            e);
      }
    }
    return 0.0;
  }
}
