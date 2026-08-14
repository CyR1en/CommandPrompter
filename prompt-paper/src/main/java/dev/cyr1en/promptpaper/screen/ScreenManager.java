package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptui.ComponentUtil;
import dev.cyr1en.promptui.DialogScreen;
import dev.cyr1en.promptpaper.screen.dialog.AnswerEncoding;
import dev.cyr1en.promptpaper.screen.dialog.DialogCompletionContext;
import dev.cyr1en.promptpaper.screen.dialog.DialogInputKind;
import dev.cyr1en.promptpaper.screen.playerui.PlayerUIScreen;
import dev.cyr1en.promptpaper.util.CancellableTask;
import dev.cyr1en.promptpaper.util.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import dev.cyr1en.promptcore.i18n.Placeholder;
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
    private final PromptFactory factory;
    private final Scheduler scheduler;
    private final Map<UUID, InputScreen> activeScreens;
    private final Map<UUID, CancellableTask> timeoutTasks;
    private final Map<UUID, Long> timeoutTokens;
    private final AtomicLong timeoutSequence;
    private final Map<UUID, DispatchMode> dispatchModes;
    private final Map<UUID, String> attachmentKeys;
    private final Set<UUID> teardownInProgress;

    public ScreenManager(CommandPrompter plugin, PromptEngine engine, PromptFactory factory, Scheduler scheduler) {
        this.plugin = plugin;
        this.engine = engine;
        this.factory = factory;
        this.scheduler = scheduler;
        this.activeScreens = new ConcurrentHashMap<>();
        this.timeoutTasks = new ConcurrentHashMap<>();
        this.timeoutTokens = new ConcurrentHashMap<>();
        this.timeoutSequence = new AtomicLong();
        this.dispatchModes = new ConcurrentHashMap<>();
        this.attachmentKeys = new ConcurrentHashMap<>();
        this.teardownInProgress = ConcurrentHashMap.newKeySet();
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
        var started = engine.runIfReloadNotInProgress(() -> showNextPrompt(player));
        if (!started) {
            engine.discard(player.getUniqueId());
            engine.rejectIfReloading(player);
            return;
        }
    }

    /**
     * Like {@link #startSession} but with a dispatch mode and optional
     * permission key for post-session command execution.
     */
    public void startDelegatedSession(Player target, String commandLine, DispatchMode mode, String permissionKey) {
        var uuid = target.getUniqueId();
        try {
            var task = target.getScheduler().run(
                    plugin,
                    scheduledTask -> startDelegatedSessionOnPlayer(
                            target, commandLine, mode, permissionKey),
                    () -> discardState(uuid));
            if (task == null) discardState(uuid);
        } catch (Throwable t) {
            plugin.getPluginLogger().err("Unable to schedule delegated session for " + uuid
                    + ": " + t.getMessage());
            discardState(uuid);
        }
    }

    private void startDelegatedSessionOnPlayer(
            Player target, String commandLine, DispatchMode mode, String permissionKey) {
        var uuid = target.getUniqueId();
        try {
            plugin.getPluginLogger().debug("Delegated session: target=" + target.getName()
                    + " mode=" + mode + " permKey=" + permissionKey);
            var normalized = commandLine.startsWith("/")
                    ? commandLine.substring(1)
                    : commandLine;
            normalized = normalized.replace("%target_player%", target.getName());
            var parsed = engine.intercept(target, normalized);
            if (parsed.isEmpty()) {
                if (!engine.commandHasTagForm(normalized)) {
                    plugin.getPluginLogger().debug("No prompts, dispatching directly");
                    dispatchDirect(target, normalized, mode, permissionKey);
                } else {
                    plugin.getPluginLogger().debug("Command had tag form but intercept rejected it "
                            + "(missing preset, no permission, or active session); not dispatching");
                }
                return;
            }
            var started = engine.runIfReloadNotInProgress(() -> {
                if (mode != DispatchMode.NORMAL) {
                    dispatchModes.put(uuid, mode);
                    if (permissionKey != null) attachmentKeys.put(uuid, permissionKey);
                }
                showNextPrompt(target);
            });
            if (!started) {
                engine.discard(uuid);
                engine.rejectIfReloading(target);
            }
        } catch (Throwable t) {
            plugin.getPluginLogger().err("Delegated session failed for " + uuid
                    + ": " + t.getMessage());
            discardState(uuid);
        }
    }

    private void dispatchDirect(Player target, String commandLine, DispatchMode mode, String permissionKey) {
        switch (mode) {
            case CONSOLE -> dispatchAsConsole(target, commandLine);
            case ATTACHMENT -> dispatchWithAttachment(
                    target, commandLine, permissionKey, capturePermissionSnapshot(permissionKey));
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
        var kind = DialogInputKind.parse(tag.filter());
        if (!tag.isCompound() && (kind == DialogInputKind.TITLE || kind == DialogInputKind.BODY)) {
            plugin.getPluginLogger().warn("Player " + player.getName()
                    + " initiated a prompt containing a non-compound tag with a layout filter: "
                    + tag.rawTag());
            player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.error.invalid_title_filter", player));
            cancelAll(player);
            return;
        }
        showPrompt(player, tag);
    }

    /**
     * Builds the completion context, creates the screen via the factory (the single
     * presentation-materialization boundary), and opens it for the player.
     */
    private void showPrompt(Player player, PromptTag tag) {
        InputScreen screen = null;
        var uuid = player.getUniqueId();
        try {
            // The tag is passed through raw: PromptFactory is now the single
            // presentation-materialization boundary and expands the prompt exactly
            // once before the screen is constructed (registry/session models stay raw).
            var context = buildCompletionContext(player, tag);
            screen = factory.createFromTag(player, tag, context);
            plugin.getPluginLogger().debug("Showing prompt for " + player.getName()
                    + " key=" + tag.key() + " screen=" + screen.getClass().getSimpleName());
            activeScreens.put(uuid, screen);
            screen.onResult(result -> handleResult(player, result));
            screen.open();
            scheduleTimeout(player);
        } catch (Throwable e) {
            if (screen != null) {
                try {
                    screen.close();
                } catch (Exception ignored) {
                    // The owning player may already be retired after an open failure.
                }
            }
            discardState(uuid);
            plugin.getPluginLogger().err("Unable to open prompt screen for " + uuid
                    + ": " + e.getMessage());
            if (e instanceof RuntimeException runtimeException) throw runtimeException;
            if (e instanceof Error error) throw error;
            throw new IllegalStateException("Prompt screen open failed", e);
        }
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
        var partial = session.buildPartialCommand();
        return new DialogCompletionContext(player, partial);
    }

    /**
     * Routes a raw chat message to the active {@link ChatPromptScreen} for the player.
     */
    public void handleChatInput(Player player, String input) {
        var screen = activeScreens.get(player.getUniqueId());
        if (screen instanceof TitleWrapperScreen wrapper) {
            screen = wrapper.delegate();
        }
        if (!(screen instanceof ChatPromptScreen chatScreen)) return;
        cancelTimeout(player);
        chatScreen.handleInput(input);
    }

    /**
     * Processes a screen result: validates the answer, handles compound
     * payloads, and either advances the session or dispatches the command.
     *
     * <p>Dialog screens (inline compound, inline single, or JSON preset) are
     * detected and unwrapped before they are removed from the active-screens
     * map and their result is routed through one arity-aware batch handler,
     * regardless of whether the parsed tag is compound.
     */
    private void handleResult(Player player, ScreenResult result) {
        plugin.getPluginLogger().debug("Screen result for " + player.getName()
                + " cancelled=" + result.cancelled());

        if (result.cancelled()) {
            cancelTimeout(player);
            activeScreens.remove(player.getUniqueId());
            teardown(player, CancelReason.GUI_EXIT, false, true);
            return;
        }

        cancelTimeout(player);
        var screen = activeScreens.remove(player.getUniqueId());
        var dialogScreen = unwrapDialogScreen(screen);

        var sessionOpt = engine.getSession(player);
        if (sessionOpt.isEmpty()) {
            plugin.getPluginLogger().debug("No session for " + player.getName() + " on result");
            return;
        }
        var tagOpt = sessionOpt.get().currentPrompt();
        if (tagOpt.isEmpty()) return;
        var tag = tagOpt.get();

        var cancelKeyword = plugin.getConfigLoader().getConfig().cancelKeyword();
        boolean isCancelKeyword = false;
        if (result.answer() != null && cancelKeyword != null && !cancelKeyword.isBlank()) {
            if (dialogScreen != null) {
                // Dialog payloads are decoded with the screen's effective arity
                // (which may be 0, 1, or N and is not the tag's compound shape).
                var decoded = decodeAnswers(result.answer(), dialogScreen.effectiveAnswerCount());
                if (decoded != null) {
                    for (var ans : decoded) {
                        if (ComponentUtil.stripColor(ans).trim().equalsIgnoreCase(cancelKeyword)) {
                            isCancelKeyword = true;
                            break;
                        }
                    }
                }
            } else if (tag.isCompound()) {
                var answerTags = answerBearingTags(tag);
                var decoded = decodeAnswers(result.answer(), answerTags.size());
                if (decoded != null) {
                    for (var ans : decoded) {
                        if (ComponentUtil.stripColor(ans).trim().equalsIgnoreCase(cancelKeyword)) {
                            isCancelKeyword = true;
                            break;
                        }
                    }
                }
            } else {
                if (ComponentUtil.stripColor(result.answer()).trim().equalsIgnoreCase(cancelKeyword)) {
                    isCancelKeyword = true;
                }
            }
        }

        if (isCancelKeyword) {
            teardown(player, CancelReason.MANUAL, false, true);
            return;
        }

        if (dialogScreen != null) {
            handleDialogResult(player, tag, result, dialogScreen);
            return;
        }

        // Compound dialogs encode multiple sub-answers with control characters (RS/US).
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
            var dispatchContext = dispatchAssembledCommand(
                    player, sessionResult.assembledCommand());
            sendCompletedCommand(player, sessionResult.assembledCommand());
            engine.dispatchPCMs(player, sessionResult, false, dispatchContext);
        } else {
            plugin.getPluginLogger().debug("Answer accepted, showing next prompt");
            showNextPrompt(player);
        }
    }

    /**
     * Unwraps a {@link TitleWrapperScreen} (if any) and returns the underlying
     * screen when it is a {@link DialogScreen}; otherwise {@code null}. Works
     * through the loadable UI-API marker so the Paper-bound dialog screen class
     * is never referenced here.
     */
    private static DialogScreen unwrapDialogScreen(InputScreen screen) {
        if (screen instanceof TitleWrapperScreen wrapper) {
            screen = wrapper.delegate();
        }
        return screen instanceof DialogScreen dialog ? dialog : null;
    }

    /**
     * Routes a {@link DialogScreen} result through the arity-aware batch path.
     * The payload is decoded with the screen's effective answer count (cached
     * at open time — never recomputed from tab completion here), each answer is
     * validated against its corresponding answer-bearing tag, and the batch is
     * submitted with that expected count.
     */
    private void handleDialogResult(
            Player player, PromptTag tag, ScreenResult result, DialogScreen dialogScreen) {
        int expected = dialogScreen.effectiveAnswerCount();
        var answers = decodeAnswers(result.answer(), expected);
        if (answers == null) {
            // Defensive fallback: re-show prompt if the dialog payload is malformed.
            plugin.getPluginLogger().warn("Malformed dialog payload from dialog for "
                    + player.getName() + ": " + result.answer());
            showPrompt(player, tag);
            return;
        }
        var answerTags = answerBearingTags(tag);
        for (var i = 0; i < answers.size(); i++) {
            var subTag = i < answerTags.size() ? answerTags.get(i) : tag;
            if (!validateSubAnswer(player, answers.get(i), subTag, tag)) {
                plugin.getPluginLogger().debug("Validation failed for answer " + i
                        + " of dialog prompt for " + player.getName());
                showPrompt(player, tag);
                return;
            }
        }
        var submitted = engine.submitAnswers(player, answers, expected);
        if (submitted.isPresent()) {
            var sessionResult = submitted.get();
            plugin.getPluginLogger().debug("Session complete, dispatching: "
                    + sessionResult.assembledCommand());
            var dispatchContext = dispatchAssembledCommand(
                    player, sessionResult.assembledCommand());
            sendCompletedCommand(player, sessionResult.assembledCommand());
            engine.dispatchPCMs(player, sessionResult, false, dispatchContext);
        } else {
            plugin.getPluginLogger().debug("Dialog answers accepted, showing next prompt");
            showNextPrompt(player);
        }
    }

    /**
     * Decodes a compound RS/US payload into sub-answers, validates each
     * against the block-level constraints, and submits all at once. Used only
     * as a defensive fallback for compound tags that did not produce a
     * {@link DialogPromptScreen}; dialog screens route through
     * {@link #handleDialogResult}. TITLE/BODY layout rows never validate or
     * submit — only answer-bearing sub-tags occupy answer positions.
     */
    private void handleCompoundResult(Player player, PromptTag tag, String rawPayload) {
        var answerTags = answerBearingTags(tag);
        var answers = decodeAnswers(rawPayload, answerTags.size());
        if (answers == null) {
            // Defensive fallback: re-show prompt if compound payload is malformed.
            plugin.getPluginLogger().warn("Malformed compound payload from dialog for "
                    + player.getName() + ": " + rawPayload);
            showPrompt(player, tag);
            return;
        }
        for (var i = 0; i < answers.size(); i++) {
            var subTag = answerTags.get(i);
            if (!validateSubAnswer(player, answers.get(i), subTag, tag)) {
                plugin.getPluginLogger().debug("Validation failed for sub-answer " + i
                        + " of compound prompt for " + player.getName());
                showPrompt(player, tag);
                return;
            }
        }
        var submitted = engine.submitAnswers(player, answers, answerTags.size());
        if (submitted.isPresent()) {
            var sessionResult = submitted.get();
            plugin.getPluginLogger().debug("Session complete, dispatching: "
                    + sessionResult.assembledCommand());
            var dispatchContext = dispatchAssembledCommand(
                    player, sessionResult.assembledCommand());
            sendCompletedCommand(player, sessionResult.assembledCommand());
            engine.dispatchPCMs(player, sessionResult, false, dispatchContext);
        } else {
            plugin.getPluginLogger().debug("Compound answers accepted, showing next prompt");
            showNextPrompt(player);
        }
    }

    /**
     * Returns the tags whose answers are actually submitted for a prompt.
     *
     * <p>For compound tags this is the sub-tag list filtered to answer-bearing
     * kinds (everything except {@link DialogInputKind#TITLE} and
     * {@link DialogInputKind#BODY} — layout rows never validate or enter
     * answer history). Non-compound tags (including JSON preset references)
     * yield the tag itself, so validation falls back to the block-level
     * constraints exactly as before.
     */
    static List<PromptTag> answerBearingTags(PromptTag tag) {
        if (!tag.isCompound()) return List.of(tag);
        return tag.subTags().stream()
                .filter(sub -> DialogInputKind.parse(sub.filter()).isAnswerBearing())
                .toList();
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
                    player.sendMessage(i18n.get("validation.invalid_integer", player));
                    return false;
                }
            }
            case STRING -> {
                if (answer.isBlank()) {
                    plugin.getPluginLogger().debug("String validation failed (blank) for "
                            + player.getName());
                    player.sendMessage(i18n.get("validation.invalid_string", player));
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
                    player.sendMessage(ComponentUtil.mini(msg));
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
                    player.sendMessage(i18n.get("validation.invalid_integer", player));
                    return false;
                }
            }
            case STRING -> {
                if (answer.isBlank()) {
                    plugin.getPluginLogger().debug("String validation failed (blank) for "
                            + player.getName());
                    player.sendMessage(i18n.get("validation.invalid_string", player));
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
                    player.sendMessage(ComponentUtil.mini(msg));
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Dispatches the final command according to the player's active
     * dispatch mode (normal, console, or permission-attachment).
     */
    private PromptEngine.DispatchContext dispatchAssembledCommand(Player player, String cmd) {
        var uuid = player.getUniqueId();
        var mode = dispatchModes.remove(uuid);
        if (mode == null) mode = DispatchMode.NORMAL;
        var key = attachmentKeys.remove(uuid);
        var permissionSnapshot = mode == DispatchMode.ATTACHMENT
                ? capturePermissionSnapshot(key)
                : List.<String>of();
        var dispatchContext = switch (mode) {
            case CONSOLE -> new PromptEngine.DispatchContext(
                    dev.cyr1en.promptpaper.preset.ExecuteAs.CONSOLE, null, false);
            case ATTACHMENT -> new PromptEngine.DispatchContext(
                    dev.cyr1en.promptpaper.preset.ExecuteAs.PLAYER,
                    key,
                    true,
                    permissionSnapshot);
            default -> PromptEngine.DispatchContext.player();
        };
        plugin.getPluginLogger().debug("Dispatching for " + player.getName()
                + " mode=" + mode + " cmd=" + cmd);
        switch (mode) {
            case CONSOLE -> dispatchAsConsole(player, cmd);
            case ATTACHMENT -> dispatchWithAttachment(player, cmd, key, permissionSnapshot);
            default -> {
                var toExecute = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                try {
                    var task = player.getScheduler().run(plugin, scheduledTask -> {
                        try {
                            if (!player.performCommand(toExecute)) {
                                sendCommandFailure(player, toExecute, "dispatch returned false");
                            }
                        } catch (Exception e) {
                            var msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                            sendCommandFailure(player, toExecute, msg);
                        }
                    }, null);
                    if (task == null) sendCommandFailure(player, toExecute, "player retired");
                } catch (Exception e) {
                    sendCommandFailure(player, toExecute, e.getMessage());
                }
            }
        }
        return dispatchContext;
    }

    private void dispatchAsConsole(Player player, String cmd) {
        var toExecute = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        plugin.getPluginLogger().debug("Dispatching as console: " + toExecute);
        scheduler.runSync(() -> {
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toExecute)) {
                    sendCommandFailure(player, toExecute, "dispatch returned false");
                }
            } catch (Exception e) {
                sendCommandFailure(player, toExecute, e.getMessage());
            }
        });
    }

    /**
     * Temporarily grants permissions, dispatches the command as the
     * player, then revokes the attachment.
     */
    private void dispatchWithAttachment(
            Player player,
            String cmd,
            String permissionKey,
            List<String> permissionSnapshot) {
        if (permissionKey == null || permissionKey.isBlank()) {
            plugin.getPluginLogger().err("Refusing attachment dispatch with an invalid permission key");
            sendCommandFailure(player, cmd, "invalid permission attachment");
            return;
        }
        if (permissionSnapshot == null || permissionSnapshot.isEmpty()) {
            plugin.getPluginLogger().err("Refusing attachment dispatch for unknown permission key: "
                    + permissionKey);
            sendCommandFailure(player, cmd, "invalid permission attachment");
            return;
        }
        var config = plugin.getConfigLoader().getConfig();
        var permissions = permissionSnapshot.toArray(new String[0]);
        var toExecute = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        plugin.getPluginLogger().debug("Dispatching with attachment key="
                + permissionKey + " perms=" + java.util.Arrays.toString(permissions));
        try {
            var scheduled = player.getScheduler().run(plugin, scheduledTask -> {
                var attachment = player.addAttachment(plugin);
                if (attachment == null) {
                    sendCommandFailure(player, toExecute, "unable to create permission attachment");
                    return;
                }
                var removed = new java.util.concurrent.atomic.AtomicBoolean();
                Runnable remove = () -> {
                    if (removed.compareAndSet(false, true)) {
                        try {
                            player.removeAttachment(attachment);
                        } catch (Exception e) {
                            plugin.getPluginLogger().debug("Unable to remove permission attachment");
                        }
                    }
                };
                boolean removalScheduled = false;
                boolean failed = false;
                try {
                    for (var perm : permissions) attachment.setPermission(perm, true);
                    attachment.getPermissible().recalculatePermissions();
                    plugin.getPluginLogger().debug("Dispatching with attachment: player="
                            + player.getName() + " perms=" + permissions.length);
                    if (!Bukkit.dispatchCommand(player, toExecute)) {
                        failed = true;
                        sendCommandFailure(player, toExecute, "dispatch returned false");
                    }
                } catch (Exception e) {
                    failed = true;
                    sendCommandFailure(player, toExecute, e.getMessage());
                }
                if (!failed && !removed.get() && config != null
                        && config.permissionAttachmentTicks() > 0) {
                    try {
                        var removalTask = player.getScheduler().runDelayed(
                                plugin,
                                scheduledRemoval -> remove.run(),
                                () -> {},
                                config.permissionAttachmentTicks());
                        removalScheduled = removalTask != null;
                    } catch (Exception e) {
                        plugin.getPluginLogger().debug("Attachment removal scheduling failed: "
                                + e.getMessage());
                    }
                }
                if (!removalScheduled) remove.run();
            }, null);
            if (scheduled == null) {
                plugin.getPluginLogger().debug("Attachment dispatch skipped for retired player");
            }
        } catch (Exception e) {
            sendCommandFailure(player, toExecute, e.getMessage());
        }
    }

    private void sendCommandFailure(Player player, String command, String detail) {
        var message = detail != null ? detail : "unknown error";
        plugin.getPluginLogger().info("Command dispatch failed for '" + command + "': " + message);
        try {
            player.getScheduler().run(
                    plugin,
                    scheduledTask -> player.sendMessage(plugin.getConfigLoader().getI18n().get(
                            "prompt.error.command_failed",
                            player,
                            Placeholder.of("message", message))),
                    null);
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Unable to send command failure feedback: " + e.getMessage());
        }
    }

    public boolean hasActiveScreen(Player player) {
        return activeScreens.containsKey(player.getUniqueId());
    }

    public boolean hasChatScreen(Player player) {
        var screen = activeScreens.get(player.getUniqueId());
        if (screen instanceof TitleWrapperScreen wrapper) {
            screen = wrapper.delegate();
        }
        return screen instanceof ChatPromptScreen;
    }

    /**
     * Cancels the active screen, timeout, and session for the player.
     */
    public void cancelAll(Player player) {
        cancelAll(player, false);
    }

    /**
     * Cancels the active screen, timeout, and session for the player, optionally notifying
     * players whose active prompt was cancelled.
     */
    public void cancelAll(Player player, boolean notifyCancelled) {
        var hadActiveSession = engine.hasActiveSession(player);
        teardown(player, CancelReason.MANUAL, true, notifyCancelled && hadActiveSession);
        plugin.getPluginLogger().debug("Cancelled all for " + player.getName());
    }

    /** Clears state after a player scheduler retires without invoking player APIs. */
    public void discardState(UUID uuid) {
        if (uuid == null) return;
        cancelTimeout(uuid);
        var screen = activeScreens.remove(uuid);
        if (screen instanceof TitleWrapperScreen wrapper) {
            wrapper.invalidateCallbacks();
        } else if (screen instanceof PlayerUIScreen playerUIScreen) {
            playerUIScreen.invalidateCallbacks();
        }
        dispatchModes.remove(uuid);
        attachmentKeys.remove(uuid);
        engine.discard(uuid);
    }

    private void teardown(
            Player player, CancelReason reason, boolean closeScreen, boolean notifyCancelled) {
        var uuid = player.getUniqueId();
        if (!teardownInProgress.add(uuid)) return;
        try {
            cancelTimeout(uuid);
            var screen = activeScreens.remove(uuid);
            if (closeScreen && screen != null) {
                try {
                    screen.close();
                } catch (Exception e) {
                    plugin.getPluginLogger().debug("Unable to close screen for " + uuid + ": "
                            + e.getMessage());
                }
            }
            var dispatchContext = takeDispatchContext(uuid);
            engine.cancel(player, reason, dispatchContext);
            if (notifyCancelled && plugin.getConfigLoader().getConfig().showCancelled()) {
                player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.cancelled", player));
            }
        } finally {
            teardownInProgress.remove(uuid);
        }
    }

    private PromptEngine.DispatchContext takeDispatchContext(UUID uuid) {
        var mode = dispatchModes.remove(uuid);
        var key = attachmentKeys.remove(uuid);
        if (mode == DispatchMode.CONSOLE) {
            return new PromptEngine.DispatchContext(
                    dev.cyr1en.promptpaper.preset.ExecuteAs.CONSOLE, null, false);
        }
        if (mode == DispatchMode.ATTACHMENT) {
            var permissionSnapshot = capturePermissionSnapshot(key);
            return new PromptEngine.DispatchContext(
                    dev.cyr1en.promptpaper.preset.ExecuteAs.PLAYER,
                    key,
                    true,
                    permissionSnapshot);
        }
        return PromptEngine.DispatchContext.player();
    }

    /** Captures the exact attachment list at the session completion/cancellation boundary. */
    private List<String> capturePermissionSnapshot(String permissionKey) {
        if (permissionKey == null || permissionKey.isBlank()) return List.of();
        try {
            var config = plugin.getConfigLoader().getConfig();
            var permissions = config == null ? null : config.getPermissionAttachment(permissionKey);
            if (permissions == null || permissions.length == 0) return List.of();
            return List.copyOf(java.util.Arrays.asList(permissions));
        } catch (Exception e) {
            plugin.getPluginLogger().debug(
                    "Unable to capture permission attachment key " + permissionKey + ": "
                            + e.getMessage());
            return List.of();
        }
    }

    private void sendCompletedCommand(Player player, String command) {
        if (plugin.getConfigLoader().getConfig().showCompleted()) {
            player.sendMessage(net.kyori.adventure.text.Component.text(command));
        }
    }

    /**
     * Schedules a timeout that auto-cancels the session if the player
     * does not respond within the configured duration.
     */
    private void scheduleTimeout(Player player) {
        cancelTimeout(player);
        var timeoutSecs = plugin.getConfigLoader().getConfig().promptTimeout();
        if (timeoutSecs <= 0) return;
        var uuid = player.getUniqueId();
        var token = timeoutSequence.incrementAndGet();
        timeoutTokens.put(uuid, token);
        plugin.getPluginLogger().debug("Scheduling timeout for " + player.getName()
                + " in " + timeoutSecs + "s");
        try {
            var task = player.getScheduler().runDelayed(
                    plugin,
                    scheduledTask -> {
                        if (!timeoutTokens.remove(uuid, token)) return;
                        timeoutTasks.remove(uuid);
                        var session = engine.getSession(player);
                        if (session.isPresent() && session.get().isActive()) {
                            plugin.getPluginLogger().debug("Timeout triggered for " + player.getName());
                            teardown(player, CancelReason.MANUAL, true, false);
                            if (plugin.getConfigLoader().getConfig().showCancelled()) {
                                player.sendMessage(plugin.getConfigLoader().getI18n().get("prompt.timed_out", player));
                            }
                        }
                    },
                    () -> discardTimeoutState(uuid, token),
                    timeoutSecs * 20L);
            if (task == null) {
                discardTimeoutState(uuid, token);
            } else if (timeoutTokens.get(uuid) == token) {
                timeoutTasks.put(uuid, task::cancel);
            } else {
                task.cancel();
            }
        } catch (Throwable t) {
            discardTimeoutState(uuid, token);
        }
    }

    private void cancelTimeout(Player player) {
        cancelTimeout(player.getUniqueId());
    }

    private void cancelTimeout(UUID uuid) {
        timeoutTokens.remove(uuid);
        var task = timeoutTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private void discardTimeoutState(UUID uuid, long token) {
        if (timeoutTokens.remove(uuid, token)) {
            discardState(uuid);
        }
    }
}
