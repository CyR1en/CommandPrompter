package com.cyr1en.commandprompter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.awt.*;
import java.util.logging.Level;

public class PluginLogger {

    private String prefix;
    private String plainPrefix;
    private String debugPrefix;

    private final ColorGradient normalGrad;
    private final ColorGradient debugGrad;

    private boolean debugMode = false;
    private boolean isFancy = true;
    private CommandPrompter plugin;

    public PluginLogger(CommandPrompter plugin, String prefix) {
        this.plugin = plugin;
        this.isFancy = plugin.getConfiguration().fancyLogger();
        this.debugMode = plugin.getConfiguration().debugMode();
        AnsiConsole.systemInstall();

        normalGrad = new ColorGradient(new Color(67, 205, 162), new Color(25, 92, 157));
        debugGrad = new ColorGradient(new Color(255, 96, 109), new Color(255, 195, 113));

        setPrefix(prefix);
    }

    public void ansiUninstall() {
        if (AnsiConsole.isInstalled())
            AnsiConsole.systemUninstall();
    }

    public void setPrefix(String prefix) {
        this.plainPrefix = prefix;
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
        if (msg.contains("%s"))
            msg = String.format(msg, args);
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

    public void debug(String msg, Object... args) {
        if (debugMode) {
            var str = new Ansi().fgRgb(255, 195, 113).a(msg).reset().toString();
            log(debugPrefix, Level.INFO, str, args);
        }
    }

    public void setDebugMode(boolean b) {
        debugMode = b;
    }

    public void bukkitWarn(String msg) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + getPrefix() + msg);
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
