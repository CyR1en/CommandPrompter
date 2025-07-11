package com.cyr1en.commandprompter.util;

import com.cyr1en.commandprompter.CommandPrompter;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.logging.Level;

import static com.cyr1en.commandprompter.util.MMUtil.*;

public class PluginLogger {
    private static final String NORMAL_GRADIENT = "#654ea3:#eaafc8";
    private static final String DEBUG_GRADIENT = "#ff606d:#ffc371";

    private Component prefix;
    private Component debugPrefix;

    private final boolean debugMode;
    private final boolean isFancy;

    private final ComponentLogger componentLogger = ComponentLogger.logger("");

    public PluginLogger(CommandPrompter plugin, String prefix) {
        this.isFancy = plugin.getConfiguration().fancyLogger();
        this.debugMode = plugin.getConfiguration().debugMode();

        setPrefix(prefix);
    }

    public void setPrefix(String prefix) {
        var sep = isFancy ? mm("<#99d65a>{0}", ">>") : Component.text(">>");
        var normal = isFancy ? mm("<gradient:{0}>{1}</gradient>", NORMAL_GRADIENT, prefix) : Component.text(prefix);
        var debug = isFancy ? mm("<gradient:{0}>{1}</gradient>", DEBUG_GRADIENT, prefix + "-" + "Debug") : Component.text(prefix + "-" + "Debug");
        this.prefix = joinComponents(normal, sep);
        this.debugPrefix = joinComponents(debug, sep);
    }

    public void log(Level level, Component msg) {
        Component component = switch (level.getName()) {
            case "FINE" ->
                    isFancy ? joinComponents(debugPrefix, mm("<#f79459>{0}", plain(msg))) : Component.text(plain(msg));
            case "WARNING" ->
                    isFancy ? joinComponents(prefix, mm("<orange>{0}</orange>", plain(msg))) : Component.text(plain(msg));
            case "SEVERE" ->
                    isFancy ? joinComponents(prefix, mm("<red>{0}</red>", plain(msg))) : Component.text(plain(msg));
            default -> msg;
        };
        componentLogger.info(joinComponents(prefix, component));
    }

    public void info(Component msg) {
        log(Level.INFO, msg);
    }

    public void info(String msg, Object... args) {
        log(Level.INFO, Component.text(FormatUtil.format(msg, args)));
    }

    public void warn(Component msg) {
        log(Level.WARNING, msg);
    }

    public void warn(String msg, Object... args) {
        log(Level.WARNING, Component.text(FormatUtil.format(msg, args)));
    }

    public void err(Component msg) {
        log(Level.SEVERE, msg);
    }

    public void err(String msg, Object... args) {
        log(Level.SEVERE, Component.text(FormatUtil.format(msg, args)));
    }

    private Class<?> lastDebugClass;

    public void debug(String msg, Object... args) {
        if (debugMode) {
            var caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
            var callerAvailable = Objects.nonNull(caller) && !caller.getSimpleName().isBlank();
            if (callerAvailable)
                lastDebugClass = caller;

            msg = FormatUtil.format(msg, args);
            msg = callerAvailable ? FormatUtil.format("[{0}] - {1}", caller.getSimpleName(), msg)
                    : Objects.isNull(lastDebugClass) ? msg
                    : FormatUtil.format("[{0}?] - {1}", lastDebugClass.getSimpleName(), msg);
            log(Level.FINE, Component.text(msg));
        }
    }

}
