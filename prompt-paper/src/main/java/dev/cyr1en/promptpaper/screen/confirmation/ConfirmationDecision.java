package dev.cyr1en.promptpaper.screen.confirmation;

import java.util.Locale;
import java.util.Optional;

/** Represents the allowed user decision for a confirmation prompt. */
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
    return switch (text.trim().toLowerCase(Locale.ROOT)) {
      case "confirm" -> Optional.of(CONFIRM);
      case "decline" -> Optional.of(DECLINE);
      default -> Optional.empty();
    };
  }
}
