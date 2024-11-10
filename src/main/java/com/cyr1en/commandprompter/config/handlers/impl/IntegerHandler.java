package com.cyr1en.commandprompter.config.handlers.impl;

import com.cyr1en.commandprompter.config.annotations.field.IntegerConstraint;
import com.cyr1en.commandprompter.config.annotations.field.NodeDefault;
import com.cyr1en.commandprompter.config.handlers.ConfigTypeHandler;
import com.cyr1en.kiso.mc.configuration.base.Config;

import java.lang.reflect.Field;

public class IntegerHandler implements ConfigTypeHandler<Integer> {
    @Override
    public Integer getValue(Config config, String nodeName, Field field) {
        int val = config.getInt(nodeName);
        IntegerConstraint constraint = field.getAnnotation(IntegerConstraint.class);
        if (constraint != null)
            val = Math.min(Math.max(val, constraint.min()), constraint.max());
        return val;
    }

    @Override
    public void setValue(Config config, String nodeName, Object value, String[] comments) {
        config.set(nodeName, value, comments);
    }

    @Override
    public Integer getDefault(Field field) {
        var defaultAnnotation = field.getAnnotation(NodeDefault.class);
        return defaultAnnotation != null ? Integer.parseInt(defaultAnnotation.value()) : 0;
    }
}
