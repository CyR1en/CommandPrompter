package dev.cyr1en.promptpaper.config.sub;

import dev.cyr1en.promptcore.ConfirmationMode;

/** Grouped configuration for the Confirmation prompt screen. */
public record ConfirmationScreenConfig(
        ConfirmationMode defaultMode,
        String guiTitle,
        ConfirmationItem confirmItem,
        ConfirmationItem cancelItem,
        ConfirmationItem infoItem,
        String defaultConfirmLabel,
        String defaultCancelLabel,
        String sound) {

    public record ConfirmationItem(
            String material,
            String name,
            int slot) {}
}
