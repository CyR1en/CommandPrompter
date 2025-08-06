package com.cyr1en.commandprompter.common.cpml.element;

import org.jetbrains.annotations.NotNull;

public record PreConfigElement(String id) implements ElementNode {

    @Override
    public @NotNull String toString() {
        return "<preconfig id=\"" + id + "\"/>";
    }
}
