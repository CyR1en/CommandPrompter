package dev.cyr1en.promptcore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic parser for confirmation prompt syntax (<c:...> / <confirm:...>).
 *
 * <p>Validates and extracts prompt text, button labels, presentation mode override, value-mode
 * flag, and sound keys according to the confirmation prompt grammar specification.
 */
public final class ConfirmationGrammar {

  private static final Pattern SOUND_KEY_PATTERN =
      Pattern.compile("^(?:[a-zA-Z0-9_.-]+:)?[a-zA-Z0-9_./-]+$");

  private ConfirmationGrammar() {}

  /**
   * Checks whether the given key is a recognized confirmation prompt key (case-insensitive).
   *
   * @param key the prompt key to test
   * @return true if key is "c" or "confirm", false otherwise
   */
  public static boolean isConfirmationKey(String key) {
    if (key == null) return false;
    return key.equalsIgnoreCase("c") || key.equalsIgnoreCase("confirm");
  }

  /**
   * Parses a confirmation {@link PromptTag} into a structured {@link ConfirmationSyntax}.
   *
   * @param tag the prompt tag to parse
   * @return the parsed confirmation syntax
   * @throws NullPointerException if tag is null
   * @throws IllegalArgumentException if the tag key is not a confirmation key or syntax is invalid
   */
  public static ConfirmationSyntax parse(PromptTag tag) {
    Objects.requireNonNull(tag, "PromptTag cannot be null");
    if (!isConfirmationKey(tag.key())) {
      throw new IllegalArgumentException(
          "PromptTag key is not a confirmation key ('c' or 'confirm'): " + tag.key());
    }
    return parse(tag.displayText());
  }

  /**
   * Parses raw confirmation tag content into a structured {@link ConfirmationSyntax}.
   *
   * @param content the raw confirmation content
   * @return the parsed confirmation syntax
   * @throws IllegalArgumentException if the content violates confirmation grammar
   */
  public static ConfirmationSyntax parse(String content) {
    if (content == null) {
      content = "";
    }

    var rawSegments = splitStructuralSegments(content);
    if (rawSegments.size() > 3) {
      throw new IllegalArgumentException(
          "Confirmation prompt cannot have more than 3 segments (prompt, confirm, cancel): "
              + content);
    }

    var state = new ParserState();
    var parsedSegments = new ArrayList<String>();

    for (var rawSeg : rawSegments) {
      parsedSegments.add(parseSegment(rawSeg, state));
    }

    var promptText = parsedSegments.isEmpty() ? "" : parsedSegments.get(0);
    String confirmLabel = null;
    String cancelLabel = null;

    if (parsedSegments.size() >= 2) {
      var seg1 = parsedSegments.get(1);
      if (!seg1.isEmpty()) {
        confirmLabel = seg1;
      }
    }

    if (parsedSegments.size() >= 3) {
      var seg2 = parsedSegments.get(2);
      if (!seg2.isEmpty()) {
        cancelLabel = seg2;
      }
    }

    return new ConfirmationSyntax(
        promptText, confirmLabel, cancelLabel, state.mode, state.valueMode, state.soundKey);
  }

  private static List<String> splitStructuralSegments(String content) {
    var segments = new ArrayList<String>();
    var current = new StringBuilder();
    var inQuotes = false;
    var i = 0;

    while (i < content.length()) {
      char c = content.charAt(i);
      if (c == '\\') {
        if (i + 1 >= content.length()) {
          throw new IllegalArgumentException(
              "Trailing escape backslash in confirmation prompt: " + content);
        }
        current.append('\\').append(content.charAt(i + 1));
        i += 2;
        continue;
      }
      if (c == '"') {
        inQuotes = !inQuotes;
        current.append('"');
        i++;
        continue;
      }
      if (c == '|' && !inQuotes) {
        segments.add(current.toString());
        current = new StringBuilder();
        i++;
        continue;
      }
      current.append(c);
      i++;
    }

    if (inQuotes) {
      throw new IllegalArgumentException("Unbalanced quotes in confirmation prompt: " + content);
    }
    segments.add(current.toString());
    return segments;
  }

  private static String parseSegment(String segment, ParserState state) {
    var spans = extractSpans(segment);
    var sb = new StringBuilder();

    for (var span : spans) {
      if (span.isQuoted()) {
        sb.append(unescape(span.text()));
      } else {
        var unquotedCleaned = processUnquotedSpan(span.text(), state);
        sb.append(unescape(unquotedCleaned));
      }
    }

    return sb.toString().trim();
  }

