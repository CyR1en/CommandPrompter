package dev.cyr1en.promptcore.parser;

import dev.cyr1en.promptcore.*;
import dev.cyr1en.promptcore.logic.condition.*;
import dev.cyr1en.promptcore.plan.PostActionSpec;
import dev.cyr1en.promptcore.plan.PreDispatchGateSpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Parses command strings into structured {@link ParsedCommand} objects.
 *
 * <p>Thread-safe after construction. No platform dependencies.
 *
 * <h2>Tag filtering</h2>
 *
 * <p>An optional {@link TagFilter} (a {@code Predicate<String>} on the raw tag content) can be
 * supplied at construction time. When set, any tag whose content matches the predicate is
 * <b>skipped</b> — it is neither treated as a prompt tag nor as a PCM, and is left intact in the
 * template command. This is how MiniMessage syntax (e.g. {@code <red>}, {@code </red>}, {@code
 * <gradient:gold:yellow>}) is ignored when the prompt delimiters are angle brackets.
 */
public class CommandLineParser {

  private static final Logger LOG = Logger.getLogger(CommandLineParser.class.getName());

  private final ParserConfig config;
  private final TagFilter tagFilter;
  private final Pattern pcmTarget;
  private final Pattern answerRef;
  private final Pattern validatorFlag;
  private final Pattern dsFlag;
  private final Pattern intFlag;
  private final Pattern strFlag;
  private final Pattern titleFlag;
  private final Pattern timeoutFlag;
  // Pattern to detect and warn about the deprecated trailing-kind dialog form.
  private static final Pattern TRAILING_KIND_DETECT =
      Pattern.compile("\\b(?:text|bool|num)(?:\\[[^\\]]*\\])?\\s*$", Pattern.CASE_INSENSITIVE);
  private final Set<String> seenDeprecationWarnings = ConcurrentHashMap.newKeySet();

  /** Maximum allowed prompt tags in a single command. */
  public static final int MAX_PROMPT_TAGS = 16;

  /** Maximum allowed pre-dispatch gate tags in a single command. */
  public static final int MAX_GATE_TAGS = 16;

  /** Maximum number of arbitrary custom flags allowed on a single prompt tag. */
  public static final int MAX_CUSTOM_FLAGS = 16;

  /** Maximum character length allowed for a single custom flag name. */
  public static final int MAX_CUSTOM_FLAG_NAME_LENGTH = 32;

  /** Maximum character length allowed for a single custom flag value. */
  public static final int MAX_CUSTOM_FLAG_VALUE_LENGTH = 512;

  /**
   * Maximum aggregate character length allowed across all custom flag values on a single prompt
   * tag.
   */
  public static final int MAX_CUSTOM_FLAGS_AGGREGATE_LENGTH = 1024;

