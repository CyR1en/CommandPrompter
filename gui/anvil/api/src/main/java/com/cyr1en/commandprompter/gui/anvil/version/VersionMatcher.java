package com.cyr1en.commandprompter.gui.anvil.version;

import java.util.Map;
import org.bukkit.Bukkit;

/**
 * Matches the server's NMS version to its {@link VersionWrapper}
 *
 */
public class VersionMatcher {
    /**
     * Maps a Minecraft version string to the corresponding revision string
     */
    private static final Map<String, String> VERSION_TO_REVISION;

    static {
        VERSION_TO_REVISION = Map.of(
                "1.21.6", "1_21_R5",
                "1.21.7", "1_21_R5",
                "1.21.8", "1_21_R5"
        );
    }

    /* This needs to be updated to reflect the newest available version wrapper */
    private static final String FALLBACK_REVISION = "1_21_R5";

    /**
     * Matches the server version to it's {@link VersionWrapper}
     *
     * @return The {@link VersionWrapper} for this server
     * @throws IllegalStateException If the version wrapper failed to be instantiated or is unable to be found
     */
    public VersionWrapper match() {
        String craftBukkitPackage = Bukkit.getServer().getClass().getPackage().getName();

        String rVersion;
        if (!craftBukkitPackage.contains(".v")) { // cb package not relocated (i.e. paper 1.20.5+)
            final String version = Bukkit.getBukkitVersion().split("-")[0];
            rVersion = VERSION_TO_REVISION.getOrDefault(version, FALLBACK_REVISION);
        } else {
            rVersion = craftBukkitPackage.split("\\.")[3].substring(1);
        }

        try {
            return (VersionWrapper)
                    getWrapperClass(rVersion).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("AnvilGUI does not support server version \"" + rVersion + "\"", exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to instantiate version wrapper for version " + rVersion, exception);
        }
    }

    private Class<?> getWrapperClass(String version) throws ClassNotFoundException {
        String pkg = getClass().getPackage().getName();
        return Class.forName(pkg + ".Wrapper" + version);
    }

}
