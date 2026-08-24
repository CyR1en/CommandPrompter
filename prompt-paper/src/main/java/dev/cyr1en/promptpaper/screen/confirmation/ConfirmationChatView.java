package dev.cyr1en.promptpaper.screen.confirmation;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptui.ComponentUtil;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Interactive chat-based implementation of {@link ConfirmationView}.
 *
 * <p>Renders interactive Adventure components with clickable confirm and decline
 * buttons bound to single-use cryptographic nonces. All player messages are dispatched
 * via the player entity scheduler.</p>
 */
public class ConfirmationChatView implements ConfirmationView, Listener {

    public static final String COMMAND_PREFIX = "/commandprompter:response";
    public static final Component DEFAULT_CONFIRM_LABEL = ComponentUtil.mini("<green>[Confirm]</green>");
    public static final Component DEFAULT_CANCEL_LABEL = ComponentUtil.mini("<red>[Cancel]</red>");

    private final Plugin plugin;
    private final Player player;
    private final Component prompt;
    private final Component confirmLabel;
    private final Component cancelLabel;
    private final NonceResponseRegistry registry;
    private final long incarnation;
    private final long generation;
    private final int promptIndex;
    private final Duration ttl;

    private Consumer<ConfirmationOutcome> callback;
    private Consumer<Throwable> failureCallback;

    private String activeNonce;
    private boolean open;
    private boolean listenerRegistered;

