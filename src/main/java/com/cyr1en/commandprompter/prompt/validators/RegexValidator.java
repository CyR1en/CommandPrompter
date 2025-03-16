package com.cyr1en.commandprompter.prompt.validators;

import com.cyr1en.commandprompter.api.prompt.CompoundableValidator;
import com.cyr1en.commandprompter.api.prompt.InputValidator;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

/**
 * A validator that uses regex to validate input.
 * <p>
 * This validator is used to validate input based on a regex pattern.
 */
public class RegexValidator implements InputValidator, CompoundableValidator {

    public static final CompoundableValidator.Type DEFAULT_TYPE = CompoundableValidator.Type.AND;
    private CompoundableValidator.Type type = DEFAULT_TYPE;

    private final String alias;
    private final Pattern regex;
    private final String messageOnFail;
    private final Player inputPlayer;


    public RegexValidator(String alias, Pattern regex, String messageOnFail, Player inputPlayer) {
        this.alias = alias;
        this.regex = regex;
        this.messageOnFail = messageOnFail;
        this.inputPlayer = inputPlayer;
    }

    @Override
    public boolean validate(String input) {
        if (input == null || regex == null)
            return false;
        return regex.matcher(input).matches();
    }

    @Override
    public String alias() {
        return this.alias;
    }

    @Override
    public String messageOnFail() {
        return this.messageOnFail;
    }

    @Override
    public Player inputPlayer() {
        return this.inputPlayer;
    }

    /**
     * Get the regex pattern.
     *
     * @return the regex pattern.
     */
    public Pattern regex() {
        return regex;
    }

    @Override
    public Type getType() {
        return this.type;
    }

    @Override
    public void setType(Type type) {
        this.type = type;
    }
}
