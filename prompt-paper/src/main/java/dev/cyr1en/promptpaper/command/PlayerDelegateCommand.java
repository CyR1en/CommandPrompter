package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.argument;

/**
 * {@code /playerdelegate <target> <permissionKey> <command>} — starts a
 * prompted command session as the target player with a temporary permission
 * attachment resolved from {@code permissionKey} in the plugin config. The
 * {@code %target_player%} placeholder is resolved on the target's region
 * thread.
 *
 * <p>Restricted to console senders with {@code promptpaper.playerdelegate}.
 * The {@code permissionKey} argument is tab-completed from the configured
 * permission keys.
 */
public class PlayerDelegateCommand extends PromptCommand implements Command<CommandSourceStack> {

    public PlayerDelegateCommand(CommandPrompter plugin) {
        super(plugin, "playerdelegate", "promptpaper.playerdelegate",
                ConsoleCommandSender.class,
                "Execute commands as player with permissions", List.of("pd"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(name())
                .requires(src -> allowed(src.getSender()))
                .then(argument("target", ArgumentTypes.player())
                        .then(argument("permissionKey", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    var config = plugin.getConfigLoader().getConfig();
                                    for (var key : config.getPermissionKeys()) {
                                        builder.suggest(key);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(argument("command", StringArgumentType.greedyString())
                                        .executes(this))))
                .build();
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var resolver = context.getArgument("target", PlayerSelectorArgumentResolver.class);
        var targets = resolver.resolve(context.getSource());
        var sender = context.getSource().getSender();
        if (targets == null || targets.isEmpty()) {
            sender.sendMessage(Component.text("No players matched the selector."));
            return 0;
        }
        var permKey = StringArgumentType.getString(context, "permissionKey");
        var command = StringArgumentType.getString(context, "command");
        for (var target : targets) {
            executeDispatch(sender, target, permKey, command);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Validates the permission key, normalizes the command, and starts the
     * delegated session. Extracted from the Brigadier executor for direct
     * unit testing. Returns {@link Command#SINGLE_SUCCESS} on success or on
     * the unknown-permission-key error path.
     */
    public int executeDispatch(CommandSender sender, Player target, String permKey, String command) {
        if (command.startsWith("/")) command = command.substring(1);
        var config = plugin.getConfigLoader().getConfig();
        var perms = config == null || permKey == null || permKey.isBlank()
                ? null
                : config.getPermissionAttachment(permKey);
        if (perms == null || perms.length == 0) {
            var senderPlayer = sender instanceof Player p ? p : null;
            sender.sendMessage(plugin.getConfigLoader().getI18n().get(
                    "command.delegate.unknown_permission",
                    senderPlayer,
                    Placeholder.of("key", permKey)));
            return Command.SINGLE_SUCCESS;
        }
        plugin.getPluginLogger().info(sender.getName()
                + " used /playerdelegate permKey=" + permKey + ": " + command);
        plugin.getScreenManager().startDelegatedSession(target, command,
                ScreenManager.DispatchMode.ATTACHMENT, permKey);
        return Command.SINGLE_SUCCESS;
    }
}
