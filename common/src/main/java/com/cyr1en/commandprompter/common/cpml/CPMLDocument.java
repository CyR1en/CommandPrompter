package com.cyr1en.commandprompter.common.cpml;

import com.cyr1en.commandprompter.common.cpml.element.ElementNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record CPMLDocument(List<ElementNode> elements) {

    @Override
    public @NotNull String toString() {
        return "<cpml>\n" + elements.stream().map(Object::toString).reduce("", (a, b) -> a + "  " + b + "\n") + "</cpml>";
    }
}
