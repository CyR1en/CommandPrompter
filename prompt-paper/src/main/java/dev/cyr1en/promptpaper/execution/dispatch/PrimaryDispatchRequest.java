package dev.cyr1en.promptpaper.execution.dispatch;

import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import java.util.Objects;
import org.bukkit.entity.Player;

/** Immutable request parameters for executing a primary prompt command. */
public record PrimaryDispatchRequest(
    Player player,
    String command,
    DispatchMode mode,
    PermissionAttachmentContext attachmentContext,
    PlayerExecutor initiatorExecutor,
    String sourceId) {
  public PrimaryDispatchRequest {
    Objects.requireNonNull(player, "player must not be null");
    Objects.requireNonNull(command, "command must not be null");
    if (mode == null) {
      mode = DispatchMode.PLAYER;
    }
    if (attachmentContext == null) {
      attachmentContext = PermissionAttachmentContext.empty();
    }
    sourceId = sourceId != null ? DispatchSanitizer.sanitizeDetail(sourceId) : null;
  }

  public static PrimaryDispatchRequest player(Player player, String command) {
    return new PrimaryDispatchRequest(player, command, DispatchMode.PLAYER, null, null, null);
  }

  public static PrimaryDispatchRequest player(
      Player player, String command, PlayerExecutor initiatorExecutor) {
    return new PrimaryDispatchRequest(
        player, command, DispatchMode.PLAYER, null, initiatorExecutor, null);
  }

  public static PrimaryDispatchRequest console(Player player, String command) {
    return new PrimaryDispatchRequest(player, command, DispatchMode.CONSOLE, null, null, null);
  }

  public static PrimaryDispatchRequest console(
      Player player, String command, PlayerExecutor initiatorExecutor) {
    return new PrimaryDispatchRequest(
        player, command, DispatchMode.CONSOLE, null, initiatorExecutor, null);
  }

  public static PrimaryDispatchRequest attachment(
      Player player, String command, PermissionAttachmentContext attachmentContext) {
    return new PrimaryDispatchRequest(
        player, command, DispatchMode.ATTACHMENT, attachmentContext, null, null);
  }

  public static PrimaryDispatchRequest attachment(
      Player player,
      String command,
      PermissionAttachmentContext attachmentContext,
      PlayerExecutor initiatorExecutor) {
    return new PrimaryDispatchRequest(
        player, command, DispatchMode.ATTACHMENT, attachmentContext, initiatorExecutor, null);
  }
}
