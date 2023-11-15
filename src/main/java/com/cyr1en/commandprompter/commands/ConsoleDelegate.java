package com.cyr1en.commandprompter.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.cyr1en.commandprompter.CommandPrompter;
//import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.PluginMessenger;
import com.cyr1en.commandprompter.prompt.ContextProcessor;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.kiso.mc.I18N;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.PlayerArgument;
import dev.jorel.commandapi.executors.CommandArguments;

public class ConsoleDelegate extends ContextProcessor {

    private final CommandPrompter commandPrompter;
    //private final PluginLogger logger;
    private final PluginMessenger messenger;
    private final I18N i18n;

    public ConsoleDelegate(JavaPlugin plugin) {
        super((CommandPrompter) plugin, ((CommandPrompter) plugin).getPromptManager());
        this.commandPrompter = (CommandPrompter) plugin;
        //this.logger = commandPrompter.getPluginLogger();
        this.messenger = commandPrompter.getMessenger();
        this.i18n = commandPrompter.getI18N();
    }

    private void doCommand(Player targetPlayer, String command) {
        var context = new PromptContext(null, targetPlayer, command);
        context.setIsConsoleDelegate(true);
        this.process(context);
    }

    public void register() {
        new CommandAPICommand("consoledelegate")
            .withPermission("commandprompter.consoledelegate")
            .withArguments(new PlayerArgument("target"))
            .withArguments(new GreedyStringArgument("command"))
            .executes(this::exec)
            .register();
            
    }

    private void exec(CommandSender sender, CommandArguments args) {
         if (!(sender instanceof ConsoleCommandSender)) {
            messenger.sendMessage(sender, i18n.getProperty("DelegateConsoleOnly"));
            return;
        }

        var arg0 = args.getRaw("target");
        var targetPlayer = commandPrompter.getServer().getPlayer(arg0);

        if (targetPlayer == null) {
            messenger.sendMessage(sender, i18n.getFormattedProperty("DelegateInvalidPlayer", arg0));
            return;
        }
 
        var delegatedCommand = args.getRaw("command");

        if (delegatedCommand.isEmpty()) {
            messenger.sendMessage(sender, i18n.getProperty("DelegateInvalidCommand"));
            return;
        }

        if (delegatedCommand.contains("%target_player%"))
            delegatedCommand = delegatedCommand.replace("%target_player%", targetPlayer.getName());

        doCommand(targetPlayer, delegatedCommand);
    }

}