package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.CommandPrompter;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /commandprompter version} — sends the running plugin version to
 * the command sender and logs a debug breadcrumb.
 */
public class VersionCommand extends PromptCommand implements Command<CommandSourceStack> {

    public VersionCommand(CommandPrompter plugin) {
        super(plugin, "version", "promptpaper.version", null,
                "Show plugin version", List.of());
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
        sendVersion(context.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Sends the running plugin version to the given sender. Extracted
     * from the Brigadier executor so it can be unit-tested with a mock
     * {@link CommandSender}.
     */
    public void sendVersion(CommandSender sender) {
        var version = plugin.getPluginMeta().getVersion();
        var player = sender instanceof Player p ? p : null;
        sender.sendMessage(plugin.getConfigLoader().getI18n().get(
                "command.version",
                player,
                Placeholder.of("version", version)));
        plugin.getPluginLogger().debug("Version check by " + sender.getName());
    }
}
