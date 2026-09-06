package dev.cyr1en.promptpaper.execution.dispatch;

import java.util.regex.Pattern;

/**
 * Sanitization utility to ensure logs and errors are C0-safe, bounded in length, and free of
 * sensitive raw commands.
 */
public final class DispatchSanitizer {

  private static final Pattern C0_CONTROLS = Pattern.compile("[\\u0000-\\u001F\\u007F]");
  private static final int MAX_DETAIL_LENGTH = 256;

  private DispatchSanitizer() {}

  /**
   * Strips leading slashes and surrounding whitespace from a command string.
   *
   * @param command the raw command string
   * @return the command without leading slash
   */
  public static String stripLeadingSlash(String command) {
    if (command == null) {
      return "";
    }
    String trimmed = command.strip();
    return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
  }

  /**
   * Sanitizes detail messages by stripping C0 control characters and capping length.
   *
   * @param raw the raw detail message
   * @return sanitized, bounded message
   */
  public static String sanitizeDetail(String raw) {
    if (raw == null || raw.isBlank()) {
      return "unknown error";
    }
    String cleaned = C0_CONTROLS.matcher(raw).replaceAll("").strip();
    if (cleaned.length() > MAX_DETAIL_LENGTH) {
      cleaned = cleaned.substring(0, MAX_DETAIL_LENGTH);
    }
    return cleaned.isEmpty() ? "unknown error" : cleaned;
  }
}
