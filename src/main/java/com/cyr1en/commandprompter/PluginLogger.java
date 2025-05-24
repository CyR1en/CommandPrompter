package com.cyr1en.commandprompter;

import org.bukkit.Bukkit;
import org.fusesource.jansi.Ansi;

import java.awt.*;
import java.util.Objects;
import java.util.UnknownFormatConversionException;
import java.util.logging.Level;

public class PluginLogger {

    private String prefix;
    private String debugPrefix;

    private final ColorGradient normalGrad;
    private final ColorGradient debugGrad;

    private final boolean debugMode;
    private final boolean isFancy;

    public PluginLogger(CommandPrompter plugin, String prefix) {
        this.isFancy = plugin.getConfiguration().fancyLogger();
        this.debugMode = plugin.getConfiguration().debugMode();

        // Spread love not war <3
        normalGrad = new ColorGradient(new Color(1, 88, 181), new Color(246, 206, 0));

        debugGrad = new ColorGradient(new Color(255, 96, 109), new Color(255, 195, 113));

        setPrefix(prefix);
    }

    public void setPrefix(String prefix) {
        var sep = isFancy ? new Ansi().fgRgb(153, 214, 90).a(">>").reset().toString() : ">>";
        var normal = isFancy ? makeGradient(prefix, normalGrad) : prefix;
        var debug = isFancy ? makeGradient(prefix + "-" + "Debug", debugGrad) : prefix + "-" + "Debug";
        this.prefix = String.format("%s %s ", normal, sep);
        this.debugPrefix = String.format("%s %s ", debug, sep);
    }

    private String makeGradient(String prefix, ColorGradient grad) {
        var colorGrad = grad.getGradient(prefix.length());
        var a = new Ansi();
        for (int i = 0; i < colorGrad.length; i++)
            a.fgRgb(colorGrad[i].getRGB()).a(prefix.charAt(i));

        return a.reset().toString();
    }

    public void log(String prefix, Level level, String msg, Object... args) {
        String pre = prefix == null ? getPrefix() : prefix;
        try {
            if (msg.matches("%s"))
                msg = String.format(msg, args);
        } catch (UnknownFormatConversionException ignore) {
        }
        Bukkit.getLogger().log(level, pre + msg);
    }

    public void log(Level level, String msg, Object... args) {
        log(null, level, msg, args);
    }

    public void info(String msg, Object... args) {
        log(Level.INFO, msg, args);
    }

    public void warn(String msg, Object... args) {
        var str = new Ansi().fgRgb(255, 195, 113).a(msg).reset().toString();
        log(Level.WARNING, str, args);
    }

    public void err(String msg, Object... args) {
        var str = new Ansi().fgRgb(255, 50, 21).a(msg).reset().toString();
        log(Level.SEVERE, str, args);
    }

    private Class<?> lastDebugClass;

    public void debug(String msg, Object... args) {
        var caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        var callerAvailable = Objects.nonNull(caller) && !caller.getSimpleName().isBlank();
        if (callerAvailable)
            lastDebugClass = caller;

        if (debugMode) {
            msg = callerAvailable ? String.format("[%s] - %s", caller.getSimpleName(), msg)
                    : Objects.isNull(lastDebugClass) ? msg
                    : String.format("[%s?] - %s", lastDebugClass.getSimpleName(), msg);
            var str = new Ansi().fgRgb(255, 195, 113).a(msg).reset().toString();
            log(debugPrefix, Level.INFO, str, args);
        }
    }

    private String getPrefix() {
        return prefix;
    }

    public record ColorGradient(Color c1, Color c2) {

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

        public Color getPercentGradient(float percent) {
            if (percent < 0 || percent > 1)
                return Color.WHITE;
            return new Color(
                    linInterpolate(c1.getRed(), c2.getRed(), percent),
                    linInterpolate(c1.getGreen(), c2.getGreen(), percent),
                    linInterpolate(c1.getBlue(), c2.getBlue(), percent));
        }

        private int linInterpolate(int f1, int f2, float percent) {
            var res = f1 + percent * (f2 - f1);
            return Math.round(res);
        }

    }
}
