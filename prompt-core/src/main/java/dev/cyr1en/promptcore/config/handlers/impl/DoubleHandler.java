package dev.cyr1en.promptcore.config.handlers.impl;

import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptcore.config.annotations.field.NodeDefault;
import dev.cyr1en.promptcore.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;

/** Reads, writes, and defaults {@code double} config nodes. */
public class DoubleHandler implements ConfigTypeHandler<Double> {
  @Override
  public Double getValue(YamlDocument config, String nodeName, Field field) {
    return config.getDouble(nodeName);
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
        return Double.parseDouble(defaultAnnotation.value());
      } catch (NumberFormatException e) {
        return 0.0;
      }
    }
    return 0.0;
  }
}
