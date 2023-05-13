package com.cyr1en.commandprompter.hook;

import com.cyr1en.commandprompter.util.Util.ConsumerFallback;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

public final class Hook<T> {

    private static final Hook<?> EMPTY = new Hook<>();

    private final T value;
    private String targetPlugin = "";

    private Hook() {
        this.value = null;
    }

    private Hook(T val) {
        this.value = val;
    }

    public static <T> Hook<T> of(T val) {
        return new Hook<>(val);
    }

    public static <T> Hook<T> empty() {
        @SuppressWarnings("unchecked") var t = (Hook<T>) EMPTY;
        return t;
    }

    private T get() {
        if (value == null)
            throw new NoSuchElementException("No value present");
        return value;
    }

    public void setTargetPlugin(String targetPlugin) {
        this.targetPlugin = targetPlugin;
    }

    public String getTargetPluginName() {
        return targetPlugin;
    }

    /**
     * Accessor to check if the plugin is hooked.
     *
     * @return if this plugin hook is actually hooked.
     */
    public boolean isHooked() {
        return Objects.nonNull(value);
    }

    public ConsumerFallback<T> ifHooked(@NotNull Consumer<T> consumer) {
        return new ConsumerFallback<>(value, consumer, this::isHooked);
    }

    @Override
    public String toString() {
        return "Hook{" +
                "targetPlugin='" + targetPlugin + '\'' +
                '}';
    }
}
