package com.cyr1en.commandprompter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.logging.Level;

public class Logger {

    public static final String ANSI_RESET = "\u001b[m";
    public static final String ANSI_GOLD_FOREGROUND = "\u001b[0;33m";
    public static final String ANSI_RED_FOREGROUND = "\u001b[0;31m";

    private static String prefix = "";
    private static String plainPrefix = "";
    private static boolean debugMode = false;

    public static void init(String prefix){
        Logger.plainPrefix = prefix;
        Logger.prefix = String.format("[%s] ", prefix);
    }

    public static void log(String prefix, Level level, String msg, Object... args) {
        String pre = prefix == null ? getPrefix() : prefix;
        if(msg.contains("%s"))
            msg = String.format(msg, args);
        Bukkit.getLogger().log(level, pre + msg);
    }

    public static void log(Level level, String msg, Object... args) {
        log(null, level, msg, args);
    }

    public static void info(String msg, Object... args) {
        log(Level.INFO, msg, args);
    }

    public static void warn(String msg, Object... args) {
        log(Level.WARNING, ANSI_GOLD_FOREGROUND + msg + ANSI_RESET, args);
    }

    public static void err(String msg, Object... args) {
        log(Level.SEVERE, ANSI_RED_FOREGROUND + msg + ANSI_RESET, args);
    }

    public static void debug(String msg, Object... args) {
        if (debugMode) {
            String pre = String.format("[%s-debug] ", plainPrefix);
            log(pre, Level.INFO, ANSI_GOLD_FOREGROUND + msg + ANSI_RESET, args);
        }
    }

    public static void setDebugMode(boolean b) {
        debugMode = b;
    }

    public static void bukkitWarn(String msg) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + getPrefix() + msg);
    }

    private static String getPrefix() {
        return prefix;
    }
}
