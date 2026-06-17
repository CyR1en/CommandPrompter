package dev.cyr1en.promptpaper.util;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.CommandPrompterConfig;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Configurable logger that emits MiniMessage-formatted output through
 * Paper's {@link ComponentLogger}. Supports a {@code fancy} mode with
 * color-gradient prefixes and level-based default colors, plus a
 * {@code debug} mode that automatically prepends the calling class
 * name via {@link StackWalker}.
 *
 * <p>All public methods take a {@link String} (with optional
 * {@link String#format}-style arguments) so callers can pass raw
 * MiniMessage markup like {@code <green>...</green>} or
 * {@code <gradient:#c6a0f6:#8aadf4>...</gradient>} directly. The string
 * is run through {@link FormatUtil#safeFormat(String, Object...)} and
 * then deserialized via {@link MiniMessage#miniMessage()} before being
 * joined to the active prefix and dispatched to the underlying
 * {@link ComponentLogger}.</p>
 */
public class PluginLogger {

    private static final StackWalker STACK_WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // MiniMessage gradient definitions (replaces the old per-character
    // ANSI ColorGradient generator).
    private static final String NORMAL_GRADIENT = "#c6a0f6:#8aadf4";
    private static final String DEBUG_GRADIENT = "#ee99a0:#f5a97f";

    private static final String PREFIX_DEFAULT = "[CommandPrompter]";

    private final CommandPrompter plugin;
    private final ComponentLogger componentLogger;

    private volatile Component prefix;
    private volatile Component debugPrefix;
    private volatile boolean isFancy;
    private volatile boolean debugMode;

    public PluginLogger(CommandPrompter plugin) {
        this.plugin = plugin;
        this.componentLogger = ComponentLogger.logger("");
        isFancy = true;
        debugMode = false;
        rebuildPrefixes();
    }

    /** Rebuilds prefixes and debug flag from the current config values. */
    public void reload(CommandPrompterConfig config) {
        isFancy = config.fancyLogger();
        debugMode = config.debugMode();
        rebuildPrefixes();
    }

    /** Reloads configuration from the plugin's current config file. */
    public void reload() {
        reload(plugin.getConfigLoader().getConfig());
    }

    private void rebuildPrefixes() {
        // Strip MiniMessage tags from the user-supplied prefix (so the
        // "Debug" variant is derived from the visible label, not the
        // markup) and compute the matching "[…-Debug]" name.
        var plainPrefix = PREFIX_DEFAULT;
        var debugName = plainPrefix.substring(0, plainPrefix.length() - 1) + "-Debug]";

        var sepText = ">>";
        var sep = isFancy
                ? MINI_MESSAGE.deserialize("<green>" + sepText + "</green>")
                : Component.text(sepText);
        var normal = isFancy
                ? MINI_MESSAGE.deserialize("<gradient:" + NORMAL_GRADIENT + ">" + plainPrefix + "</gradient>")
                : Component.text(plainPrefix);
        var debug = isFancy
                ? MINI_MESSAGE.deserialize("<gradient:" + DEBUG_GRADIENT + ">" + debugName + "</gradient>")
                : Component.text(debugName);

        this.prefix = normal.append(Component.space()).append(sep).append(Component.space());
        this.debugPrefix = debug.append(Component.space()).append(sep).append(Component.space());
    }

    // ================================================================
    // Public API
    // ================================================================

    /** Logs an informational message with the standard prefix. */
    public void info(String msg, Object... args) {
        emit(Level.INFO, prefix, msg, args);
    }

    /** Logs a warning message, colorized in fancy mode. */
    public void warn(String msg, Object... args) {
        var wrapped = isFancy ? "<gold>" + msg + "</gold>" : msg;
        emit(Level.WARNING, prefix, wrapped, args);
    }

    /** Logs a severe/error message, colorized red in fancy mode. */
    public void err(String msg, Object... args) {
        var wrapped = isFancy ? "<red>" + msg + "</red>" : msg;
        emit(Level.SEVERE, prefix, wrapped, args);
    }

    /**
     * Logs a debug message only when debug mode is enabled.
     * Automatically prepends the calling class name via {@link StackWalker}.
     */
    public void debug(String msg, Object... args) {
        if (!debugMode) return;
        var caller = STACK_WALKER.getCallerClass();
        var callerName = caller != null && !caller.getSimpleName().isBlank()
                ? caller.getSimpleName() : "?";
        var tagged = "[" + callerName + "] - " + msg;
        var wrapped = isFancy ? "<#eed49f>" + tagged + "</#eed49f>" : tagged;
        emit(Level.INFO, debugPrefix, wrapped, args);
    }

    // ================================================================
    // Internals
    // ================================================================

    /**
     * Formats {@code msg} via {@link FormatUtil#safeFormat(String, Object...)},
     * deserializes the result with {@link MiniMessage#miniMessage()}, appends
     * it to {@code pre}, and dispatches the combined component to the
     * underlying {@link ComponentLogger} at the given {@link Level}.
     */
    private void emit(Level level, Component pre, String msg, Object... args) {
        var formatted = FormatUtil.safeFormat(msg, args);
        var deserialized = MINI_MESSAGE.deserialize(formatted);
        var composed = pre.append(deserialized);
        switch (level.getName()) {
            case "SEVERE" -> componentLogger.error(composed);
            case "WARNING" -> componentLogger.warn(composed);
            case "FINE", "FINER", "CONFIG" -> componentLogger.debug(composed);
            case "FINEST" -> componentLogger.trace(composed);
            default -> componentLogger.info(composed);
        }
    }
}
