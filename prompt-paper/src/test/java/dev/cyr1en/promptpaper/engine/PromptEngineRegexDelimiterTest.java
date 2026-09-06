package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.util.MiniMessageTagFilter;
import org.junit.jupiter.api.Test;

class PromptEngineRegexDelimiterTest extends MockBukkitTest {

  @Test
  void testWithCustomRegexDelimiters() {
    when(config.argumentRegex()).thenReturn("{.*?}");
    when(config.parserConfig()).thenReturn(new dev.cyr1en.promptcore.ParserConfig("{", "}", "\\"));
    when(config.ignoreMiniMessage()).thenReturn(false);

    var engine = new PromptEngine(plugin, scheduler);
    var player = createPlayer();

    // Should intercept when brace delimiters are used
    var resultBraces = engine.intercept(player, "/cmd {name} please");
    assertTrue(resultBraces.isPresent());
    assertTrue(resultBraces.get().hasPrompts());

    // Should NOT intercept when angle bracket delimiters are used
    var resultAngles = engine.intercept(player, "/cmd <name> please");
    assertFalse(resultAngles.isPresent());
  }

  @Test
  void testWithCustomRegexAndIgnoreMiniMessage() {
    when(config.argumentRegex()).thenReturn("{.*?}");
    when(config.parserConfig()).thenReturn(new dev.cyr1en.promptcore.ParserConfig("{", "}", "\\"));
    when(config.ignoreMiniMessage()).thenReturn(true);

    var engine = new PromptEngine(plugin, scheduler);
    var player = createPlayer();

    // Tag filter should be null because delimiters are not angle brackets
    assertNull(engine.getParser().getTagFilter());

    // Should intercept {red} because no filter is attached
    var result = engine.intercept(player, "/cmd {red} please");
    assertTrue(result.isPresent());
    assertTrue(result.get().hasPrompts());
  }

  @Test
  void testWithAngleBracketRegexAndIgnoreMiniMessage() {
    when(config.argumentRegex()).thenReturn("<.*?>");
    when(config.ignoreMiniMessage()).thenReturn(true);

    var engine = new PromptEngine(plugin, scheduler);

    // Tag filter should be a MiniMessageTagFilter
    var filter = engine.getParser().getTagFilter();
    assertNotNull(filter);
    assertInstanceOf(MiniMessageTagFilter.class, filter);
  }

  @Test
  void testWithInvalidRegexFallsBackToAngleBrackets() {
    when(config.argumentRegex()).thenReturn("ab");
    when(config.ignoreMiniMessage()).thenReturn(false);

    var engine = new PromptEngine(plugin, scheduler);
    var player = createPlayer();

    // Fallback should use angle brackets
    var resultAngles = engine.intercept(player, "/cmd <name> please");
    assertTrue(resultAngles.isPresent());
    assertTrue(resultAngles.get().hasPrompts());

    // Custom delimiters shouldn't work
    var resultBraces = engine.intercept(player, "/cmd {name} please");
    assertFalse(resultBraces.isPresent());
  }
}
