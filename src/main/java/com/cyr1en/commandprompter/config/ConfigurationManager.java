package com.cyr1en.commandprompter.config;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.config.annotations.field.ConfigNode;
import com.cyr1en.commandprompter.config.annotations.field.NodeComment;
import com.cyr1en.commandprompter.config.annotations.field.NodeDefault;
import com.cyr1en.commandprompter.config.annotations.field.NodeName;
import com.cyr1en.commandprompter.config.annotations.type.ConfigHeader;
import com.cyr1en.commandprompter.config.annotations.type.ConfigPath;
import com.cyr1en.commandprompter.config.annotations.type.Configuration;
import com.cyr1en.kiso.mc.configuration.base.Config;
import com.cyr1en.kiso.mc.configuration.base.ConfigManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;

public class ConfigurationManager {

    private final ConfigManager configManager;
    private final CommandPrompter plugin;

    public ConfigurationManager(CommandPrompter plugin) {
        this.plugin = plugin;
        this.configManager = new ConfigManager(plugin);
    }

    public <T> T getConfig(Class<T> configClass) {
        if (configClass.getAnnotation(Configuration.class) == null)
            return null;

        var config = initConfigFile(configClass);

        initializeConfig(configClass, config);

        var configValues = new ArrayList<>();
        for (Field declaredField : configClass.getDeclaredFields()) {
            if (declaredField.getAnnotation(ConfigNode.class) == null) continue;

            var nameAnnotation = declaredField.getAnnotation(NodeName.class);
            if (declaredField.getType().equals(int.class))
                configValues.add(config.getInt(nameAnnotation.value()));
            else if (declaredField.getType().equals(boolean.class))
                configValues.add(config.getBoolean(nameAnnotation.value()));
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
            // add more primitives in the future
            if (f.getType().isPrimitive()) {
                if (f.getType().equals(int.class))
                    return 0;
                if (f.getType().equals(boolean.class))
                    return false;
            }
            return f.getType().getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException |
                InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Object parseDefault(Field field) {
        // add more primitives in the future
        var defaultAnnotation = field.getAnnotation(NodeDefault.class);
        if (field.getType().equals(int.class))
            return Integer.valueOf(defaultAnnotation.value());
        if (field.getType().equals(boolean.class))
            return Boolean.valueOf(defaultAnnotation.value());
        return defaultAnnotation.value();
    }
}