  /**
   * Pattern validating custom flag names (starts with a letter, lowercase/digits/underscore, <= 32
   * chars).
   */
  private static final Pattern CUSTOM_FLAG_NAME_PATTERN =
      Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,31}$");

  /** Result container for -breakIf flag extraction. */
  public record BreakIfResult(String remainingContent, Condition condition) {}

  /** Result container for trailing custom flag extraction. */
  public record CustomFlagsResult(String remainingContent, Map<String, String> flags) {}

  /** Creates a parser with angle-bracket delimiters and no tag filtering. */
  public CommandLineParser() {
    this(ParserConfig.ANGLE_BRACKETS, null);
  }

  /**
   * Creates a parser with the given config and no tag filtering.
   *
   * @param config the delimiter configuration
   */
  public CommandLineParser(ParserConfig config) {
    this(config, null);
  }

  /**
   * Creates a parser with the given config and an optional tag filter.
   *
   * <p>When {@code tagFilter} is non-null, any matched tag whose content (the text between the
   * delimiters) passes the predicate is skipped — it is not classified as a prompt tag or a PCM and
   * is left intact in the template command. This is how MiniMessage syntax is ignored.
   *
   * @param config the delimiter configuration
   * @param tagFilter a predicate that returns {@code true} for tags to skip, or {@code null} to
   *     disable filtering
   */
  public CommandLineParser(ParserConfig config, TagFilter tagFilter) {
    this.config = config;
    this.tagFilter = tagFilter;
    this.pcmTarget = Pattern.compile("(?<!\\S)@(console|player)(?=\\s|$)");
    this.answerRef = Pattern.compile("\\{(\\d+)}");
    // Flags are tokens, not substrings of display text (for example, cost-int).
    this.validatorFlag = Pattern.compile("(?<!\\S)-iv:(\\S*)(?!\\S)");
    this.dsFlag = Pattern.compile("(?<!\\S)-ds(?!\\S)");
    this.intFlag = Pattern.compile("(?<!\\S)-int(?!\\S)");
    this.strFlag = Pattern.compile("(?<!\\S)-str(?!\\S)");
    this.titleFlag = Pattern.compile("(?<!\\S)-t(?:\\b|(?=:))(?::(?:[^\"\\s]+|\"[^\"]*\")*)?");
    this.timeoutFlag = Pattern.compile("(?<!\\S)-timeout(?:\\S*)");
  }

  /** Returns the {@link ParserConfig} used by this parser. */
  public ParserConfig getConfig() {
    return config;
  }

  /**
   * Parse a raw command string into a structured {@link ParsedCommand}.
   *
   * <p>Extracts all tags delimited by the configured {@link ParserConfig}, classifies each as a
   * prompt tag or a PCM, and parses their contents including flags, answer references, and dispatch
   * targets.
   *
   * @param rawCommand the full command string (e.g. {@code "/kick <a:Why?> <! ban {0}>"})
   * @return the parsed result with ordered prompt tags and PCMs
   */
  public ParsedCommand parse(String rawCommand) {
    if (rawCommand == null || rawCommand.isBlank()) {
      return new ParsedCommand(
          rawCommand == null ? "" : rawCommand,
          List.of(),
          List.of(),
          List.of(),
          config,
          rawCommand == null ? "" : rawCommand,
          List.of());
    }

    var promptTags = new ArrayList<PromptTag>();
    var postCmds = new ArrayList<PostCommandMeta>();
    var preDispatchGates = new ArrayList<PreDispatchGateSpec>();
    var seenGateIds = new HashSet<String>();
    var spans = new ArrayList<ParsedCommand.TemplateSpan>();

    LOG.fine("Parsing command template");

    for (var match : scanTags(rawCommand)) {
      var rawContent = match.content();
      var fullTag = match.rawText();

      // Skip tags that the filter says to ignore (e.g. MiniMessage syntax).
      if (tagFilter != null && tagFilter.test(rawContent)) {
        LOG.fine("Skipping filtered tag");
        continue;
      }

      if (isGateTag(rawContent)) {
        var gateSpec = parseGateTag(rawContent, seenGateIds, preDispatchGates.size(), rawCommand);
        preDispatchGates.add(gateSpec);
        spans.add(
            new ParsedCommand.TemplateSpan(
                match.start(),
                match.end(),
                match.rawText(),
                false,
                preDispatchGates.size() - 1,
                true));
      } else if (isPCM(rawContent)) {
        parsePCM(rawContent, fullTag, postCmds);
        spans.add(
            new ParsedCommand.TemplateSpan(
                match.start(), match.end(), match.rawText(), true, postCmds.size() - 1));
      } else {
        int before = promptTags.size();
        parsePromptTag(rawContent, fullTag, promptTags);
        if (promptTags.size() > MAX_PROMPT_TAGS) {
          throw new IllegalArgumentException(
              "Command exceeds maximum allowed prompt tags ("
                  + MAX_PROMPT_TAGS
                  + "): "
                  + rawCommand);
        }
        if (promptTags.size() > before) {
          spans.add(
              new ParsedCommand.TemplateSpan(
                  match.start(), match.end(), match.rawText(), false, promptTags.size() - 1));
        }
      }
    }

    var template = unescape(rawCommand);

    LOG.fine(
        "Parsed "
            + promptTags.size()
            + " prompt tags, "
            + postCmds.size()
            + " PCMs, "
            + preDispatchGates.size()
            + " gates");

    return new ParsedCommand(
        template,
        Collections.unmodifiableList(promptTags),
        Collections.unmodifiableList(postCmds),
        Collections.unmodifiableList(preDispatchGates),
        config,
        rawCommand,
        Collections.unmodifiableList(spans));
  }

  /**
   * Whether the raw command string contains at least one non-filtered tag (prompt or PCM). Used by
   * callers that need to distinguish "no tag form at all" from "had tag form but parsing returned
   * empty for some reason" — for example, the fail-fast path in the engine.
   *
   * <p>When a {@link TagFilter} is configured, tags that match the filter are not counted — a
   * command containing only MiniMessage tags (e.g. {@code <red>}) will return {@code false}.
   */
  public boolean hasTagForm(String rawCommand) {
    if (rawCommand == null || rawCommand.isBlank()) return false;
    for (var match : scanTags(rawCommand)) {
      if (tagFilter == null || !tagFilter.test(match.content())) {
        return true;
      }
    }
    return false;
  }

  /** Returns the {@link TagFilter} used by this parser, or {@code null} if none is set. */
  public TagFilter getTagFilter() {
    return tagFilter;
  }

  private boolean isGateTag(String content) {
    if (content == null) return false;
    if (content.startsWith("!gate:")
        || content.startsWith("! gate:")
        || content.startsWith("!gate@")
        || content.startsWith("! gate@")
        || content.startsWith("!gate :")
        || content.startsWith("!gate\t")) {
      return true;
    }
    if (content.startsWith("!!") && isGateKeyword(content.substring(2))) {
      return true;
    }
    if (content.startsWith("!") && isDelayedOrTargetedGate(content)) {
      return true;
    }
    return false;
  }

  private static boolean isGateKeyword(String rest) {
    var trimmed = rest.trim();
    return trimmed.startsWith("gate:")
        || trimmed.startsWith("gate@")
        || trimmed.startsWith("gate :")
        || trimmed.equals("gate")
        || trimmed.startsWith("gate ");
  }

  private static boolean isDelayedOrTargetedGate(String content) {
    if (content.matches("^!:\\d+.*gate.*")) {
      return true;
    }
    if (content.matches("^!\\s*@(console|player)\\s+gate.*")) {
      return true;
    }
    return false;
  }

  private PreDispatchGateSpec parseGateTag(
      String rawContent, Set<String> seenGateIds, int currentGateCount, String rawCommand) {
    if (rawContent.startsWith("!!")) {
      throw new IllegalArgumentException(
          "Cancel gates (!!gate) are not supported: <" + rawContent + ">");
    }
    if (rawContent.matches("^!:\\d+.*")) {
      throw new IllegalArgumentException(
          "Delayed gates (!:N gate) are not supported: <" + rawContent + ">");
    }
    if (rawContent.matches("^!\\s*@(console|player)\\s+.*")) {
      throw new IllegalArgumentException(
          "Executor prefixes are not supported on gates: <" + rawContent + ">");
    }
    if (!rawContent.startsWith("!gate:@")) {
      throw new IllegalArgumentException(
          "Malformed gate tag (expected <!gate:@id>): <" + rawContent + ">");
    }

    var id = rawContent.substring("!gate:@".length());
    if (id.isBlank()) {
      throw new IllegalArgumentException("Gate preset ID must not be blank: <" + rawContent + ">");
    }
    if (id.length() > PreDispatchGateSpec.Approval.MAX_PRESET_ID_LENGTH) {
      throw new IllegalArgumentException(
          "Gate preset ID exceeds maximum length of "
              + PreDispatchGateSpec.Approval.MAX_PRESET_ID_LENGTH
              + " characters: "
              + id.length());
    }
    if (!id.matches("^[a-z0-9_.-]+$")) {
      if (id.matches(".*[A-Z].*")) {
        throw new IllegalArgumentException("Gate preset ID must be lowercase: " + id);
      }
      throw new IllegalArgumentException(
          "Gate preset ID contains invalid characters or extra arguments: " + id);
    }
    if (!seenGateIds.add(id)) {
      throw new IllegalArgumentException("Duplicate gate ID in command: " + id);
    }
    if (currentGateCount >= MAX_GATE_TAGS) {
      throw new IllegalArgumentException(
          "Command exceeds maximum allowed gates (" + MAX_GATE_TAGS + "): " + rawCommand);
    }

    return new PreDispatchGateSpec.Approval(id);
  }

  private boolean isPCM(String content) {
    return content.startsWith("!");
  }

  /**
   * Scans delimiter pairs in one pass. An escape consumes the following character or delimiter
   * token, and an unterminated opener consumes the remainder of the input rather than restarting a
   * search at every later opener. That makes repeated unterminated openers linear instead of
   * quadratic.
   */
  private List<TagMatch> scanTags(String rawCommand) {
    var matches = new ArrayList<TagMatch>();
    String opening = config.opening();
    String closing = config.closing();
    String escape = config.escape();
    int i = 0;
    while (i < rawCommand.length()) {
      if (rawCommand.startsWith(escape, i)) {
        int nextIdx = i + escape.length();
        if (rawCommand.startsWith(opening, nextIdx)) {
          i = nextIdx + opening.length();
          continue;
        } else if (rawCommand.startsWith(closing, nextIdx)) {
          i = nextIdx + closing.length();
          continue;
        } else if (rawCommand.startsWith(escape, nextIdx)) {
          i = nextIdx + escape.length();
          continue;
        } else {
          i = nextIdx;
          continue;
        }
      }
      if (!rawCommand.startsWith(opening, i)) {
        i++;
        continue;
      }

      int start = i;
      i += opening.length();
      boolean closed = false;
      boolean inQuotes = false;
      boolean isConfirmation = isConfirmationOpener(rawCommand, i);
      boolean isItem = isItemOpener(rawCommand, i);
      while (i < rawCommand.length()) {
        if (rawCommand.startsWith(escape, i)) {
          int nextIdx = i + escape.length();
          if (rawCommand.startsWith(closing, nextIdx)) {
            i = nextIdx + closing.length();
          } else if (rawCommand.startsWith(opening, nextIdx)) {
            i = nextIdx + opening.length();
          } else if (rawCommand.startsWith(escape, nextIdx)) {
            i = nextIdx + escape.length();
          } else {
            i = nextIdx;
          }
        } else {
          char current = rawCommand.charAt(i);
          if (current == '"') {
            inQuotes = !inQuotes;
            i++;
          } else if (!inQuotes && rawCommand.startsWith(closing, i)) {
            int end = i + closing.length();
            matches.add(
                new TagMatch(
                    start,
                    end,
                    rawCommand.substring(start + opening.length(), i),
                    rawCommand.substring(start, end)));
            closed = true;
            i = end;
            break;
          } else {
            i++;
          }
        }
      }
      if (!closed) {
        if (inQuotes || isConfirmation || isItem) {
          throw new IllegalArgumentException(
              "Unclosed tag or unbalanced quotes: " + rawCommand.substring(start));
        }
        break;
      }
    }
    return matches;
  }

  private boolean isConfirmationOpener(String s, int index) {
    if (index >= s.length()) return false;
    var sub = s.substring(index);
    return sub.regionMatches(true, 0, "c:", 0, 2) || sub.regionMatches(true, 0, "confirm:", 0, 8);
  }

  private boolean isItemOpener(String s, int index) {
    if (index >= s.length()) return false;
    var sub = s.substring(index);
    return sub.regionMatches(true, 0, "i:", 0, 2) || sub.regionMatches(true, 0, "item:", 0, 5);
  }

  private record TagMatch(int start, int end, String content, String rawText) {}

  private void parsePCM(String rawContent, String fullTag, List<PostCommandMeta> postCmds) {
    var content = rawContent;

    var onCancel = content.startsWith("!!");
    if (onCancel) {
      content = content.substring(2);
    } else {
      content = content.substring(1);
    }

    // Extract delay (!:N)
    var delay = 0;
    if (content.startsWith(":")) {
      var end = 1;
      while (end < content.length() && Character.isDigit(content.charAt(end))) {
        end++;
      }
      if (end == 1) {
        throw new IllegalArgumentException(
            "Invalid PCM delay syntax: expected digits after ':', got: " + content);
      }
      try {
        delay = Integer.parseInt(content.substring(1, end));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "PCM delay overflow or invalid number: " + content.substring(1, end), e);
      }
      if (delay < 0 || delay > PostActionSpec.MAX_DELAY_TICKS) {
        throw new IllegalArgumentException(
            "PCM delay out of bounds (0.." + PostActionSpec.MAX_DELAY_TICKS + "): " + delay);
      }
      content = content.substring(end);
    }

    content = content.trim();

    // Parse preset post-command reference: <!@id>
    if (content.startsWith("@")) {
      var id = content.substring(1);
      var space = id.indexOf(' ');
      if (space >= 0) id = id.substring(0, space);
      id = id.trim();
      if (!id.isEmpty()) {
        LOG.fine("Preset PCM: id=" + id + " onCancel=" + onCancel + " delay=" + delay);
        postCmds.add(
            new PostCommandMeta(id, new int[0], delay, onCancel, DispatchTarget.PASSTHROUGH, true));
        return;
      }
      throw new IllegalArgumentException("Blank preset post-command id: ''");
    }

    // Extract dispatch target (@console / @player)
    var target = DispatchTarget.PASSTHROUGH;
    var targetMatcher = pcmTarget.matcher(content);
    if (targetMatcher.find()) {
      target = DispatchTarget.valueOf(targetMatcher.group(1).toUpperCase(Locale.ROOT));
      content =
          (content.substring(0, targetMatcher.start()) + content.substring(targetMatcher.end()))
              .trim();
    }

    // Extract answer references ({N})
    var indices = new ArrayList<Integer>();
    var refMatcher = answerRef.matcher(content);
    while (refMatcher.find()) {
      try {
        indices.add(Integer.parseInt(refMatcher.group(1)));
      } catch (NumberFormatException e) {
        LOG.warning("Answer reference index too large, ignoring: " + refMatcher.group(1));
      }
    }

    // Clean up command text
    var command = content.trim();

    LOG.fine("PCM: delay=" + delay + " onCancel=" + onCancel + " target=" + target);

    postCmds.add(
        new PostCommandMeta(
            command,
            indices.stream().mapToInt(Integer::intValue).toArray(),
            delay,
            onCancel,
            target,
            false));
  }

  private void parsePromptTag(String rawContent, String fullTag, List<PromptTag> promptTags) {
    // Parse preset prompt reference: <@id>
    if (rawContent.startsWith("@")) {
      var id = rawContent.substring(1);
      var space = id.indexOf(' ');
      if (space >= 0) id = id.substring(0, space);
      id = id.trim();
      if (!id.isEmpty()) {
        LOG.fine("Preset prompt tag: id=" + id);
        promptTags.add(
            new PromptTag(
                fullTag,
                "",
                null,
                id,
                true,
                null,
                PromptTag.AnswerType.NONE,
                List.of(),
                true,
                null,
                null,
                Map.of(),
                null));
        return;
      }
      throw new IllegalArgumentException("Blank preset prompt id: ''");
    }

    // Parse compound dialog block containing one or more &&-separated sub-tags.
    if (containsCompoundDelimiter(rawContent)) {
      parseCompoundPromptTag(rawContent, fullTag, promptTags);
      return;
    }

    String key;
    String filter = null;
    String remainder;

    if (rawContent.startsWith("-")) {
      key = "";
      remainder = rawContent;
    } else {
      var firstColon = rawContent.indexOf(':');
      if (firstColon < 0
          || rawContent.substring(0, firstColon).contains(" ")
          || rawContent.substring(0, firstColon).contains("-")
          || rawContent.substring(0, firstColon).contains("\"")) {
        key = "";
        remainder = rawContent;
      } else {
        var rawKey = rawContent.substring(0, firstColon).trim();
        key = rawKey.toLowerCase(Locale.ROOT);
        var rest = rawContent.substring(firstColon + 1);
        if (ConfirmationGrammar.isConfirmationKey(key) || ItemGrammar.isItemKey(key)) {
          remainder = rest;
        } else {
          var secondColon = rest.indexOf(':');
          if (secondColon >= 0
              && !rest.startsWith("\"")
              && !rest.substring(0, secondColon).contains(" ")
              && !rest.substring(0, secondColon).contains("\"")
              && !rest.substring(0, secondColon).contains("-")) {
            filter = rest.substring(0, secondColon).trim();
            remainder = rest.substring(secondColon + 1);
          } else {
            remainder = rest;
          }
        }
      }
    }

    Condition breakIf = null;
    if (isBuiltInKey(key)) {
      var breakIfResult = extractBreakIf(remainder);
      remainder = breakIfResult.remainingContent();
      breakIf = breakIfResult.condition();
    }

    var dsMatcher = dsFlag.matcher(remainder);
    var newRemainder = dsMatcher.replaceAll("");
    var sanitize = remainder.length() == newRemainder.length();
    remainder = newRemainder;
    var timeout = extractTimeout(remainder);
    if (timeout != null) {
      remainder = timeoutFlag.matcher(remainder).replaceFirst("");
    }
    var validatorAlias = extractValidator(remainder);
    remainder = validatorFlag.matcher(remainder).replaceAll("");
    var type = extractType(remainder);
    remainder = intFlag.matcher(remainder).replaceAll("");
    remainder = strFlag.matcher(remainder).replaceAll("");
    var title = extractTitle(remainder);
    remainder = stripTitleFlag(remainder, title);

    // Log a one-shot migration hint if using the deprecated trailing-kind form.
    if (filter == null) warnIfTrailingKind(rawContent);

    Map<String, String> flags = Map.of();
    if (!isBuiltInKey(key)) {
      var flagResult = extractTrailingCustomFlags(remainder);
      remainder = flagResult.remainingContent();
      flags = flagResult.flags();
    }

    var displayText = unescape(remainder).trim();

    if (ConfirmationGrammar.isConfirmationKey(key)) {
      ConfirmationGrammar.parse(remainder);
    } else if (ItemGrammar.isItemKey(key)) {
      ItemGrammar.parse(remainder);
    }

    LOG.fine("Tag: key=" + key + " type=" + type + " sanitize=" + sanitize + " timeout=" + timeout);

    promptTags.add(
        new PromptTag(
            fullTag,
            key,
            filter,
            displayText,
            sanitize,
            validatorAlias,
            type,
            List.of(),
            false,
            title,
            timeout,
            flags,
            breakIf));
  }

  /**
   * Whether {@code content} contains a top-level {@code &&} delimiter (bracket, paren, quote, and
   * flag depth aware).
   *
   * <p>A literal {@code &&} inside a filter's constraint block (e.g. <code>num[0,24,&&step]</code>
   * ), inside parentheses/quotes, or inside a {@code -breakIf:} condition is ignored.
   */
  private boolean containsCompoundDelimiter(String content) {
    if (content == null) return false;
    var depth = 0;
    var inQuotes = false;
    for (var i = 0; i < content.length() - 1; i++) {
      var c = content.charAt(i);
      if (c == '\\') {
        i++;
        continue;
      }
      if (c == '"') {
        inQuotes = !inQuotes;
        continue;
      }
      if (!inQuotes) {
        if (c == '[' || c == '(') {
          depth++;
        } else if (c == ']' || c == ')') {
          if (depth > 0) depth--;
        } else if (depth == 0) {
          boolean atTokenStart = (i == 0 || Character.isWhitespace(content.charAt(i - 1)));
          if (atTokenStart && content.regionMatches(true, i, "-breakif:", 0, 9)) {
            i = findBreakIfExpressionEnd(content, i + 9) - 1;
            continue;
          }
          if (c == '&' && content.charAt(i + 1) == '&') {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static List<String> splitCompound(String s) {
    var parts = new ArrayList<String>();
    var depth = 0;
    var inQuotes = false;
    var start = 0;
    for (var i = 0; i < s.length() - 1; i++) {
      var c = s.charAt(i);
      if (c == '\\') {
        i++;
        continue;
      }
      if (c == '"') {
        inQuotes = !inQuotes;
        continue;
      }
      if (!inQuotes) {
        if (c == '[' || c == '(') {
          depth++;
        } else if (c == ']' || c == ')') {
          if (depth > 0) depth--;
        } else if (depth == 0) {
          boolean atTokenStart = (i == 0 || Character.isWhitespace(s.charAt(i - 1)));
          if (atTokenStart && s.regionMatches(true, i, "-breakif:", 0, 9)) {
            i = findBreakIfExpressionEnd(s, i + 9) - 1;
            continue;
          }
          if (c == '&' && s.charAt(i + 1) == '&') {
            parts.add(s.substring(start, i));
            i++;
            start = i + 1;
          }
        }
      }
    }
    parts.add(s.substring(start));
    return parts;
  }

  /**
   * Helper that scans forward from {@code exprStart} across a {@code -breakIf:} condition
   * expression until the end of the expression (next flag boundary, compound sub-tag delimiter, or
   * end of string).
   */
  private static int findBreakIfExpressionEnd(String content, int exprStart) {
    int j = exprStart;
    boolean exprInQuotes = false;
    int exprParenDepth = 0;
    while (j < content.length()) {
      char ec = content.charAt(j);
      if (ec == '\\') {
        j += (j + 1 < content.length()) ? 2 : 1;
        continue;
      }
      if (ec == '"') {
        exprInQuotes = !exprInQuotes;
        j++;
        continue;
      }
      if (!exprInQuotes) {
        if (ec == '(') {
          exprParenDepth++;
          j++;
          continue;
        }
        if (ec == ')') {
          if (exprParenDepth > 0) exprParenDepth--;
          j++;
          continue;
        }
        if (exprParenDepth == 0) {
          if (Character.isWhitespace(ec)) {
            int k = j;
            while (k < content.length() && Character.isWhitespace(content.charAt(k))) {
              k++;
            }
            if (k < content.length() && content.charAt(k) == '-') {
              if (k + 1 < content.length() && Character.isLetter(content.charAt(k + 1))) {
                // Following flag detected (e.g. -ds, -iv:, -t, -timeout, -breakif)
                break;
              }
            }
            if (k + 1 < content.length()
                && content.charAt(k) == '&'
                && content.charAt(k + 1) == '&') {
              if (isValidFollowingSubTag(content, k + 2)) {
                // Compound delimiter detected
                break;
              }
            }
          } else if (ec == '&' && j + 1 < content.length() && content.charAt(j + 1) == '&') {
            if (isValidFollowingSubTag(content, j + 2)) {
              // Compound delimiter detected (no whitespace before &&)
              break;
            }
          }
        }
      }
      j++;
    }
    return j;
  }

  /**
   * Lookahead check to determine if the content following {@code &&} matches a valid compound
   * sub-tag prefix/key grammar or a following block-level flag.
   *
   * @param s the full content string
   * @param index index immediately following the {@code &&}
   * @return {@code true} if the following content is a valid compound sub-tag or flag
   */
  static boolean isValidFollowingSubTag(String s, int index) {
    if (s == null || index >= s.length()) return false;
    int k = index;
    while (k < s.length() && Character.isWhitespace(s.charAt(k))) {
      k++;
    }
    if (k >= s.length()) return false;

    // Case 1: Sub-segment starts with a flag (e.g. -ds, -timeout:30, -breakIf:...)
    if (s.charAt(k) == '-') {
      return k + 1 < s.length() && Character.isLetter(s.charAt(k + 1));
    }

    // Case 2: Sub-tag starts with a prompt key followed by a colon (e.g. d:text:..., dialog:...,
    // custom:...)
    if (Character.isLetter(s.charAt(k))) {
      int idStart = k;
      while (k < s.length() && (Character.isLetterOrDigit(s.charAt(k)) || s.charAt(k) == '_')) {
        k++;
      }
      if (k > idStart && k < s.length() && s.charAt(k) == ':') {
        return true;
      }
    }

    return false;
  }

  /**
   * Parse a compound dialog tag ({@code <d:filter1:disp1 && d:filter2:disp2>}). Block-level flags
   * are extracted first and stripped before splitting on {@code &&}.
   */
  private void parseCompoundPromptTag(
      String rawContent, String fullTag, List<PromptTag> promptTags) {
    var breakIfResult = extractBreakIf(rawContent);
    var contentWithoutBreakIf = breakIfResult.remainingContent();
    var breakIf = breakIfResult.condition();

    var dsMatcher = dsFlag.matcher(contentWithoutBreakIf);
    var contentWithoutDs = dsMatcher.replaceAll("");
    var sanitize = contentWithoutBreakIf.length() == contentWithoutDs.length();
    var timeout = extractTimeout(contentWithoutDs);
    if (timeout != null) {
      contentWithoutDs = timeoutFlag.matcher(contentWithoutDs).replaceFirst("");
    }
    var validatorAlias = extractValidator(contentWithoutDs);
    var type = extractType(contentWithoutDs);
    var title = extractTitle(contentWithoutDs);

    // Strip flags from the compound tag content.
    var stripped =
        stripTitleFlag(contentWithoutDs, title)
            .replaceAll(validatorFlag.pattern(), "")
            .replaceAll(intFlag.pattern(), "")
            .replaceAll(strFlag.pattern(), "")
            .trim();

    var subContents = splitCompound(stripped);
    var subTags = new ArrayList<PromptTag>();
    for (var sub : subContents) {
      var trimmed = sub.trim();
      if (trimmed.isEmpty()) continue;
      subTags.add(buildSubTag(trimmed, fullTag, sanitize, validatorAlias, type));
    }

    if (subTags.isEmpty()) {
      // Ignore degenerate input if all sub-tags are empty.
      LOG.fine("Compound tag produced zero sub-tags after trimming");
      return;
    }

    // Disallow d:tab inside compound tags.
    for (var sub : subTags) {
      if (isTabFilter(sub.filter())) {
        throw new IllegalArgumentException(
            "d:tab is not allowed in compound prompt tags: <" + rawContent + ">");
      }
    }

    var compoundKey = subTags.get(0).key();
    LOG.fine(
        "Compound tag: key="
            + compoundKey
            + " rows="
            + subTags.size()
            + " sanitize="
            + sanitize
            + " validator="
            + validatorAlias
            + " type="
            + type
            + " timeout="
            + timeout
            + " breakIf="
            + breakIf);
    promptTags.add(
        new PromptTag(
            fullTag,
            compoundKey,
            null,
            "",
            sanitize,
            validatorAlias,
            type,
            subTags,
            false,
            title,
            timeout,
            Map.of(),
            breakIf));
  }

  /**
   * Parse one sub-content of a compound block. Reuses the standard key/filter/display extraction
   * but skips the flag detection — block-level flags have already been extracted by the caller.
   */
  private PromptTag buildSubTag(
      String subContent,
      String fullTag,
      boolean sanitize,
      String validatorAlias,
      PromptTag.AnswerType type) {
    String key;
    String filter = null;
    String remainder;
    if (subContent.startsWith("-")) {
      key = "";
      filter = null;
      remainder = subContent;
    } else {
      var firstColon = subContent.indexOf(':');
      if (firstColon < 0) {
        key = "";
        remainder = subContent;
      } else {
        key = subContent.substring(0, firstColon).trim();
        var rest = subContent.substring(firstColon + 1);
        var secondColon = rest.indexOf(':');
        if (secondColon >= 0 && !rest.substring(0, secondColon).contains(" ")) {
          filter = rest.substring(0, secondColon).trim();
          remainder = rest.substring(secondColon + 1);
        } else {
          remainder = rest;
        }
      }
    }
    var displayText = unescape(remainder).trim();
    return new PromptTag(
        fullTag,
        key,
        filter,
        displayText,
        sanitize,
        validatorAlias,
        type,
        List.of(),
        false,
        null,
        null,
        Map.of(),
        null);
  }

  /**
   * Extracts a {@link TitleConfig} from the {@code -t} flag in the given content, or returns {@code
   * null} if no title flag is present.
   *
   * <p>Syntax variants:
   *
   * <ul>
   *   <li>{@code -t} — standalone flag; {@code main} is empty (defaults to display text later),
   *       {@code sub} and {@code ticks} are {@code null}
   *   <li>{@code -t:Main} — main title only
   *   <li>{@code -t:"Main Title"|Sub|70} — main, subtitle, and ticks
   *   <li>{@code -t:"Main"||70} — main and ticks; subtitle skipped via {@code ||}
   * </ul>
   *
   * <p>Parameters after {@code -t:} are split by {@code |} (quote-aware). Surrounding double quotes
   * are removed from each part. An empty part (from {@code ||}) is treated as "skip" (null) for
   * {@code sub} and {@code ticks}; an empty {@code main} is preserved as an empty string.
   */
  TitleConfig extractTitle(String content) {
    var m = titleFlag.matcher(content);
    if (!m.find()) return null;
    var matched = m.group();
    if (matched.equals("-t")) {
      return new TitleConfig("", null, null);
    }
    var params = matched.substring(3);
    var parts = splitTitleParams(params);
    // main title
    var main = unquote(parts.get(0));
    // sub title
    String sub = null;
    if (parts.size() > 1 && !parts.get(1).isEmpty()) {
      sub = unquote(parts.get(1));
    }
    // ticks
    Integer ticks = null;
    if (parts.size() > 2 && !parts.get(2).isEmpty()) {
      try {
        ticks = Integer.parseInt(parts.get(2).trim());
      } catch (NumberFormatException e) {
        LOG.fine("Title ticks not a valid integer");
      }
    }
    return new TitleConfig(main, sub, ticks);
  }

  /**
   * Strips the first {@code -t…} match from {@code content}. If {@code title} is {@code null} (no
   * title flag was found), the content is returned unchanged.
   */
  String stripTitleFlag(String content, TitleConfig title) {
    if (title == null) return content;
    return titleFlag.matcher(content).replaceFirst("");
  }

  /**
   * Splits the title parameter string by {@code |}, respecting double-quoted segments so that a
   * pipe inside quotes is not treated as a delimiter.
   */
  private static List<String> splitTitleParams(String s) {
    var parts = new ArrayList<String>();
    var current = new StringBuilder();
    var inQuotes = false;
    for (var i = 0; i < s.length(); i++) {
      var c = s.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
        current.append(c);
      } else if (c == '|' && !inQuotes) {
        parts.add(current.toString());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    parts.add(current.toString());
    return parts;
  }

  /** Removes surrounding double quotes from {@code s} if present. */
  private static String unquote(String s) {
    if (s == null) return null;
    s = s.trim();
    if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  Integer extractTimeout(String content) {
    var m = timeoutFlag.matcher(content);
    if (!m.find()) return null;
    var matched = m.group();
    if (!matched.startsWith("-timeout:") || matched.length() <= "-timeout:".length()) {
      throw new IllegalArgumentException("Malformed -timeout flag in tag: " + matched);
    }
    var valStr = matched.substring("-timeout:".length());
    int val;
    try {
      val = Integer.parseInt(valStr);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Non-numeric -timeout value in tag: " + valStr);
    }
    if (val < 1 || val > 3600) {
      throw new IllegalArgumentException("Timeout value out of range [1, 3600]: " + val);
    }
    if (m.find()) {
      throw new IllegalArgumentException("Duplicate -timeout flag in tag");
    }
    return val;
  }

  private String extractValidator(String content) {
    var m = validatorFlag.matcher(content);
    if (!m.find()) return null;
    var alias = m.group(1);
    if (alias.isBlank()) {
      throw new IllegalArgumentException("Blank input-validator alias: ''");
    }
    return alias;
  }

  private PromptTag.AnswerType extractType(String content) {
    if (intFlag.matcher(content).find()) return PromptTag.AnswerType.INTEGER;
    if (strFlag.matcher(content).find()) return PromptTag.AnswerType.STRING;
    return PromptTag.AnswerType.NONE;
  }

  /**
   * Log a one-shot deprecation warning for the legacy trailing-kind dialog form ({@code <d:Title
   * bool>}). Only fires when the key is {@code d} to avoid noise from sign prompts ending in
   * "text".
   */
  private void warnIfTrailingKind(String rawContent) {
    if (rawContent == null) return;
    var firstColon = rawContent.indexOf(':');
    if (firstColon <= 0) return; // no key, can't be a dialog tag
    var key = rawContent.substring(0, firstColon).trim();
    if (!"d".equals(key)) return; // only warn for the default dialog key
    if (!TRAILING_KIND_DETECT.matcher(rawContent).find()) return;
    if (!seenDeprecationWarnings.add(rawContent)) return; // dedup across parses
    LOG.warning(
        "Deprecated trailing-kind dialog syntax detected. Use the unified form '<d:kind[constraints]:display>'. "
            + "The trailing form is no longer parsed; the prompt will be a text field.");
  }

  private String unescape(String input) {
    if (input == null || input.isEmpty()) return input;
    String escape = config.escape();
    String opening = config.opening();
    String closing = config.closing();
    var result = new StringBuilder(input.length());
    int i = 0;
    while (i < input.length()) {
      if (input.startsWith(escape, i)) {
        int nextIdx = i + escape.length();
        if (input.startsWith(opening, nextIdx)) {
          result.append(opening);
          i = nextIdx + opening.length();
          continue;
        } else if (input.startsWith(closing, nextIdx)) {
          result.append(closing);
          i = nextIdx + closing.length();
          continue;
        } else if (input.startsWith(escape, nextIdx)) {
          result.append(escape);
          i = nextIdx + escape.length();
          continue;
        }
      }
      result.append(input.charAt(i));
      i++;
    }
    return result.toString();
  }

  /**
   * Whether a raw filter string is the {@code tab} kind with optional {@code [N]} constraint. Null
   * or empty filter returns {@code false}.
   */
  static boolean isTabFilter(String filter) {
    if (filter == null || filter.isEmpty()) return false;
    var base = filter;
    var bracket = base.indexOf('[');
    if (bracket >= 0) base = base.substring(0, bracket);
    return base.trim().toLowerCase(Locale.ROOT).equals("tab");
  }

  /**
   * Checks whether the specified key is a built-in or reserved prompt type key (case-insensitive).
   *
   * @param key the key to check
   * @return {@code true} if built-in or empty, {@code false} for custom third-party keys
   */
  public static boolean isBuiltInKey(String key) {
    return key == null || BuiltInPromptType.resolve(key).isPresent();
  }

  /**
   * Extracts the {@code -breakIf:<condition>} flag from tag content, compiling the condition with
   * inline compile options (disallowing PAPI placeholders).
   *
   * @param content the raw content string
   * @return a {@link BreakIfResult} with remaining content and compiled {@link Condition} (or
   *     {@code null})
   * @throws IllegalArgumentException if the flag is malformed, duplicated, blank, or contains
   *     invalid syntax
   */
  public static BreakIfResult extractBreakIf(String content) {
    if (content == null || content.isEmpty()) {
      return new BreakIfResult(content == null ? "" : content, null);
    }

    int flagCount = 0;
    int firstFlagStart = -1;
    int firstExprEnd = -1;
    String conditionSource = null;

    int i = 0;
    boolean inQuotes = false;
    while (i < content.length()) {
      char c = content.charAt(i);
      if (c == '\\') {
        i += (i + 1 < content.length()) ? 2 : 1;
        continue;
      }
      if (c == '"') {
        inQuotes = !inQuotes;
        i++;
        continue;
      }
      if (!inQuotes) {
        boolean atTokenStart = (i == 0 || Character.isWhitespace(content.charAt(i - 1)));
        if (atTokenStart && content.regionMatches(true, i, "-breakif", 0, 8)) {
          int candidateStart = i;
          if (i + 8 >= content.length() || content.charAt(i + 8) != ':') {
            int tokenEnd = i + 8;
            while (tokenEnd < content.length()
                && !Character.isWhitespace(content.charAt(tokenEnd))) {
              tokenEnd++;
            }
            throw new IllegalArgumentException(
                "Malformed -breakIf flag in tag: " + content.substring(candidateStart, tokenEnd));
          }
          int exprStart = i + 9;
          if (exprStart >= content.length()) {
            throw new IllegalArgumentException("Empty condition in -breakIf flag");
          }

          // Scan expression
          int exprEnd = findBreakIfExpressionEnd(content, exprStart);
          String rawExpr = content.substring(exprStart, exprEnd).trim();
          if (rawExpr.isEmpty()) {
            throw new IllegalArgumentException("Empty condition in -breakIf flag");
          }

          flagCount++;
          if (flagCount > 1) {
            throw new IllegalArgumentException("Duplicate -breakIf flag in tag");
          }

          firstFlagStart = candidateStart;
          firstExprEnd = exprEnd;
          conditionSource = rawExpr;

          i = exprEnd;
          continue;
        }
      }
      i++;
    }

    if (flagCount == 0) {
      return new BreakIfResult(content, null);
    }

    Condition condition;
    try {
      condition = ConditionCompiler.compile(conditionSource, ConditionCompileOptions.forInline());
    } catch (ConditionException e) {
      throw new IllegalArgumentException("Invalid -breakIf condition: " + e.getMessage(), e);
    }

    String remaining = content.substring(0, firstFlagStart) + content.substring(firstExprEnd);
    return new BreakIfResult(remaining, condition);
  }

  /**
   * Extracts unambiguously trailing custom flags (e.g. {@code -glow}, {@code -rarity:epic}, {@code
   * -desc:"Super sword"}) from the tail of the given tag remainder content.
   *
   * <p>Flags are parsed from right to left at token boundaries outside quotes. Prose preceding the
   * trailing flags is preserved intact as remaining display text.
   *
   * @param content the raw remainder content
   * @return the result containing remaining display text and parsed immutable flags map
   * @throws IllegalArgumentException if flags are duplicate, malformed, contain unbalanced quotes,
   *     or exceed count/size limits
   */
  public static CustomFlagsResult extractTrailingCustomFlags(String content) {
    if (content == null || content.isEmpty()) {
      return new CustomFlagsResult(content == null ? "" : content, Map.of());
    }

    var flags = new LinkedHashMap<String, String>();
    var remaining = content;

    while (true) {
      remaining = remaining.stripTrailing();
      if (remaining.isEmpty()) {
        break;
      }

      if (remaining.endsWith("\"")) {
        int quoteEnd = remaining.length() - 1;
        int quoteStart = -1;
        for (int i = quoteEnd - 1; i >= 0; i--) {
          if (remaining.charAt(i) == '"') {
            int backslashCount = 0;
            for (int j = i - 1; j >= 0 && remaining.charAt(j) == '\\'; j--) {
              backslashCount++;
            }
            if (backslashCount % 2 == 0) {
              quoteStart = i;
              break;
            }
          }
        }

        if (quoteStart == -1) {
          throw new IllegalArgumentException(
              "Unbalanced or unclosed quotes in custom flag: " + remaining);
        }

        if (quoteStart <= 0 || remaining.charAt(quoteStart - 1) != ':') {
          // Not preceded by ':', so this is quoted text like "hello", not a -flag:"value".
          break;
        }

        int colonIdx = quoteStart - 1;
        int dashIdx = -1;
        for (int i = colonIdx - 1; i >= 0; i--) {
          char c = remaining.charAt(i);
          if (c == '-') {
            if (i == 0 || Character.isWhitespace(remaining.charAt(i - 1))) {
              dashIdx = i;
              break;
            }
          } else if (Character.isWhitespace(c)) {
            break;
          }
        }

        if (dashIdx == -1) {
          break;
        }

        String rawName = remaining.substring(dashIdx + 1, colonIdx);
        if (!CUSTOM_FLAG_NAME_PATTERN.matcher(rawName).matches()) {
          throw new IllegalArgumentException(
              "Malformed custom flag name '-" + rawName + "' in: " + remaining);
        }
        if (rawName.length() > MAX_CUSTOM_FLAG_NAME_LENGTH) {
          throw new IllegalArgumentException(
              "Custom flag name '-"
                  + rawName
                  + "' exceeds maximum length ("
                  + MAX_CUSTOM_FLAG_NAME_LENGTH
                  + ")");
        }

        String value = remaining.substring(quoteStart + 1, quoteEnd);
        if (value.length() > MAX_CUSTOM_FLAG_VALUE_LENGTH) {
          throw new IllegalArgumentException(
              "Custom flag value for '-"
                  + rawName
                  + "' exceeds maximum length ("
                  + MAX_CUSTOM_FLAG_VALUE_LENGTH
                  + ")");
        }

        String canonicalName = rawName.toLowerCase(Locale.ROOT);
        if (flags.containsKey(canonicalName)) {
          throw new IllegalArgumentException("Duplicate custom flag: -" + rawName);
        }

        flags.put(canonicalName, value);
        remaining = remaining.substring(0, dashIdx);
      } else {
        int lastSpace = -1;
        for (int i = remaining.length() - 1; i >= 0; i--) {
          if (Character.isWhitespace(remaining.charAt(i))) {
            lastSpace = i;
            break;
          }
        }

        int tokenStart = lastSpace == -1 ? 0 : lastSpace + 1;
        String token = remaining.substring(tokenStart);

        if (!token.startsWith("-")) {
          break;
        }

        int colonIdx = token.indexOf(':');
        if (colonIdx >= 0) {
          String rawName = token.substring(1, colonIdx);
          String value = token.substring(colonIdx + 1);

          if (value.isEmpty()) {
            throw new IllegalArgumentException("Malformed custom flag with empty value: " + token);
          }
          if (value.indexOf('"') >= 0) {
            throw new IllegalArgumentException("Malformed custom flag syntax: " + token);
          }
          if (!CUSTOM_FLAG_NAME_PATTERN.matcher(rawName).matches()) {
            throw new IllegalArgumentException(
                "Malformed custom flag name '-" + rawName + "' in: " + token);
          }
          if (rawName.length() > MAX_CUSTOM_FLAG_NAME_LENGTH) {
            throw new IllegalArgumentException(
                "Custom flag name '-"
                    + rawName
                    + "' exceeds maximum length ("
                    + MAX_CUSTOM_FLAG_NAME_LENGTH
                    + ")");
          }
          if (value.length() > MAX_CUSTOM_FLAG_VALUE_LENGTH) {
            throw new IllegalArgumentException(
                "Custom flag value for '-"
                    + rawName
                    + "' exceeds maximum length ("
                    + MAX_CUSTOM_FLAG_VALUE_LENGTH
                    + ")");
          }

          String canonicalName = rawName.toLowerCase(Locale.ROOT);
          if (flags.containsKey(canonicalName)) {
            throw new IllegalArgumentException("Duplicate custom flag: -" + rawName);
          }

          flags.put(canonicalName, value);
          remaining = remaining.substring(0, tokenStart);
        } else {
          String rawName = token.substring(1);
          if (rawName.isEmpty()) {
            break;
          }
          if (!CUSTOM_FLAG_NAME_PATTERN.matcher(rawName).matches()) {
            break;
          }
          if (rawName.length() > MAX_CUSTOM_FLAG_NAME_LENGTH) {
            throw new IllegalArgumentException(
                "Custom flag name '-"
                    + rawName
                    + "' exceeds maximum length ("
                    + MAX_CUSTOM_FLAG_NAME_LENGTH
                    + ")");
          }

          String canonicalName = rawName.toLowerCase(Locale.ROOT);
          if (flags.containsKey(canonicalName)) {
            throw new IllegalArgumentException("Duplicate custom flag: -" + rawName);
          }

          flags.put(canonicalName, "true");
          remaining = remaining.substring(0, tokenStart);
        }
      }
    }

    if (flags.size() > MAX_CUSTOM_FLAGS) {
      throw new IllegalArgumentException(
          "Exceeded maximum allowed custom flags ("
              + MAX_CUSTOM_FLAGS
              + "): found "
              + flags.size());
    }

    int aggregateLength = 0;
    for (String val : flags.values()) {
      aggregateLength += val.length();
    }
    if (aggregateLength > MAX_CUSTOM_FLAGS_AGGREGATE_LENGTH) {
      throw new IllegalArgumentException(
          "Custom flags aggregate value length ("
              + aggregateLength
              + ") exceeds limit of "
              + MAX_CUSTOM_FLAGS_AGGREGATE_LENGTH);
    }

    return new CustomFlagsResult(remaining, Collections.unmodifiableMap(flags));
  }
}
