package dev.cyr1en.promptcore.parser;

import dev.cyr1en.promptcore.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Parses command strings into structured {@link ParsedCommand} objects.
 *
 * <p>Thread-safe after construction. No platform dependencies.
 */
public class CommandLineParser {

  private static final Logger LOG = Logger.getLogger(CommandLineParser.class.getName());

  private final ParserConfig config;
  private final Pattern tagPattern;
  private final Pattern pcmPrefix;
  private final Pattern pcmCancelPrefix;
  private final Pattern pcmDelay;
  private final Pattern pcmTarget;
  private final Pattern answerRef;
  private final Pattern validatorFlag;
  private final Pattern dsFlag;
  private final Pattern intFlag;
  private final Pattern strFlag;
  // Deprecation warning state and detector for the legacy trailing-kind dialog form
  // (`<d:Title bool>`). The form was removed in favor of the unified `key:filter:display`
  // shape that mirrors Player UI; this detector exists only to log a one-shot migration
  // hint for any config still using the old form.
  private static final Pattern TRAILING_KIND_DETECT =
      Pattern.compile("\\b(?:text|bool|num)(?:\\[[^\\]]*\\])?\\s*$", Pattern.CASE_INSENSITIVE);
  private final Set<String> seenDeprecationWarnings = ConcurrentHashMap.newKeySet();

  public CommandLineParser() {
    this(ParserConfig.ANGLE_BRACKETS);
  }

  public CommandLineParser(ParserConfig config) {
    this.config = config;
    String open = config.opening();
    String close = config.closing();
    String escPattern = Pattern.quote(config.escape());
    this.tagPattern =
        Pattern.compile(
            "(?<!" + escPattern + ")" + Pattern.quote(open) + "(.*?)" + Pattern.quote(close));
    this.pcmPrefix = Pattern.compile("^!");
    this.pcmCancelPrefix = Pattern.compile("^!!");
    this.pcmDelay = Pattern.compile("^!:(\\d+)");
    this.pcmTarget = Pattern.compile("@(console|player)");
    this.answerRef = Pattern.compile("\\{(\\d+)}");
    this.validatorFlag = Pattern.compile("-iv:(\\w+)");
    this.dsFlag = Pattern.compile("-ds\\b");
    this.intFlag = Pattern.compile("-int\\b");
    this.strFlag = Pattern.compile("-str\\b");
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
      return new ParsedCommand(rawCommand == null ? "" : rawCommand, List.of(), List.of(), config);
    }

    var matcher = tagPattern.matcher(rawCommand);
    var promptTags = new ArrayList<PromptTag>();
    var postCmds = new ArrayList<PostCommandMeta>();

    LOG.fine("Parsing command: " + rawCommand);

    while (matcher.find()) {
      var rawContent = matcher.group(1);
      var fullTag = config.opening() + rawContent + config.closing();

      if (isPCM(rawContent)) {
        parsePCM(rawContent, fullTag, postCmds);
      } else {
        parsePromptTag(rawContent, fullTag, promptTags);
      }
    }

    var esc = config.escape();
    var template =
        rawCommand
            .replace(esc + config.opening(), config.opening())
            .replace(esc + config.closing(), config.closing());

    LOG.fine("Parsed " + promptTags.size() + " prompt tags, " + postCmds.size() + " PCMs");

