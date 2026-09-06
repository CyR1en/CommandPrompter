package dev.cyr1en.promptpaper.execution.dispatch;

import org.bukkit.entity.Player;

/** Explicit provenance descriptor indicating source trust level and console delegation status. */
public record ActionProvenance(
    ActionTrustLevel trustLevel, boolean consoleDelegated, String sourceId) {
  public ActionProvenance {
    if (trustLevel == null) {
      trustLevel = ActionTrustLevel.UNTRUSTED_INLINE;
    }
    sourceId = sourceId != null ? DispatchSanitizer.sanitizeDetail(sourceId) : null;
  }

  public static ActionProvenance trustedPreset(String sourceId) {
    return new ActionProvenance(ActionTrustLevel.TRUSTED_PRESET, false, sourceId);
  }

  public static ActionProvenance trustedPreset(String sourceId, boolean consoleDelegated) {
    return new ActionProvenance(ActionTrustLevel.TRUSTED_PRESET, consoleDelegated, sourceId);
  }

  public static ActionProvenance consoleDelegated(String sourceId) {
    return new ActionProvenance(ActionTrustLevel.CONSOLE_DELEGATED, true, sourceId);
  }

  public static ActionProvenance untrustedInline() {
    return new ActionProvenance(ActionTrustLevel.UNTRUSTED_INLINE, false, null);
  }

  public static ActionProvenance untrustedInline(boolean consoleDelegated) {
    return new ActionProvenance(ActionTrustLevel.UNTRUSTED_INLINE, consoleDelegated, null);
  }

  public boolean isTrustedPreset() {
    return trustLevel == ActionTrustLevel.TRUSTED_PRESET;
  }

  /**
   * Checks whether this action provenance authorizes console execution for the given player.
   *
   * @param player the player initiating the flow
   * @return true if authorized to execute as console; false otherwise
   */
  public boolean isConsoleAuthorized(Player player) {
    if (consoleDelegated || trustLevel == ActionTrustLevel.CONSOLE_DELEGATED) {
      return true;
    }
    if (trustLevel != ActionTrustLevel.TRUSTED_PRESET) {
      return false;
    }
    if (player == null) {
      return false;
    }
    return player.isOp()
        || player.hasPermission("promptpaper.pcm.console")
        || player.hasPermission("promptpaper.admin")
        || player.hasPermission("promptpaper.consoledelegate");
  }
}
