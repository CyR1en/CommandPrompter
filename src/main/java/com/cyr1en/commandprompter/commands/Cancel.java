package com.cyr1en.commandprompter.commands;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.kiso.mc.command.AbstractCommand;
import com.cyr1en.kiso.mc.command.CommandMessenger;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.regex.Pattern;

public class Cancel extends AbstractCommand {

    public static final Pattern commandPattern =
            Pattern.compile("(cp|commandprompter|cmdprompter|cmdprmptr|cmdp) cancel");

    private final CommandPrompter commandPrompter;

    public Cancel(JavaPlugin plugin, CommandMessenger messenger) {
        super(plugin, messenger);
        commandPrompter = (CommandPrompter) plugin;
        this.commandName = "cancel";
        this.alias = new String[]{"c"};
        this.permission = "commandprompter.cancel";
    }

    @Override
    public void doCommand(CommandSender commandSender, String[] strings) {
        var inCommand = commandPrompter.getPromptManager()
                .getPromptRegistry().inCommandProcess(commandSender);
        if (!inCommand) {
            var msg = commandPrompter.getI18N().getProperty("CommandCancelNotInCompletion");
            commandPrompter.getMessenger().sendMessage(commandSender, msg);
            return;
        }

        commandPrompter.getPromptManager().cancel(commandSender);
    }
}
