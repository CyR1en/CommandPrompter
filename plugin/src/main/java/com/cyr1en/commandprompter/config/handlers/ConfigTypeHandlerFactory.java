package com.cyr1en.commandprompter.config.handlers;

import com.cyr1en.commandprompter.config.handlers.impl.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigTypeHandlerFactory {
    private static final Map<Class<?>, ConfigTypeHandler<?>> handlers = new HashMap<>();

    static {
        handlers.put(int.class, new IntegerHandler());
        handlers.put(boolean.class, new BooleanHandler());
        handlers.put(String.class, new StringHandler());
        handlers.put(double.class, new DoubleHandler());
        handlers.put(List.class, new ListHandler());
    }

    public static ConfigTypeHandler<?> getHandler(Class<?> type) {
        if (handlers.containsKey(type)) {
            return handlers.get(type);
        }
        return handlers.getOrDefault(type, new StringHandler()); // Fallback handler
    }
}
