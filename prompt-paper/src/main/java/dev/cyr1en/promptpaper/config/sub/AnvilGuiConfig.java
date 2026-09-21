package dev.cyr1en.promptpaper.config.sub;

/** Grouped configuration for the Anvil GUI prompt screen. */
public record AnvilGuiConfig(
    boolean enableTitle,
    String customTitle,
    String promptMessage,
    boolean enableCancelItem,
    String anvilItem,
    int itemDamage,
    String itemHoverText,
    boolean itemHideTooltips,
    int itemCustomModelData,
    boolean itemAnvilEnchanted,
    String anvilResultItem,
    boolean resultItemHideTooltips,
    int resultItemCustomModelData,
    boolean resultItemAnvilEnchanted,
    String anvilCancelItem,
    String cancelItemName,
    boolean cancelItemHideTooltips,
    int cancelItemCustomModelData,
    boolean cancelItemAnvilEnchanted,
    String cancelItemHoverText,
    String displayText) {}
