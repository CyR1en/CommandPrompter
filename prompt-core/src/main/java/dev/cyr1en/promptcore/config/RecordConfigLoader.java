package dev.cyr1en.promptcore.config;

import dev.cyr1en.promptcore.config.annotations.field.ConfigNode;
import dev.cyr1en.promptcore.config.annotations.field.Match;
import dev.cyr1en.promptcore.config.annotations.field.NodeComment;
import dev.cyr1en.promptcore.config.annotations.field.NodeDefault;
import dev.cyr1en.promptcore.config.annotations.field.NodeName;
import dev.cyr1en.promptcore.config.annotations.type.ConfigHeader;
import dev.cyr1en.promptcore.config.annotations.type.ConfigPath;
import dev.cyr1en.promptcore.config.annotations.type.Configuration;
import dev.cyr1en.promptcore.config.annotations.type.SectionComment;
import dev.cyr1en.promptcore.config.annotations.type.SectionComments;
import dev.cyr1en.promptcore.config.handlers.ConfigTypeHandlerFactory;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class RecordConfigLoader {

  private final File dataFolder;

  public RecordConfigLoader(File dataFolder) {
    this.dataFolder = dataFolder;
  }

  public <T> T getConfig(Class<T> configClass) {
    if (configClass.getAnnotation(Configuration.class) == null)
      throw new IllegalArgumentException(
          "Class " + configClass.getSimpleName() + " is missing @Configuration annotation");

    var pathAnnotation = configClass.getAnnotation(ConfigPath.class);
    var filePath =
        pathAnnotation == null ? configClass.getSimpleName() + ".yml" : pathAnnotation.value();
    if (!filePath.endsWith(".yml")) filePath += ".yml";

    File file = new File(dataFolder, filePath);
    SnakeYamlDocument config = new SnakeYamlDocument(file);

    var headerAnnotation = configClass.getAnnotation(ConfigHeader.class);
    var header =
        headerAnnotation == null
            ? new String[] {configClass.getSimpleName(), "Configuration"}
            : headerAnnotation.value();

    var configValues = new ArrayList<>();
    configValues.add(config);

    for (Field field : configClass.getDeclaredFields()) {
      if (field.getAnnotation(ConfigNode.class) == null) continue;

      var nameAnnotation = field.getAnnotation(NodeName.class);
      String nodeName = nameAnnotation != null ? nameAnnotation.value() : field.getName();

      var handler = ConfigTypeHandlerFactory.getHandler(field.getType());

      if (config.get(nodeName) == null) {
        var defaultAnnotation = field.getAnnotation(NodeDefault.class);
        var nodeDefault = defaultAnnotation != null ? handler.getDefault(field) : null;

        var commentAnnotation = field.getAnnotation(NodeComment.class);
        var nodeComment = commentAnnotation != null ? commentAnnotation.value() : new String[] {};

        if (nodeDefault != null) {
          handler.setValue(config, nodeName, nodeDefault, nodeComment);
        }
      } else {
        var commentAnnotation = field.getAnnotation(NodeComment.class);
        if (commentAnnotation != null) {
          config.set(nodeName, config.get(nodeName), commentAnnotation.value());
        }
      }

      if (field.isAnnotationPresent(Match.class)) {
        var matchAnnotation = field.getAnnotation(Match.class);
        var regex = matchAnnotation.regex();
        final Pattern pattern;
        try {
          pattern = Pattern.compile(regex);
        } catch (RuntimeException e) {
          throw new ConfigurationException(
              "Invalid regex for configuration path '"
                  + nodeName
                  + "' in "
                  + file.getAbsolutePath()
                  + ": "
                  + regex,
              e);
        }
        String valStr = config.getString(nodeName);
        if (valStr != null) {
          var res = pattern.matcher(valStr).matches();
          if (!res) {
            throw new IllegalArgumentException(
                "Configured value for '"
                    + nodeName
                    + "' does not match required pattern: "
                    + regex
                    + " (got: "
                    + valStr
                    + ")");
          }
        }
      }

      configValues.add(handler.getValue(config, nodeName, field));
    }

    var sectionComments = configClass.getAnnotation(SectionComments.class);
    if (sectionComments != null) {
      for (SectionComment sc : sectionComments.value()) {
        config.setComments(sc.path(), sc.comments());
      }
    }

    try {
      var recordConfig =
          configClass.getDeclaredConstructors()[0].newInstance(configValues.toArray());
      @SuppressWarnings("unchecked")
      var out = (T) recordConfig;
      // Dump only after every value and the record constructor have validated successfully. A
      // malformed value therefore cannot cause a partially defaulted configuration to be saved.
      config.save(header);
      return out;
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException runtime) throw runtime;
      throw new RuntimeException(
          "Failed to instantiate config: " + configClass.getSimpleName(), e.getCause());
    } catch (InstantiationException | IllegalAccessException e) {
      throw new RuntimeException("Failed to instantiate config: " + configClass.getSimpleName(), e);
    }
  }

  public <T> T reload(Class<T> configClass) {
    return getConfig(configClass);
  }
}
