package com.cyr1en.commandprompter.prompt;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.commands.MainCommand.Cancel;
import com.cyr1en.commandprompter.hook.hooks.VentureChatHook;

public class ContextProcessor {
    
    private static final Pattern permissionAttachmentPattern = Pattern.compile("-pa ");

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
        if (promptManager.getPromptRegistry().inCommandProcess(context.getSender())) {
            plugin.getMessenger().sendMessage(context.getSender(),
                    plugin.getI18N().getFormattedProperty("PromptInProgress",
                            plugin.getConfiguration().cancelKeyword()));
            if(context.getCancellable() != null)
                context.getCancellable().setCancelled(true);
            return;
        }

        parsePermissionAttachment(context);
        if (!promptManager.getParser().isParsable(context)) return;
        if (!(context.getSender() instanceof Player)) {
            plugin.getMessenger().sendMessage(context.getSender(),
                    plugin.getI18N().getProperty("PromptPlayerOnly"));
            return;
        }

        if(context.getCancellable() != null)
            context.getCancellable().setCancelled(true);
            
        plugin.getPluginLogger().debug("Ctx Before Parse: " + context);
        promptManager.parse(context);
        promptManager.sendPrompt(context.getSender());
    }

    private boolean isIgnored(PromptContext context) {
        var end = context.getContent().indexOf(" ");
        end = end == -1 ? context.getContent().length() : end;
        var cmd = context.getContent().substring(0, end);
        return plugin.getConfiguration().ignoredCommands().contains(cmd) || isCmdChatChannel(cmd);
    }

    private boolean isCmdChatChannel(String cmd) {
        var out = new AtomicBoolean(false);
        var vcHook = plugin.getHookContainer().getHook(VentureChatHook.class);
        plugin.getPluginLogger().debug("VentureChat hooked: " + vcHook.isHooked());
        vcHook.ifHooked(hook -> out.set(hook.isChatChannel(cmd))).complete();
        plugin.getPluginLogger().debug("is VentureChat channel: " + out.get());
        return out.get();
    }

    private void parsePermissionAttachment(PromptContext context) {
        var matcher = permissionAttachmentPattern.matcher(context.getContent());
        if(matcher.find()) {
            context.setContent(matcher.replaceAll(""));
            context.setSetPermissionAttachment(true);
            plugin.getPluginLogger().debug("Using PermissionAttachment for command dispatch");
        }
    }
}
