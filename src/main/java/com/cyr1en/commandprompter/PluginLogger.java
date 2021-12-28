package com.cyr1en.commandprompter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class PluginLogger {

    public static final String ANSI_RESET = "\u001b[m";
    public static final String ANSI_GOLD_FOREGROUND = "\u001b[0;33m";
    public static final String ANSI_RED_FOREGROUND = "\u001b[0;31m";

    private String prefix = "";
    private String plainPrefix = "";
    private boolean debugMode = false;
    private JavaPlugin plugin;

    public PluginLogger(JavaPlugin plugin, String prefix){
        this.plainPrefix = prefix;
        this.prefix = String.format("[%s] ", prefix);
    }

    public void log(String prefix, Level level, String msg, Object... args) {
        String pre = prefix == null ? getPrefix() : prefix;
        if(msg.contains("%s"))
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
        log(Level.WARNING, ANSI_GOLD_FOREGROUND + msg + ANSI_RESET, args);
    }

    public void err(String msg, Object... args) {
        log(Level.SEVERE, ANSI_RED_FOREGROUND + msg + ANSI_RESET, args);
    }

    public void debug(String msg, Object... args) {
        if (debugMode) {
            String pre = String.format("[%s-debug] ", plainPrefix);
            log(pre, Level.INFO, ANSI_GOLD_FOREGROUND + msg + ANSI_RESET, args);
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
}
