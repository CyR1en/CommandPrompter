package dev.cyr1en.promptcore.session;

import dev.cyr1en.promptcore.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Immutable session state machine for prompt completion.
 *
 * <p>Each transition ({@link #submitAnswer(String)}, {@link #cancel(CancelReason)}) returns a new
 * {@link PromptSession} instance. The original is never modified.
 */
public final class PromptSession {

  private static final Logger LOG = Logger.getLogger(PromptSession.class.getName());
  private static final Pattern COLOR_SYMBOLS = Pattern.compile("[{}\\[\\]<>()$§&\\u00A7]+");

  private final String userId;
  private final ParsedCommand parsedCommand;
  private final List<String> answers;
  private final List<Integer> submittedAnswerCounts;
  private final List<PromptTag> remaining;
  private final List<PostCommandMeta> pcmQueue;
  private final SessionState state;
  private final CancelReason cancelReason;

  /** The lifecycle state of a {@link PromptSession}. */
  public enum SessionState {
    /** Session is waiting for the user to answer the current prompt. */
    AWAITING_INPUT,
    /** All prompts have been answered — ready for {@link #finish()}. */
    COMPLETED,
    /** Session was cancelled before completing all prompts. */
    CANCELLED
  }

  private PromptSession(
      String userId,
      ParsedCommand parsedCommand,
      List<String> answers,
      List<Integer> submittedAnswerCounts,
      List<PromptTag> remaining,
      List<PostCommandMeta> pcmQueue,
      SessionState state,
      CancelReason cancelReason) {
    this.userId = userId;
    this.parsedCommand = parsedCommand;
    this.answers = answers;
    this.submittedAnswerCounts = submittedAnswerCounts;
    this.remaining = remaining;
    this.pcmQueue = pcmQueue;
    this.state = state;
    this.cancelReason = cancelReason;
  }

  /**
   * Create a new session for a given user and parsed command.
   *
   * <p>If the command contains no prompt tags, the session is immediately {@link
   * SessionState#COMPLETED}.
   *
   * @param userId a platform-agnostic user identifier
   * @param parsedCommand the parsed command (from {@link
   *     dev.cyr1en.promptcore.parser.CommandLineParser})
   * @return a new session in {@link SessionState#AWAITING_INPUT} (or COMPLETED if no prompts)
   */
  public static PromptSession start(String userId, ParsedCommand parsedCommand) {
    var remaining = new ArrayList<>(parsedCommand.promptTags());
    var state = remaining.isEmpty() ? SessionState.COMPLETED : SessionState.AWAITING_INPUT;
    LOG.fine(
        "Session started for " + userId + " with " + remaining.size() + " prompts, state=" + state);
    return new PromptSession(
        userId,
        parsedCommand,
        List.of(),
        List.of(),
        remaining,
        List.copyOf(parsedCommand.postCmds()),
        state,
        null);
  }

  /** The platform-agnostic user identifier this session belongs to. */
  public String userId() {
    return userId;
  }

  /** The parsed command that started this session. */
  public ParsedCommand parsedCommand() {
    return parsedCommand;
  }

  /** Unmodifiable view of the post-command queue snapshot held by this session. */
  public List<PostCommandMeta> pcmQueue() {
    return Collections.unmodifiableList(pcmQueue);
  }

  /** Unmodifiable list of answers collected so far, in prompt order. */
  public List<String> answers() {
    return Collections.unmodifiableList(answers);
  }

  /**
   * Unmodifiable list of submitted-answer counts, one per consumed prompt, in consumption order.
   *
   * <p>Each entry records how many real answers were submitted for that prompt (0 for a zero-answer
   * dialog preset, {@code N > 0} for a compound/preset dialog with N inputs, 1 for a single
   * prompt). Only real answers occupy {@link #answers()} indexes; zero-arity prompts contribute
   * nothing.
   */
  public List<Integer> submittedAnswerCounts() {
    return Collections.unmodifiableList(submittedAnswerCounts);
  }

  /** The current lifecycle state of this session. */
  public SessionState state() {
    return state;
  }

  /** The cancel reason, if the session was cancelled. */
  public Optional<CancelReason> cancelReason() {
    return Optional.ofNullable(cancelReason);
  }

  /** Whether all prompts have been answered ({@link SessionState#COMPLETED}). */
  public boolean isComplete() {
    return state == SessionState.COMPLETED;
  }

  /** Whether the session was cancelled ({@link SessionState#CANCELLED}). */
  public boolean isCancelled() {
    return state == SessionState.CANCELLED;
  }

  /** Whether the session is awaiting input ({@link SessionState#AWAITING_INPUT}). */
  public boolean isActive() {
    return state == SessionState.AWAITING_INPUT;
  }

  /** Returns the current prompt waiting for an answer, or empty if no prompts remain. */
  public Optional<PromptTag> currentPrompt() {
    if (remaining.isEmpty()) return Optional.empty();
    return Optional.of(remaining.get(0));
  }

  /** Number of remaining prompts to answer. */
  public int remainingCount() {
    return remaining.size();
  }

  /**
   * 0-based ordinal of the prompt currently being answered (number of consumed prompts).
   *
   * <p>This is the consumed-prompt ordinal, not the flat answer count: a zero-answer dialog preset
   * advances the session without adding to {@link #answers()}.
   */
  public int currentIndex() {
    return parsedCommand.promptTags().size() - remaining.size();
  }

  /**
   * Rebuild the partial command from the parsed template, substituting the recorded per-prompt
   * answer arities so zero- and multi-answer dialog presets never shift later prompts or the {@code
   * {input:N}} / post-command answer indexes. See {@link
   * ParsedCommand#buildPartialCommand(ParsedCommand, List, List)}.
   */
  public String buildPartialCommand() {
    return ParsedCommand.buildPartialCommand(parsedCommand, answers, submittedAnswerCounts);
  }

  /**
   * Submit an answer to the current prompt.
   *
   * @param answer the raw answer string
   * @return a new session in the next state
   * @throws IllegalStateException if the session is not awaiting input
   */
  public PromptSession submitAnswer(String answer) {
    if (state != SessionState.AWAITING_INPUT) {
      throw new IllegalStateException("Cannot submit answer in state: " + state);
    }
    Objects.requireNonNull(answer);

    var current = remaining.get(0);
    var processedAnswer = current.sanitize() ? sanitize(answer) : answer;
    var newAnswers = new ArrayList<>(this.answers);
    newAnswers.add(processedAnswer);
    var newCounts = new ArrayList<>(submittedAnswerCounts);
    newCounts.add(1);
    var newRemaining = new ArrayList<>(remaining);
    newRemaining.remove(0);

    var newState = newRemaining.isEmpty() ? SessionState.COMPLETED : SessionState.AWAITING_INPUT;
    LOG.fine(
        "Answer submitted for "
            + userId
            + ": state="
            + newState
            + " remaining="
            + newRemaining.size()
            + " sanitized="
            + current.sanitize());
    return new PromptSession(
        userId,
        parsedCommand,
        Collections.unmodifiableList(newAnswers),
        Collections.unmodifiableList(newCounts),
        Collections.unmodifiableList(newRemaining),
        pcmQueue,
        newState,
        null);
  }

  /**
   * Submit a batch of answers to the current prompt. Used by compound dialog screens that collect N
   * answers from a single window — one per answer-bearing sub-tag.
   *
   * <p>The argument size MUST match the current prompt's expected answer count. Each answer is
   * sanitized using the <i>block-level</i> sanitize flag of the current prompt. The expected count
   * is inferred from the current prompt's tag shape: {@code subTags().size()} for compound tags, 1
   * otherwise.
   */
  public PromptSession submitAnswers(List<String> answers) {
    if (state != SessionState.AWAITING_INPUT) {
      throw new IllegalStateException("Cannot submit answers in state: " + state);
    }
    Objects.requireNonNull(answers);
    if (answers.isEmpty()) {
      throw new IllegalStateException("Compound submit requires at least one answer");
    }
    var current = remaining.get(0);
    var expected = current.isCompound() ? current.subTags().size() : 1;
    return submitAnswers(answers, expected);
  }

  /**
   * Submit a batch of answers to the current prompt with an explicit expected answer count.
   *
   * <p>This is the arity-aware entry point for dialog flows whose effective answer count is not
   * derivable from the parsed tag shape — e.g. JSON dialog presets (whose parsed {@link PromptTag}
   * is non-compound but which may submit 0, 1, or N answers) and inline dialogs with layout rows
   * ({@code title}/{@code body}) that never produce answers.
   *
   * <p>{@code expectedCount} must be non-negative and {@code answers.size()} must equal it; a zero
   * count is valid (a zero-input dialog preset consumes the prompt and adds no flat answers). Each
   * answer is sanitized exactly once using the current prompt's existing sanitize policy. Exactly
   * one prompt is consumed and its arity is recorded for later command assembly.
   *
   * @param answers the flat answer values, in row order; must not contain null elements
   * @param expectedCount the number of answers this prompt submits (may be 0)
   * @return a new session in the next state
   * @throws IllegalStateException if the session is not awaiting input
   * @throws IllegalArgumentException if {@code expectedCount} is negative or {@code answers.size()}
   *     does not equal {@code expectedCount}
   * @throws NullPointerException if {@code answers} or any element is null
   */
  public PromptSession submitAnswers(List<String> answers, int expectedCount) {
    if (state != SessionState.AWAITING_INPUT) {
      throw new IllegalStateException("Cannot submit answers in state: " + state);
    }
    Objects.requireNonNull(answers);
    if (expectedCount < 0) {
      throw new IllegalArgumentException(
          "expectedCount must be >= 0 for prompt at index "
              + currentIndex()
              + ", got "
              + expectedCount);
    }
    if (answers.size() != expectedCount) {
      throw new IllegalArgumentException(
          "Answer count mismatch for prompt at index "
              + currentIndex()
              + ": expected "
              + expectedCount
              + ", got "
              + answers.size());
    }

    var current = remaining.get(0);
    var newAnswers = new ArrayList<>(this.answers);
    var processed = new ArrayList<String>(answers.size());
    for (var raw : answers) {
      Objects.requireNonNull(raw, "answers must not contain null elements");
      processed.add(current.sanitize() ? sanitize(raw) : raw);
    }
    newAnswers.addAll(processed);
    var newCounts = new ArrayList<>(submittedAnswerCounts);
    newCounts.add(expectedCount);
    var newRemaining = new ArrayList<>(remaining);
    newRemaining.remove(0);

    var newState = newRemaining.isEmpty() ? SessionState.COMPLETED : SessionState.AWAITING_INPUT;
    LOG.fine(
        "Batch answers submitted for "
            + userId
            + ": state="
            + newState
            + " remaining="
            + newRemaining.size()
            + " answers="
            + answers.size()
            + " sanitized="
            + current.sanitize());
    return new PromptSession(
        userId,
        parsedCommand,
        Collections.unmodifiableList(newAnswers),
        Collections.unmodifiableList(newCounts),
        Collections.unmodifiableList(newRemaining),
        pcmQueue,
        newState,
        null);
  }

  /**
   * Cancel the session.
   *
   * @param reason the cancellation reason
   * @return a new cancelled session
   * @throws IllegalStateException if the session is already terminated
   */
  public PromptSession cancel(CancelReason reason) {
    Objects.requireNonNull(reason);
    if (state == SessionState.COMPLETED || state == SessionState.CANCELLED) {
      throw new IllegalStateException("Cannot cancel session in state: " + state);
    }
    LOG.fine("Session cancelled for " + userId + ": reason=" + reason);
    return new PromptSession(
        userId,
        parsedCommand,
        answers,
        submittedAnswerCounts,
        remaining,
        pcmQueue,
        SessionState.CANCELLED,
        reason);
  }

  /**
   * Build the final result.
   *
   * @return the assembled session result
   * @throws IllegalStateException if the session is still active
   */
  public SessionResult finish() {
    if (state == SessionState.AWAITING_INPUT) {
      throw new IllegalStateException("Cannot finish session with unanswered prompts");
    }

    var command =
        ParsedCommand.buildPartialCommand(parsedCommand, answers, submittedAnswerCounts).trim();

    List<PostCommandMeta> onComplete;
    List<PostCommandMeta> onCancel;

    if (state == SessionState.CANCELLED) {
      onComplete = List.of();
      onCancel = resolvePCMReferences(parsedCommand.onCancelPCMs());
    } else {
      onComplete = resolvePCMReferences(parsedCommand.onCompletePCMs());
      onCancel = List.of();
    }

    LOG.fine(
        "Session finished for "
            + userId
            + ": assembled="
            + command
            + " answers="
            + answers.size()
            + " onComplete="
            + onComplete.size()
            + " onCancel="
            + onCancel.size());
    return new SessionResult(command, new java.util.ArrayList<>(answers), onComplete, onCancel);
  }

  private List<PostCommandMeta> resolvePCMReferences(List<PostCommandMeta> pcms) {
    return pcms.stream()
        .map(
            pcm -> {
              // Match only against the original PCM template. appendReplacement prevents an answer
              // containing "{1}" from being scanned again and substituted by a later answer.
              var matcher = Pattern.compile("\\{(\\d+)}").matcher(pcm.command());
              var resolved = new StringBuffer();
              while (matcher.find()) {
                int index;
                try {
                  index = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                  index = -1;
                }
                String replacement = "";
                if (index >= 0 && index < answers.size()) {
                  replacement = answers.get(index);
                } else {
                  LOG.warning(
                      "Unresolved PCM reference "
                          + matcher.group()
                          + " in command: "
                          + pcm.command());
                }
                matcher.appendReplacement(
                    resolved, java.util.regex.Matcher.quoteReplacement(replacement));
              }
              matcher.appendTail(resolved);
              var resolvedCommand = resolved.toString();
              resolvedCommand = resolvedCommand.replaceAll("\\s+", " ").trim();
              return new PostCommandMeta(
                  resolvedCommand,
                  pcm.answerIndices(),
                  pcm.delayTicks(),
                  pcm.onCancel(),
                  pcm.dispatchTarget(),
                  pcm.preset());
            })
        .toList();
  }

  /**
   * Strip Minecraft legacy color codes and decorative symbols from an input string.
   *
   * <p>Removes {@code §} / {@code &} color codes ({@code §c}, {@code &a}, etc.) and common
   * decorative brackets/parentheses. This is a pure function with no platform dependencies.
   *
   * @param input the raw input string
   * @return the sanitized string, or null/empty if input was null/empty
   */
  static String sanitize(String input) {
    if (input == null || input.isEmpty()) return input;
    // Strip color codes and decorative symbols
    var noColor = input.replaceAll("(?i)[§&][0-9a-fklmnor]", "");
    return COLOR_SYMBOLS.matcher(noColor).replaceAll("").trim();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PromptSession that = (PromptSession) o;
    return Objects.equals(userId, that.userId)
        && Objects.equals(parsedCommand, that.parsedCommand)
        && Objects.equals(answers, that.answers)
        && Objects.equals(submittedAnswerCounts, that.submittedAnswerCounts)
        && Objects.equals(remaining, that.remaining)
        && Objects.equals(pcmQueue, that.pcmQueue)
        && state == that.state
        && cancelReason == that.cancelReason;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        userId,
        parsedCommand,
        answers,
        submittedAnswerCounts,
        remaining,
        pcmQueue,
        state,
        cancelReason);
  }

  @Override
  public String toString() {
    return "PromptSession{"
        + "userId='"
        + userId
        + '\''
        + ", state="
        + state
        + ", currentPrompt="
        + currentPrompt().map(PromptTag::key).orElse("none")
        + ", answers="
        + answers
        + ", counts="
        + submittedAnswerCounts
        + ", remaining="
        + remainingCount()
        + '}';
  }
}