    public ConfirmationChatView(
            Plugin plugin,
            Player player,
            Component prompt,
            Component confirmLabel,
            Component cancelLabel,
            NonceResponseRegistry registry,
            long incarnation,
            long generation,
            int promptIndex,
            Duration ttl) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.prompt = prompt != null ? prompt : Component.empty();
        this.confirmLabel = confirmLabel != null ? confirmLabel : DEFAULT_CONFIRM_LABEL;
        this.cancelLabel = cancelLabel != null ? cancelLabel : DEFAULT_CANCEL_LABEL;
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.incarnation = incarnation;
        this.generation = generation;
        this.promptIndex = promptIndex;
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
    }

    public ConfirmationChatView(
            Plugin plugin,
            Player player,
            Component prompt,
            NonceResponseRegistry registry,
            long incarnation,
            long generation,
            int promptIndex,
            Duration ttl) {
        this(
                plugin,
                player,
                prompt,
                DEFAULT_CONFIRM_LABEL,
                DEFAULT_CANCEL_LABEL,
                registry,
                incarnation,
                generation,
                promptIndex,
                ttl);
    }

    public ConfirmationChatView(
            Plugin plugin,
            Player player,
            String promptText,
            String confirmText,
            String cancelText,
            NonceResponseRegistry registry,
            long incarnation,
            long generation,
            int promptIndex,
            Duration ttl) {
        this(
                plugin,
                player,
                ComponentUtil.mini(promptText),
                confirmText != null ? ComponentUtil.mini(confirmText) : DEFAULT_CONFIRM_LABEL,
                cancelText != null ? ComponentUtil.mini(cancelText) : DEFAULT_CANCEL_LABEL,
                registry,
                incarnation,
                generation,
                promptIndex,
                ttl);
    }

    public ConfirmationChatView(
            Plugin plugin,
            Player player,
            String promptText,
            NonceResponseRegistry registry,
            long incarnation,
            long generation,
            int promptIndex,
            Duration ttl) {
        this(
                plugin,
                player,
                ComponentUtil.mini(promptText),
                DEFAULT_CONFIRM_LABEL,
                DEFAULT_CANCEL_LABEL,
                registry,
                incarnation,
                generation,
                promptIndex,
                ttl);
    }

    @Override
    public synchronized void open(Consumer<ConfirmationOutcome> callback) {
        if (open) {
            return;
        }
        this.callback = Objects.requireNonNull(callback, "callback must not be null");

        try {
            var binding = registry.register(
                    player.getUniqueId(),
                    incarnation,
                    generation,
                    promptIndex,
                    ttl,
                    this::handleDecision);
            this.activeNonce = binding.nonce();
            registerListener();
            this.open = true;

            var message = renderMessage(activeNonce);

            player.getScheduler().run(plugin, task -> {
                synchronized (this) {
                    if (!open) {
                        return;
                    }
                    player.sendMessage(message);
                }
            }, null);

        } catch (Throwable t) {
            this.open = false;
            cleanupNonce();
            unregisterListener();
            this.callback = null;
            if (failureCallback != null) {
                failureCallback.accept(t);
            } else if (t instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(t);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (!open) {
            return;
        }
        this.open = false;
        cleanupNonce();
        unregisterListener();
        this.callback = null;
    }

    @Override
    public synchronized boolean isOpen() {
        return open;
    }

    @Override
    public synchronized void onOpenFailure(Consumer<Throwable> failureCallback) {
        this.failureCallback = failureCallback;
    }

    /**
     * Handles an incoming confirmation decision from the nonce binding callback.
     *
     * <p>Maps {@link ConfirmationDecision#CONFIRM} to {@link ConfirmationOutcome.Confirmed}
     * and {@link ConfirmationDecision#DECLINE} to {@link ConfirmationOutcome.Declined}
     * exactly once, guarding against duplicate delivery.</p>
     *
     * @param decision the consumed decision
     */
    public void handleDecision(ConfirmationDecision decision) {
        Consumer<ConfirmationOutcome> cb = null;
        synchronized (this) {
            if (!open) {
                return;
            }
            this.open = false;
            cleanupNonce();
            unregisterListener();
            cb = this.callback;
            this.callback = null;
        }

        if (cb != null && decision != null) {
            var outcome = switch (decision) {
                case CONFIRM -> ConfirmationOutcome.confirmed();
                case DECLINE -> ConfirmationOutcome.declined();
            };
            cb.accept(outcome);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        Consumer<ConfirmationOutcome> cb = null;
        synchronized (this) {
            if (!open) {
                unregisterListener();
                return;
            }
            this.open = false;
            cleanupNonce();
            unregisterListener();
            cb = this.callback;
            this.callback = null;
        }

        if (cb != null) {
            cb.accept(ConfirmationOutcome.cancelled(CancelReason.MANUAL));
        }
    }

    /**
     * Renders the interactive message component containing the prompt and clickable buttons.
     *
     * @param nonce the cryptographic nonce for button actions
     * @return the Adventure component to send to the player
     */
    public Component renderMessage(String nonce) {
        Objects.requireNonNull(nonce, "nonce must not be null");

        var confirmCmd = COMMAND_PREFIX + " " + nonce + " " + ConfirmationDecision.CONFIRM.commandArg();
        var declineCmd = COMMAND_PREFIX + " " + nonce + " " + ConfirmationDecision.DECLINE.commandArg();

        var confirmBtn = confirmLabel.clickEvent(ClickEvent.runCommand(confirmCmd));
        if (confirmBtn.hoverEvent() == null) {
            confirmBtn = confirmBtn.hoverEvent(HoverEvent.showText(ComponentUtil.mini("<green>Click to confirm</green>")));
        }

        var declineBtn = cancelLabel.clickEvent(ClickEvent.runCommand(declineCmd));
        if (declineBtn.hoverEvent() == null) {
            declineBtn = declineBtn.hoverEvent(HoverEvent.showText(ComponentUtil.mini("<red>Click to decline</red>")));
        }

        if (Component.empty().equals(prompt)) {
            return confirmBtn.append(Component.space()).append(declineBtn);
        }

        return prompt
                .append(Component.space())
                .append(confirmBtn)
                .append(Component.space())
                .append(declineBtn);
    }

    private void cleanupNonce() {
        if (activeNonce != null) {
            registry.invalidateNonce(activeNonce);
            activeNonce = null;
        }
    }

    private synchronized void registerListener() {
        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }
    }

    private synchronized void unregisterListener() {
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    // -- Accessors for diagnostics and testing --

    public Plugin getPlugin() {
        return plugin;
    }

    public Player getPlayer() {
        return player;
    }

    public Component getPrompt() {
        return prompt;
    }

    public Component getConfirmLabel() {
        return confirmLabel;
    }

    public Component getCancelLabel() {
        return cancelLabel;
    }

    public NonceResponseRegistry getRegistry() {
        return registry;
    }

    public long getIncarnation() {
        return incarnation;
    }

    public long getGeneration() {
        return generation;
    }

    public int getPromptIndex() {
        return promptIndex;
    }

    public Duration getTtl() {
        return ttl;
    }

    public synchronized Optional<String> getActiveNonce() {
        return Optional.ofNullable(activeNonce);
    }

    public synchronized boolean isListenerRegistered() {
        return listenerRegistered;
    }
}
