package dev.cyr1en.promptcore.plan;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Platform-neutral immutable specification for a pre-dispatch execution gate.
 *
 * <p>Gates pause pipeline execution prior to primary command dispatch.
 */
public sealed interface PreDispatchGateSpec permits PreDispatchGateSpec.Approval {

  /**
   * A two-party target approval gate referencing a trusted preset definition.
   *
   * <p>At the core/parse stage, this references only a trusted preset ID. No Bukkit player or UUID
   * references exist in core definitions.
   *
   * @param presetId the identifier of the trusted approval preset in the preset registry
   */
  record Approval(String presetId) implements PreDispatchGateSpec {
    public static final int MAX_PRESET_ID_LENGTH = 64;
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-z0-9_.-]+$");

    public Approval {
      Objects.requireNonNull(presetId, "presetId must not be null");
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
    }
  }
}
