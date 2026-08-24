package dev.cyr1en.promptpaper.approval;

import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * Seam for resolving target approver string tokens to exact online Player entities.
 */
@FunctionalInterface
public interface TargetResolver {

  /**
   * Resolves a target approver name or UUID string to an online Player entity.
   *
   * @param targetNameOrUuid the target name or UUID string
   * @return optional containing the online player, or empty if offline, missing, or ambiguous
   */
  Optional<Player> resolveTarget(String targetNameOrUuid);
}
