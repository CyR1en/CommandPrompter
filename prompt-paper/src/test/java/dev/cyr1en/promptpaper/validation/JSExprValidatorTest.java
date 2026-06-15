package dev.cyr1en.promptpaper.validation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import dev.cyr1en.promptpaper.MockBukkitTest;

class JSExprValidatorTest extends MockBukkitTest {

    @Test
    void trueExpressionPasses() {
        var v = new JSExprValidator("test", "true", "fail", null, plugin);
        assertTrue(v.validate("anything"));
    }

    @Test
    void falseExpressionFails() {
        var v = new JSExprValidator("test", "false", "fail", null, plugin);
        assertFalse(v.validate("anything"));
    }

    @Test
    void promptInputIsReplaced() {
        var v = new JSExprValidator("test",
                "'%prompt_input%' == 'hello'",
                "fail", null, plugin);
        assertTrue(v.validate("hello"));
        assertFalse(v.validate("world"));
    }

    @Test
    void nonBooleanExpressionFails() {
        var v = new JSExprValidator("test", "'just a string'", "fail", null, plugin);
        assertFalse(v.validate("x"));
    }

    @Test
    void blankExpressionReturnsFalse() {
        var v = new JSExprValidator("test", "   ", "fail", null, plugin);
        assertFalse(v.validate("x"));
    }

    @Test
    void aliasReturnsConfiguredValue() {
        var v = new JSExprValidator("myAlias", "true", "msg", null, plugin);
        assertEquals("myAlias", v.alias());
    }

    @Test
    void messageOnFailReturnsConfiguredValue() {
        var v = new JSExprValidator("x", "true", "Bad JS!", null, plugin);
        assertEquals("Bad JS!", v.messageOnFail());
    }
}
