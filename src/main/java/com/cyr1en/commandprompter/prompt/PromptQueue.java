package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.api.prompt.Prompt;

import java.util.LinkedList;

public class PromptQueue extends LinkedList<Prompt> {

    private final String command;
    private final LinkedList<String> completed;
    private final boolean isOp;

    public PromptQueue(String command, boolean isOp) {
        super();
        this.command = command;
        this.completed = new LinkedList<>();
        this.isOp = isOp;
    }

    public void addCompleted(String s) {
        completed.add(s);
    }

    public boolean isOp() {
        return isOp;
    }

    public String getCompleteCommand() {
        var s = "/%s %s";
        return s.formatted(command, String.join(" ", completed)
                .replaceAll("\\s+", " ").trim());
    }

}
