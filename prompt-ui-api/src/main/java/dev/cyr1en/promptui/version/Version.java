package dev.cyr1en.promptui.version;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a parsed Minecraft version string (e.g., "1.21.4").
 * Implements {@link Comparable} for version ordering.
 */
public final class Version implements Comparable<Version> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-pre(\\d+))?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final int preRelease;

    private Version(int major, int minor, int patch, int preRelease) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
    }

    /**
     * Parses a version string into a Version.
     *
     * @param version the version string, e.g. "1.21.4" or "1.21"
     * @return the parsed Version
     * @throws IllegalArgumentException if the version string is not parseable
     */
    public static Version of(@NotNull String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version string: " + version);
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        int preRelease = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : Integer.MAX_VALUE;
        return new Version(major, minor, patch, preRelease);
    }

    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getPatch() { return patch; }
    public int getPreRelease() { return preRelease; }

    /**
     * Compares versions lexicographically by major, minor, patch, then pre-release number.
     * Pre-release versions sort before release versions of the same patch.
     */
    @Override
    public int compareTo(@NotNull Version other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.patch, other.patch);
        if (cmp != 0) return cmp;
        return Integer.compare(this.preRelease, other.preRelease);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Version other)) return false;
        return major == other.major && minor == other.minor
            && patch == other.patch && preRelease == other.preRelease;
    }

    @Override
    public int hashCode() {
        return (major << 24) ^ (minor << 16) ^ (patch << 8) ^ preRelease;
    }

    /**
     * Formats the version as a dot-separated string (e.g. "1.21.4" or "1.21.4-pre1").
     * Omits patch when zero, and omits pre-release suffix for release versions.
     */
    @Override
    public String toString() {
        if (preRelease != Integer.MAX_VALUE) {
            return major + "." + minor + "." + patch + "-pre" + preRelease;
        }
        if (patch == 0) {
            return major + "." + minor;
        }
        return major + "." + minor + "." + patch;
    }
}
