package dev.cyr1en.promptcore.config.handlers.impl;

import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptcore.config.annotations.field.NodeDefault;
import dev.cyr1en.promptcore.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;

/** Reads, writes, and defaults {@code float} config nodes. */
public class FloatHandler implements ConfigTypeHandler<Float> {
  @Override
  public Float getValue(YamlDocument config, String nodeName, Field field) {
    return (float) config.getDouble(nodeName);
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
        return Float.parseFloat(defaultAnnotation.value());
      } catch (NumberFormatException e) {
        return 0.0f;
      }
    }
    return 0.0f;
  }
}
