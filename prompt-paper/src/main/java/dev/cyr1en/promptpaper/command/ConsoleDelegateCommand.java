package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.argument;

/**
 * {@code /consoledelegate <target> <command>} — runs a prompted command on
 * behalf of the named target player, but the dispatcher is the console. The
 * {@code %target_player%} placeholder in the command string is replaced
 * with the target player's name before the session is started.
 *
 * <p>Restricted to console senders with {@code promptpaper.consoledelegate}.
 * The target is parsed via {@link ArgumentTypes#player()} which gives us
 * a {@link PlayerSelectorArgumentResolver} that supports {@code @a} / {@code @p}
 * selectors in addition to plain names.
 */
public class ConsoleDelegateCommand extends PromptCommand implements Command<CommandSourceStack> {

    public ConsoleDelegateCommand(CommandPrompter plugin) {
        super(plugin, "consoledelegate", "promptpaper.consoledelegate",
                ConsoleCommandSender.class, "Execute commands as console prompted",
                List.of("cd"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(name())
                .requires(src -> allowed(src.getSender()))
                .then(argument("target", ArgumentTypes.player())
                        .then(argument("command", StringArgumentType.greedyString())
                                .executes(this)))
                .build();
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var resolver = context.getArgument("target", PlayerSelectorArgumentResolver.class);
        var target = resolver.resolve(context.getSource()).getFirst();
        var command = StringArgumentType.getString(context, "command");
        startSession(context.getSource().getSender().getName(), target, command);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Normalizes and dispatches the delegated command. Extracted from the
     * Brigadier executor so it can be unit-tested without constructing a
     * {@link CommandContext}.
     *
     * <p>Normalization: a leading {@code /} is stripped (Bukkit would
     * strip it on dispatch anyway, but keeping the slash out of session
     * state keeps logs and parsed-template output consistent), and
     * {@code %target_player%} is replaced with the target player's name.
     */
    public void startSession(String senderName, Player target, String command) {
        if (plugin.getEngine().hasActiveSession(target)) {
            plugin.getPluginLogger().warn("Cannot delegate prompt to " + target.getName() + " because they are already in an active prompt session.");
            return;
        }
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.contains("%target_player%")) {
            command = command.replace("%target_player%", target.getName());
        }
        plugin.getPluginLogger().info(senderName
                + " used /consoledelegate -> " + target.getName() + ": " + command);
        plugin.getPluginLogger().debug("ConsoleDelegate: target=" + target.getName()
                + " uuid=" + target.getUniqueId() + " mode=CONSOLE");
        plugin.getScreenManager().startDelegatedSession(target, command,
                ScreenManager.DispatchMode.CONSOLE, null);
    }
}
