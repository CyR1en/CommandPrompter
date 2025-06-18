package com.cyr1en.commandprompter.util;

import java.util.regex.Pattern;

public class FormatUtil {
    public static final Pattern FORMAT_SPECIFIER_PATTERN =
            Pattern.compile("%([1-9]\\d*\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%](?![a-zA-Z%])");
    private static final Pattern ALL_FORMATS_PATTERN = Pattern.compile("%[^\\s%]*[a-zA-Z%]");

    public static boolean validFormats(String str) {
        if (str == null) return false;
        var matcher = ALL_FORMATS_PATTERN.matcher(str);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (!FORMAT_SPECIFIER_PATTERN.matcher(candidate).matches()) {
                return false;
            }
        }
        return true;
    }

    public static int countValidFormatSpecifiers(String str) {
        if (str == null) return 0;
        var matcher = FORMAT_SPECIFIER_PATTERN.matcher(str);
        int count = 0;
        while (matcher.find()) {
            String spec = matcher.group();
            if (!spec.equals("%%") && !spec.equals("%n")) {
                count++;
            }
        }
        return count;
    }

    public static String escapeInvalidFormats(String str) {
        if (str == null) return null;
        var sb = new StringBuilder();
        var matcher = ALL_FORMATS_PATTERN.matcher(str);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (!FORMAT_SPECIFIER_PATTERN.matcher(candidate).matches()) {
                String escaped = candidate.startsWith("%") ? "%%" + candidate.substring(1) : candidate;
                if (escaped.endsWith("%")) {
                    escaped = escaped.substring(0, escaped.length() - 1) + "%%";
                }
                matcher.appendReplacement(sb, escaped);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

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
