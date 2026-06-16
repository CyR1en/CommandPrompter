package dev.cyr1en.promptcore.config.handlers.impl;

import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptcore.config.annotations.field.IntegerConstraint;
import dev.cyr1en.promptcore.config.annotations.field.NodeDefault;
import dev.cyr1en.promptcore.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;

/**
 * Reads, writes, and defaults {@code int} config nodes, applying {@link IntegerConstraint} bounds.
 */
public class IntegerHandler implements ConfigTypeHandler<Integer> {
  @Override
  public Integer getValue(YamlDocument config, String nodeName, Field field) {
    int val = config.getInt(nodeName);
    IntegerConstraint constraint = field.getAnnotation(IntegerConstraint.class);
    if (constraint != null) val = Math.min(Math.max(val, constraint.min()), constraint.max());
    return val;
  }

  @Override
  public void setValue(YamlDocument config, String nodeName, Object value, String[] comments) {
    config.set(nodeName, value, comments);
  }

  @Override
  public Integer getDefault(Field field) {
    var defaultAnnotation = field.getAnnotation(NodeDefault.class);
    if (defaultAnnotation != null) {
      try {
        return Integer.parseInt(defaultAnnotation.value());
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
  }
}
