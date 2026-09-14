package dev.cyr1en.promptui.util;

import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.geyser.api.GeyserApi;
import org.jetbrains.annotations.Nullable;

/**
 * Utility for detecting whether a player is connected via Bedrock Edition (through GeyserMC or
 * Floodgate).
 *
 * <p>Safely handles environments where Geyser is not installed by catching linkage/class errors and
 * verifying plugin status. Provides a hook for mocking Bedrock status in unit tests.
 */
public final class BedrockUtil {

  private static volatile Predicate<UUID> bedrockChecker;

  private BedrockUtil() {}

  /**
   * Sets a custom predicate for determining Bedrock player status, useful for unit tests.
   *
   * @param checker the predicate or {@code null} to reset to standard detection
   */
  public static void setBedrockChecker(@Nullable Predicate<UUID> checker) {
    bedrockChecker = checker;
  }

  /** Resets the custom Bedrock checker to default detection logic. */
  public static void reset() {
    bedrockChecker = null;
  }

  /**
   * Checks whether the specified player is connecting via Bedrock Edition.
   *
   * @param player the player to check
   * @return {@code true} if the player is identified as a Bedrock player, {@code false} otherwise
   */
  public static boolean isBedrockPlayer(@Nullable Player player) {
    if (player == null) {
      return false;
    }
    return isBedrockPlayer(player.getUniqueId());
  }

  /**
   * Checks whether the specified UUID belongs to a player connecting via Bedrock Edition.
   *
   * @param uuid the player UUID
   * @return {@code true} if identified as a Bedrock player, {@code false} otherwise
   */
  public static boolean isBedrockPlayer(@Nullable UUID uuid) {
    if (uuid == null) {
      return false;
    }
    Predicate<UUID> checker = bedrockChecker;
    if (checker != null) {
      return checker.test(uuid);
    }
    if (isGeyserInstalled() && GeyserDelegate.isBedrockPlayer(uuid)) {
      return true;
    }
    if (isFloodgateInstalled() && isFloodgatePlayer(uuid)) {
      return true;
    }
    return false;
  }

  /**
   * Checks whether Geyser is installed and enabled on the server.
   *
   * @return {@code true} if Geyser is available and enabled
   */
  public static boolean isGeyserInstalled() {
    try {
      if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")
            && !Bukkit.getPluginManager().isPluginEnabled("Geyser")) {
          return false;
        }
      }
      Class.forName("org.geysermc.geyser.api.GeyserApi");
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean isFloodgateInstalled() {
    try {
      if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
          return false;
        }
      }
      Class.forName("org.geysermc.floodgate.api.FloodgateApi");
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean isFloodgatePlayer(UUID uuid) {
    try {
      Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
      Object instance = floodgateApiClass.getMethod("getInstance").invoke(null);
      if (instance != null) {
        Object result =
            floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, uuid);
        return Boolean.TRUE.equals(result);
      }
    } catch (Throwable ignored) {
      // Ignored
    }
    return false;
  }

  /**
   * Isolated delegate class to ensure {@link GeyserApi} is only loaded when Geyser is actually
   * present on the classpath and enabled.
   */
  private static final class GeyserDelegate {
    private static boolean isBedrockPlayer(UUID uuid) {
      try {
        GeyserApi api = GeyserApi.api();
        return api != null && api.isBedrockPlayer(uuid);
      } catch (Throwable ignored) {
        return false;
      }
    }
  }
}
