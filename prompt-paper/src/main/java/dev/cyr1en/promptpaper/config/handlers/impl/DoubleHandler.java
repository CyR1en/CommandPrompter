package dev.cyr1en.promptpaper.config.handlers.impl;

import com.cyr1en.kiso.mc.configuration.base.Config;
import dev.cyr1en.promptpaper.config.annotations.field.NodeDefault;
import dev.cyr1en.promptpaper.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;

/** Reads, writes, and defaults {@code double} config nodes. */
public class DoubleHandler implements ConfigTypeHandler<Double> {
    @Override
    public Double getValue(Config config, String nodeName, Field field) {
        return config.getDouble(nodeName);
    }

    @Override
    public void setValue(Config config, String nodeName, Object value, String[] comments) {
        config.set(nodeName, value, comments);
    }

    @Override
    public Double getDefault(Field field) {
        var defaultAnnotation = field.getAnnotation(NodeDefault.class);
        if (defaultAnnotation != null) {
            try {
                return Double.parseDouble(defaultAnnotation.value());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
