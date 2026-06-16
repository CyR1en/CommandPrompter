package dev.cyr1en.promptcore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The result of parsing a raw command string.
 *
 * <p>Contains the original command template alongside the extracted prompt tags and post-command
 * metas. The {@link #templateCommand} preserves the original string so that prompt tag bodies can
 * be replaced with answers at assembly time.
 *
 * @param templateCommand the original command string (unchanged)
 * @param promptTags ordered list of prompt tags extracted from the command
 * @param postCmds list of post-command metas extracted from the command
 * @param parserConfig the config used to parse this command
 */
public record ParsedCommand(
    String templateCommand,
    List<PromptTag> promptTags,
    List<PostCommandMeta> postCmds,
    ParserConfig parserConfig) {

  /** Compact constructor that validates non-null components. */
  public ParsedCommand {
    Objects.requireNonNull(templateCommand);
    Objects.requireNonNull(promptTags);
    Objects.requireNonNull(postCmds);
    Objects.requireNonNull(parserConfig);
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
   * <p>The first {@code answers.size()} prompt tags are replaced by their answers. For the first
   * <b>unanswered single tag</b> (and any tokens after it in the template), the command is
   * <b>truncated at the tag's position</b> so the cursor sits at the empty argument slot. This is
   * what Brigadier needs to produce completions for the current argument only — tokens after the
   * current tag have not been "typed" yet from the user's perspective and would otherwise push the
   * cursor to a position with no completions.
   *
   * <p>Compound tags (e.g. {@code <d:choice:set && d:num[0,24]:Value>}) keep the legacy replacement
   * behavior: the whole compound is replaced with sub-answers joined by spaces, with empty
   * sub-answers at the end producing a trailing space. {@code d:tab} is rejected in compound tags
   * by the parser, so this branch is unreachable for tab completion and the legacy behavior is
   * preserved.
   *
   * <p>When all tags are answered (e.g. {@link
   * dev.cyr1en.promptcore.session.PromptSession#finish}), the loop processes every tag and the
   * truncation branch is never reached — the result is the fully-assembled command.
   *
   * <p>Unanswered tags are removed (not left as raw markup) so Brigadier does not see {@code
   * <d:...>} tokens it cannot parse. Post-command metas ({@code <! ...>} / {@code <!! ...>}) are
   * stripped from the result — they are not part of the user's typed command path.
   *
   * <p>The result is trimmed and suffixed with a single trailing space so the cursor sits at the
   * start of the next argument (the completion point).
   */
  public static String buildPartialCommand(ParsedCommand parsed, List<String> answers) {
    Objects.requireNonNull(parsed);
    Objects.requireNonNull(answers);
    var template = parsed.templateCommand();
    var answerIdx = 0;
    var command = template;
    for (var tag : parsed.promptTags()) {
      if (answerIdx >= answers.size() && !tag.isCompound()) {
        int idx = command.indexOf(tag.rawTag());
        if (idx >= 0) {
          command = command.substring(0, idx);
        }
        break;
      }
      if (tag.isCompound()) {
        var parts = new ArrayList<String>(tag.subTags().size());
        for (int i = 0; i < tag.subTags().size(); i++) {
          parts.add(answerIdx < answers.size() ? answers.get(answerIdx) : "");
          answerIdx++;
        }
        var joined = parts.stream().filter(p -> !p.isEmpty()).collect(Collectors.joining(" "));
        command = command.replace(tag.rawTag(), joined);
      } else {
        String replacement = answerIdx < answers.size() ? answers.get(answerIdx) : "";
        command = command.replace(tag.rawTag(), replacement);
        answerIdx++;
      }
    }
    var config = parsed.parserConfig();
    for (var pcm : parsed.postCmds()) {
      command =
          command.replaceAll(
              Pattern.quote(config.opening()) + "!.*?" + Pattern.quote(config.closing()), "");
    }
    var trimmed = command.trim();
    return trimmed.endsWith(" ") ? trimmed : trimmed + " ";
  }
}
