package com.cyr1en.commandprompter.gui.sign.version;

import com.cyr1en.commandprompter.gui.sign.exception.SignGUIVersionException;
import org.bukkit.Bukkit;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * A utility class to get the version wrapper for the server version.
 */
public class VersionMatcher {

    private static final Map<String, String> VERSIONS;
    private static final String NEWEST_VERSION = "1_21_R5";

    private static boolean initialized;
    private static VersionWrapper wrapper;

    static {
        VERSIONS = Map.of(
                "1.21.6", "1_21_R5",
                "1.21.7", "1_21_R5",
                "1.21.8", "1_21_R5"
        );
    }

    /**
     * Returns the appropriate version wrapper for the current server version.
     * If this method is called for the first time, the wrapper is initialized beforehand.
     *
     * @return The {@link VersionWrapper} instance
     * @throws SignGUIVersionException If the server version is not supported by this api or an error occured during initialization.
     */
    public static VersionWrapper getWrapper() throws SignGUIVersionException {
        if (!initialized) {
            initialized = true;
            return wrapper = initWrapper();
        } else if (wrapper == null) {
            throw new SignGUIVersionException("The previous attempt to initialize the version wrapper failed. " +
                    "This could be because this server version is not supported or " +
                    "because an error occured during initialization.");
        } else {
            return wrapper;
        }
    }

    /**
     * Internal method to initialize the version wrapper.
     */
    private static VersionWrapper initWrapper() throws SignGUIVersionException {
        String craftBukkitPackage = Bukkit.getServer().getClass().getPackage().getName();
        String version = craftBukkitPackage.contains(".v") ? craftBukkitPackage.split("\\.")[3].substring(1) :
                VERSIONS.getOrDefault(Bukkit.getBukkitVersion().split("-")[0], NEWEST_VERSION);

        String className = VersionWrapper.class.getPackage().getName() + ".Wrapper" + version;

        try {
            return (VersionWrapper) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException |
                 InvocationTargetException exception) {
            throw new SignGUIVersionException("Failed to load support for server version " + version, exception);
        } catch (ClassNotFoundException exception) {
            throw new SignGUIVersionException("SignGUI does not support the server version \"" + version + "\"", exception);
        }
    }
}
