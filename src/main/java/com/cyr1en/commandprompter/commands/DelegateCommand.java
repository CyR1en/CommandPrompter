package com.cyr1en.commandprompter.commands;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.PluginMessenger;
import com.cyr1en.commandprompter.prompt.ContextProcessor;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.kiso.mc.I18N;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.PlayerArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class DelegateCommand extends ContextProcessor {

    protected final CommandPrompter plugin;
    private final PluginLogger logger;
    private final PluginMessenger messenger;
    private final I18N i18n;


    private DelegateCommand(JavaPlugin plugin) {
        super((CommandPrompter) plugin, ((CommandPrompter) plugin).getPromptManager());
        this.plugin = (CommandPrompter) plugin;
        this.logger = this.plugin.getPluginLogger();
        this.messenger = this.plugin.getMessenger();
        this.i18n = this.plugin.getI18N();
    }

    public abstract void register();

    public abstract void doCommand(Player targetPlayer, String command, CommandArguments args);

    protected void exec(CommandSender sender, CommandArguments args) {
        logger.debug("Command Arguments: " + args.fullInput());

        if (!(sender instanceof ConsoleCommandSender)) {
            messenger.sendMessage(sender, i18n.getProperty("DelegateConsoleOnly"));
            return;
        }

        var arg0 = args.getRaw("target");
        if (arg0 == null || arg0.isEmpty()) return;

        var targetPlayer = plugin.getServer().getPlayer(arg0);

        if (targetPlayer == null) {
            messenger.sendMessage(sender, i18n.getFormattedProperty("DelegateInvalidPlayer", arg0));
            return;
        }

        var delegatedCommand = args.getRaw("command");

        if (delegatedCommand == null || delegatedCommand.isEmpty()) {
            messenger.sendMessage(sender, i18n.getProperty("DelegateInvalidCommand"));
            return;
        }

        if (delegatedCommand.contains("%target_player%"))
            delegatedCommand = delegatedCommand.replace("%target_player%", targetPlayer.getName());

        doCommand(targetPlayer, delegatedCommand, args);
    }

    public static class ConsoleDelegate extends DelegateCommand {

        ConsoleDelegate(JavaPlugin plugin) {
            super(plugin);
        }

        public void register() {
            new CommandAPICommand("consoledelegate")
                    .withPermission("commandprompter.consoledelegate")
                    .withArguments(new PlayerArgument("target"))
                    .withArguments(new GreedyStringArgument("command"))
                    .executesConsole(this::exec)
                    .register();
        }

        public void doCommand(Player targetPlayer, String command, CommandArguments args) {
            var context = new PromptContext.Builder()
                    .setSender(targetPlayer)
                    .setContent(command)
                    .setConsoleDelegate(true)
                    .build();
            this.process(context);
        }
    }

    public static class PlayerDelegate extends DelegateCommand {


        PlayerDelegate(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void register() {
            var keys = plugin.getConfiguration().getPermissionKeys();
            ArgumentSuggestions<CommandSender> suggestions = ArgumentSuggestions.strings(keys);
            var permissionArg = new StringArgument("permission")
                    .replaceSuggestions(suggestions);

            new CommandAPICommand("playerdelegate")
                    .withPermission("commandprompter.playerdelegate")
                    .withArguments(new PlayerArgument("target"))
                    .withArguments(permissionArg)
                    .withArguments(new GreedyStringArgument("command"))
                    .executesConsole(this::exec)
                    .register();
        }

        @Override
        public void doCommand(Player targetPlayer, String command, CommandArguments args) {
            var context = new PromptContext.Builder()
                    .setSender(targetPlayer)
                    .setContent(command)
                    .setPaKey(args.getRaw("permission"))
                    .build();
            this.process(context);
        }
    }

}
