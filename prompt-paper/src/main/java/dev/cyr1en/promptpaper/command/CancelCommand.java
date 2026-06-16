package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptpaper.CommandPrompter;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /commandprompter cancel} — cancels the executing player's own
 * active prompt session. Console senders get an error message; players
 * with no active session get a polite notice.
 */
public class CancelCommand extends PromptCommand implements Command<CommandSourceStack> {

    public CancelCommand(CommandPrompter plugin) {
        super(plugin, "cancel", "promptpaper.cancel", null, "Cancel your active prompt", List.of());
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
        executeCancel(context.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Cancels the sender's active prompt, when the sender is a player with
     * a live session. Non-player senders and players without a session get
     * a polite message. Extracted for direct unit testing.
     */
    public void executeCancel(CommandSender sender) {
        var i18n = plugin.getConfigLoader().getI18n();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(i18n.get("command.error.players_only"));
            return;
        }
        if (!plugin.getEngine().hasActiveSession(player)) {
            player.sendMessage(i18n.get("command.cancel.no_active_prompt"));
            return;
        }
        plugin.getEngine().cancel(player, CancelReason.MANUAL);
        player.sendMessage(i18n.get("prompt.cancelled"));
    }
}
