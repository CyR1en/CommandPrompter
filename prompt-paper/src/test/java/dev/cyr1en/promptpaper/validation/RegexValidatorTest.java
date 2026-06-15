package dev.cyr1en.promptpaper.validation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RegexValidatorTest {

    @Test
    void matchingInputReturnsTrue() {
        var v = new RegexValidator("int", Pattern.compile("^\\d+$"), "Not a number", null);
        assertTrue(v.validate("42"));
        assertTrue(v.validate("0"));
        assertTrue(v.validate("123456789"));
    }

    @Test
    void nonMatchingInputReturnsFalse() {
        var v = new RegexValidator("alpha", Pattern.compile("[A-Za-z]+"), "Not alpha", null);
        assertFalse(v.validate("123"));
        assertFalse(v.validate("hello!"));
        assertFalse(v.validate(""));
    }

    @Test
    void nullInputReturnsFalse() {
        var v = new RegexValidator("test", Pattern.compile(".*"), "fail", null);
        assertFalse(v.validate(null));
    }

    @Test
    void nullPatternReturnsFalse() {
        var v = new RegexValidator("test", null, "fail", null);
        assertFalse(v.validate("anything"));
    }

    @Test
    void aliasReturnsConfiguredValue() {
        var v = new RegexValidator("myAlias", Pattern.compile(".*"), "msg", null);
        assertEquals("myAlias", v.alias());
    }

    @Test
    void messageOnFailReturnsConfiguredValue() {
        var v = new RegexValidator("x", Pattern.compile(".*"), "Bad input!", null);
        assertEquals("Bad input!", v.messageOnFail());
    }

    @Test
    void inputPlayerReturnsNullWhenNone() {
        var v = new RegexValidator("x", Pattern.compile(".*"), "msg", null);
        assertNull(v.inputPlayer());
    }

    @Test
    void validatorTypeDefaultsToAnd() {
        var v = new RegexValidator("x", Pattern.compile(".*"), "msg", null);
        assertEquals(CompoundableValidator.Type.AND, v.getType());
    }

    @Test
    void validatorTypeCanBeSetToOr() {
        var v = new RegexValidator("x", Pattern.compile(".*"), "msg", null);
        v.setType(CompoundableValidator.Type.OR);
        assertEquals(CompoundableValidator.Type.OR, v.getType());
    }
}
