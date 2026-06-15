package dev.cyr1en.promptpaper.config.handlers;

import com.cyr1en.kiso.mc.configuration.base.Config;
import java.lang.reflect.Field;

/**
 * SPI for reading, writing, and defaulting a specific Java type in YAML configs.
 *
 * <p>Implementations handle the conversion between SnakeYAML values and the
 * target Java type. Register new types in {@link ConfigTypeHandlerFactory}.
 *
 * @param <T> the Java type this handler manages
 */
public interface ConfigTypeHandler<T> {

    /** Reads the value for {@code nodeName} from the config. */
    T getValue(Config config, String nodeName, Field field);

    /** Writes {@code value} (with optional comment lines) to the config under {@code nodeName}. */
    void setValue(Config config, String nodeName, Object value, String[] comments);

    /** Returns the default value parsed from the field's {@link NodeDefault} annotation. */
    T getDefault(Field field);
}
