package dev.cyr1en.promptpaper.screen.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.cyr1en.promptpaper.MockBukkitTest;
import org.junit.jupiter.api.Test;

class DialogInputKindTest extends MockBukkitTest {

    @Test
    void nullFilterReturnsText() {
        assertEquals(DialogInputKind.TEXT, DialogInputKind.parse(null));
    }

    @Test
    void emptyFilterReturnsText() {
        assertEquals(DialogInputKind.TEXT, DialogInputKind.parse(""));
    }

    @Test
    void textReturnsText() {
        assertEquals(DialogInputKind.TEXT, DialogInputKind.parse("text"));
    }

    @Test
    void numReturnsNumber() {
        assertEquals(DialogInputKind.NUMBER, DialogInputKind.parse("num"));
    }

    @Test
    void choiceReturnsChoice() {
        assertEquals(DialogInputKind.CHOICE, DialogInputKind.parse("choice"));
    }

    @Test
    void numberReturnsNumber() {
        assertEquals(DialogInputKind.NUMBER, DialogInputKind.parse("number"));
    }

    @Test
    void numberrangeReturnsNumber() {
        assertEquals(DialogInputKind.NUMBER, DialogInputKind.parse("numberrange"));
    }

    @Test
    void unknownReturnsText() {
        assertEquals(DialogInputKind.TEXT, DialogInputKind.parse("slider"));
    }

    @Test
    void caseInsensitive() {
        assertEquals(DialogInputKind.NUMBER, DialogInputKind.parse("Num"));
        assertEquals(DialogInputKind.TEXT, DialogInputKind.parse("Text"));
        assertEquals(DialogInputKind.CHOICE, DialogInputKind.parse("Choice"));
    }

    @Test
    void stripsBracket() {
        assertEquals(DialogInputKind.TEXT, DialogInputKind.parse("text[100]"));
    }

    @Test
    void stripsBracketAndCase() {
        assertEquals(DialogInputKind.CHOICE, DialogInputKind.parse("Choice[opt1,opt2]"));
    }

    @Test
    void malformedBracketStillResolvesKind() {
        assertEquals(DialogInputKind.NUMBER, DialogInputKind.parse("num[malformed"));
    }

    // ============================== Tab-completion prompt ==============================

    @Test
    void tabReturnsTab() {
        assertEquals(DialogInputKind.TAB, DialogInputKind.parse("tab"));
    }

    @Test
    void tabWithBracketReturnsTab() {
        assertEquals(DialogInputKind.TAB, DialogInputKind.parse("tab[5]"));
    }

    @Test
    void tabIsCaseInsensitive() {
        assertEquals(DialogInputKind.TAB, DialogInputKind.parse("Tab"));
        assertEquals(DialogInputKind.TAB, DialogInputKind.parse("TAB"));
        assertEquals(DialogInputKind.TAB, DialogInputKind.parse("Tab[42]"));
    }

    @Test
    void tabDoesNotMisparseChoiceOptionsContainingTab() {
        // `choice[tab,foo,bar]` should resolve to CHOICE, not TAB. The
        // bracket-aware kind parser must not mistake the literal token
        // `tab` inside the options list for the TAB kind.
        assertEquals(DialogInputKind.CHOICE, DialogInputKind.parse("choice[tab,foo,bar]"));
    }
}
