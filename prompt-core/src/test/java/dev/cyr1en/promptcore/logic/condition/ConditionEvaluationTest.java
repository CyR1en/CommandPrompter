package dev.cyr1en.promptcore.logic.condition;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConditionEvaluationTest {

  @Nested
  @DisplayName("Numeric Comparisons & Precision")
  class NumericComparisonTests {

    @ParameterizedTest
    @CsvSource({
      "10, ==, 10, true",
      "10.00, ==, 10, true",
      "10.5, ==, 10.50, true",
      "10, !=, 20, true",
      "10, !=, 10.0, false",
      "5, <, 10, true",
      "10, <, 10, false",
      "10, <=, 10, true",
      "10, <=, 15, true",
      "20, >, 10, true",
      "10, >, 10, false",
      "10, >=, 10, true",
      "10, >=, 5, true",
      "-5, <, 0, true",
      "-5.5, <, -5.0, true",
      "+15, ==, 15, true"
    })
    @DisplayName("Numeric operator evaluation")
    void testNumericOperators(String left, String op, String right, boolean expected) {
      Condition condition = Condition.compile("{0} " + op + " " + right);
      ConditionBindings bindings = ConditionBindings.ofAnswers(left);
      assertEquals(expected, condition.evaluate(bindings));
    }

    @Test
    @DisplayName("Comparison between two answer references")
    void testTwoAnswerRefs() {
      Condition condition = Condition.compile("{0} >= {1}");
      assertTrue(condition.evaluate(ConditionBindings.ofAnswers("100", "50")));
      assertTrue(condition.evaluate(ConditionBindings.ofAnswers("100", "100")));
      assertFalse(condition.evaluate(ConditionBindings.ofAnswers("50", "100")));
    }

    @Test
    @DisplayName("Fail closed with no coercion when left or right operand is non-numeric")
    void testNonNumericThrows() {
      Condition condition = Condition.compile("{0} > 10");
      assertThrows(
          ConditionEvaluationException.class,
          () -> condition.evaluate(ConditionBindings.ofAnswers("abc")));
      assertThrows(
          ConditionEvaluationException.class,
          () -> condition.evaluate(ConditionBindings.ofAnswers("")));
      assertThrows(
          ConditionEvaluationException.class,
          () -> condition.evaluate(ConditionBindings.ofAnswers("10.0.0")));
    }
  }

  @Nested
  @DisplayName("Case-Sensitive String Operators & Linear-Time Matching")
  class StringComparisonTests {

    @ParameterizedTest
    @CsvSource({
      "admin, equals, admin, true",
      "Admin, equals, admin, false",
      "hello world, contains, world, true",
      "hello world, contains, World, false",
      "hello world, startsWith, hello, true",
      "hello world, startsWith, Hello, false",
      "hello world, endsWith, world, true",
      "hello world, endsWith, World, false"
    })
    @DisplayName("String comparison operations")
    void testStringOperators(String left, String op, String right, boolean expected) {
      Condition condition = Condition.compile("{0} " + op + " \"" + right + "\"");
      ConditionBindings bindings = ConditionBindings.ofAnswers(left);
      assertEquals(expected, condition.evaluate(bindings));
    }

    @Test
    @DisplayName("String comparisons between answers")
    void testStringBetweenAnswers() {
      Condition condition = Condition.compile("{0} equals {1}");
      assertTrue(condition.evaluate(ConditionBindings.ofAnswers("test", "test")));
      assertFalse(condition.evaluate(ConditionBindings.ofAnswers("test", "Test")));
    }
  }

  @Nested
  @DisplayName("SEC-08: Injection As Data & Sandboxing")
  class InjectionAsDataTests {

    @Test
    @DisplayName("Answer containing boolean operators is treated as opaque string data")
    void testAnswerWithBooleanInjection() {
      // Condition checks if {0} equals "admin"
      Condition condition = Condition.compile("{0} equals \"admin\"");

      // Attacker passes injection payload: admin" || "a" equals "a
      String injection = "admin\" || \"a\" equals \"a";
      ConditionBindings bindings = ConditionBindings.ofAnswers(injection);

      // Must evaluate strictly to false (not parsed or executed)
      assertFalse(condition.evaluate(bindings));
    }

    @Test
    @DisplayName("Answer containing SQL/command injection is treated as opaque data")
    void testAnswerWithCommandInjection() {
      Condition condition = Condition.compile("{0} equals \"; op attacker\"");
      ConditionBindings match = ConditionBindings.ofAnswers("; op attacker");
      ConditionBindings noMatch = ConditionBindings.ofAnswers("safe_user");

      assertTrue(condition.evaluate(match));
      assertFalse(condition.evaluate(noMatch));
    }

    @Test
    @DisplayName("Numeric comparison on boolean-injection string fails closed")
    void testNumericComparisonOnInjectionStringFailsClosed() {
      Condition condition = Condition.compile("{0} == 1");
      ConditionBindings injection = ConditionBindings.ofAnswers("1 || 1 == 1");

      assertThrows(ConditionEvaluationException.class, () -> condition.evaluate(injection));
    }
  }

  @Nested
  @DisplayName("Missing & Unresolvable Operands (Fail-Closed)")
  class UnresolvableOperandsTests {

    @Test
    @DisplayName("Missing answer index fails closed with typed ConditionEvaluationException")
    void testMissingAnswerIndex() {
      Condition condition = Condition.compile("{0} == 10");
      ConditionBindings empty = ConditionBindings.empty();
      assertThrows(ConditionEvaluationException.class, () -> condition.evaluate(empty));

      ConditionBindings outOfBounds = ConditionBindings.ofAnswers("val0");
      Condition conditionIndex1 = Condition.compile("{1} == 10");
      assertThrows(ConditionEvaluationException.class, () -> conditionIndex1.evaluate(outOfBounds));
    }

    @Test
    @DisplayName("Missing PAPI placeholder fails closed with typed ConditionEvaluationException")
    void testMissingPapiPlaceholder() {
      Condition condition =
          Condition.compile("%vault_eco_balance% >= 100", ConditionCompileOptions.forPreset());
      ConditionBindings noPapi = ConditionBindings.ofAnswers("100");
      assertThrows(ConditionEvaluationException.class, () -> condition.evaluate(noPapi));

      ConditionBindings withPapi =
          ConditionBindings.of(List.of("100"), Map.of("vault_eco_balance", "500"));
      assertTrue(condition.evaluate(withPapi));
    }
  }

  @Nested
  @DisplayName("Resolved Operand Length Bound (1024 chars)")
  class OperandLengthBoundTests {

    @Test
    @DisplayName("Resolved answer <= 1024 chars succeeds")
    void testResolvedAnswer1024Success() {
      String exactly1024 = "a".repeat(1024);
      Condition condition = Condition.compile("{0} startsWith \"a\"");
      assertTrue(condition.evaluate(ConditionBindings.ofAnswers(exactly1024)));
    }

    @Test
    @DisplayName("Resolved answer > 1024 chars fails closed with ConditionEvaluationException")
    void testResolvedAnswerExceeding1024Throws() {
      String length1025 = "a".repeat(1025);
      Condition condition = Condition.compile("{0} startsWith \"a\"");
      assertThrows(
          ConditionEvaluationException.class,
          () -> condition.evaluate(ConditionBindings.ofAnswers(length1025)));
    }

    @Test
    @DisplayName("Resolved PAPI placeholder > 1024 chars fails closed")
    void testResolvedPapiExceeding1024Throws() {
      String length1025 = "b".repeat(1025);
      Condition condition =
          Condition.compile("%papi_val% equals \"test\"", ConditionCompileOptions.forPreset());
      ConditionBindings bindings = ConditionBindings.of(List.of(), Map.of("papi_val", length1025));
      assertThrows(ConditionEvaluationException.class, () -> condition.evaluate(bindings));
    }
  }

  @Nested
  @DisplayName("Boolean Logic, Short-Circuiting & Negation")
  class BooleanLogicTests {

    @Test
    @DisplayName("AND logic with short-circuiting")
    void testAndLogic() {
      Condition condition = Condition.compile("{0} == 1 && {1} == 2");
      assertTrue(condition.evaluate(ConditionBindings.ofAnswers("1", "2")));
      assertFalse(condition.evaluate(ConditionBindings.ofAnswers("1", "3")));
      assertFalse(condition.evaluate(ConditionBindings.ofAnswers("0", "2")));

      // Short-circuit: if left is false, right is not evaluated (even if right is missing/invalid)
      assertFalse(condition.evaluate(ConditionBindings.ofAnswers("0")));
    }

    @Test
    @DisplayName("OR logic with short-circuiting")
    void testOrLogic() {
      Condition condition = Condition.compile("{0} == 1 || {1} == 2");
      assertTrue(condition.evaluate(ConditionBindings.ofAnswers("1", "0")));
      assertTrue(condition.evaluate(ConditionBindings.ofAnswers("0", "2")));
      assertFalse(condition.evaluate(ConditionBindings.ofAnswers("0", "0")));

      // Short-circuit: if left is true, right is not evaluated (even if right is missing/invalid)
      assertTrue(condition.evaluate(ConditionBindings.ofAnswers("1")));
    }

    @Test
    @DisplayName("NOT logic")
    void testNotLogic() {
      Condition condition = Condition.compile("!({0} equals \"admin\")");
      assertTrue(condition.evaluate(ConditionBindings.ofAnswers("player")));
      assertFalse(condition.evaluate(ConditionBindings.ofAnswers("admin")));
    }

    @Test
    @DisplayName("Complex combined logic")
    void testComplexLogic() {
      // ({0} >= 18 && {1} equals "yes") || %bypass_perm% equals "true"
      Condition condition =
          Condition.compile(
              "({0} >= 18 && {1} equals \"yes\") || %bypass_perm% equals \"true\"",
              ConditionCompileOptions.forPreset());

      assertTrue(
          condition.evaluate(
              ConditionBindings.of(List.of("20", "yes"), Map.of("bypass_perm", "false"))));
      assertFalse(
          condition.evaluate(
              ConditionBindings.of(List.of("16", "yes"), Map.of("bypass_perm", "false"))));
      assertTrue(
          condition.evaluate(
              ConditionBindings.of(List.of("16", "no"), Map.of("bypass_perm", "true"))));
    }
  }
}
