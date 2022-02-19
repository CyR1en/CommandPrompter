package com.cyr1en.commandprompter.hook;

import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

public final class Hook<T> {

    private static final Hook<?> EMPTY = new Hook<>();

    private final T value;

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

    /**
     * Accessor to check if the plugin is hooked.
     *
     * @return if this plugin hook is actually hooked.
     */
    public boolean isHooked() {
        return Objects.nonNull(value);
    }

    public void ifHooked(@NotNull Consumer<T> consumer) {
        if (isHooked())
            consumer.accept(value);
    }
}
