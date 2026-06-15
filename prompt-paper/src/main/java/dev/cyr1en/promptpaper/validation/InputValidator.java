package dev.cyr1en.promptpaper.validation;

import org.bukkit.entity.Player;

/**
 * Strategy interface for validating a single user input string from a prompt.
 * Implementations may be composed via {@link CompoundedValidator}.
 */
public interface InputValidator {
    /** Returns {@code true} if the input passes validation. */
    boolean validate(String input);
    /** Human-readable alias identifying this validator instance (used in config). */
    String alias();
    /** Message shown to the player when validation fails. */
    String messageOnFail();
    /** The player whose input is being validated, or {@code null} for context-free validators. */
    Player inputPlayer();
}
