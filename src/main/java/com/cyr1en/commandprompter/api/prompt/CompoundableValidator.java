package com.cyr1en.commandprompter.api.prompt;


/**
 * A class that represents a compoundable validator.
 * <p>
 * This class is used to represent a validator that can be combined with other validators.
 */
public interface CompoundableValidator {

    /**
     * Get the type of the compoundable validator.
     *
     * @return the type of the compoundable validator.
     */
    public Type getType();

    /**
     * Set the type of the compoundable validator.
     *
     * @param type the type of the compoundable validator.
     */
    public void setType(Type type);

    public enum Type {
        AND,
        OR,
        DEFAULT
    }
}
