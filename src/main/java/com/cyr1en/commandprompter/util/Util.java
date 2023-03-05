package com.cyr1en.commandprompter.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Util {
    public static String stripColor(String msg) {
        return ChatColor.stripColor(color(msg));
    }

    public static String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public static Material getCheckedMaterial(String materialString, Material defaultMaterial) {
        materialString = materialString.toUpperCase(Locale.ROOT);
        var mat = Material.getMaterial(materialString);
        return Objects.isNull(mat) ? defaultMaterial : mat;
    }

    public static class ConsumerFallback<T> {

        private final T val;
        private final Consumer<T> consumer;
        private final Supplier<Boolean> test;
        private Runnable runnable;

        public ConsumerFallback(T val, Consumer<T> consumer, Supplier<Boolean> test) {
            this.val = val;
            this.consumer = consumer;
            this.test = test;
            this.runnable = () -> {
            };
        }

        public void complete() {
            if (test.get()) consumer.accept(val);
            else runnable.run();
        }

        public ConsumerFallback<T> orElse(Runnable runnable) {
            this.runnable = runnable;
            return this;
        }
    }
}