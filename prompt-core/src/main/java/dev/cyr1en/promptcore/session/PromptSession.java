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
      List<PromptTag> remaining,
      List<PostCommandMeta> pcmQueue,
      SessionState state,
      CancelReason cancelReason) {
    this.userId = userId;
    this.parsedCommand = parsedCommand;
    this.answers = answers;
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
        remaining,
        List.copyOf(parsedCommand.postCmds()),
        state,
        null);
  }

  // --- Accessors ---

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

  /** 0-based index of the current prompt being answered. */
  public int currentIndex() {
    return answers.size();
  }

  // --- Transitions ---

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
    var newAnswers = new ArrayList<>(answers);
    newAnswers.add(processedAnswer);
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
        Collections.unmodifiableList(newRemaining),
        pcmQueue,
        newState,
        null);
  }

  /**
   * Submit a batch of answers to the current prompt. Used by compound dialog screens that collect N
   * answers from a single window — one per sub-tag.
   *
   * <p>The argument size MUST match the current prompt's expected answer count. Each answer is
   * sanitized using the <i>block-level</i> sanitize flag of the current prompt.
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
    if (answers.size() != expected) {
      throw new IllegalStateException(
          "Answer count mismatch for prompt at index "
              + currentIndex()
              + ": expected "
              + expected
              + " ("
              + (current.isCompound() ? "compound" : "single")
              + "), got "
              + answers.size());
    }

    var newAnswers = new ArrayList<>(answers);
    var processed = new ArrayList<String>(newAnswers.size());
    for (var raw : newAnswers) {
      processed.add(current.sanitize() ? sanitize(raw) : raw);
    }
    var newRemaining = new ArrayList<>(remaining);
    newRemaining.remove(0);

    var newState = newRemaining.isEmpty() ? SessionState.COMPLETED : SessionState.AWAITING_INPUT;
    LOG.fine(
        "Compound answers submitted for "
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
        Collections.unmodifiableList(processed),
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
        userId, parsedCommand, answers, remaining, pcmQueue, SessionState.CANCELLED, reason);
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

    var command = ParsedCommand.buildPartialCommand(parsedCommand, answers).trim();

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
            + " onComplete="
            + onComplete.size()
            + " onCancel="
            + onCancel.size());
    return new SessionResult(command, onComplete, onCancel);
  }

  // --- Internal ---

  private List<PostCommandMeta> resolvePCMReferences(List<PostCommandMeta> pcms) {
    return pcms.stream()
        .map(
            pcm -> {
              var resolved = pcm.command();
              for (int i = 0; i < answers.size(); i++) {
                resolved = resolved.replace("{" + i + "}", answers.get(i));
              }
              // Warn about unresolved references
              var unresolved = Pattern.compile("\\{\\d+}").matcher(resolved);
              if (unresolved.find()) {
                LOG.warning(
                    "Unresolved PCM reference "
                        + unresolved.group()
                        + " in command: "
                        + pcm.command());
                resolved = unresolved.replaceAll("");
              }
              resolved = resolved.replaceAll("\\s+", " ").trim();
              return new PostCommandMeta(
                  resolved,
                  pcm.answerIndices(),
                  pcm.delayTicks(),
                  pcm.onCancel(),
                  pcm.dispatchTarget());
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
    // Strip Minecraft legacy color codes (§, &)
    var noColor = input.replaceAll("(?i)[§&][0-9a-fklmnor]", "");
    // Strip decorative symbols
    return COLOR_SYMBOLS.matcher(noColor).replaceAll("").trim();
  }

  // --- Equality ---

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PromptSession that = (PromptSession) o;
    return Objects.equals(userId, that.userId)
        && Objects.equals(parsedCommand, that.parsedCommand)
        && Objects.equals(answers, that.answers)
        && Objects.equals(remaining, that.remaining)
        && Objects.equals(pcmQueue, that.pcmQueue)
        && state == that.state
        && cancelReason == that.cancelReason;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, parsedCommand, answers, remaining, pcmQueue, state, cancelReason);
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
        + ", remaining="
        + remainingCount()
        + '}';
  }
}
