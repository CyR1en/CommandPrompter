package com.cyr1en.commandprompter.commands;

import java.util.Collections;
import java.util.regex.Pattern;

import org.bukkit.command.CommandSender;

import com.cyr1en.commandprompter.CommandPrompter;
//import com.cyr1en.commandprompter.PluginLogger;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.CommandArguments;

public class MainCommand {
    private final CommandPrompter plugin;
    // private final PluginLogger logger;

    public MainCommand(CommandPrompter plugin) {
        this.plugin = plugin;
        // this.logger = plugin.getPluginLogger();
    }

    public void register() {
        var command = new CommandAPICommand("commandprompter")
                .withAliases("cmdp")
                .withSubcommand(new Reload(plugin))
                .withSubcommand(new Cancel(plugin));

        if (!plugin.getConfiguration().showCompleted())
            command.setArguments(Collections.emptyList());

        command.register();
    }

    public class Reload extends CommandAPICommand {

        private final CommandPrompter plugin;

        public Reload(CommandPrompter plugin) {
            super("reload");
            this.plugin = plugin;
            withPermission("commandprompter.reload");
            executes(this::exec);
        }

        public void exec(CommandSender sender, CommandArguments args) {
            plugin.reload(true);
            String message = plugin.getI18N().getProperty("CommandReloadSuccess");
            plugin.getMessenger().sendMessage(sender, message);
        }

    }

    public class Cancel extends CommandAPICommand {

        public static final Pattern commandPattern = Pattern.compile("(commandprompter|cmdp) cancel");

        private final CommandPrompter plugin;

        public Cancel(CommandPrompter plugin) {
            super("cancel");
            this.plugin = plugin;
            withPermission("commandprompter.cancel");
            executes(this::exec);
        }

        public void exec(CommandSender sender, CommandArguments args) {
            var inCommand = plugin.getPromptManager()
                    .getPromptRegistry().inCommandProcess(sender);
            if (!inCommand) {
                var msg = plugin.getI18N().getProperty("CommandCancelNotInCompletion");
                plugin.getMessenger().sendMessage(sender, msg);
                return;
            }

            plugin.getPromptManager().cancel(sender);
        }

    }

    public class RebuildHeadCache extends CommandAPICommand {

        private final CommandPrompter plugin;

        public RebuildHeadCache(CommandPrompter plugin) {
            super("rebuildheadcache");
            this.plugin = plugin;
            withAliases("rhc");
            withPermission("commandprompter.rebuildheadcache");
            executes(this::exec);
        }

        public void exec(CommandSender sender, CommandArguments args) {
            var currMillis = System.currentTimeMillis();
            plugin.getHeadCache().reBuildCache().thenAccept(c -> {
                var timeTaken = System.currentTimeMillis() - currMillis;
                var msg = plugin.getI18N().getFormattedProperty("CommandRebuildHeadCacheSuccess", timeTaken + "");
                plugin.getMessenger().sendMessage(sender, msg);
                plugin.getPluginLogger().debug("Rebuilt head cache in " + timeTaken + "ms");
            });
        }

    }

}
