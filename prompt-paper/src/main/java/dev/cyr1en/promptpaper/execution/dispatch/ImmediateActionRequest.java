package dev.cyr1en.promptpaper.execution.dispatch;

import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import java.util.Objects;
import org.bukkit.entity.Player;

/** Immutable request parameters for executing an immediate action. */
public record ImmediateActionRequest(
    Player player,
    String command,
    ExecuteAs executeAs,
    ActionProvenance provenance,
    PlayerExecutor initiatorExecutor,
    String sourceId) {
  public ImmediateActionRequest {
    Objects.requireNonNull(player, "player must not be null");
    Objects.requireNonNull(command, "command must not be null");
    if (executeAs == null) {
      executeAs = ExecuteAs.PLAYER;
    }
    if (provenance == null) {
      provenance = ActionProvenance.untrustedInline();
    }
    sourceId = sourceId != null ? DispatchSanitizer.sanitizeDetail(sourceId) : null;
  }

  public static ImmediateActionRequest player(Player player, String command) {
    return new ImmediateActionRequest(
        player, command, ExecuteAs.PLAYER, ActionProvenance.untrustedInline(), null, null);
  }

  public static ImmediateActionRequest player(
      Player player, String command, PlayerExecutor initiatorExecutor) {
    return new ImmediateActionRequest(
        player,
        command,
        ExecuteAs.PLAYER,
        ActionProvenance.untrustedInline(),
        initiatorExecutor,
        null);
  }

  public static ImmediateActionRequest trustedPreset(
      Player player, String command, ExecuteAs executeAs, String sourceId) {
    return new ImmediateActionRequest(
        player, command, executeAs, ActionProvenance.trustedPreset(sourceId), null, sourceId);
  }

  public static ImmediateActionRequest trustedPreset(
      Player player,
      String command,
      ExecuteAs executeAs,
      String sourceId,
      PlayerExecutor initiatorExecutor) {
    return new ImmediateActionRequest(
        player,
        command,
        executeAs,
        ActionProvenance.trustedPreset(sourceId),
        initiatorExecutor,
        sourceId);
  }

  public static ImmediateActionRequest consoleDelegated(
      Player player, String command, String sourceId) {
    return new ImmediateActionRequest(
        player,
        command,
        ExecuteAs.CONSOLE,
        ActionProvenance.consoleDelegated(sourceId),
        null,
        sourceId);
  }

  public static ImmediateActionRequest consoleDelegated(
      Player player, String command, String sourceId, PlayerExecutor initiatorExecutor) {
    return new ImmediateActionRequest(
        player,
        command,
        ExecuteAs.CONSOLE,
        ActionProvenance.consoleDelegated(sourceId),
        initiatorExecutor,
        sourceId);
  }
}
