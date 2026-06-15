package dev.cyr1en.promptpaper.config.handlers.impl;

import com.cyr1en.kiso.mc.configuration.base.Config;
import dev.cyr1en.promptpaper.config.annotations.field.IntegerConstraint;
import dev.cyr1en.promptpaper.config.annotations.field.NodeDefault;
import dev.cyr1en.promptpaper.config.handlers.ConfigTypeHandler;
import java.lang.reflect.Field;

/** Reads, writes, and defaults {@code int} config nodes, applying {@link IntegerConstraint} bounds. */
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
        if (defaultAnnotation != null) {
            try {
                return Integer.parseInt(defaultAnnotation.value());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
