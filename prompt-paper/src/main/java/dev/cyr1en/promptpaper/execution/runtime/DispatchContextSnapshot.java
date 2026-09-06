package dev.cyr1en.promptpaper.execution.runtime;

import dev.cyr1en.promptpaper.preset.ExecuteAs;
import java.util.List;

/**
 * Immutable snapshot of the execution dispatch context.
 *
 * <p>Captures execute-as mode, delegation flags, permission key, and an immutable snapshot of
 * permissions. Contains no mutable Bukkit objects or live attachments.
 *
 * @param executeAs target execution entity (Console vs Player)
 * @param permissionKey optional permission node key
 * @param attachmentRequired whether temporary permission attachment was requested/required
 * @param permissionSnapshot immutable list of effective permissions at capture time
 */
public record DispatchContextSnapshot(
    ExecuteAs executeAs,
    String permissionKey,
    boolean attachmentRequired,
    List<String> permissionSnapshot) {

  public DispatchContextSnapshot {
    if (executeAs == null) {
      executeAs = ExecuteAs.PLAYER;
    }
    permissionSnapshot = permissionSnapshot == null ? List.of() : List.copyOf(permissionSnapshot);
  }

  /** Creates a standard player dispatch context snapshot without delegation. */
  public static DispatchContextSnapshot player() {
    return new DispatchContextSnapshot(ExecuteAs.PLAYER, null, false, List.of());
  }

  /** Creates a console dispatch context snapshot. */
  public static DispatchContextSnapshot console() {
    return new DispatchContextSnapshot(ExecuteAs.CONSOLE, null, false, List.of());
  }

  /** Convenience factory for creating a snapshot. */
  public static DispatchContextSnapshot of(
      ExecuteAs executeAs,
      String permissionKey,
      boolean attachmentRequired,
      List<String> permissionSnapshot) {
    return new DispatchContextSnapshot(
        executeAs, permissionKey, attachmentRequired, permissionSnapshot);
  }

  /** Returns {@code true} if this dispatch runs with console privileges. */
  public boolean isConsoleDelegated() {
    return executeAs == ExecuteAs.CONSOLE;
  }

  /** Returns {@code true} if this dispatch requires delegation or console context. */
  public boolean isDelegated() {
    return executeAs == ExecuteAs.CONSOLE || attachmentRequired;
  }

  /**
   * Checks whether the permission snapshot contains the specified permission.
   *
   * @param permission permission string to check
   * @return true if permission is present in snapshot
   */
  public boolean hasPermission(String permission) {
    if (permission == null) {
      return false;
    }
    return permissionSnapshot.contains(permission);
  }
}
