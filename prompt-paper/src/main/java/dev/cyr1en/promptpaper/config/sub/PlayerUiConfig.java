package dev.cyr1en.promptpaper.config.sub;

/**
 * Grouped configuration for the Player UI prompt screen.
 *
 * <p>Returned by {@code PromptConfig.playerUi()}. The underlying fields are still
 * loaded as flat {@code @ConfigNode}s; this record is a pure ergonomic view.
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
}
