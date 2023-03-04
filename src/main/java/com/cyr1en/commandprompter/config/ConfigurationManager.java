package com.cyr1en.commandprompter.config;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.config.annotations.field.*;
import com.cyr1en.commandprompter.config.annotations.type.ConfigHeader;
import com.cyr1en.commandprompter.config.annotations.type.ConfigPath;
import com.cyr1en.commandprompter.config.annotations.type.Configuration;
import com.cyr1en.kiso.mc.configuration.base.Config;
import com.cyr1en.kiso.mc.configuration.base.ConfigManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    public ConfigurationManager(CommandPrompter plugin) {
        this.configManager = new ConfigManager(plugin);
    }

    public <T> T getConfig(Class<T> configClass) {
        if (configClass.getAnnotation(Configuration.class) == null)
            return null;

        var config = initConfigFile(configClass);

        initializeConfig(configClass, config);

        var configValues = new ArrayList<>();
        configValues.add(config);

        for (Field declaredField : configClass.getDeclaredFields()) {
            if (declaredField.getAnnotation(ConfigNode.class) == null) continue;

            var nameAnnotation = declaredField.getAnnotation(NodeName.class);
            if (declaredField.getType().equals(int.class)) {
                var val = config.getInt(nameAnnotation.value());
                var constraint = declaredField.getAnnotation(IntegerConstraint.class);
                if (constraint != null)
                    val = val > constraint.max() ? constraint.max() : Math.max(val, constraint.min());
                configValues.add(val);
            } else if (declaredField.getType().equals(boolean.class))
                configValues.add(config.getBoolean(nameAnnotation.value()));
            else if (declaredField.getType().equals(double.class))
                configValues.add(config.getDouble(nameAnnotation.value()));
            else if (declaredField.getType().equals(List.class))
                configValues.add(config.getList(nameAnnotation.value()));
            else configValues.add(config.getString(nameAnnotation.value()));
        }
        try {
            // Records only have 1 constructor so just access index 0
            var recordConfig = configClass.getDeclaredConstructors()[0].newInstance(configValues.toArray());
            @SuppressWarnings("unchecked") var out = (T) recordConfig;
            return out;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    public <T> T reload(Class<T> configClass) {
        return getConfig(configClass);
    }

    private void initializeConfig(Class<?> configClass, Config config) {
        var fields = configClass.getDeclaredFields();
        for (Field field : fields)
            initializeField(field, config);
    }

    private Config initConfigFile(Class<?> configClass) {
        var pathAnnotation = configClass.getAnnotation(ConfigPath.class);
        var filePath = pathAnnotation == null ? configClass.getSimpleName() : pathAnnotation.value();

        var headerAnnotation = configClass.getAnnotation(ConfigHeader.class);
        var header = headerAnnotation == null ? new String[]{configClass.getSimpleName(), "Configuration"} :
                headerAnnotation.value();
        return configManager.getNewConfig(filePath, header);
    }

    private void initializeField(Field field, Config config) {
        if (field.getAnnotation(ConfigNode.class) == null) return;

        var nameAnnotation = field.getAnnotation(NodeName.class);
        var nodeName = nameAnnotation == null ? field.getName() : nameAnnotation.value();

        var defaultAnnotation = field.getAnnotation(NodeDefault.class);
        var nodeDefault = defaultAnnotation == null ? constructDefaultField(field) : parseDefault(field);

        var commentAnnotation = field.getAnnotation(NodeComment.class);
        var nodeComment = commentAnnotation == null ? new String[]{} : commentAnnotation.value();

        if (config.get(nodeName) != null) return;
        config.set(nodeName, nodeDefault, nodeComment);
        config.saveConfig();
    }

    private Object constructDefaultField(Field f) {
        try {
            if (f.getType().isPrimitive()) {
                if (f.getType().equals(int.class))
                    return 0;
                if (f.getType().equals(boolean.class))
                    return false;
                if (f.getType().equals(double.class))
                    return 0.0;
                if (f.getType().equals(List.class))
                    return new ArrayList<>();
            }
            return f.getType().getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException |
                 InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Object parseDefault(Field field) {
        var defaultAnnotation = field.getAnnotation(NodeDefault.class);
        if (field.getType().equals(int.class))
            return Integer.valueOf(defaultAnnotation.value());
        if (field.getType().equals(boolean.class))
            return Boolean.valueOf(defaultAnnotation.value());
        if (field.getType().equals(double.class))
            return Double.valueOf(defaultAnnotation.value());
        if (field.getType().equals(List.class))
            return Arrays.stream(defaultAnnotation.value().split(",\\s+")).toList();
        return defaultAnnotation.value();
    }
}
