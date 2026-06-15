package dev.cyr1en.promptpaper.validation;

/**
 * Marker interface for validators that can be composed into a {@link CompoundedValidator}
 * with either AND (all must pass) or OR (any must pass) semantics.
 */
public interface CompoundableValidator {
    /** Returns the composition mode for this validator. */
    Type getType();
    /** Sets the composition mode (AND or OR). */
    void setType(Type type);

    /** Composition semantics: {@code AND} requires all validators to pass; {@code OR} requires at least one. */
    enum Type {
        AND,
        OR,
        DEFAULT
    }
}
