package dev.cyr1en.promptcore.config.handlers.impl;

import dev.cyr1en.promptcore.config.ConfigurationException;
import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptcore.config.annotations.field.NodeDefault;
import dev.cyr1en.promptcore.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.List;

/**
 * Reads, writes, and defaults {@code List} config nodes; defaults are comma-split from the
 * annotation.
 */
public class ListHandler implements ConfigTypeHandler<List<?>> {
  @Override
  public List<?> getValue(YamlDocument config, String nodeName, Field field) {
    final List<?> value;
    try {
      value = config.getList(nodeName);
    } catch (ConfigurationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ConfigurationException(
          "Invalid list configuration value for '" + nodeName + "'", e);
    }
    if (value == null) {
      throw new ConfigurationException(
          "Invalid list configuration value for '" + nodeName + "': list must not be null");
    }

    var elementType = elementType(field);
    for (var element : value) {
      if (element == null) {
        throw new ConfigurationException(
            "Invalid list configuration value for '"
                + nodeName
                + "': list elements must not be null");
      }
      if (element instanceof List<?> || element instanceof java.util.Map<?, ?>) {
        throw new ConfigurationException(
            "Invalid list configuration value for '"
                + nodeName
                + "': expected scalar elements, got "
                + element.getClass().getName());
      }
      if (elementType != null && !elementType.isInstance(element)) {
        throw new ConfigurationException(
            "Invalid list configuration value for '"
                + nodeName
                + "': expected elements of type "
                + elementType.getTypeName()
                + ", got "
                + element.getClass().getName());
      }
    }
    return List.copyOf(value);
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

  private static Class<?> elementType(Field field) {
    if (!(field.getGenericType() instanceof ParameterizedType parameterized)) return null;
    var argument = parameterized.getActualTypeArguments()[0];
    return argument instanceof Class<?> clazz ? clazz : null;
  }
}
