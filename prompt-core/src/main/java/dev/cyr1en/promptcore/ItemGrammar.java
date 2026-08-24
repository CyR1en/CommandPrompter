package dev.cyr1en.promptcore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic parser for item selector prompt syntax (<i:...> / <item:...>).
 *
 * <p>Validates and extracts prompt text, source location, output format, catalog category, sound
 * key, and timeout override according to the item selector prompt grammar specification.
 */
public final class ItemGrammar {

  private static final Pattern C0_CONTROLS = Pattern.compile("[\\u0000-\\u001F\\u007F]");

  public static final Pattern SOUND_KEY_PATTERN =
      Pattern.compile("^(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+$");
  public static final int MAX_SOUND_KEY_LENGTH = 256;

  public static final Pattern CATEGORY_PATTERN = Pattern.compile("^[a-z0-9_.-]{1,64}$");

  private ItemGrammar() {}

  /**
   * Validates a sound key against Adventure-compatible lowercase namespaced key syntax and length
   * bounds.
   *
   * @param soundKey the sound key to validate
   * @throws IllegalArgumentException if soundKey is null, blank, exceeds 256 characters, contains
   *     uppercase or control characters, or violates syntax
   */
  public static void validateSoundKey(String soundKey) {
    if (soundKey == null || soundKey.isBlank()) {
      throw new IllegalArgumentException("Sound key cannot be blank or null");
    }
    if (C0_CONTROLS.matcher(soundKey).find()) {
      throw new IllegalArgumentException("Sound key cannot contain control characters");
    }
    if (soundKey.length() > MAX_SOUND_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "Sound key exceeds maximum length of " + MAX_SOUND_KEY_LENGTH + ": " + soundKey.length());
    }
    if (!SOUND_KEY_PATTERN.matcher(soundKey).matches()) {
      throw new IllegalArgumentException("Invalid sound key syntax: " + soundKey);
    }
  }

  /**
   * Validates a catalog category name against catalog loader syntax (^[a-z0-9_.-]{1,64}$).
   *
   * @param category the category name to validate
   * @throws IllegalArgumentException if category is null, blank, exceeds 64 characters, or violates
   *     syntax
   */
  public static void validateCategory(String category) {
    if (category == null || category.isBlank()) {
      throw new IllegalArgumentException("Category filter cannot be blank or null");
    }
    if (C0_CONTROLS.matcher(category).find()) {
      throw new IllegalArgumentException("Category filter cannot contain control characters");
    }
    if (!CATEGORY_PATTERN.matcher(category).matches()) {
      throw new IllegalArgumentException(
          "Invalid category syntax: '" + category + "'; must match " + CATEGORY_PATTERN.pattern());
    }
  }

  /**
   * Checks whether the given key is a recognized item prompt key (case-insensitive).
   *
   * @param key the prompt key to test
   * @return true if key is "i" or "item", false otherwise
   */
  public static boolean isItemKey(String key) {
    if (key == null) return false;
    return key.equalsIgnoreCase("i") || key.equalsIgnoreCase("item");
  }

  /**
   * Parses an item {@link PromptTag} into a structured {@link ItemSyntax}.
   *
   * @param tag the prompt tag to parse
   * @return the parsed item syntax
   * @throws NullPointerException if tag is null
   * @throws IllegalArgumentException if the tag key is not an item key or syntax is invalid
   */
  public static ItemSyntax parse(PromptTag tag) {
    Objects.requireNonNull(tag, "PromptTag cannot be null");
    if (!isItemKey(tag.key())) {
      throw new IllegalArgumentException(
          "PromptTag key is not an item key ('i' or 'item'): " + tag.key());
    }
    return parse(tag.displayText());
  }

  /**
   * Parses raw item tag content into a structured {@link ItemSyntax}.
   *
   * @param content the raw item tag content
   * @return the parsed item syntax
   * @throws IllegalArgumentException if the content violates item prompt grammar
   */
  public static ItemSyntax parse(String content) {
    if (content == null) {
      content = "";
    }
    if (C0_CONTROLS.matcher(content).find()) {
      throw new IllegalArgumentException("Item prompt content cannot contain control characters");
    }

    var spans = extractSpans(content);
    var state = new ParserState();
    var sb = new StringBuilder();

    for (var span : spans) {
      if (span.isQuoted()) {
        sb.append(unescape(span.text()));
      } else {
        var unquotedCleaned = processUnquotedSpan(span.text(), state);
        sb.append(unescape(unquotedCleaned));
      }
    }

    var promptText = sb.toString().trim();
    var source = state.source == null ? ItemSource.INVENTORY : state.source;
    var outputFormat = state.outputFormat == null ? ItemOutputFormat.KEY : state.outputFormat;
    var category = state.category;

    // Enforce source and category invariants
    if (source == ItemSource.CATALOG) {
      if (category == null || category.isBlank()) {
        category = "all";
      }
      if (outputFormat == ItemOutputFormat.SLOT) {
        throw new IllegalArgumentException(
            "Output format 'slot' is not supported for catalog source");
      }
    } else {
      if (category != null) {
        throw new IllegalArgumentException(
            "Category filter '-cat:' is only valid for catalog source, but source was: " + source);
      }
    }

    return new ItemSyntax(promptText, source, outputFormat, category, state.soundKey);
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
        } else {
          throw new IllegalArgumentException("Trailing escape backslash in item prompt: " + seg);
        }
      }
      if (c == '"') {
        if (inQuotes) {
          spans.add(new Span(current.toString(), true));
          current = new StringBuilder();
          inQuotes = false;
        } else {
          if (isFlagValueOpener(current)) {
            // It's the value of a flag like -cat:"something"
            current.append('"');
            i++;
            boolean foundClose = false;
            while (i < seg.length()) {
              char qc = seg.charAt(i);
              if (qc == '\\' && i + 1 < seg.length()) {
                current.append('\\').append(seg.charAt(i + 1));
                i += 2;
                continue;
              }
              current.append(qc);
              if (qc == '"') {
                foundClose = true;
                break;
              }
              i++;
            }
            if (!foundClose) {
              throw new IllegalArgumentException("Unbalanced quotes in item prompt: " + seg);
            }
            i++;
            continue;
          }

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

    if (inQuotes) {
      throw new IllegalArgumentException("Unbalanced quotes in item prompt: " + seg);
    }
    if (current.length() > 0) {
      spans.add(new Span(current.toString(), false));
    }
    return spans;
  }

  private static boolean isFlagValueOpener(StringBuilder current) {
    if (current.length() == 0) return false;
    String s = current.toString();
    int colon = s.lastIndexOf(':');
    if (colon < 0 || colon != s.length() - 1) return false;
    int dash = s.lastIndexOf('-', colon);
    if (dash < 0) return false;
    if (dash > 0 && !Character.isWhitespace(s.charAt(dash - 1))) return false;
    String flagName = s.substring(dash + 1, colon);
    return flagName.matches("^[a-zA-Z][a-zA-Z0-9_]*$");
  }

  private static List<String> tokenizeRespectingQuotes(String text) {
    var tokens = new ArrayList<String>();
    var current = new StringBuilder();
    var inQuotes = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\' && i + 1 < text.length()) {
        current.append('\\').append(text.charAt(i + 1));
        i++;
        continue;
      }
      if (c == '"') {
        inQuotes = !inQuotes;
        current.append(c);
        continue;
      }
      if (Character.isWhitespace(c) && !inQuotes) {
        if (current.length() > 0) {
          tokens.add(current.toString());
          current = new StringBuilder();
        }
        tokens.add(String.valueOf(c));
        continue;
      }
      current.append(c);
    }
    if (current.length() > 0) {
      tokens.add(current.toString());
    }
    return tokens;
  }

  private static String processUnquotedSpan(String text, ParserState state) {
    var tokens = tokenizeRespectingQuotes(text);
    var remaining = new StringBuilder();

    for (var token : tokens) {
      var trimmed = token.trim();
      if (trimmed.isEmpty()) {
        remaining.append(token);
        continue;
      }

      if (trimmed.startsWith("-")) {
        // Check if it's a negative number in prompt text
        if (isNegativeNumber(trimmed)) {
          remaining.append(token);
          continue;
        }

        if (trimmed.startsWith("-source")) {
          processSourceFlag(trimmed, state);
        } else if (trimmed.startsWith("-out")) {
          processOutFlag(trimmed, state);
        } else if (trimmed.startsWith("-cat")) {
          processCatFlag(trimmed, state);
        } else if (trimmed.startsWith("-sound")) {
          processSoundFlag(trimmed, state);
        } else if (trimmed.startsWith("-timeout")) {
          processTimeoutFlag(trimmed, state);
        } else if (isOtherStandardFlag(trimmed)) {
          // Standard flags like -ds, -int, -str, -t, -iv are stripped
        } else {
          throw new IllegalArgumentException(
              "Unknown or unsupported flag for item prompt: " + token);
        }
      } else {
        remaining.append(token);
      }
    }

    return remaining.toString();
  }

  private static boolean isNegativeNumber(String token) {
    if (token.length() <= 1) return false;
    char firstAfterDash = token.charAt(1);
    return Character.isDigit(firstAfterDash);
  }

  private static void processSourceFlag(String token, ParserState state) {
    if (state.source != null) {
      throw new IllegalArgumentException("Duplicate -source flag in item prompt: " + token);
    }
    if (!token.startsWith("-source:") || token.length() <= "-source:".length()) {
      throw new IllegalArgumentException("Malformed -source flag: " + token);
    }
    var val = token.substring("-source:".length()).trim();
    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
      val = val.substring(1, val.length() - 1).trim();
    }
    if (val.isEmpty()) {
      throw new IllegalArgumentException("Malformed -source flag with empty value: " + token);
    }
    state.source = ItemSource.fromAlias(val);
  }

  private static void processOutFlag(String token, ParserState state) {
    if (state.outputFormat != null) {
      throw new IllegalArgumentException("Duplicate -out flag in item prompt: " + token);
    }
    if (!token.startsWith("-out:") || token.length() <= "-out:".length()) {
      throw new IllegalArgumentException("Malformed -out flag: " + token);
    }
    var val = token.substring("-out:".length()).trim();
    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
      val = val.substring(1, val.length() - 1).trim();
    }
    if (val.isEmpty()) {
      throw new IllegalArgumentException("Malformed -out flag with empty value: " + token);
    }
    state.outputFormat = ItemOutputFormat.fromAlias(val);
  }

  private static void processCatFlag(String token, ParserState state) {
    if (state.category != null) {
      throw new IllegalArgumentException("Duplicate -cat flag in item prompt: " + token);
    }
    if (!token.startsWith("-cat:") || token.length() <= "-cat:".length()) {
      throw new IllegalArgumentException("Malformed -cat flag: " + token);
    }
    var val = token.substring("-cat:".length()).trim();
    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
      val = val.substring(1, val.length() - 1).trim();
    }
    validateCategory(val);
    state.category = val;
  }

  private static void processSoundFlag(String token, ParserState state) {
    if (state.soundKey != null) {
      throw new IllegalArgumentException("Duplicate -sound flag in item prompt: " + token);
    }
    if (!token.startsWith("-sound:") || token.length() <= "-sound:".length()) {
      throw new IllegalArgumentException("Malformed -sound flag: " + token);
    }
    var soundKey = token.substring("-sound:".length()).trim();
    if (soundKey.startsWith("\"") && soundKey.endsWith("\"") && soundKey.length() >= 2) {
      soundKey = soundKey.substring(1, soundKey.length() - 1).trim();
    }
    validateSoundKey(soundKey);
    state.soundKey = soundKey;
  }

  private static void processTimeoutFlag(String token, ParserState state) {
    if (state.hasTimeout) {
      throw new IllegalArgumentException("Duplicate -timeout flag in item prompt: " + token);
    }
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
    state.hasTimeout = true;
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
    private ItemSource source = null;
    private ItemOutputFormat outputFormat = null;
    private String category = null;
    private String soundKey = null;
    private boolean hasTimeout = false;
  }

  private record Span(String text, boolean isQuoted) {}
}
