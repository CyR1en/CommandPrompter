package com.cyr1en.commandprompter.common.cpml.element;

import org.jetbrains.annotations.NotNull;

public record PostCommandElement(String id, String type, String args, String content) implements ElementNode {

    @Override
    public @NotNull String toString() {
        return "<postcommand id=\"" + id + "\" type=\"" + type + "\" args=\"" + args + "\">" + content + "</postcommand>";
    }
}
