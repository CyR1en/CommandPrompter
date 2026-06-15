package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptui.ComponentUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.argument;

/**
 * {@code /playerdelegate <target> <permissionKey> <command>} — starts a
 * prompted command session as the target player with a temporary permission
 * attachment resolved from {@code permissionKey} in the plugin config. The
 * {@code %target_player%} placeholder in the command string is replaced
 * with the target player's name.
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
        var target = resolver.resolve(context.getSource()).getFirst();
        var permKey = StringArgumentType.getString(context, "permissionKey");
        var command = StringArgumentType.getString(context, "command");
        return executeDispatch(context.getSource().getSender(), target, permKey, command);
    }

    /**
     * Validates the permission key, normalizes the command, and starts the
     * delegated session. Extracted from the Brigadier executor for direct
     * unit testing. Returns {@link Command#SINGLE_SUCCESS} on success or on
     * the unknown-permission-key error path.
     */
    public int executeDispatch(CommandSender sender, Player target, String permKey, String command) {
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.contains("%target_player%")) {
            command = command.replace("%target_player%", target.getName());
        }
        var config = plugin.getConfigLoader().getConfig();
        var perms = config.getPermissionAttachment(permKey);
        if (perms.length == 0) {
            sender.sendMessage(
                    ComponentUtil.mini("<red>Unknown permission key: " + permKey + "</red>"));
            return Command.SINGLE_SUCCESS;
        }
        plugin.getPluginLogger().info(sender.getName()
                + " used /playerdelegate -> " + target.getName()
                + " permKey=" + permKey + ": " + command);
        plugin.getPluginLogger().debug("PlayerDelegate: target=" + target.getName()
                + " uuid=" + target.getUniqueId()
                + " permKey=" + permKey + " perms=" + perms.length
                + " mode=ATTACHMENT");
        plugin.getScreenManager().startDelegatedSession(target, command,
                ScreenManager.DispatchMode.ATTACHMENT, permKey);
        return Command.SINGLE_SUCCESS;
    }
}
