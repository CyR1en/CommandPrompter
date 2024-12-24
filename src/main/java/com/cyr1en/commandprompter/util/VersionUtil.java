package com.cyr1en.commandprompter.util;

import org.bukkit.Bukkit;

public class VersionUtil {

    public static boolean isAtOrAbove(String targetVersion) {
        // "CraftBukkit version git-Spigot-093165d-8cd8197 (MC: 1.16.5)"
        String versionInfo = Bukkit.getServer().getVersion();
        String currentVersion = extractMinecraftVersion(versionInfo);

        return compareVersions(currentVersion, targetVersion) >= 0;
    }

    // get Minecraft Version, " (MC: 1.16.5)" ->  "1.16.5"
    private static String extractMinecraftVersion(String versionInfo) {
        int startIndex = versionInfo.indexOf("(MC: ") + 5;
        int endIndex = versionInfo.indexOf(")", startIndex);
        return versionInfo.substring(startIndex, endIndex).trim();
    }

    // Compare two versions, return 1 if currentVersion is greater than targetVersion, 0 if equal, -1 if less than
    private static int compareVersions(String currentVersion, String targetVersion) {
        String[] currentParts = currentVersion.split("\\.");
        String[] targetParts = targetVersion.split("\\.");

        int length = Math.max(currentParts.length, targetParts.length);
        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            int targetPart = i < targetParts.length ? Integer.parseInt(targetParts[i]) : 0;

            if (currentPart > targetPart) {
                return 1;
            } else if (currentPart < targetPart) {
                return -1;
            }
        }
        return 0;
    }
}
