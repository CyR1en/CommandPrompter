package dev.cyr1en.promptcore.logic.condition;

/**
 * Immutable options governing the compilation of condition expressions.
 *
 * @param allowPapiRefs whether PlaceholderAPI references (e.g. {@code %vault_eco_balance%}) are
 *     permitted
 */
public record ConditionCompileOptions(boolean allowPapiRefs) {

  private static final ConditionCompileOptions INLINE_DEFAULT = new ConditionCompileOptions(false);
  private static final ConditionCompileOptions PRESET_DEFAULT = new ConditionCompileOptions(true);

  /** Default options for untrusted/inline condition expressions (PAPI references disallowed). */
  public static ConditionCompileOptions defaultOptions() {
    return INLINE_DEFAULT;
  }

  /** Options for inline condition expressions (PAPI references disallowed). */
  public static ConditionCompileOptions forInline() {
    return INLINE_DEFAULT;
  }

  /** Options for trusted preset condition expressions (PAPI references permitted). */
  public static ConditionCompileOptions forPreset() {
    return PRESET_DEFAULT;
  }
}
