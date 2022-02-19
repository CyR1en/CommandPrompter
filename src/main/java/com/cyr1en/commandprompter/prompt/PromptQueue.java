package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.api.prompt.Prompt;

import java.util.LinkedList;

public class PromptQueue extends LinkedList<Prompt> {

    private String command;
    private final LinkedList<String> completed;
    private final String escapedRegex;
    private final boolean isOp;

    public PromptQueue(String command, boolean isOp, String escapedRegex) {
        super();
        this.command = command;
        this.escapedRegex = escapedRegex;
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
        command = command.formatted(completed);
        while (!completed.isEmpty())
            command = command.replaceFirst(escapedRegex, completed.pollFirst());
        return "/" + command;
    }

}
