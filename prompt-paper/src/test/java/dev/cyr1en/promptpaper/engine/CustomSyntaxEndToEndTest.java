package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptcore.ParserConfig;
import dev.cyr1en.promptcore.logic.transform.MathMode;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.execution.dispatch.ActionTrustLevel;
import dev.cyr1en.promptpaper.execution.postaction.template.ActionTemplateBindings;
import dev.cyr1en.promptpaper.execution.postaction.template.ActionTemplateCompiler;
import dev.cyr1en.promptpaper.execution.postaction.template.PapiReferenceResolver;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Custom Syntax End-To-End & Interception Tests")
class CustomSyntaxEndToEndTest extends MockBukkitTest {

  @Test
  @DisplayName("Custom syntax {...} prompts, [[0]], [[0|upper]], {!say [[0]]} works end-to-end")
  void testCustomSyntaxExampleEndToEnd() {
    TemplateSyntax customSyntax = new TemplateSyntax("[[", "]]", "|", "\\");
    ParserConfig customParserConfig = new ParserConfig("{", "}", "\\");

    when(config.syntaxPromptOpen()).thenReturn("{");
    when(config.syntaxPromptClose()).thenReturn("}");
    when(config.syntaxTemplateOpen()).thenReturn("[[");
    when(config.syntaxTemplateClose()).thenReturn("]]");
    when(config.syntaxTransformSeparator()).thenReturn("|");
    when(config.syntaxTemplateEscape()).thenReturn("\\");
    when(config.ignoreMiniMessage()).thenReturn(true);
    when(config.parserConfig()).thenReturn(customParserConfig);
    when(config.templateSyntax()).thenReturn(customSyntax);

    var engine = new PromptEngine(plugin, scheduler);
    var player = createPlayer();

    // 1. MiniMessage filter must NOT be attached when prompt delimiters are not < >
    assertNull(engine.getParser().getTagFilter());

    // 2. Intercept command with custom prompt delimiters and PCM tag {!say [[0]]}
    String cmd = "/test {Enter name} {!say [[0]]}";
    var interceptResult = engine.intercept(player, cmd);
    assertTrue(interceptResult.isPresent());
    var res = interceptResult.get();
    assertTrue(res.hasPrompts());
    assertEquals(1, res.promptCount());
    assertEquals(1, res.postCmds().size());
    assertEquals("say [[0]]", res.postCmds().get(0).command());

    // 3. Compile template and post-actions with custom syntax
    var compiledSimple =
        ActionTemplateCompiler.compile(
            "say [[0]]", customSyntax, ActionTrustLevel.UNTRUSTED_INLINE);
    var bindings =
        ActionTemplateBindings.of(
            List.of("Alice"), player.getName(), PapiReferenceResolver.empty());
    var renderSimple = compiledSimple.render(bindings, MathMode.LEGACY);
    assertTrue(renderSimple.isSuccess());
    assertEquals("say \"Alice\"", renderSimple.renderedText());

    // 4. Test transformer with custom transform separator |
    var compiledUpper =
        ActionTemplateCompiler.compile(
            "msg [[player|upper]] [[0|upper]]", customSyntax, ActionTrustLevel.UNTRUSTED_INLINE);
    var renderUpper = compiledUpper.render(bindings, MathMode.LEGACY);
    assertTrue(renderUpper.isSuccess());
    assertEquals(
        "msg " + player.getName().toUpperCase() + " \"ALICE\"", renderUpper.renderedText());
  }

  @Test
  @DisplayName("MiniMessage filter is active only when prompt delimiters are < >")
  void testMiniMessageFilterActiveOnlyForAngleBrackets() {
    when(config.syntaxPromptOpen()).thenReturn("<");
    when(config.syntaxPromptClose()).thenReturn(">");
    when(config.ignoreMiniMessage()).thenReturn(true);
    when(config.parserConfig()).thenReturn(ParserConfig.ANGLE_BRACKETS);

    var engine = new PromptEngine(plugin, scheduler);
    assertNotNull(engine.getParser().getTagFilter());

    when(config.syntaxPromptOpen()).thenReturn("[");
    when(config.syntaxPromptClose()).thenReturn("]");
    when(config.parserConfig()).thenReturn(new ParserConfig("[", "]", "\\"));

    engine.reloadParser();
    assertNull(engine.getParser().getTagFilter());
  }

  @Test
  @DisplayName("Reload retains current parser on failure")
  void testReloadRetainsCurrentParserOnFailure() {
    when(config.syntaxPromptOpen()).thenReturn("<");
    when(config.syntaxPromptClose()).thenReturn(">");
    when(config.parserConfig()).thenReturn(ParserConfig.ANGLE_BRACKETS);

    var engine = new PromptEngine(plugin, scheduler);
    var initialParser = engine.getParser();
    assertNotNull(initialParser);
    assertEquals("<", initialParser.getConfig().opening());

    // Simulate config returning null or invalid during reload
    when(configLoader.getConfig()).thenReturn(null);
    engine.reloadParser();

    // Parser must be retained
    assertSame(initialParser, engine.getParser());
  }
}
