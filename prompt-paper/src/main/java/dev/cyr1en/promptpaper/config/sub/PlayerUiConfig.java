package dev.cyr1en.promptpaper.config.sub;

/**
 * Grouped configuration for the Player UI prompt screen.
 *
 * <p>Returned by {@code PromptConfig.playerUi()}. The underlying fields are still loaded as flat
 * {@code @ConfigNode}s; this record is a pure ergonomic view.
 */
public record PlayerUiConfig(
    String skullNameFormat,
    int skullCustomModelData,
    int size,
    int cacheSize,
    int cacheDelay,
    ControlItem previous,
    ControlItem next,
    ControlItem cancel,
    ControlItem search,
    SearchAnvilItem searchAnvil,
    boolean sorted,
    String emptyMessage,
    String worldFilterFormat,
    String radialFilterFormat) {

  public PlayerUiConfig {
    if (size != 18 && size != 27 && size != 36 && size != 45 && size != 54) {
      throw new IllegalArgumentException(
          "Player UI inventory size must be one of 18, 27, 36, 45, or 54; got " + size);
    }
    if (size / 9 - 1 <= 0) {
      throw new IllegalArgumentException("Player UI page size must be greater than zero");
    }
  }
}
