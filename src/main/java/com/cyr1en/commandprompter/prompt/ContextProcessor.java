package com.cyr1en.commandprompter.prompt;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.fusesource.jansi.Ansi;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.commands.MainCommand.Cancel;
import com.cyr1en.commandprompter.hook.hooks.VentureChatHook;

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
            plugin.getPluginLogger().info(new Ansi().fgGreen().a(context.getContent()).reset() + " is configured to be ignored.");
            return;
        }

        // Check if the command is CommandPrompter's cancel command
        if (context.getContent().matches(Cancel.commandPattern.toString()))
            return;

        if (!context.getSender().hasPermission("commandprompter.use") &&
                plugin.getConfiguration().enablePermission()) {
            plugin.getMessenger().sendMessage(context.getSender(),
                    plugin.getI18N().getProperty("PromptNoPerm"));
            return;
        }
        if (shouldBlock(context)) {
            plugin.getMessenger().sendMessage(context.getSender(),
                    plugin.getI18N().getFormattedProperty("PromptInProgress",
                            plugin.getConfiguration().cancelKeyword()));
            if (context.getCancellable() != null)
                context.getCancellable().setCancelled(true);
            return;
        }

        if (!promptManager.getParser().isParsable(context)) return;
        if (!(context.getSender() instanceof Player)) {
            plugin.getMessenger().sendMessage(context.getSender(),
                    plugin.getI18N().getProperty("PromptPlayerOnly"));
            return;
        }

        if (context.getCancellable() != null)
            context.getCancellable().setCancelled(true);

        plugin.getPluginLogger().debug("Ctx Before Parse: " + context);
        promptManager.parse(context);
        promptManager.sendPrompt(context.getSender());
    }

    private boolean shouldBlock(PromptContext context) {
        var fulfilling = promptManager.getPromptRegistry().inCommandProcess(context.getSender());
        var cmd = extractCommand(context.getContent());
        var cmds = plugin.getConfiguration().allowedWhileInPrompt();
        return fulfilling && (!cmds.contains(cmd) && promptManager.getParser().isParsable(context));
    }

    private boolean isIgnored(PromptContext context) {
        var cmd = extractCommand(context.getContent());
        return plugin.getConfiguration().ignoredCommands().contains(cmd) || isCmdChatChannel(cmd);
    }

    private String extractCommand(String content) {
        var end = content.indexOf(" ");
        end = end == -1 ? content.length() : end;
        return content.substring(0, end);
    }

    private boolean isCmdChatChannel(String cmd) {
        var out = new AtomicBoolean(false);
        var vcHook = plugin.getHookContainer().getHook(VentureChatHook.class);
        plugin.getPluginLogger().debug("VentureChat hooked: " + vcHook.isHooked());
        vcHook.ifHooked(hook -> out.set(hook.isChatChannel(cmd))).complete();
        plugin.getPluginLogger().debug("is VentureChat channel: " + out.get());
        return out.get();
    }


}
