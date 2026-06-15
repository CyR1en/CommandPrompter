package dev.cyr1en.promptpaper.validation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Validates that the input matches the name of a currently online player.
 * Implements {@link CompoundableValidator} with a configurable AND/OR type.
 */
public class OnlinePlayerValidator implements InputValidator, CompoundableValidator {

    public static final Type DEFAULT_TYPE = Type.AND;
    private Type type = DEFAULT_TYPE;

    private final String alias;
    private final String messageOnFail;
    private final Player inputPlayer;

    public OnlinePlayerValidator(String alias, String messageOnFail, Player inputPlayer) {
        this.alias = alias;
        this.messageOnFail = messageOnFail;
        this.inputPlayer = inputPlayer;
    }

    /** Returns {@code true} if the input matches an online player's name. */
    @Override
    public boolean validate(String input) {
        if (input == null || input.isBlank()) return false;
        var player = Bukkit.getPlayer(input);
        return player != null && player.isOnline();
    }

    @Override
    public String alias() { return alias; }

    @Override
    public String messageOnFail() { return messageOnFail; }

    @Override
    public Player inputPlayer() { return inputPlayer; }

    @Override
    public Type getType() { return type; }

    @Override
    public void setType(Type type) { this.type = type; }
}
