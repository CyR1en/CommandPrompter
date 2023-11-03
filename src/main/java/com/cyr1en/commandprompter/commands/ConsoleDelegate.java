package com.cyr1en.commandprompter.commands;

import java.util.Arrays;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.PluginMessenger;
import com.cyr1en.commandprompter.prompt.ContextProcessor;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.kiso.mc.I18N;

public class ConsoleDelegate extends ContextProcessor implements CommandExecutor {

    private final CommandPrompter commandPrompter;
    private final PluginLogger logger;
    private final PluginMessenger messenger;
    private final I18N i18n;
    private final String usage;

    public ConsoleDelegate(JavaPlugin plugin) {
        super((CommandPrompter) plugin, ((CommandPrompter) plugin).getPromptManager());
        this.commandPrompter = (CommandPrompter) plugin;
        this.logger = commandPrompter.getPluginLogger();
        this.messenger = commandPrompter.getMessenger();
        this.i18n = commandPrompter.getI18N();
        this.usage = "consoledelegate <target player> <command...>";
    }

    private void doCommand(Player targetPlayer, String command) {
        var context = new PromptContext(null, targetPlayer, command);
        context.setIsConsoleDelegate(true);
        this.process(context);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        logger.debug("Command: " + command);
        logger.debug("Label: " + label);
        logger.debug("Args: " + Arrays.toString(args));

        if (!(sender instanceof ConsoleCommandSender)) {
            messenger.sendMessage(sender, i18n.getProperty("DelegateConsoleOnly"));
            return true;
        }

        if (args.length < 2) {
            messenger.sendMessage(sender, i18n.getProperty("DelegateInvalidArgs"));
            messenger.sendMessage(sender, i18n.getFormattedProperty("DelegateUsage", this.usage));
            return true;
        }

        var arg0 = args[0];
        var targetPlayer = commandPrompter.getServer().getPlayer(arg0);

        if (targetPlayer == null) {
            messenger.sendMessage(sender, i18n.getFormattedProperty("DelegateInvalidPlayer", arg0));
            return true;
        }

        var delegatedCommand = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (delegatedCommand.isEmpty()) {
            messenger.sendMessage(sender, i18n.getProperty("DelegateInvalidCommand"));
            return true;
        }

        if (delegatedCommand.contains("%target_player%"))
            delegatedCommand = delegatedCommand.replace("%target_player%", targetPlayer.getName());

        doCommand(targetPlayer, delegatedCommand);
        return true;
    }

}