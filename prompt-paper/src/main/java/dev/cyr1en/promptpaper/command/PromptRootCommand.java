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
 * {@code /commandprompter} — composite root command. Owns three leaf
 * subcommands ({@code reload}, {@code cancel}, {@code version}) and prints
 * help text when invoked with no arguments. The root itself has no
 * permission gate; each subcommand applies its own permission inside its
 * own {@link #build()} method, so a non-admin can run individual
 * subcommands they have permission for.
 */
public class PromptRootCommand extends PromptCommand implements Command<CommandSourceStack> {

    private final List<PromptCommand> children;

    public PromptRootCommand(CommandPrompter plugin) {
        super(plugin, "commandprompter", null, null,
                "Main CommandPrompter command", List.of("cmdp"));
        this.children = List.of(
                new ReloadCommand(plugin),
                new CancelCommand(plugin),
                new VersionCommand(plugin));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        var literal = Commands.literal(name())
                .requires(src -> allowed(src.getSender()))
                .executes(this);
        for (var child : children) {
            literal.then(child.build());
        }
        return literal.build();
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        sendHelp(context.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Sends the help text. Extracted from the Brigadier executor for
     * direct unit testing with a mock {@link CommandSender}.
     */
    public void sendHelp(CommandSender sender) {
        var msg = """
                <gold>CommandPrompterPaper</gold> <gray>-</gray> \
                <white>Making Commands More Interactive!</white>
                <gold>/commandprompter cancel</gold> <gray>- Cancel your active prompt</gray>
                <gold>/commandprompter reload</gold> <gray>- Reload configuration</gray>
                <gold>/commandprompter version</gold> <gray>- Show plugin version</gray>""";
        sender.sendMessage(ComponentUtil.mini(msg));
    }
}
