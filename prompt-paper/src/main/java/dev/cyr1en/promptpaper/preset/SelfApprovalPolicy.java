package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;

/**
 * Policy defining whether an action initiated by an authorized approver requires approval.
 */
public enum SelfApprovalPolicy {
  /**
   * Approver's own action is automatically approved without gating.
   */
  @SerializedName("auto_approve")
  AUTO_APPROVE,

  /**
   * Approver must explicitly confirm their own action.
   */
  @SerializedName("require_confirm")
  REQUIRE_CONFIRM;

  public static SelfApprovalPolicy fromString(String name) {
    if (name == null || name.isBlank()) {
      return AUTO_APPROVE;
    }
    for (SelfApprovalPolicy policy : values()) {
      if (policy.name().equalsIgnoreCase(name)
          || policy.name().replace("_", "").equalsIgnoreCase(name.replace("_", ""))) {
        return policy;
      }
    }
    throw new IllegalArgumentException("Unknown SelfApprovalPolicy: " + name);
  }
}
