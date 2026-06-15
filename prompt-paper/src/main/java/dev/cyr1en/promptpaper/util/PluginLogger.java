package dev.cyr1en.promptpaper.util;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.CommandPrompterConfig;
import java.awt.Color;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

/**
 * Configurable logger with optional ANSI color gradients and automatic
 * caller-class tagging for debug messages. Supports a {@code fancy} mode
 * with colorized prefixes and a plain-text fallback.
 */
public class PluginLogger {

    private static final StackWalker STACK_WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final CommandPrompter plugin;
    private volatile String prefix;
    private volatile String debugPrefix;
    private volatile boolean isFancy;
    private volatile boolean debugMode;

    private final ColorGradient normalGrad;
    private final ColorGradient debugGrad;

    public PluginLogger(CommandPrompter plugin) {
        this.plugin = plugin;
        normalGrad = new ColorGradient(new Color(198, 160, 246), new Color(138, 173, 244));
        debugGrad = new ColorGradient(new Color(238, 153, 160), new Color(245, 169, 127));
        isFancy = true;
        debugMode = false;
        rebuildPrefixes("[Prompter]");
    }

    /** Rebuilds prefixes and debug flag from the current config values. */
    public void reload(CommandPrompterConfig config) {
        isFancy = config.fancyLogger();
        debugMode = config.debugMode();
        rebuildPrefixes(config.promptPrefix());
    }

    /** Reloads configuration from the plugin's current config file. */
    public void reload() {
        reload(plugin.getConfigLoader().getConfig());
    }

    private void rebuildPrefixes(String rawPrefix) {
        var plainPrefix = MINI_MESSAGE.stripTags(rawPrefix).trim();
        var debugName = plainPrefix.endsWith("]")
                ? plainPrefix.substring(0, plainPrefix.length() - 1) + "-Debug]"
                : plainPrefix + "-Debug";
        var sep = isFancy ? ansi(166, 218, 149, ">>") : ">>";
        var normal = isFancy ? makeGradient(plainPrefix, normalGrad) : plainPrefix;
        var debug = isFancy ? makeGradient(debugName, debugGrad) : debugName;
        this.prefix = normal + " " + sep + " ";
        this.debugPrefix = debug + " " + sep + " ";
    }

    private String makeGradient(String text, ColorGradient grad) {
        var colors = grad.getGradient(text.length());
        var sb = new StringBuilder();
        for (int i = 0; i < colors.length; i++)
            sb.append(ansi(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), text.charAt(i)));
        return sb.toString();
    }

    private static String ansi(int r, int g, int b, Object text) {
        return "\u001B[38;2;" + r + ";" + g + ";" + b + "m" + text + "\u001B[0m";
    }

    private static String wrapAnsi(int r, int g, int b, String msg) {
        return "\u001B[38;2;" + r + ";" + g + ";" + b + "m" + msg + "\u001B[0m";
    }

    // ================================================================
    // Public API — same signatures as original
    // ================================================================

    /** Logs an informational message with the standard prefix. */
    public void info(String msg, Object... args) {
        log(prefix, Level.INFO, msg, args);
    }

    /** Logs a warning message, colorized in fancy mode. */
    public void warn(String msg, Object... args) {
        var colored = isFancy ? wrapAnsi(255, 195, 113, msg) : msg;
        log(prefix, Level.WARNING, colored, args);
    }

    /** Logs a severe/error message, colorized red in fancy mode. */
    public void err(String msg, Object... args) {
        var colored = isFancy ? wrapAnsi(255, 50, 21, msg) : msg;
        log(prefix, Level.SEVERE, colored, args);
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
        msg = "[" + callerName + "] - " + msg;
        var colored = isFancy ? wrapAnsi(238, 212, 159, msg) : msg;
        log(debugPrefix, Level.INFO, colored, args);
    }

    private void log(String prefix, Level level, String msg, Object... args) {
        var formatted = FormatUtil.safeFormat(msg, args);
        Bukkit.getLogger().log(level, prefix + formatted);
    }

    // ================================================================
    // Color gradient (reusable, copied verbatim from original)
    // ================================================================

    /**
     * Linear-interpolated color gradient between two anchor colors,
     * used to generate per-character ANSI coloring for log prefixes.
     */
    public record ColorGradient(Color c1, Color c2) {

        /** Returns an array of {@code segmentCount} interpolated colors spanning the gradient. */
        public Color[] getGradient(int segmentCount) {
            var colors = new Color[segmentCount];
            var seg = 1.0F / segmentCount;
            var currSeg = 0.0F;
            for (int i = 0; i < segmentCount; i++) {
                colors[i] = getPercentGradient(currSeg);
                currSeg += seg;
            }
            return colors;
        }

        /** Returns the color at the given position (0.0–1.0) along the gradient. */
        public Color getPercentGradient(float percent) {
            if (percent < 0 || percent > 1)
                return Color.WHITE;
            return new Color(
                    lerp(c1.getRed(), c2.getRed(), percent),
                    lerp(c1.getGreen(), c2.getGreen(), percent),
                    lerp(c1.getBlue(), c2.getBlue(), percent));
        }

        private static int lerp(int f1, int f2, float percent) {
            return Math.round(f1 + percent * (f2 - f1));
        }
    }
}
