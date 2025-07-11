package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.commands.MainCommand.Cancel;

import static com.cyr1en.commandprompter.util.MMUtil.mm;

public class ContextProcessor {

    private final CommandPrompter plugin;
    private final PromptManager promptManager;

    public ContextProcessor(CommandPrompter plugin, PromptManager promptManager) {
        this.plugin = plugin;
        this.promptManager = promptManager;
    }

    protected void process(PromptContext context) {
        // Sanity Checks
        plugin.getPluginLogger().debug("Command: " + context.getContent());
        plugin.getPluginLogger().debug("Command Caught using: %s", this.getClass().getSimpleName());

        if (isIgnored(context)) {
            plugin.getPluginLogger().debug("Caught command is ignored.");
            plugin.getPluginLogger().info(mm("<green>" + context.getContent() + "</green> is configured to be ignored."));
            return;
        }

        // Check if the command is CommandPrompter's cancel command
        if (context.getContent().matches(Cancel.commandPattern.toString()))
            return;

        if (!context.getPromptedPlayer().hasPermission("commandprompter.use") &&
                plugin.getConfiguration().enablePermission()) {
            plugin.getMessenger().sendMessage(context.getPromptedPlayer(),
                    plugin.getI18N().getProperty("PromptNoPerm"));
            return;
        }
        if (shouldBlock(context)) {
            plugin.getMessenger().sendMessage(context.getPromptedPlayer(),
                    plugin.getI18N().getFormattedProperty("PromptInProgress",
                            plugin.getConfiguration().cancelKeyword()));
            if (context.getCancellable() != null)
                context.getCancellable().setCancelled(true);
            return;
        }

        if (!promptManager.getParser().isParsable(context)) return;

//        if (!(context.getPromptedPlayer() instanceof Player)) {
//            plugin.getMessenger().sendMessage(context.getSender(),
//                    plugin.getI18N().getProperty("PromptPlayerOnly"));
//            return;
//        }

        if (context.getCancellable() != null)
            context.getCancellable().setCancelled(true);

        plugin.getPluginLogger().debug("Ctx Before Parse: " + context);
        promptManager.parse(context);
        promptManager.sendPrompt(context.getPromptedPlayer());
    }

    private boolean shouldBlock(PromptContext context) {
        var fulfilling = promptManager.getPromptRegistry().inCommandProcess(context.getPromptedPlayer());
        var cmd = extractCommand(context.getContent());
        var cmds = plugin.getConfiguration().allowedWhileInPrompt();
        return fulfilling && (!cmds.contains(cmd) && promptManager.getParser().isParsable(context));
    }

    private boolean isIgnored(PromptContext context) {
        var cmd = extractCommand(context.getContent());
        return plugin.getConfiguration().ignoredCommands().contains(cmd);
    }

    private String extractCommand(String content) {
        var end = content.indexOf(" ");
        end = end == -1 ? content.length() : end;
        return content.substring(0, end);
    }

}
