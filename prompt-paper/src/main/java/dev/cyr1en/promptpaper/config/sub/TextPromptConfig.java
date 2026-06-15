package dev.cyr1en.promptpaper.config.sub;

/** Grouped configuration for the chat text prompt screen. */
public record TextPromptConfig(
        boolean sendCancelText,
        String cancelMessage,
        String cancelHoverMessage,
        String responseListenerPriority) {
}
