package com.cyr1en.commandprompter.prompt.validators;

import com.cyr1en.commandprompter.api.prompt.CompoundableValidator;
import com.cyr1en.commandprompter.api.prompt.InputValidator;
import org.bukkit.entity.Player;

import java.util.stream.Stream;

public class CompoundedValidator implements InputValidator {

    private final String alias;
    private final String messageOnFail;
    private final Player inputPlayer;
    private final InputValidator[] validators;


    public CompoundedValidator(String alias, String messageOnFail, Player inputPlayer, InputValidator... validators) {
        this.alias = alias;
        this.messageOnFail = messageOnFail;
        this.inputPlayer = inputPlayer;
        this.validators = validators;
    }

    @Override
    public boolean validate(String input) {
        if (input == null || input.isBlank())
            return false;

        var andValidators = getValidatorsWithType(CompoundableValidator.Type.AND);
        var orValidators = getValidatorsWithType(CompoundableValidator.Type.OR);

        return andValidators.allMatch(validator -> validator.validate(input))
                || orValidators.anyMatch(validator -> validator.validate(input));
    }

    private Stream<InputValidator> getValidatorsWithType(CompoundableValidator.Type type) {
        return Stream.of(validators)
                .filter(validator -> validator instanceof CompoundableValidator)
                .filter(validator -> ((CompoundableValidator) validator).getType() == type);
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
}
