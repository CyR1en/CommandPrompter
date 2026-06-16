package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.ParsedCommand;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import dev.cyr1en.promptui.ComponentUtil;
import dev.cyr1en.promptpaper.screen.dialog.DialogCompletionContext;
import dev.cyr1en.promptpaper.screen.dialog.DialogInputKind;
import dev.cyr1en.promptpaper.util.CancellableTask;
import dev.cyr1en.promptpaper.util.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.cyr1en.promptcore.i18n.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Orchestrates prompt sessions by routing tags to the appropriate screen
 * type, collecting answers, and dispatching the assembled command.
 */
public class ScreenManager {

    public enum DispatchMode {
        NORMAL,
        CONSOLE,
        ATTACHMENT
    }

    private final CommandPrompter plugin;
    private final PromptEngine engine;
    private final ScreenRouter router;
    private final Scheduler scheduler;
    private final Map<UUID, InputScreen> activeScreens;
    private final Map<UUID, CancellableTask> timeoutTasks;
    private final Map<UUID, DispatchMode> dispatchModes;
    private final Map<UUID, String> attachmentKeys;

    public ScreenManager(CommandPrompter plugin, PromptEngine engine, ScreenRouter router, Scheduler scheduler) {
        this.plugin = plugin;
        this.engine = engine;
        this.router = router;
        this.scheduler = scheduler;
        this.activeScreens = new ConcurrentHashMap<>();
        this.timeoutTasks = new ConcurrentHashMap<>();
        this.dispatchModes = new ConcurrentHashMap<>();
        this.attachmentKeys = new ConcurrentHashMap<>();
    }

    /**
     * Intercepts the command line for prompt tags and begins showing
     * the first prompt to the player.
     */
    public void startSession(Player player, String commandLine) {
        var parsed = engine.intercept(player, commandLine);
        if (parsed.isEmpty()) {
            plugin.getPluginLogger().debug("No prompts to show for " + player.getName());
            return;
        }
        plugin.getPluginLogger().debug("Starting session for " + player.getName()
                + " with " + parsed.get().promptTags().size() + " prompts");
        showNextPrompt(player);
    }

    /**
     * Like {@link #startSession} but with a dispatch mode and optional
     * permission key for post-session command execution.
     */
    public void startDelegatedSession(Player target, String commandLine, DispatchMode mode, String permissionKey) {
        plugin.getPluginLogger().debug("Delegated session: target=" + target.getName()
                + " mode=" + mode + " permKey=" + permissionKey);
        var parsed = engine.intercept(target, commandLine);
        if (parsed.isEmpty()) {
            plugin.getPluginLogger().debug("No prompts, dispatching directly");
            dispatchDirect(target, commandLine, mode, permissionKey);
            return;
        }
        if (mode != DispatchMode.NORMAL) {
            dispatchModes.put(target.getUniqueId(), mode);
            if (permissionKey != null) {
                attachmentKeys.put(target.getUniqueId(), permissionKey);
            }
        }
        showNextPrompt(target);
    }

    private void dispatchDirect(Player target, String commandLine, DispatchMode mode, String permissionKey) {
        switch (mode) {
            case CONSOLE -> dispatchAsConsole(target, commandLine);
            case ATTACHMENT -> dispatchWithAttachment(target, commandLine, permissionKey);
            default -> dispatchAssembledCommand(target, commandLine);
        }
    }

    /**
     * Advances the session to the next prompt tag and displays it,
     * or completes the session if all prompts have been answered.
     */
    public void showNextPrompt(Player player) {
        var session = engine.getSession(player);
        if (session.isEmpty() || !session.get().isActive()) return;
        var current = session.get().currentPrompt();
        if (current.isEmpty()) return;
        var tag = current.get();
        if (!tag.isCompound() && DialogInputKind.parse(tag.filter()) == DialogInputKind.TITLE) {
            plugin.getPluginLogger().warn("Player " + player.getName()
                    + " initiated a prompt containing a non-compound tag with a TITLE filter: "
                    + tag.rawTag());
            player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.error.invalid_title_filter"));
            cancelAll(player);
            return;
        }
        showPrompt(player, tag);
    }