    return new ParsedCommand(
        template,
        Collections.unmodifiableList(promptTags),
        Collections.unmodifiableList(postCmds),
        config);
  }

  /**
   * Whether the raw command string contains at least one tag (prompt or PCM). Used by callers that
   * need to distinguish "no tag form at all" from "had tag form but parsing returned empty for some
   * reason" — for example, the fail-fast path in the engine.
   */
  public boolean hasTagForm(String rawCommand) {
    if (rawCommand == null || rawCommand.isBlank()) return false;
    return tagPattern.matcher(rawCommand).find();
  }

  private boolean isPCM(String content) {
    return pcmPrefix.matcher(content).find();
  }

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
      if (end > 1) {
        delay = Integer.parseInt(content.substring(1, end));
        content = content.substring(end);
      }
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
    }

    // Extract dispatch target (@console / @player)
    var target = DispatchTarget.PASSTHROUGH;
    var targetMatcher = pcmTarget.matcher(content);
    if (targetMatcher.find()) {
      target = DispatchTarget.valueOf(targetMatcher.group(1).toUpperCase());
      content = content.replaceAll("@(console|player)", "").trim();
    }

    // Extract answer references ({N})
    var indices = new ArrayList<Integer>();
    var refMatcher = answerRef.matcher(content);
    while (refMatcher.find()) {
      indices.add(Integer.parseInt(refMatcher.group(1)));
    }

    // Clean up command text
    var command = content.trim();

    LOG.fine(
        "PCM: cmd=" + command + " delay=" + delay + " onCancel=" + onCancel + " target=" + target);

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
                null));
        return;
      }
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
          || rawContent.substring(0, firstColon).contains("-")) {
        key = "";
        remainder = rawContent;
      } else {
        key = rawContent.substring(0, firstColon).trim();
        var rest = rawContent.substring(firstColon + 1);
        var secondColon = rest.indexOf(':');
        if (secondColon >= 0 && !rest.substring(0, secondColon).contains(" ")) {
          filter = rest.substring(0, secondColon).trim();
          remainder = rest.substring(secondColon + 1);
        } else {
          remainder = rest;
        }
      }
    }

    var sanitize = !dsFlag.matcher(remainder).find();
    var validatorAlias = extractValidator(remainder);
    var type = extractType(remainder);

    // The legacy trailing-kind form (`<d:Title bool>`, `<d:Amount num[1,64]>`) was removed
    // in favor of the unified `key:filter:display` form. If we see the deprecated form —
    // and the two-colon form did NOT already populate `filter` (which would mean the user
    // already migrated) — log a one-shot migration hint so the affected config can be
    // updated. The behavior is a silent fallback to a text field.
    if (filter == null) warnIfTrailingKind(rawContent);

    var displayText =
        remainder
            .replaceAll("-ds\\b", "")
            .replaceAll("-iv:\\w+", "")
            .replaceAll("-int\\b", "")
            .replaceAll("-str\\b", "")
            .replace(config.escape() + config.opening(), config.opening())
            .replace(config.escape() + config.closing(), config.closing())
            .trim();

    LOG.fine(
        "Tag: key="
            + key
            + " filter="
            + filter
            + " sanitize="
            + sanitize
            + " validator="
            + validatorAlias
            + " type="
            + type
            + " title="
            + title
            + " display="
            + displayText);

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
            title));
  }

  /**
   * Whether {@code content} contains a top-level {@code &&} delimiter (bracket-depth aware).
   *
   * <p>A literal {@code &&} inside a filter's constraint block (e.g. <code>num[0,24,&&step]</code>)
   * is ignored.
   */
  private boolean containsCompoundDelimiter(String content) {
    if (content == null) return false;
    var depth = 0;
    for (var i = 0; i < content.length() - 1; i++) {
      var c = content.charAt(i);
      if (c == '[') depth++;
      else if (c == ']') depth--;
      else if (depth == 0 && c == '&' && content.charAt(i + 1) == '&') {
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
    var sanitize = !dsFlag.matcher(rawContent).find();
    var validatorAlias = extractValidator(rawContent);
    var type = extractType(rawContent);

    // Strip the flags from the whole content. After stripping, the sub-tag
    // content is "clean" — no stray flag tokens leaking into display text.
    // A side-effect: a literal "-ds" in a display label (very unusual) would
    // also get stripped. We document this limitation; users who need it
    // shouldn't put bare flag tokens in display text.
    var stripped =
        rawContent
            .replaceAll("-ds\\b", "")
            .replaceAll("-iv:\\w+", "")
            .replaceAll("-int\\b", "")
            .replaceAll("-str\\b", "")
            .trim();

    var subContents = stripped.split("&&");
    var subTags = new ArrayList<PromptTag>();
    for (var sub : subContents) {
      var trimmed = sub.trim();
      if (trimmed.isEmpty()) continue;
      subTags.add(buildSubTag(trimmed, fullTag, sanitize, validatorAlias, type));
    }

    if (subTags.isEmpty()) {
      // Ignore degenerate input if all sub-tags are empty.
      LOG.fine("Compound tag produced zero sub-tags after trimming: " + rawContent);
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
            + type);
    promptTags.add(
        new PromptTag(
            fullTag, compoundKey, null, "", sanitize, validatorAlias, type, subTags, false, title));
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
    var displayText =
        remainder
            .replace(config.escape() + config.opening(), config.opening())
            .replace(config.escape() + config.closing(), config.closing())
            .trim();
    return new PromptTag(
        fullTag, key, filter, displayText, sanitize, validatorAlias, type, List.of(), false);
  }

  private String extractValidator(String content) {
    var m = validatorFlag.matcher(content);
    return m.find() ? m.group(1) : null;
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
        "Deprecated trailing-kind dialog syntax in '"
            + config.opening()
            + rawContent
            + config.closing()
            + "'. Use the unified form '<d:kind[constraints]:display>'. "
            + "The trailing form is no longer parsed; the prompt will be a text field.");
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
}
