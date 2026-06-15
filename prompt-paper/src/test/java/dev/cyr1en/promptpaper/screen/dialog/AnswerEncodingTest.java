package dev.cyr1en.promptpaper.screen.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the compound-answer payload format.
 *
 * <p>Lifted out of {@code DialogPromptScreen.encodeAnswers} / {@code
 * ScreenManager.decodeAnswers} so the encoding logic is testable without
 * loading the Paper-bound dialog classes. The class is split to make
 * MockBukkit-only unit coverage possible.
 */
class AnswerEncodingTest {

    private static final char RS = '\u001E';
    private static final char US = '\u001F';

    @Test
    void encodeSingleIsIdentity() {
        assertEquals("hello", AnswerEncoding.encode(List.of("hello")));
    }

    @Test
    void encodeEmptyListIsJustWrappers() {
        assertEquals("" + RS + RS, AnswerEncoding.encode(List.of()));
    }

    @Test
    void encodeTwoAnswersUsesUnitSeparator() {
        var encoded = AnswerEncoding.encode(List.of("a", "b"));
        assertEquals("" + RS + "a" + US + "b" + RS, encoded);
    }

    @Test
    void encodeThreeAnswersHasTwoSeparators() {
        var encoded = AnswerEncoding.encode(List.of("a", "b", "c"));
        assertEquals("" + RS + "a" + US + "b" + US + "c" + RS, encoded);
    }

    @Test
    void encodePreservesEmptyStringAnswers() {
        // The split with limit -1 in decode() must keep empty strings so
        // an empty answer slot (e.g. user hit confirm on an empty text
        // input) survives the round-trip.
        var encoded = AnswerEncoding.encode(List.of("", "b"));
        assertEquals("" + RS + "" + US + "b" + RS, encoded);
    }

    @Test
    void decodeEmptyPayloadReturnsEmptyList() {
        assertEquals(List.of(), AnswerEncoding.decode("", 0));
    }

    @Test
    void decodeNullPayloadReturnsEmptyList() {
        assertEquals(List.of(), AnswerEncoding.decode(null, 0));
    }

    @Test
    void decodeValidPayloadSplits() {
        var decoded = AnswerEncoding.decode("" + RS + "a" + US + "b" + US + "c" + RS, 3);
        assertEquals(List.of("a", "b", "c"), decoded);
    }

    @Test
    void decodeRoundTripPreservesEmptyString() {
        var encoded = AnswerEncoding.encode(List.of("", "b"));
        var decoded = AnswerEncoding.decode(encoded, 2);
        assertEquals(List.of("", "b"), decoded);
    }

    @Test
    void decodeMissingLeadingRecordSeparatorReturnsNull() {
        assertNull(AnswerEncoding.decode("a" + US + "b" + RS, 2));
    }

    @Test
    void decodeMissingTrailingRecordSeparatorReturnsNull() {
        assertNull(AnswerEncoding.decode(RS + "a" + US + "b", 2));
    }

    @Test
    void decodeWrongCountReturnsNull() {
        // Two answers in the payload, three expected.
        assertNull(AnswerEncoding.decode("" + RS + "a" + US + "b" + RS, 3));
    }

    @Test
    void encodeDecodeRoundTripsArbitraryContent() {
        // User-typed content that doesn't include the control characters
        // must round-trip cleanly. (Control characters in user input
        // are an attacker concern that the dialog screen does not address;
        // the encoder/decoder is content-agnostic for safe input.)
        var original = List.of("hello world", "goodbye world", "with spaces & \"quotes\"");
        var encoded = AnswerEncoding.encode(original);
        var decoded = AnswerEncoding.decode(encoded, original.size());
        assertEquals(original, decoded);
    }

    @Test
    void encodeNeverProducesTrailingUnitSeparator() {
        // The format guarantees US only between answers, never at the
        // end. This guards the round-trip from producing a spurious
        // empty trailing element.
        var encoded = AnswerEncoding.encode(List.of("only"));
        assertTrue(encoded.indexOf(US) < 0,
                "single-answer encoding must not contain a unit separator");
    }
}
