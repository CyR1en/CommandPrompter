package dev.cyr1en.promptpaper.screen.dialog;

import java.util.List;

/**
 * Shared encoder/decoder for the compound-answer payload used by
 * {@link dev.cyr1en.promptpaper.screen.DialogPromptScreen} and
 * {@link dev.cyr1en.promptpaper.screen.ScreenManager}.
 *
 * <p>Compound dialogs encode N sub-answers into a single string with ASCII
 * control characters as delimiters: a record-separator ({@code \u001E})
 * wraps the payload, and unit-separators ({@code \u001F}) join sub-answers.
 * User input should never contain these characters.
 *
 * <p>Lives in its own class so unit tests can verify the contract without
 * loading the Paper-bound {@code DialogPromptScreen} class.
 */
public final class AnswerEncoding {

    private static final char RECORD_SEPARATOR = '\u001E';
    private static final char UNIT_SEPARATOR = '\u001F';

    private AnswerEncoding() {}

    /**
     * Encode N answer strings as a single payload. A single-answer list
     * is returned as-is (no delimiters). A multi-answer list is wrapped
     * in record-separators and joined with unit-separators.
     */
    public static String encode(List<String> answers) {
        if (answers.size() == 1) return answers.get(0);
        var sb = new StringBuilder();
        sb.append(RECORD_SEPARATOR);
        for (var i = 0; i < answers.size(); i++) {
            if (i > 0) sb.append(UNIT_SEPARATOR);
            sb.append(answers.get(i));
        }
        sb.append(RECORD_SEPARATOR);
        return sb.toString();
    }

    /**
     * Decode a compound payload back into the list of sub-answers. Returns
     * {@code null} if the payload does not look like a compound payload
     * (no leading/trailing record-separator, or wrong sub-answer count).
     * An empty input returns an empty list.
     */
    public static List<String> decode(String payload, int expected) {
        if (payload == null || payload.isEmpty()) return List.of();
        if (payload.charAt(0) != RECORD_SEPARATOR) return null;
        if (payload.charAt(payload.length() - 1) != RECORD_SEPARATOR) return null;
        var inner = payload.substring(1, payload.length() - 1);
        var parts = inner.split(String.valueOf(UNIT_SEPARATOR), -1);
        if (parts.length != expected) return null;
        return List.of(parts);
    }
}
