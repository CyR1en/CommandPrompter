package dev.cyr1en.promptpaper.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FormatUtilTest {

    @Test
    void validStringFormatIsRecognized() {
        assertTrue(FormatUtil.validFormats("Hello %s!"));
    }

    @Test
    void validNumberFormatIsRecognized() {
        assertTrue(FormatUtil.validFormats("Number %d"));
    }

    @Test
    void nullStringIsInvalidFormat() {
        assertFalse(FormatUtil.validFormats(null));
    }

    @Test
    void safeFormatWithValidArgs() {
        assertEquals("Hello World", FormatUtil.safeFormat("Hello %s", "World"));
    }

    @Test
    void safeFormatWithTooManyArgs() {
        assertEquals("Hello World", FormatUtil.safeFormat("Hello %s", "World", "Extra"));
    }

    @Test
    void safeFormatHandlesInvalidSpecifierGracefully() {
        var result = FormatUtil.safeFormat("%q is not valid");
        assertNotNull(result);
    }

    @Test
    void escapeInvalidFormatsChangesInvalidSpecifiers() {
        var result = FormatUtil.escapeInvalidFormats("%abc is invalid");
        assertNotEquals("%abc is invalid", result);
    }

    @Test
    void countValidFormatSpecifiers() {
        assertEquals(2, FormatUtil.countValidFormatSpecifiers("%s %d"));
    }

    @Test
    void countValidSkipsPercentPercent() {
        assertEquals(0, FormatUtil.countValidFormatSpecifiers("%%"));
    }

    @Test
    void nullStringReturnsNull() {
        assertNull(FormatUtil.safeFormat(null));
    }

    @Test
    void ansiEscapeSequencesAreSkippedDuringValidation() {
        var str = "\u001B[38;2;255;0;0mHello %s\u001B[0m";
        assertTrue(FormatUtil.validFormats(str));
        var expected = "\u001B[38;2;255;0;0mHello World\u001B[0m";
        assertEquals(expected, FormatUtil.safeFormat(str, "World"));
    }

    @Test
    void safeFormatWithTooFewArgs() {
        String result = FormatUtil.safeFormat("Hello %s %s", "World");
        assertTrue(result.contains("World"));
    }
}
