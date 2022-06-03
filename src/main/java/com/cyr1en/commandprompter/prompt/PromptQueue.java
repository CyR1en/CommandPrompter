package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.api.prompt.Prompt;

import java.util.LinkedList;

public class PromptQueue extends LinkedList<Prompt> {

    private String command;
    private final LinkedList<String> completed;
    private final String escapedRegex;
    private final boolean isOp;

    private final boolean isSetPermissionAttachment;

    public PromptQueue(String command, boolean isOp, boolean isSetPermissionAttachment, String escapedRegex) {
        super();
        this.command = command;
        this.escapedRegex = escapedRegex;
        this.completed = new LinkedList<>();
        this.isOp = isOp;
        this.isSetPermissionAttachment = isSetPermissionAttachment;
    }

    public void addCompleted(String s) {
        completed.add(s);
    }

    public boolean isOp() {
        return isOp;
    }

    public boolean isSetPermissionAttachment() {
        return isSetPermissionAttachment;
    }

    public String getCompleteCommand() {
        command = command.formatted(completed);
        while (!completed.isEmpty())
            command = command.replaceFirst(escapedRegex, completed.pollFirst());
        return "/" + command;
    }

}
