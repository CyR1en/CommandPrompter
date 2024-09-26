package com.cyr1en.commandprompter.config.handlers.impl;

import com.cyr1en.commandprompter.config.annotations.field.NodeDefault;
import com.cyr1en.commandprompter.config.handlers.ConfigTypeHandler;
import com.cyr1en.kiso.mc.configuration.base.Config;

import java.lang.reflect.Field;

public class StringHandler implements ConfigTypeHandler<String> {
    @Override
    public String getValue(Config config, String nodeName, Field field) {
        return config.getString(nodeName);
    }

    @Override
    public void setValue(Config config, String nodeName, Object value, String[] comments) {
        config.set(nodeName, value, comments);
    }

    @Override
    public String getDefault(Field field) {
        var defaultAnnotation = field.getAnnotation(NodeDefault.class);
        return defaultAnnotation != null ? defaultAnnotation.value() : "";
    }
}
