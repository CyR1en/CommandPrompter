package dev.cyr1en.promptpaper.validation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CompoundedValidatorTest {

    private RegexValidator passingAnd() {
        var v = new RegexValidator("int", Pattern.compile("^\\d+$"), "fail", null);
        return v;
    }

    private RegexValidator failingAnd() {
        var v = new RegexValidator("str", Pattern.compile("[A-Za-z]+"), "fail", null);
        return v;
    }

    private RegexValidator passingOr() {
        var v = new RegexValidator("or1", Pattern.compile("yes|no"), "fail", null);
        v.setType(CompoundableValidator.Type.OR);
        return v;
    }

    @Test
    void allAndPassWhenAllMatch() {
        var c = new CompoundedValidator("test", "fail", null,
                passingAnd(), passingAnd());
        assertTrue(c.validate("42"));
    }

    @Test
    void anyAndFailWhenOneFails() {
        var c = new CompoundedValidator("test", "fail", null,
                passingAnd(), failingAnd());
        assertFalse(c.validate("42"));
    }

    @Test
    void orPassesWhenAnyMatches() {
        var c = new CompoundedValidator("test", "fail", null,
                failingAnd(), passingOr());
        assertTrue(c.validate("yes"));
    }

    @Test
    void orFailsWhenNoneMatch() {
        var c = new CompoundedValidator("test", "fail", null,
                failingAnd(), passingOr());
        assertFalse(c.validate("xyz"));
    }

    @Test
    void nullInputReturnsFalse() {
        var c = new CompoundedValidator("test", "fail", null, passingAnd());
        assertFalse(c.validate(null));
    }

    @Test
    void blankInputReturnsFalse() {
        var c = new CompoundedValidator("test", "fail", null, passingAnd());
        assertFalse(c.validate(""));
    }

    @Test
    void emptyValidatorsAllPass() {
        var c = new CompoundedValidator("test", "fail", null);
        assertTrue(c.validate("anything"));
    }

    @Test
    void aliasReturnsConfiguredValue() {
        var c = new CompoundedValidator("myAlias", "fail", null, passingAnd());
        assertEquals("myAlias", c.alias());
    }

    @Test
    void messageOnFailReturnsConfiguredValue() {
        var c = new CompoundedValidator("x", "Bad stuff!", null, passingAnd());
        assertEquals("Bad stuff!", c.messageOnFail());
    }
}
