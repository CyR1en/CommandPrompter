package dev.cyr1en.promptpaper.custom;

import java.util.regex.Pattern;

/**
 * Narrow consumer interface for auditing custom screen registration and lifecycle events.
 *
 * <p>Ensures that audit sinks receive safe, sanitized key and owner names.</p>
 */
@FunctionalInterface
public interface CustomScreenAuditLogger {

    Pattern UNSAFE_CHARS = Pattern.compile("[^a-zA-Z0-9_.-]");

    /**
     * Receives an audit event.
     *
     * @param event the structured audit event
     */
    void onAuditEvent(CustomScreenAuditEvent event);

    /**
     * Returns a no-op audit logger.
     */
    static CustomScreenAuditLogger noop() {
        return event -> {};
    }

    /**
     * Sanitizes a plugin name or provider identifier for safe audit logging.
     *
     * @param input the raw name string
     * @return a sanitized string truncated to at most 64 characters
     */
    static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "unknown";
        }
        String stripped = UNSAFE_CHARS.matcher(input.trim()).replaceAll("_");
        if (stripped.length() > 64) {
            return stripped.substring(0, 64);
        }
        return stripped;
    }
}
