package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;

public class BaseHook {

    private final CommandPrompter plugin;

    public BaseHook(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    public CommandPrompter getPlugin() {
        return this.plugin;
    }
}
