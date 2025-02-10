package com.cyr1en.commandprompter.prompt.validators;

import com.cyr1en.commandprompter.api.prompt.InputValidator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * A validator that checks if the input is an online player.
 *
 * @param alias The alias for this validator.
 * @param messageOnFail The message to send when validation fails.
 */
public record OnlinePlayerValidator(String alias, String messageOnFail, Player inputPlayer) implements InputValidator {

    @Override
    public boolean validate(String input) {
        if (input == null || input.isBlank())
            return false;
        var player = Bukkit.getPlayer(input);
        return player != null && player.isOnline();
    }

}
