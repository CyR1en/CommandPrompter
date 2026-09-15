package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptcore.parser.LegacyPromptMigrator;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.util.MiniMessageTagFilter;
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

/** Migrates a single configuration file, with paths relative to the plugins directory. */
public final class MigrateCommand extends PromptCommand {
  public MigrateCommand(CommandPrompter plugin) {
    super(plugin, "migrate", "promptpaper.migrate", null, "Migrate V2 prompt syntax", List.of());
  }

  @Override
  public LiteralCommandNode<CommandSourceStack> build() {
    return Commands.literal(name())
        .requires(src -> allowed(src.getSender()))
        .executes(ctx -> usage(ctx.getSource().getSender()))
        .then(pathArgument(false))
        .then(
            Commands.literal("undo")
                .executes(ctx -> usage(ctx.getSource().getSender(), "undo.usage"))
                .then(
                    Commands.argument("backup", StringArgumentType.greedyString())
                        .suggests(
                            (ctx, builder) -> suggest(ctx.getSource().getSender(), builder, true))
                        .executes(
                            ctx ->
                                undo(
                                    ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "backup")))))
        .then(
            Commands.literal("--dry-run")
                .executes(ctx -> usage(ctx.getSource().getSender()))
                .then(pathArgument(true)))
        .build();
  }

  private RequiredArgumentBuilder<CommandSourceStack, String> pathArgument(boolean dryRun) {
    return Commands.argument("path", StringArgumentType.greedyString())
        .suggests((ctx, builder) -> suggest(ctx.getSource().getSender(), builder))
        .executes(
            ctx ->
                execute(
                    ctx.getSource().getSender(),
                    StringArgumentType.getString(ctx, "path"),
                    dryRun));
  }

  private int usage(CommandSender sender) {
    return usage(sender, "usage");
  }

  private int usage(CommandSender sender, String key) {
    sender.sendMessage(
        plugin
            .getConfigLoader()
            .getI18n()
            .get("command.migrate." + key, sender instanceof Player p ? p : null));
    return 0;
  }

  CompletableFuture<Suggestions> suggest(CommandSender sender, SuggestionsBuilder builder) {
    return suggest(sender, builder, false);
  }

  CompletableFuture<Suggestions> suggest(
      CommandSender sender, SuggestionsBuilder builder, boolean undo) {
    if (!allowed(sender)) return builder.buildFuture();
    var future = new CompletableFuture<Suggestions>();
    var service = new MigrationService(plugin.getDataFolder().toPath());
    try {
      plugin
          .getScheduler()
          .runAsync(
              () -> {
                try {
                  service.suggestions(builder.getRemaining(), undo).forEach(builder::suggest);
                  future.complete(builder.build());
                } catch (Exception e) {
                  future.complete(Suggestions.empty().join());
                }
              });
    } catch (Exception e) {
      future.complete(Suggestions.empty().join());
    }
    return future;
  }

  int undo(CommandSender sender, String path) {
    return execute(sender, path, false, true);
  }

  int execute(CommandSender sender, String path, boolean dryRun) {
    return execute(sender, path, dryRun, false);
  }

  private int execute(CommandSender sender, String path, boolean dryRun, boolean undo) {
    Location location =
        sender instanceof BlockCommandSender block ? block.getBlock().getLocation().clone() : null;
    if (!allowed(sender)) {
      reply(sender, location, "denied");
      return 0;
    }
    var config = plugin.getConfigLoader().getConfig();
    var converter =
        new LegacyPromptMigrator(
            config.parserConfig(), new MiniMessageTagFilter(), config.templateSyntax());
    var service = new MigrationService(plugin.getDataFolder().toPath());
    reply(sender, location, undo ? "undo.started" : "started", value("path", path));
    try {
      plugin
          .getScheduler()
          .runAsync(
              () -> {
                try {
                  if (undo) {
                    var restored = service.undo(path);
                    reply(
                        sender,
                        location,
                        restored.backup() == null ? "undo.nothing" : "undo.success",
                        value("path", restored.file()));
                    if (restored.backup() != null) {
                      reply(sender, location, "backup", value("path", restored.backup()));
                      reply(
                          sender,
                          location,
                          "undo.hint",
                          value("path", service.backupArgument(restored.backup())));
                      reply(sender, location, "reload");
                    }
                    return;
                  }
                  var outcome = service.migrate(path, dryRun, converter);
                  var result = outcome.result();
                  if (!result.diagnostics().isEmpty()) {
                    reply(sender, location, "manual", value("count", result.diagnostics().size()));
                    result.diagnostics().stream()
                        .limit(20)
                        .forEach(
                            issue ->
                                reply(
                                    sender,
                                    location,
                                    "issue." + issue.reason(),
                                    value("line", issue.line())));
                    return;
                  }
                  if (result.changes().isEmpty()) {
                    reply(sender, location, "nothing", value("path", path));
                    return;
                  }
                  if (dryRun) {
                    reply(
                        sender,
                        location,
                        "preview",
                        value("count", result.changes().size()),
                        value("path", path));
                    result.changes().stream()
                        .limit(20)
                        .forEach(
                            change ->
                                reply(
                                    sender,
                                    location,
                                    "change",
                                    value("line", change.line()),
                                    value("before", change.before()),
                                    value("after", change.after())));
                  } else {
                    reply(
                        sender,
                        location,
                        "success",
                        value("count", result.changes().size()),
                        value("path", path));
                    reply(sender, location, "backup", value("path", outcome.backup()));
                    reply(
                        sender,
                        location,
                        "undo.hint",
                        value("path", service.backupArgument(outcome.backup())));
                    reply(sender, location, "reload");
                  }
                } catch (MigrationService.Failure e) {
                  if (e.getCause() != null)
                    plugin
                        .getLogger()
                        .log(Level.WARNING, "Prompt migration failed: " + e.reason(), e);
                  reply(
                      sender,
                      location,
                      undo && e.reason().equals("invalid_path")
                          ? "undo.invalid_path"
                          : "error." + e.reason());
                  if (e.backup() != null)
                    reply(sender, location, "backup", value("path", e.backup()));
                } catch (Exception e) {
                  plugin.getLogger().log(Level.SEVERE, "Prompt migration failed", e);
                  reply(sender, location, "error.unexpected");
                }
              });
    } catch (Exception e) {
      plugin.getLogger().log(Level.WARNING, "Unable to schedule prompt migration", e);
      reply(sender, location, "error.unexpected");
      return 0;
    }
    return 1;
  }

  private static Placeholder value(String name, Object value) {
    return Placeholder.of(name, MiniMessage.miniMessage().escapeTags(String.valueOf(value)));
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
                        "command.migrate." + key,
                        sender instanceof Player p ? p : null,
                        placeholders));
    try {
      if (sender instanceof Player player) {
        player.getScheduler().run(plugin, task -> send.run(), () -> {});
      } else if (location != null) {
        Bukkit.getRegionScheduler().run(plugin, location, task -> send.run());
      } else {
        plugin.getScheduler().runSync(send);
      }
    } catch (Exception e) {
      plugin.getLogger().log(Level.FINE, "Migration sender unavailable", e);
    }
  }
}
