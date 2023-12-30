package com.cyr1en.commandprompter.api.prompt;


/**
 * A functional interface for validating input.
 *
 * <p>Implement this interface to create a custom validator for your prompt.</p>
 */
public interface InputValidator {

    /**
     * Validate the input.
     *
     * <p>Return true if the input is valid, false otherwise.</p>
     *
     * @param input the input to validate
     * @return true if the input is valid, false otherwise
     */
    boolean validate(String input);

    /**
     * Get the alias for this validator.
     *
     * <p>The alias is used to identify the validator.</p>
     *
     * @return the alias for this validator
     */
    String alias();

    /**
     * Get the message to send when validation fails.
     *
     * @return the message to send when validation fails
     */
    String messageOnFail();
}
