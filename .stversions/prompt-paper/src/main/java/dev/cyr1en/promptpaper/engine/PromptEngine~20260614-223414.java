package dev.cyr1en.promptpaper.engine;

import dev.cyr1en.promptcore.*;
import dev.cyr1en.promptcore.parser.CommandLineParser;
import dev.cyr1en.promptcore.session.PromptSession;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.util.Scheduler;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Manages the lifecycle of interactive prompt sessions for players.
 * Parses command lines for prompt tags, tracks per-player sessions,
 * collects answers, and dispatches post-completion/cancellation commands.
 * All session state is held in a {@link ConcurrentHashMap} keyed by player UUID.
 */
public class PromptEngine {

    private final CommandPrompter plugin;
    private final CommandLineParser parser;
    private final Map<UUID, PromptSession> sessions;
    private final Scheduler scheduler;

    public PromptEngine(CommandPrompter plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.parser = new CommandLineParser();
        this.sessions = new ConcurrentHashMap<>();
        this.scheduler = scheduler;
    }

    /**
     * Parses a command line and, if it contains prompt tags, starts a new
     * session for the player and returns the parsed result.
     *
     * <p>When {@code config.enablePermission()} is {@code true}, this method
     * returns empty (and starts no session) for any player that lacks
     * {@code promptpaper.use}. This is the per-player control gate for the
     * prompting feature, distinct from the per-command permissions checked
     * by the command system.
     *
     * @return the parsed command with prompts, or empty if no prompts were found
     */
    public Optional<ParsedCommand> intercept(Player player, String commandLine) {
        var config = plugin.getConfigLoader().getConfig();
        if (config.enablePermission() && !player.hasPermission("promptpaper.use")) {
            plugin.getPluginLogger().debug("Player " + player.getName()
                    + " lacks promptpaper.use, skipping prompt intercept");
            return Optional.empty();
        }
        var parsed = parser.parse(commandLine);
        if (!parsed.hasPrompts()) {
            plugin.getPluginLogger().debug("No prompts in command from " + player.getName());
            return Optional.empty();
        }
        sessions.put(player.getUniqueId(), PromptSession.start(player.getUniqueId().toString(), parsed));
        plugin.getPluginLogger().debug("Intercepted " + parsed.promptTags().size()
                + " prompts for " + player.getName());
        return Optional.of(parsed);
    }

    /**
     * Submits a single answer for the player's current prompt session.
     *
     * @return the completed session result if all prompts are now answered, or empty
     */
    public Optional<SessionResult> submit(Player player, String answer) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || !session.isActive()) {
            plugin.getPluginLogger().debug("No active session for " + player.getName() + " on submit");
            return Optional.empty();
        }
        session = session.submitAnswer(answer);
        sessions.put(player.getUniqueId(), session);
        if (session.isComplete()) {
            sessions.remove(player.getUniqueId());
            plugin.getPluginLogger().debug("Session complete for " + player.getName());
            return Optional.of(session.finish());
        }
        plugin.getPluginLogger().debug("Answer accepted for " + player.getName()
                + ", " + session.remainingCount() + " remaining");
        return Optional.empty();
    }

    /**
     * Submit a batch of answers to the current prompt. The current prompt
     * must be a compound tag with the matching number of sub-answers. See
     * {@link dev.cyr1en.promptcore.session.PromptSession#submitAnswers} for
     * the size-validation rules.
     */
    public Optional<SessionResult> submitAnswers(Player player, java.util.List<String> answers) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || !session.isActive()) {
            plugin.getPluginLogger().debug("No active session for " + player.getName() + " on submitAnswers");
            return Optional.empty();
        }
        session = session.submitAnswers(answers);
        sessions.put(player.getUniqueId(), session);
        if (session.isComplete()) {
            sessions.remove(player.getUniqueId());
            plugin.getPluginLogger().debug("Session complete for " + player.getName());
            return Optional.of(session.finish());
        }
        plugin.getPluginLogger().debug("Compound answers accepted for " + player.getName()
                + ", " + session.remainingCount() + " remaining");
        return Optional.empty();
    }

    /**
     * Returns the active session for a player, if one exists.
     */
    public Optional<PromptSession> getSession(Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    /**
     * Cancels the player's active session and dispatches on-cancel commands if present.
     */
    public void cancel(Player player, CancelReason reason) {
        var session = sessions.remove(player.getUniqueId());
        if (session == null) {
            plugin.getPluginLogger().debug("No session to cancel for " + player.getName());
            return;
        }
        plugin.getPluginLogger().debug("Session cancelled for " + player.getName() + " reason=" + reason);
        var cancelled = session.cancel(reason);
        if (cancelled.isCancelled()) {
            dispatchPCMs(player, cancelled.finish(), true);
        }
    }

    /**
     * Cancels all active sessions (e.g. during plugin shutdown).
     */
    public void cancelAll() {
        var snapshot = new java.util.ArrayList<>(sessions.keySet());
        for (var uuid : snapshot) {
            var player = plugin.getServer().getPlayer(uuid);
            if (player != null) cancel(player, CancelReason.MANUAL);
        }
        sessions.clear();
    }

    /**
     * Returns whether the player has an active (non-complete, non-cancelled) session.
     */
    public boolean hasActiveSession(Player player) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    /**
     * Dispatches post-completion or on-cancel commands (PCMs) from a session result.
     * Commands with a positive delay are scheduled; others run synchronously.
     */
    public void dispatchPCMs(Player player, SessionResult result, boolean wasCancelled) {
        var pcms = wasCancelled ? result.onCancelCmds() : result.onCompleteCmds();
        plugin.getPluginLogger().debug("Dispatching " + pcms.size() + " PCMs for "
                + player.getName() + " (cancelled=" + wasCancelled + ")");
        for (var pcm : pcms) {
            Runnable task = () -> executePCM(player, pcm);
            if (pcm.delayTicks() > 0) {
                scheduler.runLater(task, pcm.delayTicks());
            } else {
                scheduler.runSync(task);
            }
        }
    }

    /**
     * Executes a single post-command meta against the appropriate command sender
     * (console, player, or passthrough → console).
     */
    private void executePCM(Player player, PostCommandMeta pcm) {
        if (pcm.command().isEmpty()) {
            plugin.getPluginLogger().debug("Empty PCM command, skipping");
            return;
        }
        var sender = switch (pcm.dispatchTarget()) {
            case CONSOLE -> plugin.getServer().getConsoleSender();
            case PLAYER -> player;
            case PASSTHROUGH -> plugin.getServer().getConsoleSender();
        };
        plugin.getPluginLogger().debug("Executing PCM: target=" + pcm.dispatchTarget()
                + " cmd=" + pcm.command());
        plugin.getServer().dispatchCommand(sender, pcm.command());
    }
}
