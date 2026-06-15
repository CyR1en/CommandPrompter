package dev.cyr1en.promptpaper.config.sub;

/** A controllable control item in the Player UI bottom bar. */
public record ControlItem(
        String item,
        int customModelData,
        int column,
        String text) {
}
