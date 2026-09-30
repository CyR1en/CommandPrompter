package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.geyser.StandaloneJarPatcher;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Produces a standalone proxy JAR from a file in the plugin's compat directory. */
public final class PatchCommand extends PromptCommand {
  public PatchCommand(CommandPrompter plugin) {
    super(plugin, "patch", "promptpaper.patch", null, "Patch a Geyser-Standalone JAR", List.of());
  }

  @Override
  public LiteralCommandNode<CommandSourceStack> build() {
    return Commands.literal(name())
        .requires(source -> allowed(source.getSender()))
        .executes(
            context -> {
              var sender = context.getSource().getSender();
              reply(sender, senderLocation(sender), "usage");
              return 0;
            })
        .then(
            Commands.argument("jar", StringArgumentType.greedyString())
                .suggests((context, builder) -> suggest(context.getSource().getSender(), builder))
                .executes(
                    context ->
                        execute(
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "jar"))))
        .build();
  }

  CompletableFuture<Suggestions> suggest(CommandSender sender, SuggestionsBuilder builder) {
    if (!allowed(sender)) return builder.buildFuture();
    var future = new CompletableFuture<Suggestions>();
    try {
      plugin
          .getScheduler()
          .runAsync(
              () -> {
                try {
                  plugin
                      .getGeyserStandaloneSupport()
                      .patcher()
                      .suggestions(builder.getRemaining())
                      .forEach(builder::suggest);
                  future.complete(builder.build());
                } catch (Exception failure) {
                  future.complete(Suggestions.empty().join());
                }
              });
    } catch (Exception failure) {
      future.complete(Suggestions.empty().join());
    }
    return future;
  }

  int execute(CommandSender sender, String jar) {
    Location location = senderLocation(sender);
    if (!allowed(sender)) {
      reply(sender, location, "denied");
      return 0;
    }
    // Patch targets must end in .jar, so a filename genuinely ending in --force cannot exist.
    String argument = jar.strip();
    boolean force = argument.endsWith("--force");
    String target =
        force
            ? argument.substring(0, argument.length() - "--force".length()).stripTrailing()
            : argument;
    if (target.isEmpty()) {
      reply(sender, location, "usage");
      return 0;
    }
    reply(sender, location, "started", value("file", target));
    try {
      plugin
          .getScheduler()
          .runAsync(
              () -> {
                try {
                  var support = plugin.getGeyserStandaloneSupport();
                  var output = support.patcher().patch(target, support.assets(), force);
                  reply(sender, location, "success", value("file", output.getFileName()));
                  reply(sender, location, "setup");
                } catch (StandaloneJarPatcher.Failure failure) {
                  Placeholder[] placeholders =
                      failure.details().entrySet().stream()
                          .map(entry -> value(entry.getKey(), entry.getValue()))
                          .toArray(Placeholder[]::new);
                  reply(sender, location, "error." + failure.reason(), placeholders);
                  if (failure.getCause() != null)
                    plugin.getLogger().log(Level.WARNING, "Standalone patch failed", failure);
                } catch (Exception failure) {
                  plugin.getLogger().log(Level.WARNING, "Standalone patch failed", failure);
                  reply(sender, location, "error.unexpected");
                }
              });
    } catch (Exception failure) {
      plugin.getLogger().log(Level.WARNING, "Could not schedule standalone patch", failure);
      reply(sender, location, "error.unexpected");
      return 0;
    }
    return 1;
  }

  private static Placeholder value(String name, Object value) {
    return Placeholder.of(name, MiniMessage.miniMessage().escapeTags(String.valueOf(value)));
  }

  private static Location senderLocation(CommandSender sender) {
    return sender instanceof BlockCommandSender block
        ? block.getBlock().getLocation().clone()
        : null;
  }

  private void reply(
      CommandSender sender, Location location, String key, Placeholder... placeholders) {
    Runnable send =
        () ->
            sender.sendMessage(
                plugin
                    .getConfigLoader()
                    .getI18n()
                    .get(
                        "command.patch." + key,
                        sender instanceof Player player ? player : null,
                        placeholders));
    try {
      if (sender instanceof Player player)
        player.getScheduler().run(plugin, task -> send.run(), () -> {});
      else if (location != null)
        Bukkit.getRegionScheduler().run(plugin, location, task -> send.run());
      else plugin.getScheduler().runSync(send);
    } catch (Exception failure) {
      plugin.getLogger().log(Level.FINE, "Patch command sender unavailable", failure);
    }
  }
}
