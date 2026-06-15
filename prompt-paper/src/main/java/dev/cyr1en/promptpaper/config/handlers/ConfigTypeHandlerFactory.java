package dev.cyr1en.promptpaper.config.handlers;

import dev.cyr1en.promptpaper.config.handlers.impl.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that maps Java primitive/wrapper types to their {@link ConfigTypeHandler}.
 *
 * <p>Built-in handlers cover {@code int}, {@code boolean}, {@code double},
 * {@code float}, {@code String}, and {@code List}. Unregistered types fall
 * back to {@link StringHandler}.
 */
public class ConfigTypeHandlerFactory {
    private static final Map<Class<?>, ConfigTypeHandler<?>> handlers = new HashMap<>();

    static {
        handlers.put(int.class, new IntegerHandler());
        handlers.put(boolean.class, new BooleanHandler());
        handlers.put(String.class, new StringHandler());
        handlers.put(double.class, new DoubleHandler());
        handlers.put(float.class, new FloatHandler());
        handlers.put(List.class, new ListHandler());
    }

    /** Returns the handler for {@code type}, falling back to {@link StringHandler} if unregistered. */
    public static ConfigTypeHandler<?> getHandler(Class<?> type) {
        if (handlers.containsKey(type)) return handlers.get(type);
        return handlers.getOrDefault(type, new StringHandler());
    }
}
