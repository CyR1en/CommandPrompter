package com.cyr1en.commandprompter.prompt.validators;

import com.cyr1en.commandprompter.api.prompt.CompoundableValidator;
import com.cyr1en.commandprompter.api.prompt.InputValidator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * A validator that checks if the input is an online player.
 **/
public class OnlinePlayerValidator implements InputValidator, CompoundableValidator {


    public static final CompoundableValidator.Type DEFAULT_TYPE = Type.AND;
    private CompoundableValidator.Type type = DEFAULT_TYPE;

    private final String alias;
    private final String messageOnFail;
    private final Player inputPlayer;

    public OnlinePlayerValidator(String alias, String messageOnFail, Player inputPlayer) {
        this.alias = alias;
        this.messageOnFail = messageOnFail;
        this.inputPlayer = inputPlayer;
    }

    @Override
    public boolean validate(String input) {
        if (input == null || input.isBlank())
            return false;
        var player = Bukkit.getPlayer(input);
        return player != null && player.isOnline();
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

    @Override
    public Type getType() {
        return this.type;
    }

    @Override
    public void setType(Type type) {
        this.type = type;
    }
}
