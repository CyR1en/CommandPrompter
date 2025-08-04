package com.cyr1en.commandprompter.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.NonNull;
import com.cyr1en.kiso.mc.Version;
import java.util.List;

public class AdventureUtil {

    /**
     * A helper function that determines if the list contains raw prompts
     * or mini messages.
     * 
     * @param strs
     * @return
     */
    public static List<String> filterOutMiniMessageTags(List<String> strs) {
        return strs.stream().filter(str -> !isMiniMessageTag(str)).toList();
    }

    /**
     * Function that checks if the prompt is a mini message tag.
     *
     * @param str Prompt to check
     * @return true if the prompt is a mini message tag, false otherwise.
     */
    public static boolean isMiniMessageTag(String str) {
        var serializer = MiniMessage.builder()
                .tags(StandardTags.defaults()).build();
        var parsed = serializer.deserialize(str);
        return !Component.text(str).equals(parsed);
    }

    /**
     * Function that formats the string using MiniMessage.
     * 
     * <p>
     * This function will format the string using MiniMessage and return
     * a Component that can be used in Adventure.
     * 
     * <p>
     * Additionally, it also supports placeholders and will format them
     * using the {@link Util#format(String, Object...)} method.
     * 
     * @param str  String to format
     * @param args Arguments to format the string with
     * @return Formatted Component
     */
    public static Component mm(@NonNull String str, Object... args) {
        str = Util.format(str, args);
        return MiniMessage.miniMessage().deserialize(str);
    }

    /**
     * A function that joins multiple components into one.
     * 
     * <p>
     * This function will join the components using a space as a separator.
     * 
     * @param components Components to join
     * @return Joined components
     */
    public static Component joinComponents(@NonNull Component... components) {
        return Component.join(JoinConfiguration.separator(Component.space()), components);
    }

    /**
     * A helper function that joins multiple components into one using the given
     * separator.
     * 
     * @param separator  The separator to use
     * @param components Components to join
     * @return Joined components
     */
    public static Component joiComponent(@NonNull String separator, @NonNull Component... components) {
        return Component.join(JoinConfiguration.separator(Component.text(separator)), components);
    }

    /**
     * A function that converts a Component to a plain string.
     * 
     * <p>
     * This function will convert the Component to a plain string removing all
     * formatting and colors.
     * 
     * @param component Component to convert
     * @return Plain string representation of the component
     */
    public static String plain(@NonNull Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * A function that converts a formatted string to a plain string.
     * 
     * <p>
     * This function will convert the formatted string to a plain string removing
     * all
     * 
     * @param str Formatted string to convert
     * @return Plain string representation of the formatted string
     */
    public static String plain(@NonNull String str) {
        return PlainTextComponentSerializer.plainText().serialize(Component.text(str));
    }

    /**
     * A function the converts a string to a colorized Component
     * 
     * <p>
     * This supports legacy color codes but will return a Component rather than a
     * String.
     * If needed, use {@link #legacyColor(String)} to retrieve a string. This is
     * similar to
     * ChatColor.
     * 
     * @param str String to convert
     * @return A colorized adventure component.
     */
    public static Component color(String str) {
        var supportedHex = ServerUtil.parsedVersion().isNewerThan(Version.parse("1.15.0"));
        if (supportedHex) {
            str = str.replaceAll("&([0-9a-fA-Fk-orK-OR])", "<color:$1>");
            str = str.replaceAll("#([a-fA-F0-9]{6})", "<color:#$1>");
        }
        return MiniMessage.miniMessage().deserialize(str);
    }

    /**
     * A function that converts a raw string to a colorized String.
     * 
     * @param str The string to convert
     * @return A colorized String.
     */
    public static String legacyColor(String str) {
        var component = color(str);
        return toLegacyColor(component);
    }

    /**
     * A function to convert an Adventure component to legacy color supported
     * string.
     * 
     * @param component component to convert
     * @return A seralized component.
     */
    public static String toLegacyColor(@NonNull Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

}
