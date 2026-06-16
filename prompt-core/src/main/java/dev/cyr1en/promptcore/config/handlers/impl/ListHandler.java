package dev.cyr1en.promptcore.config.handlers.impl;

import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptcore.config.annotations.field.NodeDefault;
import dev.cyr1en.promptcore.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Reads, writes, and defaults {@code List} config nodes; defaults are comma-split from the
 * annotation.
 */
public class ListHandler implements ConfigTypeHandler<List<?>> {
  @Override
  public List<?> getValue(YamlDocument config, String nodeName, Field field) {
    return config.getList(nodeName);
  }

  @Override
  public void setValue(YamlDocument config, String nodeName, Object value, String[] comments) {
    config.set(nodeName, value, comments);
  }

  @Override
  public List<?> getDefault(Field field) {
    var defaultAnnotation = field.getAnnotation(NodeDefault.class);
    if (defaultAnnotation != null)
      return List.of(defaultAnnotation.value().split(",")).stream().map(String::trim).toList();
    return List.of();
  }
}
