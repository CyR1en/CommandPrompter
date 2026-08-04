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
   */
  public static String buildPartialCommand(ParsedCommand parsed, List<String> answers) {
    Objects.requireNonNull(parsed);
    Objects.requireNonNull(answers);

    var rawTemplate = parsed.rawTemplateCommand();
    var spans = parsed.templateSpans();
    if (spans.isEmpty() && (!parsed.promptTags.isEmpty() || !parsed.postCmds.isEmpty())) {
      return buildLegacyPartialCommand(parsed, answers);
    }

    var command = new StringBuilder(rawTemplate.length());
    var cursor = 0;
    var answerIndex = 0;
    var stoppedAtUnanswered = false;
    for (var span : spans) {
      command.append(rawTemplate, cursor, span.start());
      if (span.pcm()) {
        cursor = span.end();
        continue;
      }

      var tag = parsed.promptTags.get(span.parsedIndex());
      if (answerIndex >= answers.size() && !tag.isCompound()) {
        // The current tag and everything after it are not part of the command Brigadier should
        // parse. The literal prefix has already been appended.
        stoppedAtUnanswered = true;
        break;
      }

      if (tag.isCompound()) {
        var parts = new ArrayList<String>(tag.subTags().size());
        for (int i = 0; i < tag.subTags().size(); i++) {
          parts.add(answerIndex < answers.size() ? answers.get(answerIndex) : "");
          answerIndex++;
        }
        command.append(parts.stream().filter(p -> !p.isEmpty()).collect(Collectors.joining(" ")));
      } else {
        command.append(answers.get(answerIndex++));
      }
      cursor = span.end();
    }

    // If a single prompt was unanswered, the loop intentionally stopped before advancing the
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
    var template = parsed.templateCommand;
    var answerIndex = 0;
    var cursor = 0;
    var command = new StringBuilder(template.length());
    for (var tag : parsed.promptTags) {
      var index = template.indexOf(tag.rawTag(), cursor);
      if (index < 0) break;
      command.append(template, cursor, index);
      if (answerIndex >= answers.size() && !tag.isCompound()) break;
      if (tag.isCompound()) {
        var parts = new ArrayList<String>(tag.subTags().size());
        for (int i = 0; i < tag.subTags().size(); i++) {
          parts.add(answerIndex < answers.size() ? answers.get(answerIndex) : "");
          answerIndex++;
        }
        command.append(parts.stream().filter(p -> !p.isEmpty()).collect(Collectors.joining(" ")));
      } else {
        command.append(answers.get(answerIndex++));
      }
      cursor = index + tag.rawTag().length();
    }
    if (cursor == 0 || answerIndex >= parsed.promptTags.size()) {
      command.append(template.substring(cursor));
    }
    // Do not use a wildcard PCM expression here: only spans from the parser are authoritative.
    var trimmed = command.toString().trim();
    return trimmed.endsWith(" ") ? trimmed : trimmed + " ";
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
