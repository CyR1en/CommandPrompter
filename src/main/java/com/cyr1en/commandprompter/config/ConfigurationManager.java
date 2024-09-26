package com.cyr1en.commandprompter.config;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.config.annotations.field.*;
import com.cyr1en.commandprompter.config.annotations.type.ConfigHeader;
import com.cyr1en.commandprompter.config.annotations.type.ConfigPath;
import com.cyr1en.commandprompter.config.annotations.type.Configuration;
import com.cyr1en.commandprompter.config.handlers.ConfigTypeHandlerFactory;
import com.cyr1en.kiso.mc.configuration.base.Config;
import com.cyr1en.kiso.mc.configuration.base.ConfigManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Class that manages your plugin's configuration(s)
 *
 * <p>
 * This class will turn a configuration record into a YAML configuration file.
 * For this class to work, a record must be:
 * - Annotated with {@link Configuration}.
 * - Fields of the record must be annotated with {@link ConfigNode}
 *
 * <p>
 * Reflection is heavily used in this class to grab the types of each field in a
 * record and initializes the defaults for the config file. It then uses reflection
 * again to query those default values in the config file to instantiate the actual
 * record with its respective data.
 */
public class ConfigurationManager {

    private final ConfigManager configManager;
    private final PluginLogger logger;

    /**
     * Constructs a new ConfigurationManager for a given plugin.
     *
     * @param plugin The plugin instance which this manager will handle configurations for.
     */
    public ConfigurationManager(CommandPrompter plugin) {
        this.configManager = new ConfigManager(plugin);
        this.logger = plugin.getPluginLogger();
    }

    /**
     * Retrieves or initializes the configuration for a specified configuration class.
     *
     * @param <T>         The type of the configuration record.
     * @param configClass The class of the configuration record.
     * @return An instance of T with values from the configuration, or null if configuration class is not annotated properly.
     */
    public <T> T getConfig(Class<T> configClass) {
        if (configClass.getAnnotation(Configuration.class) == null)
            return null;

        var config = initConfigFile(configClass);

        initializeConfig(configClass, config);

        var configValues = new ArrayList<>();
        configValues.add(config);

        for (Field field : configClass.getDeclaredFields()) {
            if (field.getAnnotation(ConfigNode.class) == null) continue;

            var nameAnnotation = field.getAnnotation(NodeName.class);
            String nodeName = nameAnnotation != null ? nameAnnotation.value() : field.getName();

            var handler = ConfigTypeHandlerFactory.getHandler(field.getType());

            if (field.isAnnotationPresent(Match.class)) {
                var matchAnnotation = field.getAnnotation(Match.class);
                var regex = matchAnnotation.regex();
                var pattern = Pattern.compile(regex);
                var res = pattern.matcher(config.getString(nodeName)).matches();
                if (!res) {
                    logger.warn("Configured value for " + nodeName + " is invalid! Using default.");
                    configValues.add(handler.getDefault(field));
                    continue;
                }
            }

            configValues.add(handler.getValue(config, nodeName, field));
        }
        try {
            // Since records in Java have canonical constructors, we directly use the first constructor
            var recordConfig = configClass.getDeclaredConstructors()[0].newInstance(configValues.toArray());
            @SuppressWarnings("unchecked") var out = (T) recordConfig;
            return out;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            logger.err("Failed to instantiate config: " + configClass.getSimpleName());
        }
        return null;
    }

    /**
     * Reloads the configuration for the given configuration class. This method simply calls getConfig.
     *
     * @param <T>         The type of the configuration record.
     * @param configClass The class of the configuration record to reload.
     * @return A reloaded instance of T or null if reload fails or configClass is invalid.
     */
    public <T> T reload(Class<T> configClass) {
        return getConfig(configClass);
    }

    /**
     * Initializes the configuration file with default values defined in the record.
     *
     * @param configClass The class of the record for which configuration needs to be initialized.
     * @param config      The Config object representing the YAML file.
     */
    private void initializeConfig(Class<?> configClass, Config config) {
        var fields = configClass.getDeclaredFields();
        for (Field field : fields)
            initializeField(field, config);
    }

    /**
     * Initializes or retrieves the configuration file for the given configuration class.
     *
     * @param configClass The class of the configuration record.
     * @return A Config object representing the configuration file.
     */
    private Config initConfigFile(Class<?> configClass) {
        // Determine file path and header for the config file
        var pathAnnotation = configClass.getAnnotation(ConfigPath.class);
        var filePath = pathAnnotation == null ? configClass.getSimpleName() : pathAnnotation.value();

        var headerAnnotation = configClass.getAnnotation(ConfigHeader.class);
        var header = headerAnnotation == null ? new String[]{configClass.getSimpleName(), "Configuration"} :
                headerAnnotation.value();
        return configManager.getNewConfig(filePath, header);
    }

    /**
     * Initializes a single field of the configuration with its default value or annotated default.
     *
     * @param field  The field from the record to initialize in the config.
     * @param config The Config object where the field should be initialized.
     */
    private void initializeField(Field field, Config config) {
        if (field.getAnnotation(ConfigNode.class) == null) return;

        var nameAnnotation = field.getAnnotation(NodeName.class);
        var nodeName = nameAnnotation != null ? nameAnnotation.value() : field.getName();

        var handler = ConfigTypeHandlerFactory.getHandler(field.getType());

        var defaultAnnotation = field.getAnnotation(NodeDefault.class);
        var nodeDefault = defaultAnnotation != null ? parseDefault(field) : handler.getDefault(field);

        var commentAnnotation = field.getAnnotation(NodeComment.class);
        var nodeComment = commentAnnotation != null ? commentAnnotation.value() : new String[]{};

        if (config.get(nodeName) == null) {
            handler.setValue(config, nodeName, nodeDefault, nodeComment);
            config.saveConfig();
        }
    }

    /**
     * Constructs a default value for a field when no default is provided via annotations.
     *
     * @param f The field for which to construct a default value.
     * @return An object representing the default value for the field, or null on failure.
     */
    private Object constructDefaultField(Field f) {

        try {
            if (f.getType().isPrimitive()) {
                var handler = ConfigTypeHandlerFactory.getHandler(f.getType());
                return handler.getDefault(f);
            }
            return f.getType().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.err("Failed to instantiate default value for field: " + f.getName());
        }
        return null;
    }

    /**
     * Parses the default value from the @NodeDefault annotation for a field.
     *
     * @param field The field with a @NodeDefault annotation.
     * @return The parsed default value as an object, according to the field's type.
     */
    private Object parseDefault(Field field) {
        var handler = ConfigTypeHandlerFactory.getHandler(field.getType());
        return handler.getDefault(field);
    }
}
