package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.logic.condition.Condition;
import java.util.Map;

/** Execution options persisted with a prompt, independent of its presentation type. */
public record PromptBehavior(
    @SerializedName("validator") String validatorAlias,
    @SerializedName("answer_type") PromptTag.AnswerType answerType,
    Integer timeout,
    Map<String, String> flags,
    @SerializedName("break_if") String breakIf) {

  public PromptBehavior {
    answerType = answerType == null ? PromptTag.AnswerType.NONE : answerType;
    flags = flags == null ? Map.of() : Map.copyOf(flags);
    if (validatorAlias != null && validatorAlias.isBlank()) {
      throw new IllegalArgumentException("validator must not be blank");
    }
    if (timeout != null && (timeout < 1 || timeout > 3600)) {
      throw new IllegalArgumentException("timeout must be between 1 and 3600 seconds");
    }
    if (breakIf != null) Condition.compile(breakIf);
  }

  public static PromptBehavior fromTag(PromptTag tag) {
    if (tag.validatorAlias() == null
        && tag.type() == PromptTag.AnswerType.NONE
        && tag.timeout() == null
        && tag.flags().isEmpty()
        && tag.breakIf() == null) return null;
    return new PromptBehavior(
        tag.validatorAlias(),
        tag.type(),
        tag.timeout(),
        tag.flags(),
        tag.breakIf() == null ? null : tag.breakIf().source());
  }

  public PromptTag apply(PromptTag tag, boolean sanitize) {
    return new PromptTag(
        tag.rawTag(),
        tag.key(),
        tag.filter(),
        tag.displayText(),
        sanitize,
        validatorAlias,
        answerType,
        tag.subTags(),
        tag.preset(),
        tag.title(),
        timeout,
        flags,
        breakIf == null ? null : Condition.compile(breakIf));
  }
}
