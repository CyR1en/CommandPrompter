package com.cyr1en.commandprompter.util;

import com.cyr1en.kiso.mc.Version;
import org.bukkit.Bukkit;

public class ServerUtil {


    public static boolean isAtOrAbove(String versionString) {
        var current = parsedVersion();
        var target = Version.parse(versionString);
        return current.isNewerThan(target) || current.equals(target);
    }

    public static String version() {
        return Bukkit.getServer().getVersion();
    }

    public static Version parsedVersion() {
        var version = version();
        version = version.substring(version.indexOf("MC: ") + 4, version.length() - 1);
        return Version.parse(version);
    }

    public static boolean isMojangMapped() {
        var resolved = ServerType.resolve();
        var ver = parsedVersion();
        return (resolved == ServerType.Paper || resolved == ServerType.Purpur) && ver.isNewerThan(Version.parse("1.20.4"));
    }

    public static boolean BUNGEE_CHAT_AVAILABLE() {
        try {
            Class.forName("net.md_5.bungee.api.ChatColor");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static ServerType resolve() {
        return ServerType.resolve();
    }

    /**
     * Pretty much useless as of now. But I'm keeping it just in case we need
     * some logic for different server types in the future.
     */
    public enum ServerType {
        CraftBukkit,
        Spigot,
        Paper,
        Purpur,
        CatServer,
        Mohist,
        Other;

        private static ServerType resolved;

        public static ServerType resolve() {
            if (resolved != null) return resolved;

            for (ServerType type : values()) {
                var typeName = type.name().toLowerCase();
                var serverName = Bukkit.getServer().getName().toLowerCase();
                if (serverName.contains(typeName)) {
                    resolved = type;
                }
            }
            resolved = Other;
            return resolved;
        }
    }

}
