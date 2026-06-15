package dev.cyr1en.promptpaper.util;

import java.util.regex.Pattern;

/**
 * Utilities for safely handling {@link String#format} specifiers. Validates,
 * counts, escapes, and applies format strings while protecting against
 * malformed specifiers that would throw at runtime.
 */
public final class FormatUtil {

    public static final Pattern FORMAT_SPECIFIER_PATTERN =
            Pattern.compile("%([1-9]\\d*\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%](?![a-zA-Z%])");
    private static final Pattern ALL_FORMATS_PATTERN = Pattern.compile("%[^\\s%]*[a-zA-Z%]");
    public static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\\u001B\\[[;\\d]*m");

    private FormatUtil() {}

    /**
     * Returns whether every {@code %}-sequence in the string is a valid
     * {@link String#format} specifier, ignoring ANSI escape sequences.
     */
    public static boolean validFormats(String str) {
        if (str == null) return false;
        int lastEnd = 0;
        var ansiMatcher = ANSI_ESCAPE_PATTERN.matcher(str);
        while (ansiMatcher.find()) {
            if (!validFormatsSegment(str.substring(lastEnd, ansiMatcher.start())))
                return false;
            lastEnd = ansiMatcher.end();
        }
        return validFormatsSegment(str.substring(lastEnd));
    }

    private static boolean validFormatsSegment(String segment) {
        var matcher = ALL_FORMATS_PATTERN.matcher(segment);
        while (matcher.find()) {
            if (!FORMAT_SPECIFIER_PATTERN.matcher(matcher.group()).matches())
                return false;
        }
        return true;
    }

    /**
     * Counts valid format specifiers in the string, excluding {@code %%} and {@code %n}.
     */
    public static int countValidFormatSpecifiers(String str) {
        if (str == null) return 0;
        var matcher = FORMAT_SPECIFIER_PATTERN.matcher(str);
        int count = 0;
        while (matcher.find()) {
            var spec = matcher.group();
            if (!spec.equals("%%") && !spec.equals("%n")) count++;
        }
        return count;
    }

    /**
     * Escapes invalid {@code %}-sequences by doubling the leading percent sign
     * so they survive a subsequent {@code String.format} call.
     */
    public static String escapeInvalidFormats(String str) {
        if (str == null) return null;
        var sb = new StringBuilder();
        var matcher = ALL_FORMATS_PATTERN.matcher(str);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (!FORMAT_SPECIFIER_PATTERN.matcher(candidate).matches()) {
                String escaped = candidate.startsWith("%") ? "%%" + candidate.substring(1) : candidate;
                if (escaped.endsWith("%"))
                    escaped = escaped.substring(0, escaped.length() - 1) + "%%";
                matcher.appendReplacement(sb, escaped);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Applies {@code String.format} safely: escapes invalid specifiers first,
     * pads or truncates the argument array to match the specifier count,
     * and returns the original string unchanged on any formatting error.
     */
    public static String safeFormat(String str, Object... args) {
        if (str == null) return null;
        if (!validFormats(str))
            str = escapeInvalidFormats(str);

        int count = countValidFormatSpecifiers(str);
        Object[] usableArgs = args;
        if (args.length < count) {
            usableArgs = new Object[count];
            System.arraycopy(args, 0, usableArgs, 0, args.length);
        } else if (args.length > count) {
            usableArgs = new Object[count];
            System.arraycopy(args, 0, usableArgs, 0, count);
        }
        try {
            return String.format(str, usableArgs);
        } catch (Exception e) {
            return str;
        }
    }
}
