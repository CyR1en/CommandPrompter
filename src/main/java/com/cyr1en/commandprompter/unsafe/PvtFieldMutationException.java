package com.cyr1en.commandprompter.unsafe;

public class PvtFieldMutationException extends RuntimeException {
    public PvtFieldMutationException(Class<?> targetClass) {
        super("Failed to set '" + targetClass.getSimpleName() + "' to private target field");
    }

    public PvtFieldMutationException(String newVal) {
        super("Failed to set '" + newVal + "' to private target field");
    }
}
