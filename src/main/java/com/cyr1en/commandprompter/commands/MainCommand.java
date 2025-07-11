package com.cyr1en.commandprompter.commands;

import com.cyr1en.commandprompter.CommandPrompter;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.regex.Pattern;

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
                .withSubcommand(new Cancel(plugin))
                .withSubcommand(new RebuildHeadCache(plugin));

        if (!plugin.getConfiguration().showCompleted())
            command.setArguments(Collections.emptyList());

        command.register();
    }

    public static class Reload extends CommandAPICommand {

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

    public static class Cancel extends CommandAPICommand {

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

    public static class RebuildHeadCache extends CommandAPICommand {

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
