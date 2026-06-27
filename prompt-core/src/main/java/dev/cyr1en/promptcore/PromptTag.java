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
 *   <li>{@code preset = false} — not a JSON preset reference
 * </ul>
 *
 * <h2>Preset tags</h2>
 *
 * <p>A tag of the form {@code <@id>} is a <b>preset reference</b>: the id is the key into the
 * plugin's {@code PresetRegistry}. For preset tags, {@code displayText} holds the id and {@link
 * #isPreset()} returns {@code true}. {@code key} is the empty string because the screen type comes
 * from the JSON definition, not the inline tag.
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
 * @param displayText the text content after stripping flags; empty for compound tags. For preset
 *     tags this holds the preset id
 * @param sanitize whether to strip color codes and symbols from the answer (block-level)
 * @param validatorAlias the input validator alias (nullable, block-level)
 * @param type the expected answer type constraint (block-level)
 * @param subTags individual input rows for compound tags; empty list for single-row tags
 * @param preset whether this tag is a JSON preset reference (e.g. {@code <@my_prompt>})
 * @param title optional title-wrapper configuration extracted from the {@code -t} flag; {@code
 *     null} when no title wrapper is requested
 */
public record PromptTag(
    String rawTag,
    String key,
    String filter,
    String displayText,
    boolean sanitize,
    String validatorAlias,
    AnswerType type,
    List<PromptTag> subTags,
    boolean preset) {

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
   * validator, no type constraint, no title wrapper, and {@code preset = false}.
   */
  public PromptTag(String rawTag, String key, String filter, String displayText) {
    this(rawTag, key, filter, displayText, true, null, AnswerType.NONE, List.of(), false, null);
  }

  /**
   * Convenience shortcut with sanitize and validator but no type constraint, no sub-tags, no title
   * wrapper, and {@code preset = false}.
   */
  public PromptTag(
      String rawTag,
      String key,
      String filter,
      String displayText,
      boolean sanitize,
      String validatorAlias) {
    this(
        rawTag,
        key,
        filter,
        displayText,
        sanitize,
        validatorAlias,
        AnswerType.NONE,
        List.of(),
        false,
        null);
  }

  /**
   * Convenience shortcut carrying all block-level fields but no title wrapper. Equivalent to the
   * canonical constructor with {@code title = null}. Preserves backward compatibility for callers
   * that construct a {@code PromptTag} before the title-wrapper feature was introduced.
   */
  public PromptTag(
      String rawTag,
      String key,
      String filter,
      String displayText,
      boolean sanitize,
      String validatorAlias,
      AnswerType type,
      List<PromptTag> subTags,
      boolean preset) {
    this(rawTag, key, filter, displayText, sanitize, validatorAlias, type, subTags, preset, null);
  }

  /**
   * Whether this tag carries one or more {@code &&}-delimited sub-tags. Compound tags render as one
   * dialog with multiple input rows.
   */
  public boolean isCompound() {
    return !subTags.isEmpty();
  }

  /**
   * Whether this tag is a JSON preset reference of the form {@code <@id>}. Preset tags do not carry
   * inline display text or screen-type routing; their content comes from the {@code
   * PresetRegistry}.
   */
  public boolean isPreset() {
    return preset;
  }
}
