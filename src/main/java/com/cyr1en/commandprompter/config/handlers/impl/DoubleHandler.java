package com.cyr1en.commandprompter.config.handlers.impl;

import com.cyr1en.commandprompter.config.annotations.field.NodeDefault;
import com.cyr1en.commandprompter.config.handlers.ConfigTypeHandler;
import com.cyr1en.kiso.mc.configuration.base.Config;

import java.lang.reflect.Field;

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
        return defaultAnnotation != null ? Double.parseDouble(defaultAnnotation.value()) : 0.0;
    }
}
