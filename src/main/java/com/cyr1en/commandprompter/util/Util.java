package com.cyr1en.commandprompter.util;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    public static boolean checkSHA1(File file, String checksum) {
        try {
            var data = Files.readAllBytes(file.toPath());
            var hash = MessageDigest.getInstance("SHA-1").digest(data);
            var sb = new StringBuilder();
            for (byte b : hash)
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            return sb.toString().equals(checksum);
        } catch (NoSuchAlgorithmException | IOException e) {
            return false;
        }
    }

    public static boolean isBundledVersion(Plugin plugin) {
        var is = plugin.getResource("META-INF/MANIFEST.MF");
        if (is == null) return false;
        try {
            var str = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));
            return str.contains("Bundled: true");
        } catch (IOException e) {
            return false;
        }
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