  private static List<Span> extractSpans(String seg) {
    var spans = new ArrayList<Span>();
    var current = new StringBuilder();
    var inQuotes = false;
    var i = 0;

    while (i < seg.length()) {
      char c = seg.charAt(i);
      if (c == '\\') {
        if (i + 1 < seg.length()) {
          current.append('\\').append(seg.charAt(i + 1));
          i += 2;
          continue;
        }
      }
      if (c == '"') {
        if (inQuotes) {
          spans.add(new Span(current.toString(), true));
          current = new StringBuilder();
          inQuotes = false;
        } else {
          if (current.length() > 0) {
            spans.add(new Span(current.toString(), false));
            current = new StringBuilder();
          }
          inQuotes = true;
        }
        i++;
        continue;
      }
      current.append(c);
      i++;
    }

    if (current.length() > 0) {
      spans.add(new Span(current.toString(), inQuotes));
    }
    return spans;
  }

  private static String processUnquotedSpan(String text, ParserState state) {
    var tokens = text.split("(?<=\\s)|(?=\\s)");
    var remaining = new StringBuilder();

    for (var token : tokens) {
      var trimmed = token.trim();
      if (trimmed.isEmpty()) {
        remaining.append(token);
        continue;
      }

      if (trimmed.startsWith("-mode")) {
        processModeFlag(trimmed, state);
      } else if (trimmed.startsWith("-value")) {
        processValueFlag(trimmed, state);
      } else if (trimmed.startsWith("-sound")) {
        processSoundFlag(trimmed, state);
      } else if (trimmed.startsWith("-timeout")) {
        processTimeoutFlag(trimmed);
      } else if (isOtherStandardFlag(trimmed)) {
        // Strip other standard flags from display text
      } else {
        remaining.append(token);
      }
    }

    return remaining.toString();
  }

  private static void processModeFlag(String token, ParserState state) {
    if (state.mode != null) {
      throw new IllegalArgumentException("Duplicate -mode flag in confirmation prompt: " + token);
    }
    if (!token.startsWith("-mode:") || token.length() <= "-mode:".length()) {
      throw new IllegalArgumentException("Malformed -mode flag: " + token);
    }
    var val = token.substring("-mode:".length()).trim().toLowerCase(Locale.ROOT);
    switch (val) {
      case "gui" -> state.mode = ConfirmationMode.GUI;
      case "dialog" -> state.mode = ConfirmationMode.DIALOG;
      case "chat" -> state.mode = ConfirmationMode.CHAT;
      default -> throw new IllegalArgumentException("Unknown confirmation mode: " + val);
    }
  }

  private static void processValueFlag(String token, ParserState state) {
    if (!token.equals("-value")) {
      throw new IllegalArgumentException("Malformed -value flag: " + token);
    }
    if (state.valueMode) {
      throw new IllegalArgumentException("Duplicate -value flag in confirmation prompt");
    }
    state.valueMode = true;
  }

  private static void processSoundFlag(String token, ParserState state) {
    if (state.soundKey != null) {
      throw new IllegalArgumentException("Duplicate -sound flag in confirmation prompt: " + token);
    }
    if (!token.startsWith("-sound:") || token.length() <= "-sound:".length()) {
      throw new IllegalArgumentException("Malformed -sound flag: " + token);
    }
    var soundKey = token.substring("-sound:".length()).trim();
    if (soundKey.isEmpty()) {
      throw new IllegalArgumentException("Sound key cannot be blank: " + token);
    }
    if (!SOUND_KEY_PATTERN.matcher(soundKey).matches()) {
      throw new IllegalArgumentException("Invalid sound key syntax: " + soundKey);
    }
    state.soundKey = soundKey;
  }

  private static void processTimeoutFlag(String token) {
    if (!token.startsWith("-timeout:") || token.length() <= "-timeout:".length()) {
      throw new IllegalArgumentException("Malformed -timeout flag in tag: " + token);
    }
    var valStr = token.substring("-timeout:".length()).trim();
    int val;
    try {
      val = Integer.parseInt(valStr);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Non-numeric -timeout value in tag: " + valStr);
    }
    if (val < 1 || val > 3600) {
      throw new IllegalArgumentException("Timeout value out of range [1, 3600]: " + val);
    }
  }

  private static boolean isOtherStandardFlag(String token) {
    return token.equals("-ds")
        || token.equals("-int")
        || token.equals("-str")
        || token.equals("-t")
        || token.startsWith("-t:")
        || token.startsWith("-iv:");
  }

  private static String unescape(String input) {
    if (input == null || input.isEmpty()) return input;
    var sb = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '\\' && i + 1 < input.length()) {
        char next = input.charAt(i + 1);
        sb.append(next);
        i++;
        continue;
      }
      sb.append(c);
    }
    return sb.toString();
  }

  private static final class ParserState {
    private ConfirmationMode mode = null;
    private boolean valueMode = false;
    private String soundKey = null;
  }

  private record Span(String text, boolean isQuoted) {}
}
