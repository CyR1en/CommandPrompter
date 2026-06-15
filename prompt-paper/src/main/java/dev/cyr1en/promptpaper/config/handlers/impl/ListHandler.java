package dev.cyr1en.promptpaper.config.handlers.impl;

import com.cyr1en.kiso.mc.configuration.base.Config;
import dev.cyr1en.promptpaper.config.annotations.field.NodeDefault;
import dev.cyr1en.promptpaper.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;
import java.util.List;

/** Reads, writes, and defaults {@code List} config nodes; defaults are comma-split from the annotation. */
public class ListHandler implements ConfigTypeHandler<List<?>> {
    @Override
    public List<?> getValue(Config config, String nodeName, Field field) {
        return config.getList(nodeName);
    }

    @Override
    public void setValue(Config config, String nodeName, Object value, String[] comments) {
        config.set(nodeName, value, comments);
    }

    @Override
    public List<?> getDefault(Field field) {
        var defaultAnnotation = field.getAnnotation(NodeDefault.class);
        if (defaultAnnotation != null) return List.of(defaultAnnotation.value().split(",")).stream().map(String::trim).toList();
        return List.of();
    }
}
