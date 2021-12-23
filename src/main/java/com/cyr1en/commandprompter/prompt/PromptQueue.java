package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.api.prompt.Prompt;

import java.util.LinkedList;
import java.util.PriorityQueue;

public class PromptQueue extends PriorityQueue<Prompt> {

    private final String command;
    private final LinkedList<String> completed;

    public PromptQueue(String command) {
        super();
        this.command = command;
        this.completed = new LinkedList<>();
    }

    public void addCompleted(String s) {
        completed.add(s);
    }

    public String getCompleteCommand() {
        var s = "/%s %s";
        return s.formatted(command, String.join(" ", completed));
    }
}
