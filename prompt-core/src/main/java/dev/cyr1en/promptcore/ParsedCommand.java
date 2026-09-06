package dev.cyr1en.promptcore;

import dev.cyr1en.promptcore.plan.PreDispatchGateSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The result of parsing a raw command string.
 *
 * <p>{@link #templateCommand()} retains the historical unescaped view used by callers. The raw
 * command and the exact spans of parsed tags are retained separately so assembly can replace tags
 * in the original source, rather than searching through already-replaced answer text.
 */
public record ParsedCommand(
    String templateCommand,
    List<PromptTag> promptTags,
    List<PostCommandMeta> postCmds,
    List<PreDispatchGateSpec> preDispatchGates,
    ParserConfig parserConfig,
    String rawTemplateCommand,
    List<TemplateSpan> templateSpans) {

  /**
   * Describes one tag that was actually parsed. Filtered tags, escaped tags, and ordinary literals
   * deliberately have no span and therefore remain untouched during assembly.
   *
   * @param start start offset in {@link ParsedCommand#rawTemplateCommand()}
   * @param end exclusive end offset in the raw command
   * @param rawText the exact source text covered by this span
   * @param pcm whether the span is a parsed post-command meta
   * @param parsedIndex index in {@link #postCmds()}, {@link #promptTags()}, or {@link
   *     #preDispatchGates()}, according to {@code pcm} and {@code gate}
   * @param gate whether the span is a parsed pre-dispatch gate
   */
  public record TemplateSpan(
      int start, int end, String rawText, boolean pcm, int parsedIndex, boolean gate) {
    public TemplateSpan {
      Objects.requireNonNull(rawText, "rawText");
      if (start < 0 || end < start || end - start != rawText.length()) {
        throw new IllegalArgumentException("Invalid parsed template span: " + start + ".." + end);
      }
      if (parsedIndex < 0) throw new IllegalArgumentException("parsedIndex must not be negative");
    }

    public TemplateSpan(int start, int end, String rawText, boolean pcm, int parsedIndex) {
      this(start, end, rawText, pcm, parsedIndex, false);
    }

    public boolean isGate() {
      return gate;
    }
  }

  /**
   * Backward-compatible constructor for callers that construct a parsed command directly without
   * gates. Commands produced by {@link dev.cyr1en.promptcore.parser.CommandLineParser} use the
   * raw-span constructor.
   */
  public ParsedCommand(
      String templateCommand,
      List<PromptTag> promptTags,
      List<PostCommandMeta> postCmds,
      ParserConfig parserConfig) {
    this(
        templateCommand, promptTags, postCmds, List.of(), parserConfig, templateCommand, List.of());
  }

  /**
   * Backward-compatible constructor for callers that construct a parsed command with gates directly
   * (no spans).
   */
  public ParsedCommand(
      String templateCommand,
      List<PromptTag> promptTags,
      List<PostCommandMeta> postCmds,
      List<PreDispatchGateSpec> preDispatchGates,
      ParserConfig parserConfig) {
    this(
        templateCommand,
        promptTags,
        postCmds,
        preDispatchGates,
        parserConfig,
        templateCommand,
        List.of());
  }

  /** Backward-compatible constructor with spans but no gates. */
  public ParsedCommand(
      String templateCommand,
      List<PromptTag> promptTags,
      List<PostCommandMeta> postCmds,
      ParserConfig parserConfig,
      String rawTemplateCommand,
      List<TemplateSpan> templateSpans) {
    this(
        templateCommand,
        promptTags,
        postCmds,
        List.of(),
        parserConfig,
        rawTemplateCommand,
        templateSpans);
  }

  /** Compact constructor that defensively copies all live collections. */
  public ParsedCommand {
    Objects.requireNonNull(templateCommand);
    Objects.requireNonNull(promptTags);
    Objects.requireNonNull(postCmds);
    Objects.requireNonNull(preDispatchGates);
    Objects.requireNonNull(parserConfig);
    Objects.requireNonNull(rawTemplateCommand);
    Objects.requireNonNull(templateSpans);
    if (promptTags.size() > 16) {
      throw new IllegalArgumentException(
          "Prompt tags count cannot exceed 16, got " + promptTags.size());
    }
    if (preDispatchGates.size() > 16) {
      throw new IllegalArgumentException(
          "Pre-dispatch gates count cannot exceed 16, got " + preDispatchGates.size());
    }
    promptTags = List.copyOf(promptTags);
    postCmds = List.copyOf(postCmds);
    preDispatchGates = List.copyOf(preDispatchGates);
    templateSpans = List.copyOf(templateSpans);
    validateSpans(
        rawTemplateCommand,
        templateSpans,
        promptTags.size(),
        postCmds.size(),
        preDispatchGates.size());
  }

  /** Number of prompt tags in this command. */
  public int promptCount() {
    return promptTags.size();
  }

  /** Number of post-command metas in this command. */
  public int pcmCount() {
    return postCmds.size();
  }

  /** Number of pre-dispatch gates in this command. */
  public int gateCount() {
    return preDispatchGates.size();
  }

  /** Whether this command contains any prompt tags. */
  public boolean hasPrompts() {
    return !promptTags.isEmpty();
  }

  /** Whether this command contains any pre-dispatch gates. */
  public boolean hasGates() {
    return !preDispatchGates.isEmpty();
  }

  /** PCMs that run on successful completion ({@code <!...>}). */
  public List<PostCommandMeta> onCompletePCMs() {
    return postCmds.stream().filter(pcm -> !pcm.onCancel()).toList();
  }

  /** PCMs that run on cancellation ({@code <!!...>}). */
  public List<PostCommandMeta> onCancelPCMs() {
    return postCmds.stream().filter(PostCommandMeta::onCancel).toList();
  }

  /**
   * Build a "partial command" string suitable for Brigadier parsing at the current prompt position.
   *
   * <p>The assembly walks the original raw template once. It never searches the command after an
   * answer has been inserted, so duplicate tags remain distinct and answer text containing markup
   * cannot be interpreted as another tag. Unescaping is performed only after all replacements.
   *
   * <p>Backward-compatible overload: the per-prompt answer counts are inferred from the tag shape
   * (compound tags consume {@code subTags().size()} answers, single tags one).
   */
  public static String buildPartialCommand(ParsedCommand parsed, List<String> answers) {
    Objects.requireNonNull(parsed);
    Objects.requireNonNull(answers);
    return buildPartialCommand(parsed, answers, inferSubmittedCounts(parsed, answers));
  }

  /**
   * Build a "partial command" string using the recorded per-prompt answer arities.
   *
   * <p>For each consumed prompt (in order of appearance in {@code submittedCounts}): a count of
   * zero removes the raw prompt tag without consuming an answer; a positive count consumes that
   * many flat answers and joins them (ignoring empty values) in place of the tag. Assembly stops at
   * the first prompt without a recorded count, truncating the remainder of the command — this is
   * how the current, still-unanswered prompt is excluded from the parseable input.
   *
   * <p>This keeps later prompts, {@code {input:N}} placeholders, and post-command indexes aligned
   * to real answers when a preceding dialog preset submitted zero or multiple answers.
   *
   * @param parsed the parsed command
   * @param answers flat answer values, in prompt order (only real answers occupy indexes)
   * @param submittedCounts one non-negative entry per consumed prompt, in consumption order
   * @return the partial command with a trailing space
   * @throws IllegalArgumentException if a submitted count is negative or consumes more answers than
   *     are available
   */
  public static String buildPartialCommand(
      ParsedCommand parsed, List<String> answers, List<Integer> submittedCounts) {
    Objects.requireNonNull(parsed);
    Objects.requireNonNull(answers);
    Objects.requireNonNull(submittedCounts);
    for (var count : submittedCounts) {
      if (count == null || count < 0) {
        throw new IllegalArgumentException(
            "submitted answer counts must be non-negative: " + count);
      }
    }

    var rawTemplate = parsed.rawTemplateCommand();
    var spans = parsed.templateSpans();
    if (spans.isEmpty() && (!parsed.promptTags.isEmpty() || !parsed.postCmds.isEmpty())) {
      return buildLegacyPartialCommand(parsed, answers, submittedCounts);
    }

    var command = new StringBuilder(rawTemplate.length());
    var cursor = 0;
    var answerIndex = 0;
    var countIndex = 0;
    var stoppedAtUnanswered = false;
    for (var span : spans) {
      command.append(unescape(rawTemplate.substring(cursor, span.start()), parsed.parserConfig()));
      if (span.pcm() || span.gate()) {
        cursor = span.end();
        continue;
      }

      if (countIndex >= submittedCounts.size()) {
        // The current prompt and everything after it are not part of the command Brigadier should
        // parse. The literal prefix has already been appended.
        stoppedAtUnanswered = true;
        break;
      }

      var count = submittedCounts.get(countIndex++);
      if (count > 0) {
        if (answerIndex + count > answers.size()) {
          throw new IllegalArgumentException(
              "Not enough answers for prompt at span "
                  + span.start()
                  + ".."
                  + span.end()
                  + ": count "
                  + count
                  + " but only "
                  + (answers.size() - answerIndex)
                  + " answer(s) remain");
        }
        var parts = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
          parts.add(formatCommandToken(answers.get(answerIndex++)));
        }
        command.append(String.join(" ", parts));
      }
      // count == 0: drop the raw tag without consuming an answer.
      cursor = span.end();
    }

    // If a prompt was unanswered, the loop intentionally stopped before advancing the
    // cursor. Otherwise all ordinary literals after the final parsed span are retained.
    if (!stoppedAtUnanswered) {
      command.append(unescape(rawTemplate.substring(cursor), parsed.parserConfig()));
    }

    var trimmed = command.toString().trim();
    return trimmed.endsWith(" ") ? trimmed : trimmed + " ";
  }

  /**
   * Arity-aware fallback for the old four-argument constructor (no source spans). Mirrors the
   * span-based assembly: consume recorded counts per prompt, drop zero-count tags, and stop at the
   * first prompt without a recorded count.
   */
  private static String buildLegacyPartialCommand(
      ParsedCommand parsed, List<String> answers, List<Integer> submittedCounts) {
    var template = parsed.templateCommand;
    var answerIndex = 0;
    var countIndex = 0;
    var cursor = 0;
    var stoppedEarly = false;
    var command = new StringBuilder(template.length());
    for (var tag : parsed.promptTags) {
      var index = template.indexOf(tag.rawTag(), cursor);
      if (index < 0) {
        stoppedEarly = true;
        break;
      }
      command.append(unescape(template.substring(cursor, index), parsed.parserConfig()));
      if (countIndex >= submittedCounts.size()) {
        stoppedEarly = true;
        break;
      }
      var count = submittedCounts.get(countIndex++);
      if (count > 0) {
        if (answerIndex + count > answers.size()) {
          throw new IllegalArgumentException(
              "Not enough answers for prompt tag '"
                  + tag.rawTag()
                  + "': count "
                  + count
                  + " but only "
                  + (answers.size() - answerIndex)
                  + " answer(s) remain");
        }
        var parts = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
          parts.add(formatCommandToken(answers.get(answerIndex++)));
        }
        command.append(String.join(" ", parts));
      }
      cursor = index + tag.rawTag().length();
    }
    if (!stoppedEarly) {
      command.append(unescape(template.substring(cursor), parsed.parserConfig()));
    }
    // Do not use a wildcard PCM expression here: only spans from the parser are authoritative.
    var trimmed = command.toString().trim();
    return trimmed.endsWith(" ") ? trimmed : trimmed + " ";
  }

  /**
   * Infers the per-prompt submitted-answer counts from the tag shapes, reproducing the legacy
   * two-argument assembly: single tags consume one answer each and stop when answers run out;
   * compound tags consume up to {@code subTags().size()} answers (fewer when the flat answer list
   * is exhausted, which the empty-value join makes equivalent to the historical fill-with-empty
   * behavior).
   */
  private static List<Integer> inferSubmittedCounts(ParsedCommand parsed, List<String> answers) {
    var counts = new ArrayList<Integer>();
    var answerIndex = 0;
    for (var tag : parsed.promptTags) {
      if (tag.isCompound()) {
        int count = Math.min(tag.subTags().size(), Math.max(0, answers.size() - answerIndex));
        counts.add(count);
        answerIndex += count;
      } else {
        if (answerIndex >= answers.size()) break;
        counts.add(1);
        answerIndex++;
      }
    }
    return counts;
  }

  /**
   * Formats a player answer as a single double-quoted command token with quotes and backslashes
   * escaped.
   *
   * @param answer the answer string to format
   * @return the formatted token wrapped in double quotes, or empty string if answer is null
   */
  public static String formatCommandToken(String answer) {
    if (answer == null) {
      return "";
    }
    var sb = new StringBuilder(answer.length() + 2);
    sb.append('"');
    for (int i = 0; i < answer.length(); i++) {
      char c = answer.charAt(i);
      if (c == '\\' || c == '"') {
        sb.append('\\');
      }
      sb.append(c);
    }
    sb.append('"');
    return sb.toString();
  }

  private static String unescape(String input, ParserConfig config) {
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

  private static void validateSpans(
      String rawTemplate, List<TemplateSpan> spans, int promptCount, int pcmCount, int gateCount) {
    var previousEnd = 0;
    for (var span : spans) {
      if (span.start() < previousEnd || span.end() > rawTemplate.length()) {
        throw new IllegalArgumentException(
            "Parsed template spans must be ordered and non-overlapping");
      }
      if (!rawTemplate.regionMatches(span.start(), span.rawText(), 0, span.rawText().length())) {
        throw new IllegalArgumentException("Parsed template span does not match the raw template");
      }
      int count;
      if (span.gate()) {
        count = gateCount;
      } else if (span.pcm()) {
        count = pcmCount;
      } else {
        count = promptCount;
      }
      if (span.parsedIndex() >= count) {
        throw new IllegalArgumentException("Parsed template span has an invalid parsed index");
      }
      previousEnd = span.end();
    }
  }
}
