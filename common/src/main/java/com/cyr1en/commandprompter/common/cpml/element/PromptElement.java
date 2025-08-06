package com.cyr1en.commandprompter.common.cpml.element;

import org.jetbrains.annotations.NotNull;

public record PromptElement(String id, String type, String args, String content) implements ElementNode {

    @Override
    public @NotNull String toString() {
        return "<prompt id=\"" + id + "\" type=\"" + type + "\" args=\"" + args + "\">" + content + "</prompt>";
    }
}
