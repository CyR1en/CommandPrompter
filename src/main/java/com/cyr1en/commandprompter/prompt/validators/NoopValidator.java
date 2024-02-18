package com.cyr1en.commandprompter.prompt.validators;

import com.cyr1en.commandprompter.api.prompt.InputValidator;

/**
 * A validator that does nothing.
 * <p>
 * This will always return true.
 */
public class NoopValidator implements InputValidator {
    @Override
    public boolean validate(String input) {
        return true;
    }

    @Override
    public String alias() {
        return "noop";
    }

    @Override
    public String messageOnFail() {
        return "";
    }
}
