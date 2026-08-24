package dev.cyr1en.promptcore.logic.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CompiledTemplateTest {

  @Test
  void render_indexedAndNamedBindings() {
    CompiledTemplate template =
        TemplateCompiler.compile("give {player} diamond {0:math(*2)} {1:upper}");

    TemplateBindings bindings =
        TemplateBindings.combine(
            TemplateBindings.fromIndexed("10", "emerald"),
            TemplateBindings.of(Map.of("player", "Steve")));

    RenderResult result = template.render(bindings);
    assertTrue(result.isSuccess());
    assertEquals("give Steve diamond 20.00 EMERALD", result.renderedText());
    assertEquals("give Steve diamond 20.00 EMERALD", result.getOrThrow());
  }

  @Test
  void render_defaultFallback_whenUnboundOrBlank() {
    CompiledTemplate template = TemplateCompiler.compile("hello {0:default=\"world\"}");

    RenderResult res1 = template.render(TemplateBindings.of(Map.of()));
    assertTrue(res1.isSuccess());
    assertEquals("hello world", res1.renderedText());

    RenderResult res2 = template.render(TemplateBindings.fromIndexed(""));
    assertTrue(res2.isSuccess());
    assertEquals("hello world", res2.renderedText());

    RenderResult res3 = template.render(TemplateBindings.fromIndexed("there"));
    assertTrue(res3.isSuccess());
    assertEquals("hello there", res3.renderedText());
  }

  @Test
  void render_missingBinding_failsClosed() {
    CompiledTemplate template = TemplateCompiler.compile("give {0} diamond 1");

    RenderResult result = template.render(TemplateBindings.of(Map.of()));
    assertTrue(result.isFailure());
    assertEquals(TransformErrorCode.MISSING_BINDING, result.error().code());
  }

  @Test
  void render_inputTooLong_failsClosed() {
    CompiledTemplate template = TemplateCompiler.compile("say {0}");
    String longInput = "x".repeat(1025);

    RenderResult result = template.render(TemplateBindings.fromIndexed(longInput));
    assertTrue(result.isFailure());
    assertEquals(TransformErrorCode.INPUT_TOO_LONG, result.error().code());
  }

  @Test
  void render_outputTooLong_failsClosed() {
    CompiledTemplate template = TemplateCompiler.compile("prefix " + "a".repeat(1000) + " {0}");
    String input = "b".repeat(50); // Total > 1024

    RenderResult result = template.render(TemplateBindings.fromIndexed(input));
    assertTrue(result.isFailure());
    assertEquals(TransformErrorCode.OUTPUT_TOO_LONG, result.error().code());
  }

  @Test
  void render_controlCharacterInInput_failsClosed() {
    CompiledTemplate template = TemplateCompiler.compile("say {0}");
    String badInput = "hello\0world";

    RenderResult result = template.render(TemplateBindings.fromIndexed(badInput));
    assertTrue(result.isFailure());
    assertEquals(TransformErrorCode.CONTROL_CHARACTER_DETECTED, result.error().code());
  }

  @Test
  void render_collectsNotices() {
    CompiledTemplate template = TemplateCompiler.compile("div1={0:math(/0)} div2={1:math(/0)}");

    RenderResult result =
        template.render(TemplateBindings.fromIndexed("10", "20"), MathMode.LEGACY);

    assertTrue(result.isSuccess());
    assertEquals("div1=0 div2=0", result.renderedText());
    assertEquals(2, result.notices().size());
    assertEquals(TransformNotice.DIVISION_BY_ZERO_SUBSTITUTED, result.notices().get(0));
    assertEquals(TransformNotice.DIVISION_BY_ZERO_SUBSTITUTED, result.notices().get(1));
  }
}