    /**
     * Resolves placeholders, builds the completion context, creates the
     * screen via the router, and opens it for the player.
     */
    private void showPrompt(Player player, PromptTag tag) {
        var displayText = resolvePlaceholders(player, tag.displayText());
        var resolvedTag =
            new PromptTag(
                tag.rawTag(),
                tag.key(),
                tag.filter(),
                displayText,
                tag.sanitize(),
                tag.validatorAlias(),
                tag.type(),
                tag.subTags());
        var context = buildCompletionContext(player, resolvedTag);
        var screen = router.create(player, resolvedTag, context);
        plugin.getPluginLogger().debug("Showing prompt for " + player.getName()
                + " key=" + tag.key() + " screen=" + screen.getClass().getSimpleName());
        activeScreens.put(player.getUniqueId(), screen);
        screen.onResult(result -> handleResult(player, result));
        screen.open();
        scheduleTimeout(player);
    }

    /**
     * Builds a {@link DialogCompletionContext} for TAB prompts by
     * reconstructing the partial command from the session's parsed
     * command and current answers. Returns null for non-TAB prompts.
     */
    private DialogCompletionContext buildCompletionContext(Player player, PromptTag tag) {
        if (!"d".equals(tag.key())) return null;
        if (DialogInputKind.parse(tag.filter()) != DialogInputKind.TAB) {
            return null;
        }
        var session = engine.getSession(player).orElse(null);
        if (session == null) return null;
        var partial = ParsedCommand.buildPartialCommand(
                session.parsedCommand(), session.answers());
        return new DialogCompletionContext(player, partial);
    }

    private String resolvePlaceholders(Player player, String text) {
        return plugin.getHookContainer().getHook(PapiHook.class)
                .map(h -> h.setPlaceholder(player, text))
                .orElse(text);
    }

    /**
     * Routes a raw chat message to the active {@link ChatPromptScreen} for the player.
     */
    public void handleChatInput(Player player, String input) {
        var screen = activeScreens.get(player.getUniqueId());
        if (!(screen instanceof ChatPromptScreen chatScreen)) return;
        cancelTimeout(player);
        chatScreen.handleInput(input);
    }

    /**
     * Processes a screen result: validates the answer, handles compound
     * payloads, and either advances the session or dispatches the command.
     */
    private void handleResult(Player player, ScreenResult result) {
        cancelTimeout(player);
        activeScreens.remove(player.getUniqueId());

        plugin.getPluginLogger().debug("Screen result for " + player.getName()
                + " cancelled=" + result.cancelled());

        if (result.cancelled()) {
            engine.cancel(player, CancelReason.GUI_EXIT);
            player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.cancelled"));
            return;
        }

        var sessionOpt = engine.getSession(player);
        if (sessionOpt.isEmpty()) {
            plugin.getPluginLogger().debug("No session for " + player.getName() + " on result");
            return;
        }
        var tagOpt = sessionOpt.get().currentPrompt();
        if (tagOpt.isEmpty()) return;
        var tag = tagOpt.get();

        // Compound dialogs encode N sub-answers into the result payload.
        // The dialog screen uses ASCII control chars (RS=0x1E, US=0x1F) as
        // delimiters. A leading RS marks a compound payload; a plain string
        // is a single-tag answer.
        if (tag.isCompound()) {
            handleCompoundResult(player, tag, result.answer());
            return;
        }

        if (!validateAnswer(player, result.answer(), tag)) {
            plugin.getPluginLogger().debug("Validation failed for " + player.getName());
            showPrompt(player, tag);
            return;
        }

        var submitted = engine.submit(player, result.answer());
        if (submitted.isPresent()) {
            var sessionResult = submitted.get();
            plugin.getPluginLogger().debug("Session complete, dispatching: "
                    + sessionResult.assembledCommand());
            dispatchAssembledCommand(player, sessionResult.assembledCommand());
            engine.dispatchPCMs(player, sessionResult, false);
        } else {
            plugin.getPluginLogger().debug("Answer accepted, showing next prompt");
            showNextPrompt(player);
        }
    }

    /**
     * Decodes a compound RS/US payload into sub-answers, validates each
     * against the block-level constraints, and submits all at once.
     */
    private void handleCompoundResult(Player player, PromptTag tag, String rawPayload) {
        var answers = decodeAnswers(rawPayload, tag.subTags().size());
        if (answers == null) {
            // Malformed payload — re-show the dialog. This shouldn't happen
            // with a properly-built DialogPromptScreen, but defensive.
            plugin.getPluginLogger().warn("Malformed compound payload from dialog for "
                    + player.getName() + ": " + rawPayload);
            showPrompt(player, tag);
            return;
        }
        for (var i = 0; i < answers.size(); i++) {
            var subTag = tag.subTags().get(i);
            if (!validateSubAnswer(player, answers.get(i), subTag, tag)) {
                plugin.getPluginLogger().debug("Validation failed for sub-answer " + i
                        + " of compound prompt for " + player.getName());
                showPrompt(player, tag);
                return;
            }
        }
        var submitted = engine.submitAnswers(player, answers);
        if (submitted.isPresent()) {
            var sessionResult = submitted.get();
            plugin.getPluginLogger().debug("Session complete, dispatching: "
                    + sessionResult.assembledCommand());
            dispatchAssembledCommand(player, sessionResult.assembledCommand());
            engine.dispatchPCMs(player, sessionResult, false);
        } else {
            plugin.getPluginLogger().debug("Compound answers accepted, showing next prompt");
            showNextPrompt(player);
        }
    }

