package dev.cyr1en.promptpaper.config.handlers.impl;

import com.cyr1en.kiso.mc.configuration.base.Config;
import dev.cyr1en.promptpaper.config.annotations.field.NodeDefault;
import dev.cyr1en.promptpaper.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;

/** Reads, writes, and defaults {@code boolean} config nodes. */
public class BooleanHandler implements ConfigTypeHandler<Boolean> {
    @Override
    public Boolean getValue(Config config, String nodeName, Field field) {
        return config.getBoolean(nodeName);
    }

    @Override
    public void setValue(Config config, String nodeName, Object value, String[] comments) {
        config.set(nodeName, value, comments);
    }

    @Override
    public Boolean getDefault(Field field) {
        var defaultAnnotation = field.getAnnotation(NodeDefault.class);
        return defaultAnnotation != null && Boolean.parseBoolean(defaultAnnotation.value());
    }
}
