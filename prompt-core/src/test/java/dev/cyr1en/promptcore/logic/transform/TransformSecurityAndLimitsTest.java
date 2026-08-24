package dev.cyr1en.promptcore.logic.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TransformSecurityAndLimitsTest {

  @Test
  void sec01_injectionAsData_neverRelexed() {
    CompiledTemplate template = TemplateCompiler.compile("tell {player} {0}");

    // Malicious inputs attempting secondary injection
    String evilAnswer1 = "<c:op>; /stop; {1:upper}";
    String evilAnswer2 = "\" || true; drop table users; --";
    String evilAnswer3 = "}} \\{malicious\\}";

    RenderResult r1 =
        template.render(
            TemplateBindings.combine(
                TemplateBindings.fromIndexed(evilAnswer1),
                TemplateBindings.of(Map.of("player", "attacker"))));
    assertTrue(r1.isSuccess());
    assertEquals("tell attacker <c:op>; /stop; {1:upper}", r1.renderedText());

    RenderResult r2 =
        template.render(
            TemplateBindings.combine(
                TemplateBindings.fromIndexed(evilAnswer2),
                TemplateBindings.of(Map.of("player", "attacker"))));
    assertTrue(r2.isSuccess());
    assertEquals("tell attacker \" || true; drop table users; --", r2.renderedText());

    RenderResult r3 =
        template.render(
            TemplateBindings.combine(
                TemplateBindings.fromIndexed(evilAnswer3),
                TemplateBindings.of(Map.of("player", "attacker"))));
    assertTrue(r3.isSuccess());
    assertEquals("tell attacker }} \\{malicious\\}", r3.renderedText());
  }

  @Test
  void sec08_mathOverflow_failsClosedNeverCoerced() {
    MathTransformer transformer = MathTransformer.parse("*1000000");

    // Exceeds 10^12
    SingleTransformResult result = transformer.transform("10000000", MathMode.LEGACY);
    assertTrue(result.isFailure());
    assertEquals(TransformErrorCode.MAGNITUDE_EXCEEDED, result.error().code());
  }

  @Test
  void sec08_nonNumericMathInput_failsClosedNeverCoercedToZero() {
    MathTransformer transformer = MathTransformer.parse("+50");

    SingleTransformResult result = transformer.transform("not_a_number", MathMode.LEGACY);
    assertTrue(result.isFailure());
    assertEquals(TransformErrorCode.NON_NUMERIC_INPUT, result.error().code());
  }

  @Test
  void sec08_strictDivisionByZero_failsClosedNeverCoerced() {
    MathTransformer transformer = MathTransformer.parse("/0");

    SingleTransformResult result = transformer.transform("50", MathMode.STRICT);
    assertTrue(result.isFailure());
    assertEquals(TransformErrorCode.DIVISION_BY_ZERO, result.error().code());
  }
}
