package dev.cyr1en.promptpaper.screen.confirmation;

import java.util.Optional;

/**
 * Represents the allowed user decision for a confirmation prompt.
 */
public enum ConfirmationDecision {
    CONFIRM("confirm"),
    DECLINE("decline");

    private final String commandArg;

    ConfirmationDecision(String commandArg) {
        this.commandArg = commandArg;
    }

    public String commandArg() {
        return commandArg;
    }

    /**
     * Parses a raw decision string case-insensitively.
     *
     * @param text the input string
     * @return the parsed decision, or empty if text is not "confirm" or "decline"
     */
    public static Optional<ConfirmationDecision> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        var trimmed = text.trim();
        if (trimmed.equalsIgnoreCase("confirm")) {
            return Optional.of(CONFIRM);
        }
        if (trimmed.equalsIgnoreCase("decline")) {
            return Optional.of(DECLINE);
        }
        return Optional.empty();
    }
}
