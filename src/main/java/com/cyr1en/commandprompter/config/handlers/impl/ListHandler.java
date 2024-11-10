package com.cyr1en.commandprompter.config.handlers.impl;

import com.cyr1en.commandprompter.config.annotations.field.NodeDefault;
import com.cyr1en.commandprompter.config.handlers.ConfigTypeHandler;
import com.cyr1en.kiso.mc.configuration.base.Config;

import java.lang.reflect.Field;
import java.util.List;

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
        if (defaultAnnotation != null) {
            return List.of(defaultAnnotation.value().split(","));
        }
        return List.of();
    }
}
