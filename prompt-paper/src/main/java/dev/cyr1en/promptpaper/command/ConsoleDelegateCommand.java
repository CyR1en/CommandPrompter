package dev.cyr1en.promptpaper.command;

import static io.papermc.paper.command.brigadier.Commands.argument;

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
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /consoledelegate <target> <command>} — runs a prompted command on behalf of each resolved
 * target player, but the dispatcher is the console. The {@code %target_player%} placeholder is
 * resolved on the target's region thread before the session is started.
 *
 * <p>Restricted to console senders with {@code promptpaper.consoledelegate}. The target is parsed
 * via {@link ArgumentTypes#player()} which gives us a {@link PlayerSelectorArgumentResolver} that
 * supports {@code @a} / {@code @p} selectors in addition to plain names.
 */
public class ConsoleDelegateCommand extends PromptCommand implements Command<CommandSourceStack> {

  public ConsoleDelegateCommand(CommandPrompter plugin) {
    super(
        plugin,
        "consoledelegate",
        "promptpaper.consoledelegate",
        ConsoleCommandSender.class,
        "Execute commands as console prompted",
        List.of("cd"));
  }

  @Override
  public LiteralCommandNode<CommandSourceStack> build() {
    return Commands.literal(name())
        .requires(src -> allowed(src.getSender()))
        .then(
            argument("target", ArgumentTypes.player())
                .then(argument("command", StringArgumentType.greedyString()).executes(this)))
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
    var command = StringArgumentType.getString(context, "command");
    for (var target : targets) {
      startSession(sender.getName(), target, command);
    }
    return Command.SINGLE_SUCCESS;
  }

  /**
   * Normalizes and dispatches the delegated command. Extracted from the Brigadier executor so it
   * can be unit-tested without constructing a {@link CommandContext}.
   *
   * <p>Normalization: a leading {@code /} is stripped. Target-dependent normalization is completed
   * by {@link ScreenManager} on the target's region thread.
   */
  public void startSession(String senderName, Player target, String command) {
    if (command.startsWith("/")) command = command.substring(1);
    plugin.getPluginLogger().info(senderName + " used /consoledelegate");
    plugin
        .getScreenManager()
        .startDelegatedSession(target, command, ScreenManager.DispatchMode.CONSOLE, null);
  }
}
