package dev.cyr1en.promptcore.plan;

import dev.cyr1en.promptcore.DispatchTarget;
import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Platform-neutral immutable specification for a post-dispatch action. */
public sealed interface PostActionSpec
    permits PostActionSpec.ImmediateCommand,
        PostActionSpec.PresetReference,
        PostActionSpec.Delayed {

  /** Maximum allowed length for a preset ID. */
  int MAX_PRESET_ID_LENGTH = 64;

  /** Maximum allowed delay in ticks (72000 ticks = 1 hour). */
  int MAX_DELAY_TICKS = 72000;

  /** Maximum number of unique referenced answer indices. */
  int MAX_ANSWER_INDICES = 16;

  /**
   * Canonicalizes raw answer indices to distinct values preserving first-seen order, validating
   * that no index is negative and that the unique index count does not exceed {@link
   * #MAX_ANSWER_INDICES}.
   *
   * @param rawIndices the raw array of answer indices
   * @return a distinct, defensive copy of the answer indices in first-seen order
   */
  static int[] canonicalizeAnswerIndices(int[] rawIndices) {
    Objects.requireNonNull(rawIndices, "answerIndices must not be null");
    int[] temp = new int[rawIndices.length];
    int count = 0;
    for (int idx : rawIndices) {
      if (idx < 0) {
        throw new IllegalArgumentException("answer index must not be negative: " + idx);
      }
      boolean exists = false;
      for (int i = 0; i < count; i++) {
        if (temp[i] == idx) {
          exists = true;
          break;
        }
      }
      if (!exists) {
        temp[count++] = idx;
      }
    }
    if (count > MAX_ANSWER_INDICES) {
      throw new IllegalArgumentException(
          "answerIndices count exceeds limit of " + MAX_ANSWER_INDICES + ": " + count);
    }
    return Arrays.copyOf(temp, count);
  }

  /**
   * Extracts distinct non-negative numeric answer indices from a compiled template's referenced
   * keys in first-seen order. Non-answer keys (such as named variables or placeholders) are
   * ignored.
   *
   * @param template the compiled template
   * @return array of unique answer indices referenced by the template
   */
  static int[] extractAnswerIndices(CompiledTemplate template) {
    Objects.requireNonNull(template, "commandTemplate must not be null");
    List<Integer> indices = new ArrayList<>();
    for (String key : template.referencedKeys()) {
      try {
        int idx = Integer.parseInt(key);
        if (idx >= 0 && !indices.contains(idx)) {
          indices.add(idx);
        }
      } catch (NumberFormatException ignored) {
        // Non-answer key (e.g. "player", "target") - do not treat as answer index
      }
    }
    int[] result = new int[indices.size()];
    for (int i = 0; i < indices.size(); i++) {
      result[i] = indices.get(i);
    }
    return result;
  }

  /**
   * When this action triggers.
   *
   * @return the action trigger
   */
  ActionTrigger trigger();

  /**
   * Returns the total number of action nodes represented by this specification, including any
   * wrapped or delegate specifications.
   *
   * @return total action node count
   */
  default int nodeCount() {
    return 1;
  }

  /**
   * Immediate command execution action with a pre-compiled template.
   *
   * @param commandTemplate the immutable pre-compiled command template
   * @param answerIndices prompt answer indices referenced by this command
   * @param trigger whether this runs on completion or cancellation
   * @param target dispatch target (player, console, passthrough)
   */
  record ImmediateCommand(
      CompiledTemplate commandTemplate,
      int[] answerIndices,
      ActionTrigger trigger,
      DispatchTarget target)
      implements PostActionSpec {

    public ImmediateCommand {
      Objects.requireNonNull(commandTemplate, "commandTemplate must not be null");
      Objects.requireNonNull(answerIndices, "answerIndices must not be null");
      Objects.requireNonNull(trigger, "trigger must not be null");
      Objects.requireNonNull(target, "target must not be null");

      answerIndices = canonicalizeAnswerIndices(answerIndices);
    }

    /**
     * Constructs an immediate command action with answer indices derived automatically from the
     * pre-compiled template's referenced keys.
     *
     * @param commandTemplate the immutable pre-compiled command template
     * @param trigger whether this runs on completion or cancellation
     * @param target dispatch target (player, console, passthrough)
     */
    public ImmediateCommand(
        CompiledTemplate commandTemplate, ActionTrigger trigger, DispatchTarget target) {
      this(commandTemplate, extractAnswerIndices(commandTemplate), trigger, target);
    }

    @Override
    public int[] answerIndices() {
      return answerIndices.clone();
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof ImmediateCommand that)) return false;
      return trigger == that.trigger
          && target == that.target
          && commandTemplate.equals(that.commandTemplate)
          && Arrays.equals(answerIndices, that.answerIndices);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(commandTemplate, trigger, target);
      return 31 * result + Arrays.hashCode(answerIndices);
    }

    @Override
    public String toString() {
      return "ImmediateCommand[commandTemplate="
          + commandTemplate
          + ", answerIndices="
          + Arrays.toString(answerIndices)
          + ", trigger="
          + trigger
          + ", target="
          + target
          + "]";
    }
  }

  /**
   * Reference to a trusted preset post-action.
   *
   * @param presetId identifier of the preset in the registry
   * @param answerIndices prompt answer indices passed to the preset
   * @param trigger whether this runs on completion or cancellation
   * @param target dispatch target
   */
  record PresetReference(
      String presetId, int[] answerIndices, ActionTrigger trigger, DispatchTarget target)
      implements PostActionSpec {

    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    public PresetReference {
      Objects.requireNonNull(presetId, "presetId must not be null");
      Objects.requireNonNull(answerIndices, "answerIndices must not be null");
      Objects.requireNonNull(trigger, "trigger must not be null");
      Objects.requireNonNull(target, "target must not be null");

      if (presetId.isBlank()) {
        throw new IllegalArgumentException("presetId must not be blank");
      }
      if (presetId.length() > MAX_PRESET_ID_LENGTH) {
        throw new IllegalArgumentException(
            "presetId length exceeds limit of " + MAX_PRESET_ID_LENGTH + ": " + presetId.length());
      }
      if (!VALID_ID_PATTERN.matcher(presetId).matches()) {
        throw new IllegalArgumentException("presetId contains invalid characters: " + presetId);
      }
      answerIndices = canonicalizeAnswerIndices(answerIndices);
    }

    @Override
    public int[] answerIndices() {
      return answerIndices.clone();
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof PresetReference that)) return false;
      return trigger == that.trigger
          && target == that.target
          && presetId.equals(that.presetId)
          && Arrays.equals(answerIndices, that.answerIndices);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(presetId, trigger, target);
      return 31 * result + Arrays.hashCode(answerIndices);
    }

    @Override
    public String toString() {
      return "PresetReference[presetId="
          + presetId
          + ", answerIndices="
          + Arrays.toString(answerIndices)
          + ", trigger="
          + trigger
          + ", target="
          + target
          + "]";
    }
  }

  /**
   * Wraps another post-action with a tick delay.
   *
   * @param delegate the inner post-action to execute after the delay
   * @param delayTicks the delay in server ticks (must be &gt;= 1)
   * @param trigger the action trigger (must match delegate trigger)
   */
  record Delayed(PostActionSpec delegate, int delayTicks, ActionTrigger trigger)
      implements PostActionSpec {

    public Delayed {
      Objects.requireNonNull(delegate, "delegate must not be null");
      Objects.requireNonNull(trigger, "trigger must not be null");
      if (delegate instanceof Delayed) {
        throw new IllegalArgumentException(
            "Cannot nest Delayed action inside another Delayed action");
      }
      if (trigger != delegate.trigger()) {
        throw new IllegalArgumentException(
            "Delayed trigger ("
                + trigger
                + ") must match delegate trigger ("
                + delegate.trigger()
                + ")");
      }
      if (delayTicks < 1 || delayTicks > MAX_DELAY_TICKS) {
        throw new IllegalArgumentException(
            "delayTicks must be between 1 and " + MAX_DELAY_TICKS + ", got " + delayTicks);
      }
    }

    public Delayed(PostActionSpec delegate, int delayTicks) {
      this(
          delegate,
          delayTicks,
          Objects.requireNonNull(delegate, "delegate must not be null").trigger());
    }

    @Override
    public int nodeCount() {
      return 1 + delegate.nodeCount();
    }
  }
}
