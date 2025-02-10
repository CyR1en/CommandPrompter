package com.cyr1en.commandprompter.prompt.validators;

import com.cyr1en.commandprompter.api.prompt.InputValidator;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

/**
 * A validator that uses regex to validate input.
 * <p>
 * This validator is used to validate input based on a regex pattern.
 *
 * @param alias The alias for this validator.
 * @param regex The regex pattern to use for validation.
 * @param messageOnFail The message to send when validation fails.
 */
public record RegexValidator(String alias, Pattern regex, String messageOnFail, Player inputPlayer) implements InputValidator {

    @Override
    public boolean validate(String input) {
        if (input == null || regex == null)
            return false;
        return regex.matcher(input).matches();
    }

    /**
     * Get the regex pattern.
     *
     * @return the regex pattern.
     */
    @Override
    public Pattern regex() {
        return regex;
    }
}
