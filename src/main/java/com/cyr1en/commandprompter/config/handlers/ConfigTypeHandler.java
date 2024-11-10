package com.cyr1en.commandprompter.config.handlers;

import com.cyr1en.kiso.mc.configuration.base.Config;

import java.lang.reflect.Field;

public interface ConfigTypeHandler<T> {
    T getValue(Config config, String nodeName, Field field);

    void setValue(Config config, String nodeName, Object value, String[] comments);

    T getDefault(Field field);
}