package dev.cyr1en.promptpaper.screen.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the dialog-answer payload format.
 *
 * <p>Lifted out of {@code DialogPromptScreen.encodeAnswers} / {@code ScreenManager.decodeAnswers}
 * so the encoding logic is testable without loading the Paper-bound dialog classes. The class is
 * split to make MockBukkit-only unit coverage possible.
 *
 * <p>The contract is arity-aware and symmetric:
 *
 * <ul>
 *   <li>0 answers {@literal ->} canonical zero frame {@code \u001E\u001E}
 *   <li>1 answer {@literal ->} the answer verbatim (no framing)
 *   <li>N ≥ 2 answers {@literal ->} framed {@code \u001E a\u001Fb\u001E}
 * </ul>
 */
class AnswerEncodingTest {

  private static final char RS = '\u001E';
  private static final char US = '\u001F';

  // ------------------------------------------------------------------
  // encode
  // ------------------------------------------------------------------

  @Test
  void encodeSingleIsIdentity() {
    assertEquals("hello", AnswerEncoding.encode(List.of("hello")));
  }

  @Test
  void encodeEmptyListIsCanonicalZeroFrame() {
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
  void encodeNeverProducesTrailingUnitSeparator() {
    // The format guarantees US only between answers, never at the
    // end. This guards the round-trip from producing a spurious
    // empty trailing element.
    var encoded = AnswerEncoding.encode(List.of("only"));
    assertTrue(encoded.indexOf(US) < 0, "single-answer encoding must not contain a unit separator");
  }

  // ------------------------------------------------------------------
  // decode — expected 0
  // ------------------------------------------------------------------

  @Test
  void decodeEmptyListRoundTripsAtExpectedZero() {
    assertEquals(List.of(), AnswerEncoding.decode(AnswerEncoding.encode(List.of()), 0));
  }

  @Test
  void decodeCanonicalZeroFrameAtExpectedZero() {
    assertEquals(List.of(), AnswerEncoding.decode("" + RS + RS, 0));
  }

  @Test
  void decodeEmptyStringAtExpectedZeroIsRejected() {
    // expected 0 accepts ONLY the canonical zero frame, not the empty
    // string (which is the single-answer encoding of an empty answer).
    assertNull(AnswerEncoding.decode("", 0));
  }

  @Test
  void decodeFramedSingleAnswerAtExpectedZeroIsRejected() {
    assertNull(AnswerEncoding.decode("" + RS + "a" + RS, 0));
  }

  // ------------------------------------------------------------------
  // decode — expected 1
  // ------------------------------------------------------------------

  @Test
  void decodeEmptyStringAtExpectedOneIsSingleAnswer() {
    assertEquals(List.of(""), AnswerEncoding.decode("", 1));
  }

  @Test
  void decodeSingleAnswerAtExpectedOne() {
    assertEquals(List.of("a"), AnswerEncoding.decode("a", 1));
  }

  @Test
  void decodeRoundTripAtExpectedOne() {
    var encoded = AnswerEncoding.encode(List.of("a"));
    assertEquals(List.of("a"), AnswerEncoding.decode(encoded, 1));
  }

  // ------------------------------------------------------------------
  // decode — expected >= 2
  // ------------------------------------------------------------------

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
  void decodeTwoAnswersRoundTripAtExpectedTwo() {
    var encoded = AnswerEncoding.encode(List.of("a", "b"));
    assertEquals(List.of("a", "b"), AnswerEncoding.decode(encoded, 2));
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
  void decodeUnframedPayloadAtExpectedTwoReturnsNull() {
    // A single-answer encoding is not a valid two-answer payload.
    assertNull(AnswerEncoding.decode("ab", 2));
  }

  @Test
  void decodeWrongCountReturnsNull() {
    // Two answers in the payload, three expected.
    assertNull(AnswerEncoding.decode("" + RS + "a" + US + "b" + RS, 3));
  }

  // ------------------------------------------------------------------
  // decode — rejection contract
  // ------------------------------------------------------------------

  @Test
  void decodeNullPayloadRejectedAtAnyExpectedCount() {
    assertNull(AnswerEncoding.decode(null, 0));
    assertNull(AnswerEncoding.decode(null, 1));
    assertNull(AnswerEncoding.decode(null, 2));
  }

  @Test
  void decodeNegativeExpectedRejected() {
    assertNull(AnswerEncoding.decode("x", -1));
  }

  @Test
  void decodeZeroFrameAtExpectedTwoReturnsNull() {
    assertNull(AnswerEncoding.decode("" + RS + RS, 2));
  }

  @Test
  void decodeTwoAnswerPayloadAtExpectedZeroReturnsNull() {
    // Wrong framing for the requested arity: a two-answer payload is not
    // the canonical zero-answer frame.
    assertNull(AnswerEncoding.decode("" + RS + "a" + US + "b" + RS, 0));
  }

  @Test
  void decodeFramedPayloadAtExpectedOneReturnsNull() {
    assertNull(AnswerEncoding.decode("" + RS + "a" + US + "b" + RS, 1));
  }

  @Test
  void decodeProtocolDelimiterAtExpectedOneReturnsNull() {
    assertNull(AnswerEncoding.decode("a" + US + "b", 1));
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
  void encodeDecodeRoundTripsAllArities() {
    assertEquals(List.of(), AnswerEncoding.decode(AnswerEncoding.encode(List.of()), 0));
    assertEquals(List.of("a"), AnswerEncoding.decode(AnswerEncoding.encode(List.of("a")), 1));
    assertEquals(
        List.of("x", "y"), AnswerEncoding.decode(AnswerEncoding.encode(List.of("x", "y")), 2));
    assertEquals(
        List.of("1", "2", "3"),
        AnswerEncoding.decode(AnswerEncoding.encode(List.of("1", "2", "3")), 3));
  }
}
