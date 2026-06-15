package dev.cyr1en.promptpaper.validation;

import java.util.regex.Pattern;
import org.bukkit.entity.Player;

/**
 * Validates user input against a compiled {@link Pattern}. The full input
 * must match the regex (uses {@link java.util.regex.Matcher#matches}).
 */
public class RegexValidator implements InputValidator, CompoundableValidator {

    public static final Type DEFAULT_TYPE = Type.AND;
    private Type type = DEFAULT_TYPE;

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

    /** Returns {@code true} if the entire input matches the configured regex. */
    @Override
    public boolean validate(String input) {
        if (input == null || regex == null) return false;
        return regex.matcher(input).matches();
    }

    @Override
    public String alias() { return alias; }

    @Override
    public String messageOnFail() { return messageOnFail; }

    @Override
    public Player inputPlayer() { return inputPlayer; }

    public Pattern regex() { return regex; }

    @Override
    public Type getType() { return type; }

    @Override
    public void setType(Type type) { this.type = type; }
}
