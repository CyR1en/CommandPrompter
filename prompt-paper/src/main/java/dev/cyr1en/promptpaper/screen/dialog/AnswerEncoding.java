package dev.cyr1en.promptpaper.screen.dialog;

import java.util.List;

/**
 * Shared encoder/decoder for the dialog-answer payload used by
 * {@link dev.cyr1en.promptpaper.screen.DialogPromptScreen} and
 * {@link dev.cyr1en.promptpaper.screen.ScreenManager}.
 *
 * <p>Dialogs encode their N submitted answers into a single string with ASCII
 * control characters as delimiters: a record-separator ({@code \u001E})
 * wraps the payload, and unit-separators ({@code \u001F}) join sub-answers.
 * User input should never contain these characters.
 *
 * <p>Encoding is arity-aware and symmetric with {@link #decode}:
 *
 * <ul>
 *   <li>0 answers → the canonical zero-answer frame {@code \u001E\u001E}
 *   <li>1 answer → the answer verbatim (no framing)
 *   <li>N ≥ 2 answers → framed {@code \u001E a\u001Fb\u001E}
 * </ul>
 *
 * <p>Lives in its own class so unit tests can verify the contract without
 * loading the Paper-bound {@code DialogPromptScreen} class.
 */
public final class AnswerEncoding {

    private static final char RECORD_SEPARATOR = '\u001E';
    private static final char UNIT_SEPARATOR = '\u001F';
    private static final String ZERO_ANSWER_FRAME = "" + RECORD_SEPARATOR + RECORD_SEPARATOR;

    private AnswerEncoding() {}

    /**
     * Encode N answer strings as a single payload. A single-answer list
     * is returned as-is (no delimiters). A multi-answer list is wrapped
     * in record-separators and joined with unit-separators; an empty list
     * produces the canonical zero-answer frame.
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
     * Decode a payload back into the list of submitted answers for an expected
     * answer count. The contract is strict and symmetric with {@link #encode}:
     *
     * <ul>
     *   <li>{@code expected == 0}: accepts only the canonical zero-answer frame
     *       produced by {@code encode(List.of())} ({@code \u001E\u001E}).
     *   <li>{@code expected == 1}: accepts any unframed payload — including the
     *       empty string — as a single answer.
     *   <li>{@code expected >= 2}: requires a framed RS/US payload with exactly
     *       the expected number of sub-answers.
     * </ul>
     *
     * @return the decoded answers, or {@code null} when the payload is null,
     *     the expected count is negative, or the framing/count does not match
     *     the expected arity
     */
    public static List<String> decode(String payload, int expected) {
        if (payload == null || expected < 0) return null;
        if (expected == 0) {
            return payload.equals(ZERO_ANSWER_FRAME) ? List.of() : null;
        }
        if (expected == 1) {
            if (payload.indexOf(RECORD_SEPARATOR) >= 0 || payload.indexOf(UNIT_SEPARATOR) >= 0) {
                return null;
            }
            return List.of(payload);
        }
        if (payload.length() < 2) return null;
        if (payload.charAt(0) != RECORD_SEPARATOR) return null;
        if (payload.charAt(payload.length() - 1) != RECORD_SEPARATOR) return null;
        var inner = payload.substring(1, payload.length() - 1);
        var parts = inner.split(String.valueOf(UNIT_SEPARATOR), -1);
        if (parts.length != expected) return null;
        return List.of(parts);
    }
}
