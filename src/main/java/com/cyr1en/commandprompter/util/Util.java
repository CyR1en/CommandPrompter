package com.cyr1en.commandprompter.util;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.kiso.mc.Version;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import net.md_5.bungee.api.ChatColor;

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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class Util {

    public static String stripColor(String msg) {
        return ChatColor.stripColor(color(msg));
    }

    private static Optional<PluginLogger> getLogger() {
        if (CommandPrompter.getInstance() == null)
            return Optional.empty();
        return Optional.of(CommandPrompter.getInstance().getPluginLogger());
    }


    public static String color(String msg) {
        if (!ServerUtil.BUNGEE_CHAT_AVAILABLE())
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);

        var supportedHex = ServerUtil.parsedVersion().isNewerThan(Version.parse("1.15.0"));
        if (supportedHex) {
            var pattern = Pattern.compile("#[a-fA-F0-9]{6}");
            var matcher = pattern.matcher(msg);

            while (matcher.find()) {
                String color = msg.substring(matcher.start(), matcher.end());
                msg = msg.replace(color, ChatColor.of(color) + "");
                matcher = pattern.matcher(msg);
            }
        }
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

    public static void deleteFile(File file, Consumer<File> onFail) {
        if (file.exists())
            if (!file.delete())
                onFail.accept(file);
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