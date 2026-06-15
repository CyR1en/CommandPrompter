package dev.cyr1en.promptcore;

import java.util.List;
import java.util.Objects;

/**
 * A single parsed prompt tag extracted from a command string.
 *
 * <p>A tag like {@code <a:Why? -ds -iv:req>} produces a PromptTag with:
 *
 * <ul>
 *   <li>{@code rawTag = "<a:Why? -ds -iv:req>"} — the original tag text for replacement
 *   <li>{@code key = "a"} — the prompt type key (empty for chat, "a" for anvil, etc.)
 *   <li>{@code displayText = "Why?"} — the text shown to the user
 *   <li>{@code sanitize = false} — answer sanitization disabled
 *   <li>{@code validatorAlias = "req"} — input validator to apply
 *   <li>{@code type = NONE} — no type constraint on the answer
 *   <li>{@code subTags = []} — empty for a single-row tag
 * </ul>
 *
 * <h2>Compound tags</h2>
 *
 * <p>Dialog tags support a compound form: {@code <d:choice[set,add]:Sub && d:num[0,24]:Value>}
 * produces a {@code PromptTag} with two {@code subTags} — one per {@code &&}-separated row. The
 * outer tag's {@code key}, {@code filter}, {@code displayText}, {@code sanitize}, {@code
 * validatorAlias}, and {@code type} are the <b>block-level</b> values; the individual input rows
 * live in {@code subTags()}. Block-level flags are extracted from whichever sub-segment of the
 * original tag contains them, but typically the user writes them at the end of the compound block.
 * The block-level {@code filter} and {@code displayText} are empty for compound tags — they have no
 * row-level meaning.
 *
 * @param rawTag the complete original tag including delimiters (e.g. {@code <a:text>})
 * @param key the prompt type key (empty string for chat prompts); for compound tags, the first
 *     sub-tag's key
 * @param filter the filter slot (second colon segment); null/empty for compound tags
 * @param displayText the text content after stripping flags; empty for compound tags
 * @param sanitize whether to strip color codes and symbols from the answer (block-level)
 * @param validatorAlias the input validator alias (nullable, block-level)
 * @param type the expected answer type constraint (block-level)
 * @param subTags individual input rows for compound tags; empty list for single-row tags
 */
public record PromptTag(
    String rawTag,
    String key,
    String filter,
    String displayText,
    boolean sanitize,
    String validatorAlias,
    AnswerType type,
    List<PromptTag> subTags) {

  /** Describes an optional type constraint on the answer value. */
  public enum AnswerType {
    /** No type constraint — accept any string. */
    NONE,
    /** Answer must parse as an integer. */
    INTEGER,
    /** Answer must be a non-empty string. */
    STRING
  }

  /** Compact constructor that validates non-null components. */
  public PromptTag {
    Objects.requireNonNull(rawTag);
    Objects.requireNonNull(key);
    Objects.requireNonNull(displayText);
    Objects.requireNonNull(type);
    subTags = subTags == null ? List.of() : List.copyOf(subTags);
  }

  /**
   * Convenience shortcut for a single-row tag (no sub-tags) with default sanitize=true, no
   * validator, no type constraint.
   */
  public PromptTag(String rawTag, String key, String filter, String displayText) {
    this(rawTag, key, filter, displayText, true, null, AnswerType.NONE, List.of());
  }

  /** Convenience shortcut with sanitize and validator but no type constraint and no sub-tags. */
  public PromptTag(
      String rawTag,
      String key,
      String filter,
      String displayText,
      boolean sanitize,
      String validatorAlias) {
    this(rawTag, key, filter, displayText, sanitize, validatorAlias, AnswerType.NONE, List.of());
  }

  /**
   * Whether this tag carries one or more {@code &&}-delimited sub-tags. Compound tags render as one
   * dialog with multiple input rows.
   */
  public boolean isCompound() {
    return !subTags.isEmpty();
  }
}
