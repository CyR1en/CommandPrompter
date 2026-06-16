package dev.cyr1en.promptcore.config.handlers.impl;

import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptcore.config.annotations.field.NodeDefault;
import dev.cyr1en.promptcore.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;

/** Reads, writes, and defaults {@code String} config nodes. */
public class StringHandler implements ConfigTypeHandler<String> {
  @Override
  public String getValue(YamlDocument config, String nodeName, Field field) {
    return config.getString(nodeName);
  }

  @Override
  public void setValue(YamlDocument config, String nodeName, Object value, String[] comments) {
    config.set(nodeName, value, comments);
  }

  @Override
  public String getDefault(Field field) {
    var defaultAnnotation = field.getAnnotation(NodeDefault.class);
    return defaultAnnotation != null ? defaultAnnotation.value() : "";
  }
}
