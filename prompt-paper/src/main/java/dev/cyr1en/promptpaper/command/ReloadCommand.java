package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptui.ComponentUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /commandprompter reload} — cancels every active session across
 * every online player, then reloads the configuration from disk. The cancel
 * pass prevents stale sessions from holding references to the previous
 * config values.
 */
public class ReloadCommand extends PromptCommand implements Command<CommandSourceStack> {

    public ReloadCommand(CommandPrompter plugin) {
        super(plugin, "reload", "promptpaper.reload", null,
                "Reload configuration", List.of());
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(name())
                .requires(src -> allowed(src.getSender()))
                .executes(this)
                .build();
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        executeReload(context.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Cancels all active sessions and reloads the configuration from disk.
     * Extracted from the Brigadier executor so it can be unit-tested
     * directly with a mock {@link CommandSender}.
     */
    public void executeReload(CommandSender sender) {
        plugin.getPluginLogger().info("Configuration reloaded by " + sender.getName());
        // Cancel all active sessions before reloading config — prevents stale sessions
        // with references to old config values (ROADMAP: "old sessions are cancelled cleanly")
        plugin.getPluginLogger().debug("Reload: cancelling all active sessions before config reload");
        if (plugin.getScreenManager() != null && plugin.getEngine() != null) {
            for (var player : plugin.getServer().getOnlinePlayers()) {
                plugin.getScreenManager().cancelAll(player);
            }
            plugin.getEngine().cancelAll();
        }
        try {
            plugin.getConfigLoader().reload();
            var loader = plugin.getConfigLoader();
            var cfg = loader.getConfig();
            if (cfg != null) {
                plugin.getPluginLogger().reload(cfg);
            }
            sender.sendMessage(ComponentUtil.mini("<green>Configuration reloaded.</green>"));
        } catch (Exception e) {
            sender.sendMessage(ComponentUtil.mini("<red>Failed to reload: " + e.getMessage() + "</red>"));
        }
    }
}
