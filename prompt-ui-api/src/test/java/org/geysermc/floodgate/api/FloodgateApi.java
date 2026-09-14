package org.geysermc.floodgate.api;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Test stub mirroring FloodgateApi for testing BedrockUtil's reflection-based Floodgate detection.
 */
public class FloodgateApi {

  private static FloodgateApi instance;
  private Predicate<UUID> playerChecker;

  public static FloodgateApi getInstance() {
    return instance;
  }

  public static void setInstance(FloodgateApi api) {
    instance = api;
  }

  public void setPlayerChecker(Predicate<UUID> playerChecker) {
    this.playerChecker = playerChecker;
  }

  public boolean isFloodgatePlayer(UUID uuid) {
    return playerChecker != null && playerChecker.test(uuid);
  }
}
