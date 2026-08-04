package dev.cyr1en.promptcore.config.handlers.impl;

import dev.cyr1en.promptcore.config.ConfigurationException;
import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptcore.config.annotations.field.NodeDefault;
import dev.cyr1en.promptcore.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;

/** Reads, writes, and defaults {@code boolean} config nodes. */
public class BooleanHandler implements ConfigTypeHandler<Boolean> {
  @Override
  public Boolean getValue(YamlDocument config, String nodeName, Field field) {
    try {
      return config.getBoolean(nodeName);
    } catch (ConfigurationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ConfigurationException(
          "Invalid boolean configuration value for '" + nodeName + "'", e);
    }
  }

  @Override
  public void setValue(YamlDocument config, String nodeName, Object value, String[] comments) {
    config.set(nodeName, value, comments);
  }

  @Override
  public Boolean getDefault(Field field) {
    var defaultAnnotation = field.getAnnotation(NodeDefault.class);
    if (defaultAnnotation == null) return false;
    var value = defaultAnnotation.value();
    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
      throw new IllegalArgumentException(
          "Invalid boolean default for '" + field.getName() + "': " + value);
    }
    return Boolean.parseBoolean(value);
  }
}
