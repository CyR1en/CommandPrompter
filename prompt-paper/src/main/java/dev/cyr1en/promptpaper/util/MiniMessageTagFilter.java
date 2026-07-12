package dev.cyr1en.promptpaper.util;

import dev.cyr1en.promptcore.TagFilter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

/**
 * A {@link TagFilter} that skips MiniMessage formatting tags.
 *
 * <p>When the prompt delimiters are angle brackets ({@code < >}), MiniMessage tags like
 * {@code <red>}, {@code </red>}, {@code <gradient:gold:yellow>}, and {@code <bold>} use the same
 * syntax and would be incorrectly parsed as prompt tags. This filter detects them by deserializing
 * the tag content with a standard MiniMessage instance and comparing the result to plain text.
 *
 * <p>Both opening tags ({@code red}) and closing tags ({@code /red}) are detected. A closing tag is
 * recognized when the content starts with {@code /} and the remainder (without the slash) is a
 * valid MiniMessage tag.
 *
 * <p>The filter is stateless and thread-safe.
 */
public final class MiniMessageTagFilter implements TagFilter {

  private static final MiniMessage MINI =
      MiniMessage.builder().tags(StandardTags.defaults()).build();

  /**
   * Returns {@code true} if the tag content (text between the delimiters) is a MiniMessage
   * formatting tag that should be skipped.
   *
   * <p>For closing tags (content starting with {@code /}), the slash is stripped before checking.
   *
   * @param content the raw text between the delimiters (e.g. {@code "red"}, {@code "/red"},
   *     {@code "gradient:gold:yellow"})
   * @return {@code true} if this is a MiniMessage tag, {@code false} otherwise
   */
  @Override
  public boolean test(String content) {
    if (content == null || content.isBlank()) return false;

    // Handle closing tags: </red> → content is "/red"
    var toCheck = content.startsWith("/") ? content.substring(1) : content;
    if (toCheck.isBlank()) return false;

    // Reconstruct the tag with angle brackets so MiniMessage can parse it.
    var reconstructed = "<" + toCheck + ">";
    try {
      var parsed = MINI.deserialize(reconstructed);
      // If deserialization produced something different from plain text, it's a MiniMessage tag.
      return !Component.text(reconstructed).equals(parsed);
    } catch (Exception e) {
      // If MiniMessage throws, it's not a valid tag — don't skip it.
      return false;
    }
  }
}