    /**
     * Validates one sub-answer against the block-level type constraint
     * and custom validator. Sub-tag-level constraints are ignored.
     */
    private boolean validateSubAnswer(Player player, String answer, PromptTag subTag, PromptTag block) {
        var i18n = plugin.getConfigLoader().getI18n();
        switch (block.type()) {
            case INTEGER -> {
                try {
                    Integer.parseInt(answer);
                } catch (NumberFormatException e) {
                    plugin.getPluginLogger().debug("Integer validation failed for "
                            + player.getName() + ": " + answer);
                    player.sendMessage(i18n.get("validation.invalid_integer"));
                    return false;
                }
            }
            case STRING -> {
                if (answer.isBlank()) {
                    plugin.getPluginLogger().debug("String validation failed (blank) for "
                            + player.getName());
                    player.sendMessage(i18n.get("validation.invalid_string"));
                    return false;
                }
            }
            case NONE -> {}
        }
        if (block.validatorAlias() != null && !block.validatorAlias().isBlank()) {
            var config = plugin.getConfigLoader().getPromptConfig();
            var validator = config.getInputValidator(block.validatorAlias(), player, plugin);
            var valid = validator.validate(answer);
            if (!valid) {
                var msg = validator.messageOnFail();
                if (!msg.isBlank()) {
                    if (msg.contains("&")) {
                        player.sendMessage(ComponentUtil.mini(toMini(msg)));
                    } else {
                        player.sendMessage(ComponentUtil.mini(msg));
                    }
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Decodes a compound payload via {@link AnswerEncoding#decode}.
     * Returns null if the payload is malformed.
     */
    static java.util.List<String> decodeAnswers(String payload, int expected) {
        return dev.cyr1en.promptpaper.screen.dialog.AnswerEncoding.decode(payload, expected);
    }

    /**
     * Validates a single answer against the tag's type constraint
     * and custom validator alias.
     */
    private boolean validateAnswer(Player player, String answer, PromptTag tag) {
        var i18n = plugin.getConfigLoader().getI18n();
        switch (tag.type()) {
            case INTEGER -> {
                try {
                    Integer.parseInt(answer);
                } catch (NumberFormatException e) {
                    plugin.getPluginLogger().debug("Integer validation failed for "
                            + player.getName() + ": " + answer);
                    player.sendMessage(i18n.get("validation.invalid_integer"));
                    return false;
                }
            }
            case STRING -> {
                if (answer.isBlank()) {
                    plugin.getPluginLogger().debug("String validation failed (blank) for "
                            + player.getName());
                    player.sendMessage(i18n.get("validation.invalid_string"));
                    return false;
                }
            }
            case NONE -> {}
        }

        if (tag.validatorAlias() != null && !tag.validatorAlias().isBlank()) {
            var config = plugin.getConfigLoader().getPromptConfig();
            var validator = config.getInputValidator(tag.validatorAlias(), player, plugin);
            var valid = validator.validate(answer);
            plugin.getPluginLogger().debug("Validator " + tag.validatorAlias()
                    + " for " + player.getName() + ": valid=" + valid);
            if (!valid) {
                var msg = validator.messageOnFail();
                if (!msg.isBlank()) {
                    if (msg.contains("&")) {
                        player.sendMessage(ComponentUtil.mini(toMini(msg)));
                    } else {
                        player.sendMessage(ComponentUtil.mini(msg));
                    }
                }
                return false;
            }
        }
        return true;
    }

    private static String toMini(String legacy) {
        var component = LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
        return ComponentUtil.serialize(component);
    }

    /**
     * Dispatches the final command according to the player's active
     * dispatch mode (normal, console, or permission-attachment).
     */
    private void dispatchAssembledCommand(Player player, String cmd) {
        var mode = dispatchModes.remove(player.getUniqueId());
        if (mode == null) mode = DispatchMode.NORMAL;
        plugin.getPluginLogger().debug("Dispatching for " + player.getName()
                + " mode=" + mode + " cmd=" + cmd);
        switch (mode) {
            case CONSOLE -> dispatchAsConsole(player, cmd);
            case ATTACHMENT -> {
                var key = attachmentKeys.remove(player.getUniqueId());
                dispatchWithAttachment(player, cmd, key);
            }
            default -> {
                var toExecute = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                player.getScheduler().run(plugin, scheduledTask -> {
                    try {
                        player.performCommand(toExecute);
                    } catch (Exception e) {
                        var msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                        plugin.getPluginLogger().info("Command dispatch failed: " + msg);
                        player.sendMessage(plugin.getConfigLoader().getI18n().get(
                                "prompt.error.command_failed",
                                player,
                                Placeholder.of("message", msg != null ? msg : "")));
                    }
                }, null);
            }
        }
    }

    private void dispatchAsConsole(Player player, String cmd) {
        var toExecute = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        plugin.getPluginLogger().debug("Dispatching as console: " + toExecute);
        scheduler.runSync(() ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toExecute));
    }

    /**
     * Temporarily grants permissions, dispatches the command as the
     * player, then revokes the attachment.
     */
    private void dispatchWithAttachment(Player player, String cmd, String permissionKey) {
        if (permissionKey == null) {
            plugin.getPluginLogger().debug("No permission key, falling back to console dispatch");
            dispatchAsConsole(player, cmd);
            return;
        }
        var config = plugin.getConfigLoader().getConfig();
        var perms = config.getPermissionAttachment(permissionKey);
        if (perms == null || perms.length == 0) {
            plugin.getPluginLogger().debug("Permission key " + permissionKey
                    + " has no permissions, falling back to console dispatch");
            dispatchAsConsole(player, cmd);
            return;
        }
        var toExecute = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        plugin.getPluginLogger().debug("Dispatching with attachment key="
                + permissionKey + " perms=" + java.util.Arrays.toString(perms));
        player.getScheduler().run(plugin, scheduledTask -> {
            var attachment = player.addAttachment(plugin);
            if (attachment == null) {
                plugin.getPluginLogger().err("Unable to create PermissionAttachment for " + player.getName());
                return;
            }
            try {
                for (var perm : perms) {
                    attachment.setPermission(perm, true);
                }
                attachment.getPermissible().recalculatePermissions();
                plugin.getPluginLogger().debug("Dispatching with attachment: player="
                        + player.getName() + " perms=" + perms.length);
                Bukkit.dispatchCommand(player, toExecute);
            } finally {
                player.removeAttachment(attachment);
                plugin.getPluginLogger().debug("Attachment removed for " + player.getName());
            }
        }, null);
    }

    public boolean hasActiveScreen(Player player) {
        return activeScreens.containsKey(player.getUniqueId());
    }

    public boolean hasChatScreen(Player player) {
        return activeScreens.get(player.getUniqueId()) instanceof ChatPromptScreen;
    }

    /**
     * Cancels the active screen, timeout, and session for the player.
     */
    public void cancelAll(Player player) {
        cancelTimeout(player);
        var screen = activeScreens.remove(player.getUniqueId());
        if (screen != null) screen.close();
        dispatchModes.remove(player.getUniqueId());
        attachmentKeys.remove(player.getUniqueId());
        engine.cancel(player, CancelReason.MANUAL);
        plugin.getPluginLogger().debug("Cancelled all for " + player.getName());
    }

    /**
     * Schedules a timeout that auto-cancels the session if the player
     * does not respond within the configured duration.
     */
    private void scheduleTimeout(Player player) {
        cancelTimeout(player);
        var timeoutSecs = plugin.getConfigLoader().getConfig().promptTimeout();
        if (timeoutSecs <= 0) return;
        plugin.getPluginLogger().debug("Scheduling timeout for " + player.getName()
                + " in " + timeoutSecs + "s");
        var task = scheduler.runLater(() -> {
            var session = engine.getSession(player);
            if (session.isPresent() && session.get().isActive()) {
                plugin.getPluginLogger().debug("Timeout triggered for " + player.getName());
                cancelAll(player);
                player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.timed_out"));
            }
        }, timeoutSecs * 20L);
        timeoutTasks.put(player.getUniqueId(), task);
    }

    private void cancelTimeout(Player player) {
        var task = timeoutTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }
}
