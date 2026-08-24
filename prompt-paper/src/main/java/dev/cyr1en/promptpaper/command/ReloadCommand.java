package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code /commandprompter reload} — cancels every active session across
 * every online player, reloads the configuration from disk, and rebuilds the
 * command-line parser (so {@code Argument-Regex} and {@code Ignore-MiniMessage}
 * take effect without a restart). The cancel pass prevents stale sessions
 * from holding references to the previous config values.
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
        plugin.getPluginLogger().info("Reload requested by " + sender.getName());
        plugin.getPluginLogger().debug("Reload: cancelling all active sessions before config reload");
        var feedbackLocation = captureBlockLocation(sender);
        var gateOwner = plugin.getEngine();
        if (gateOwner != null) {
            boolean acquired;
            try {
                acquired = gateOwner.beginReload();
            } catch (Exception e) {
                plugin.getPluginLogger().err("Unable to acquire reload barrier: " + e.getMessage());
                sendResult(sender, feedbackLocation, "command.reload.failed",
                        reloadError(e.getMessage()));
                return;
            }
            if (!acquired) {
                plugin.getPluginLogger().debug("Reload already in progress; rejecting reload request");
                sendResult(sender, feedbackLocation, "command.reload.failed",
                        reloadError("another reload is already in progress"));
                return;
            }
        }
        ArrayList<Player> players;
        try {
            players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        } catch (Throwable t) {
            plugin.getPluginLogger().err("Unable to enumerate players for reload: " + t.getMessage());
            try {
                sendResult(sender, feedbackLocation, "command.reload.failed",
                        reloadError(t.getMessage()));
            } finally {
                releaseReloadGate(gateOwner);
            }
            return;
        }
        if (plugin.getScreenManager() == null || plugin.getEngine() == null || players.isEmpty()) {
            scheduleReload(sender, feedbackLocation, gateOwner);
            return;
        }

        var remaining = new AtomicInteger(players.size());
        var reloadScheduled = new AtomicBoolean();
        for (var player : players) {
            var uuid = player.getUniqueId();
            var completed = new AtomicBoolean();
            Runnable finish = () -> {
                if (completed.compareAndSet(false, true)
                        && remaining.decrementAndGet() == 0
                        && reloadScheduled.compareAndSet(false, true)) {
                    scheduleReload(sender, feedbackLocation, gateOwner);
                }
            };
            try {
                var task = player.getScheduler().run(
                        plugin,
                        scheduledTask -> {
                            try {
                                plugin.getScreenManager().cancelAll(player, dev.cyr1en.promptpaper.engine.CancellationMode.DISCARD_ONLY, true);
                            } finally {
                                finish.run();
                            }
                        },
                        () -> discardPlayerState(uuid, finish));
                if (task == null) {
                    discardPlayerState(uuid, finish);
                }
            } catch (Throwable t) {
                plugin.getPluginLogger().debug(
                        "Player teardown scheduling failed for " + uuid + ": " + t.getMessage());
                discardPlayerState(uuid, finish);
            }
        }
    }

    private void scheduleReload(
            CommandSender sender, Location feedbackLocation, PromptEngine gateOwner) {
        try {
            plugin.getScheduler().runSync(
                    () -> reloadAfterTeardown(sender, feedbackLocation, gateOwner));
        } catch (Throwable t) {
            plugin.getPluginLogger().err("Unable to schedule configuration reload: " + t.getMessage());
            try {
                sendResult(sender, feedbackLocation, "command.reload.failed",
                        reloadError(t.getMessage()));
            } finally {
                releaseReloadGate(gateOwner);
            }
        }
    }

    private void reloadAfterTeardown(
            CommandSender sender, Location feedbackLocation, PromptEngine gateOwner) {
        try {
            if (plugin.getExecutionCoordinator() != null)
                plugin.getExecutionCoordinator().cancelAll();
            if (plugin.getEngine() != null)
                plugin.getEngine().discardAll();
            var loader = plugin.getConfigLoader();
            var preparedConfig = loader.prepareReload();
            var engine = plugin.getEngine();
            var preparedParser = engine != null
                    ? engine.prepareParser(preparedConfig.config())
                    : null;

            var registry = plugin.getPresetRegistry();
            var preparedPresets = registry != null
                    ? registry.prepareReload(preparedConfig.config().templateSyntax())
                    : null;

            var catalogRegistry = plugin.getItemCatalogRegistry();
            var preparedCatalog = catalogRegistry != null
                    ? catalogRegistry.prepareReload()
                    : null;

            // Nothing becomes visible until every file and parser has been validated.
            loader.publishReload(preparedConfig, () -> {
                if (registry != null) registry.publishReload(preparedPresets);
                if (catalogRegistry != null) catalogRegistry.publishReload(preparedCatalog);
                if (engine != null) engine.publishParser(preparedParser);
            });

            try {
                plugin.getPluginLogger().reload(preparedConfig.config());
            } catch (Throwable t) {
                plugin.getLogger().warning("Configuration published but logger refresh failed: "
                        + t.getMessage());
            }

            if (registry != null) {
                var presetMsg = "Loaded presets: <green>" + registry.promptCount() + " prompts</green>, <gold>" +
                        registry.postCommandCount() + " post commands</gold>";
                plugin.getPluginLogger().info(presetMsg);
                plugin.getPluginLogger().debug("Loaded prompt IDs: " + String.join(", ", registry.getPromptIds()));
                plugin.getPluginLogger()
                        .debug("Loaded post-command IDs: " + String.join(", ", registry.getPostCommandIds()));
            }
            if (catalogRegistry != null) {
                var catSnapshot = catalogRegistry.getSnapshot();
                var catalogMsg = "Loaded item catalogs: <green>" + catSnapshot.categoryCount() + " categories</green>, <gold>" +
                        catSnapshot.totalEntryCount() + " items</gold>";
                plugin.getPluginLogger().info(catalogMsg);
                plugin.getPluginLogger().debug("Loaded catalog categories: " + String.join(", ", catSnapshot.categories()));
            }
            sendResult(sender, feedbackLocation, "command.reload.success");
        } catch (Exception e) {
            sendResult(sender, feedbackLocation, "command.reload.failed",
                    reloadError(e.getMessage()));
        } finally {
            releaseReloadGate(gateOwner);
        }
    }

    /**
     * Routes a localized reload message to the sender.
     *
     * <p>Player senders localize <em>inside</em> the player scheduler task with the player as the
     * i18n context (so PaperI18n/PapiExpander can expand {@code %...%} for that player); console
     * and block senders use context-free formatting. The message key and placeholders are passed
     * through so no pre-localized {@link Component} ever loses the sender context.
     */
    void sendResult(CommandSender sender, Location feedbackLocation, String key, Placeholder... placeholders) {
        if (sender instanceof Player player) {
            try {
                var task = player.getScheduler().run(
                        plugin,
                        scheduledTask -> player.sendMessage(localize(player, key, placeholders)),
                        () -> {});
                if (task == null) {
                    plugin.getPluginLogger().debug("Reload result sender retired; omitting feedback");
                }
            } catch (Exception e) {
                plugin.getPluginLogger().debug("Reload result sender retired: " + e.getMessage());
            }
            return;
        }

        var message = localize(null, key, placeholders);
        if (sender instanceof BlockCommandSender blockSender) {
            if (feedbackLocation == null) {
                plugin.getPluginLogger().debug(
                        "Unable to route reload result: block sender has no captured location");
                return;
            }
            try {
                Bukkit.getRegionScheduler().run(
                        plugin,
                        feedbackLocation,
                        scheduledTask -> blockSender.sendMessage(message));
            } catch (Exception e) {
                plugin.getPluginLogger().debug(
                        "Unable to route reload result to block sender: " + e.getMessage());
            }
            return;
        }

        try {
            // PaperScheduler maps runSync to the global-region scheduler.
            plugin.getScheduler().runSync(() -> sender.sendMessage(message));
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Unable to send reload result: " + e.getMessage());
        }
    }

    /**
     * Localizes a reload message with the sender as the i18n context when the sender is a player;
     * console/block senders (and a {@code null} sender) keep context-free formatting.
     */
    private Component localize(CommandSender sender, String key, Placeholder... placeholders) {
        var context = sender instanceof Player player ? player : null;
        return plugin.getConfigLoader().getI18n().get(key, context, placeholders);
    }

    /** Builds the {@code command.reload.failed} error placeholder for the given detail string. */
    private static Placeholder reloadError(String detail) {
        return Placeholder.of("error", detail != null ? detail : "");
    }

    private Location captureBlockLocation(CommandSender sender) {
        if (!(sender instanceof BlockCommandSender blockSender))
            return null;
        try {
            return blockSender.getBlock().getLocation().clone();
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Unable to capture block sender location: " + e.getMessage());
            return null;
        }
    }

    private void releaseReloadGate(PromptEngine gateOwner) {
        if (gateOwner == null)
            return;
        try {
            gateOwner.endReload();
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Unable to release reload barrier: " + e.getMessage());
        }
    }

    private void discardPlayerState(UUID uuid, Runnable finish) {
        try {
            plugin.getScreenManager().discardState(uuid);
            plugin.getEngine().discard(uuid);
        } catch (Throwable t) {
            plugin.getPluginLogger().debug(
                    "Unable to discard player state for " + uuid + ": " + t.getMessage());
        } finally {
            finish.run();
        }
    }
}
