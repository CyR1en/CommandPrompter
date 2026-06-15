package dev.cyr1en.promptcore.parser;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CommandLineParserTabTest {

  private final CommandLineParser parser = new CommandLineParser();

  @Test
  void tabKindIsCapturedInFilterSlot() {
    var result = parser.parse("/cmd <d:tab:Player?>");
    var tag = result.promptTags().get(0);
    assertEquals("d", tag.key());
    assertEquals("tab", tag.filter());
    assertEquals("Player?", tag.displayText());
    assertFalse(tag.isCompound());
  }

  @Test
  void tabWithExplicitMaxIsCapturedVerbatim() {
    var result = parser.parse("/cmd <d:tab[5]:Player?>");
    var tag = result.promptTags().get(0);
    assertEquals("tab[5]", tag.filter());
  }

  @Test
  void tabInCompoundIsHardParserError() {
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse("/cmd <d:choice[a,b]:Sub && d:tab:Value>"));
    assertTrue(
        ex.getMessage().contains("d:tab"),
        "Error message must identify the offending feature: " + ex.getMessage());
  }

  @Test
  void tabInCompoundWithExplicitMaxIsAlsoRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/cmd <d:choice[a,b]:Sub && d:tab[3]:Value>"));
  }

  @Test
  void tabKindCaseInsensitive() {
    // Parser captures verbatim; downstream normalizes.
    var r1 = parser.parse("/cmd <d:TAB:Foo>");
    var r2 = parser.parse("/cmd <d:Tab[10]:Foo>");
    assertEquals("TAB", r1.promptTags().get(0).filter());
    assertEquals("Tab[10]", r2.promptTags().get(0).filter());
    // isTabFilter must accept both via the case-insensitive normalize.
    assertTrue(CommandLineParser.isTabFilter(r1.promptTags().get(0).filter()));
    assertTrue(CommandLineParser.isTabFilter(r2.promptTags().get(0).filter()));
  }
}
