package dev.cyr1en.promptcore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
   * @param parsedIndex index in {@link #postCmds()} or {@link #promptTags()}, according to {@code
   *     pcm}
   */
  public record TemplateSpan(int start, int end, String rawText, boolean pcm, int parsedIndex) {
    public TemplateSpan {
      Objects.requireNonNull(rawText, "rawText");
      if (start < 0 || end < start || end - start != rawText.length()) {
        throw new IllegalArgumentException("Invalid parsed template span: " + start + ".." + end);
      }
      if (parsedIndex < 0) throw new IllegalArgumentException("parsedIndex must not be negative");
    }
  }

  /**
   * Backward-compatible constructor for callers that construct a parsed command directly. Commands
   * produced by {@link dev.cyr1en.promptcore.parser.CommandLineParser} use the raw-span
   * constructor.
   */
  public ParsedCommand(
      String templateCommand,
      List<PromptTag> promptTags,
      List<PostCommandMeta> postCmds,
      ParserConfig parserConfig) {
    this(templateCommand, promptTags, postCmds, parserConfig, templateCommand, List.of());
  }

  /** Compact constructor that defensively copies all live collections. */
  public ParsedCommand {
    Objects.requireNonNull(templateCommand);
    Objects.requireNonNull(promptTags);
    Objects.requireNonNull(postCmds);
    Objects.requireNonNull(parserConfig);
    Objects.requireNonNull(rawTemplateCommand);
    Objects.requireNonNull(templateSpans);
    promptTags = List.copyOf(promptTags);
    postCmds = List.copyOf(postCmds);
    templateSpans = List.copyOf(templateSpans);
    validateSpans(rawTemplateCommand, templateSpans, promptTags.size(), postCmds.size());
  }

  /** Number of prompt tags in this command. */
  public int promptCount() {
    return promptTags.size();
  }

  /** Number of post-command metas in this command. */
  public int pcmCount() {
    return postCmds.size();
  }

  /** Whether this command contains any prompt tags. */
  public boolean hasPrompts() {
    return !promptTags.isEmpty();
  }

  /** Defensive accessor for the parsed prompt list. */
  @Override
  public List<PromptTag> promptTags() {
    return List.copyOf(promptTags);
  }

  /** Defensive accessor for the parsed PCM list. */
  @Override
  public List<PostCommandMeta> postCmds() {
    return List.copyOf(postCmds);
  }

  /** Defensive accessor for source spans. */
  @Override
  public List<TemplateSpan> templateSpans() {
    return List.copyOf(templateSpans);
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
      command.append(rawTemplate, cursor, span.start());
      if (span.pcm()) {
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
          parts.add(answers.get(answerIndex++));
        }
        command.append(parts.stream().filter(p -> !p.isEmpty()).collect(Collectors.joining(" ")));
      }
      // count == 0: drop the raw tag without consuming an answer.
      cursor = span.end();
    }

    // If a prompt was unanswered, the loop intentionally stopped before advancing the
    // cursor. Otherwise all ordinary literals after the final parsed span are retained.
    if (!stoppedAtUnanswered) command.append(rawTemplate, cursor, rawTemplate.length());

    var unescaped = unescape(command.toString(), parsed.parserConfig());
    var trimmed = unescaped.trim();
    return trimmed.endsWith(" ") ? trimmed : trimmed + " ";
  }

  /**
   * Fallback for the old four-argument constructor. New parser output always carries spans; this
   * path keeps source compatibility for integrations that build ParsedCommand values themselves.
   */
  private static String buildLegacyPartialCommand(ParsedCommand parsed, List<String> answers) {
    return buildLegacyPartialCommand(parsed, answers, inferSubmittedCounts(parsed, answers));
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
      command.append(template, cursor, index);
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
          parts.add(answers.get(answerIndex++));
        }
        command.append(parts.stream().filter(p -> !p.isEmpty()).collect(Collectors.joining(" ")));
      }
      cursor = index + tag.rawTag().length();
    }
    if (cursor == 0 || !stoppedEarly) {
      command.append(template.substring(cursor));
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

  private static String unescape(String input, ParserConfig config) {
    if (input == null || input.isEmpty()) return input;
    char escape = config.escape().charAt(0);
    char opening = config.opening().charAt(0);
    char closing = config.closing().charAt(0);
    var result = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char current = input.charAt(i);
      if (current == escape && i + 1 < input.length()) {
        char next = input.charAt(i + 1);
        if (next == opening || next == closing) {
          result.append(next);
          i++;
          continue;
        }
      }
      result.append(current);
    }
    return result.toString();
  }

  private static void validateSpans(
      String rawTemplate, List<TemplateSpan> spans, int promptCount, int pcmCount) {
    var previousEnd = 0;
    for (var span : spans) {
      if (span.start() < previousEnd || span.end() > rawTemplate.length()) {
        throw new IllegalArgumentException(
            "Parsed template spans must be ordered and non-overlapping");
      }
      if (!rawTemplate.regionMatches(span.start(), span.rawText(), 0, span.rawText().length())) {
        throw new IllegalArgumentException("Parsed template span does not match the raw template");
      }
      int count = span.pcm() ? pcmCount : promptCount;
      if (span.parsedIndex() >= count) {
        throw new IllegalArgumentException("Parsed template span has an invalid parsed index");
      }
      previousEnd = span.end();
    }
  }
}
