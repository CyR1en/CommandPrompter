package dev.cyr1en.promptpaper.config.sub;

/** The search-anvil item rendered when a player starts a search. */
public record SearchAnvilItem(
        String title,
        String material,
        int customModelData,
        String text) {
}
