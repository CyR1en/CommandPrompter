package dev.cyr1en.promptui;

import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

/**
 * Utility methods for converting between MiniMessage, legacy {@code &}-code,
 * and Adventure {@link Component} formats.
 *
 * <p>All public methods are null-safe: they return {@code null} when given
 * {@code null} input. Malformed MiniMessage input falls back to a plain text
 * component or string rather than throwing.</p>
 */
public final class ComponentUtil {

  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY_AMPERSAND =
      LegacyComponentSerializer.legacyAmpersand();
  private static final LegacyComponentSerializer LEGACY_SECTION =
      LegacyComponentSerializer.legacySection();
  private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");

  /** Single-char {@code &X} → MiniMessage tag name. */
  private static final Map<Character, String> LEGACY_CODE_TO_MINI = Map.ofEntries(
      Map.entry('0', "black"),
      Map.entry('1', "dark_blue"),
      Map.entry('2', "dark_green"),
      Map.entry('3', "dark_aqua"),
      Map.entry('4', "dark_red"),
      Map.entry('5', "dark_purple"),
      Map.entry('6', "gold"),
      Map.entry('7', "gray"),
      Map.entry('8', "dark_gray"),
      Map.entry('9', "blue"),
      Map.entry('a', "green"),
      Map.entry('b', "aqua"),
      Map.entry('c', "red"),
      Map.entry('d', "light_purple"),
      Map.entry('e', "yellow"),
      Map.entry('f', "white"),
      Map.entry('k', "obfuscated"),
      Map.entry('l', "bold"),
      Map.entry('m', "strikethrough"),
      Map.entry('n', "underlined"),
      Map.entry('o', "italic"),
      Map.entry('r', "reset"),
      Map.entry('x', "color"),
      Map.entry('X', "color"),
      Map.entry('K', "obfuscated"),
      Map.entry('L', "bold"),
      Map.entry('M', "strikethrough"),
      Map.entry('N', "underlined"),
      Map.entry('O', "italic"),
      Map.entry('R', "reset"));

  private ComponentUtil() {}

  /**
   * Parses a string into a {@link Component}, accepting MiniMessage tags, legacy ampersand codes,
   * or any mix of the two. Mixed strings (e.g. {@code &6<bold>name</bold>}) work because legacy
   * codes are converted inline before deserialization.
   *
   * <p>Returns {@code null} on {@code null} input. Falls back to a plain text component on
   * malformed MiniMessage.
   */
  public static Component mini(String input) {
    if (input == null) return null;
    String normalized = convertLegacyInline(input);
    try {
      return MINI_MESSAGE.deserialize(normalized);
    } catch (Exception e) {
      return Component.text(input);
    }
  }

  /**
   * Serializes a {@link Component} back to its MiniMessage string form.
   */
  public static String serialize(Component component) {
    return MINI_MESSAGE.serialize(component);
  }

  /**
   * Strips {@code §} and {@code &} color/formatting codes from the given text.
   */
  public static String stripColor(String text) {
    return text.replaceAll("[§&][0-9a-fk-orxlmno]", "");
  }

  /**
   * Converts a {@code &}-coded string to its legacy {@code §X} form, translating
   * {@code &}-prefixed hex codes ({@code &#RRGGBB} and {@code &x&R&G&B...}) as well.
   *
   * @return the legacy-formatted string, or {@code null} on null input
   */
  public static String toLegacy(String msg) {
    if (msg == null) return null;
    var matcher = HEX_PATTERN.matcher(msg);
    while (matcher.find()) {
      String hex = msg.substring(matcher.start(), matcher.end());
      msg = msg.replace(hex, ChatColor.of(hex).toString());
      matcher = HEX_PATTERN.matcher(msg);
    }
    return ChatColor.translateAlternateColorCodes('&', msg);
  }

  /**
   * Converts a legacy {@code &}-coded string to its MiniMessage equivalent.
   *
   * @return the MiniMessage string, or {@code null} on null input
   */
  public static String toMini(String legacy) {
    if (legacy == null) return null;
    var component = LEGACY_AMPERSAND.deserialize(legacy);
    return serialize(component);
  }

  /**
   * Converts a MiniMessage-tagged string to its legacy {@code §X} (section-sign) form.
   *
   * <p>Use this when a {@code -ds} (don't-sanitize) prompt answer must be inserted into a
   * Bukkit command dispatch path that does not understand MiniMessage. Falls back to the
   * raw input on malformed MiniMessage.
   *
   * @return the legacy string, or {@code null}/{@code empty} on null/empty input
   */
  public static String miniToLegacy(String input) {
    if (input == null || input.isEmpty()) return input;
    try {
      var component = MINI_MESSAGE.deserialize(convertLegacyInline(input));
      return LEGACY_SECTION.serialize(component);
    } catch (Exception e) {
      return input;
    }
  }

  /**
   * Rewrites {@code &}-prefixed legacy codes to MiniMessage tags in-place. Handles
   * single codes ({@code &6}), 6-digit hex ({@code &#RRGGBB}), and spelled-out hex
   * ({@code &x&R&G&B...}). Non-code characters and existing MiniMessage tags pass through.
   */
  private static String convertLegacyInline(String input) {
    if (input.indexOf('&') < 0) return input;
    StringBuilder out = new StringBuilder(input.length() + 8);
    int i = 0;
    int n = input.length();
    while (i < n) {
      char c = input.charAt(i);
      if (c != '&' || i + 1 >= n) {
        out.append(c);
        i++;
        continue;
      }
      char next = input.charAt(i + 1);
      // &#RRGGBB — 6-digit hex
      if (next == '#' && i + 8 <= n) {
        String hex = input.substring(i + 2, i + 8);
        if (hex.matches("[0-9a-fA-F]{6}")) {
          out.append("<color:#").append(hex).append(">");
          i += 8;
          continue;
        }
        out.append(c);
        i++;
        continue;
      }
      // &x&R&G&B&R2&G2&B2 — spelled-out hex (12 chars total)
      if ((next == 'x' || next == 'X')
          && i + 14 <= n
          && input.charAt(i + 2) == '&'
          && input.charAt(i + 4) == '&'
          && input.charAt(i + 6) == '&'
          && input.charAt(i + 8) == '&'
          && input.charAt(i + 10) == '&') {
        String hex =
            String.valueOf(input.charAt(i + 3))
                + input.charAt(i + 5)
                + input.charAt(i + 7)
                + input.charAt(i + 9)
                + input.charAt(i + 11)
                + input.charAt(i + 13);
        if (hex.matches("[0-9a-fA-F]{6}")) {
          out.append("<color:#").append(hex).append(">");
          i += 14;
          continue;
        }
        out.append(c);
        i++;
        continue;
      }
      // &X — single code char
      String tag = LEGACY_CODE_TO_MINI.get(next);
      if (tag != null) {
        out.append('<').append(tag).append('>');
        i += 2;
        continue;
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }
}
