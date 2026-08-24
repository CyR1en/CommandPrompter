package dev.cyr1en.promptcore.logic.condition;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConditionCompilerTest {

  @Nested
  @DisplayName("Source Length & C0 Validation")
  class SourceValidationTests {

    @Test
    @DisplayName("Reject null source")
    void testNullSource() {
      assertThrows(NullPointerException.class, () -> Condition.compile(null));
    }

    @Test
    @DisplayName("Reject blank source")
    void testBlankSource() {
      assertThrows(ConditionParseException.class, () -> Condition.compile("   "));
      assertThrows(ConditionParseException.class, () -> Condition.compile(""));
    }

    @Test
    @DisplayName("Reject source exceeding 1024 characters")
    void testSourceExceeding1024() {
      String padding = " ".repeat(1020);
      String source = "{0} == 1" + padding;
      assertTrue(source.length() > 1024);
      assertThrows(ConditionParseException.class, () -> Condition.compile(source));
    }

    @ParameterizedTest
    @ValueSource(chars = {'\u0000', '\u0001', '\u0007', '\u0008', '\u001B', '\u001F', '\u007F'})
    @DisplayName("Reject raw C0 control characters in source")
    void testRawC0Controls(char controlChar) {
      String source = "{0} ==" + controlChar + " 1";
      assertThrows(ConditionParseException.class, () -> Condition.compile(source));
    }
  }

  @Nested
  @DisplayName("Operator Precedence & Grammar")
  class PrecedenceTests {

    @Test
    @DisplayName("Verify && has higher precedence than ||")
    void testAndOverOr() {
      // {0} == 1 || {1} == 2 && {2} == 3  -->  ({0} == 1) || (({1} == 2) && ({2} == 3))
      Condition condition = Condition.compile("{0} == 1 || {1} == 2 && {2} == 3");
      assertInstanceOf(OrNode.class, condition.root());
      OrNode orNode = (OrNode) condition.root();
      assertInstanceOf(NumericComparisonNode.class, orNode.left());
      assertInstanceOf(AndNode.class, orNode.right());
    }

    @Test
    @DisplayName("Verify parentheses override precedence")
    void testParenthesesOverride() {
      // ({0} == 1 || {1} == 2) && {2} == 3  -->  (({0} == 1) || ({1} == 2)) && ({2} == 3)
      Condition condition = Condition.compile("({0} == 1 || {1} == 2) && {2} == 3");
      assertInstanceOf(AndNode.class, condition.root());
      AndNode andNode = (AndNode) condition.root();
      assertInstanceOf(OrNode.class, andNode.left());
      assertInstanceOf(NumericComparisonNode.class, andNode.right());
    }

    @Test
    @DisplayName("Verify ! has highest logical precedence")
    void testNotPrecedence() {
      // !{0} == 1 && {1} == 2  -->  (!({0} == 1)) && ({1} == 2)
      Condition condition = Condition.compile("!{0} == 1 && {1} == 2");
      assertInstanceOf(AndNode.class, condition.root());
      AndNode andNode = (AndNode) condition.root();
      assertInstanceOf(NotNode.class, andNode.left());
      assertInstanceOf(NumericComparisonNode.class, andNode.right());
    }

    @Test
    @DisplayName("Verify nested parentheses with NOT")
    void testNestedNotParentheses() {
      Condition condition = Condition.compile("!({0} == 1 || {1} == 2)");
      assertInstanceOf(NotNode.class, condition.root());
      NotNode notNode = (NotNode) condition.root();
      assertInstanceOf(OrNode.class, notNode.child());
    }
  }

  @Nested
  @DisplayName("Quoting & Escape Sequences")
  class QuotingTests {

    @Test
    @DisplayName("Valid escapes: \\\" and \\\\")
    void testValidEscapes() {
      Condition condition = Condition.compile("{0} equals \"hello \\\"world\\\" \\\\ test\"");
      assertInstanceOf(StringComparisonNode.class, condition.root());
      StringComparisonNode node = (StringComparisonNode) condition.root();
      StringLiteralOperand right = (StringLiteralOperand) node.right();
      assertEquals("hello \"world\" \\ test", right.value());
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "{0} equals \"hello \\n world\"",
          "{0} equals \"hello \\t world\"",
          "{0} equals \"hello \\r world\"",
          "{0} equals \"hello \\a world\"",
          "{0} equals \"hello \\0 world\"",
          "{0} equals \"hello \\x41 world\""
        })
    @DisplayName("Reject unknown escape sequences")
    void testRejectUnknownEscapes(String source) {
      assertThrows(ConditionParseException.class, () -> Condition.compile(source));
    }

    @Test
    @DisplayName("Reject unclosed quoted string")
    void testUnclosedString() {
      assertThrows(ConditionParseException.class, () -> Condition.compile("{0} equals \"unclosed"));
    }

    @Test
    @DisplayName("Reject trailing escape backslash")
    void testTrailingEscapeBackslash() {
      assertThrows(
          ConditionParseException.class, () -> Condition.compile("{0} equals \"trailing escape\\"));
    }
  }

  @Nested
  @DisplayName("Disallowed Tokens, Booleans, Identifiers & Syntax Errors")
  class DisallowedTokensTests {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "{0} == true",
          "{0} == false",
          "true",
          "false",
          "{0} == null",
          "{0} == undefined",
          "foo({0}) == 1",
          "{0} matches \"[a-z]+\"",
          "{0} =~ /test/",
          "val == 123",
          "var x = 1"
        })
    @DisplayName("Reject identifiers, boolean literals, regex, unquoted strings, and functions")
    void testRejectDisallowedTokens(String source) {
      assertThrows(ConditionParseException.class, () -> Condition.compile(source));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "==",
          "{0} ==",
          "== {0}",
          "{0} == 1 ==",
          "{0} == 1 2",
          "()",
          "({0} == 1",
          "{0} == 1)",
          "{0} & {1}",
          "{0} | {1}",
          "{0} = 1",
          "{0} == 1 &&",
          "{0} == 1 ||",
          "!",
          "! && {0} == 1",
          "{0} == 1 == 2",
          "{0} equals",
          "equals \"foo\"",
          "{0} contains",
          "{-1} == 1",
          "{abc} == 1",
          "{} == 1"
        })
    @DisplayName("Reject malformed expression syntax")
    void testMalformedSyntax(String source) {
      assertThrows(ConditionParseException.class, () -> Condition.compile(source));
    }
  }

  @Nested
  @DisplayName("PlaceholderAPI Compile Options Boundary")
  class PapiOptionsTests {

    @Test
    @DisplayName("Reject PAPI refs under default inline options")
    void testPapiRejectedInDefaultInline() {
      assertThrows(
          ConditionParseException.class, () -> Condition.compile("%vault_eco_balance% >= 1000"));
      assertThrows(
          ConditionParseException.class,
          () ->
              Condition.compile(
                  "%vault_eco_balance% >= 1000", ConditionCompileOptions.forInline()));
    }

    @Test
    @DisplayName("Accept PAPI refs under trusted preset options")
    void testPapiAcceptedInPreset() {
      Condition condition =
          Condition.compile("%vault_eco_balance% >= 1000", ConditionCompileOptions.forPreset());
      assertTrue(condition.hasPapiRefs());
      assertTrue(condition.papiPlaceholders().contains("vault_eco_balance"));
      assertEquals(1, condition.depth());
      assertEquals(1, condition.nodeCount());
    }

    @Test
    @DisplayName("Reject empty or unclosed PAPI refs")
    void testMalformedPapiRefs() {
      assertThrows(
          ConditionParseException.class,
          () -> Condition.compile("%% >= 1000", ConditionCompileOptions.forPreset()));
      assertThrows(
          ConditionParseException.class,
          () -> Condition.compile("%unclosed >= 1000", ConditionCompileOptions.forPreset()));
    }
  }

  @Nested
  @DisplayName("FLOW-03 & Depth / Node Bounds")
  class BoundsTests {

    @Test
    @DisplayName("Depth <= 10 compiles successfully")
    void testDepth10Success() {
      // 10 levels of nesting with NOT
      // depth 1: {0} == 0
      // depth 10: 9 NOTs wrapping depth 1
      String source = "!" + "!".repeat(8) + "({0} == 0)";
      Condition condition = Condition.compile(source);
      assertEquals(10, condition.depth());
    }

    @Test
    @DisplayName("FLOW-03: Depth > 10 throws ConditionDepthException and never coerces to boolean")
    void testDepthExceededThrows() {
      // depth 11: 10 NOTs wrapping depth 1
      String source = "!" + "!".repeat(9) + "({0} == 0)";
      assertThrows(ConditionDepthException.class, () -> Condition.compile(source));

      // 11 comparisons chained with &&
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 11; i++) {
        if (i > 0) sb.append(" && ");
        sb.append("{").append(i).append("} == ").append(i);
      }
      assertThrows(ConditionDepthException.class, () -> Condition.compile(sb.toString()));
    }

    @Test
    @DisplayName("Node count <= 128 compiles successfully")
    void testNodeCountWithinLimit() {
      // 64 comparisons: 64 leaves + 63 binary nodes = 127 nodes (depth 7 <= 10)
      // Build a balanced tree of depth 6
      String balanced = buildBalancedTree(6); // 2^6 = 64 leaves, depth 7, 127 nodes
      Condition condition = Condition.compile(balanced);
      assertEquals(127, condition.nodeCount());
      assertEquals(7, condition.depth());
    }

    @Test
    @DisplayName("Node count > 128 throws ConditionNodeLimitException")
    void testNodeCountExceededThrows() {
      // 127 nodes from balanced tree (depth 7) + 3 nodes from right subtree = 131 nodes (depth 8 <=
      // 10, length ~590 <= 1024)
      String large = "(" + buildBalancedTree(6) + "&&({0}<0&&{0}<0))";
      assertTrue(large.length() <= 1024);
      assertThrows(ConditionNodeLimitException.class, () -> Condition.compile(large));
    }

    private String buildBalancedTree(int depth) {
      if (depth == 0) {
        return "{0}<0";
      }
      String child = buildBalancedTree(depth - 1);
      return "(" + child + "&&" + child + ")";
    }
  }

  @Nested
  @DisplayName("Metadata & Inspections")
  class MetadataTests {

    @Test
    @DisplayName("Collect answer indices and PAPI placeholders accurately")
    void testMetadataCollection() {
      Condition condition =
          Condition.compile(
              "({0} == 10 && %papi_1% equals \"foo\") || ({2} > 5 && %papi_2% startsWith \"bar\")",
              ConditionCompileOptions.forPreset());
      assertEquals(List.of(0, 2), condition.answerIndices().stream().sorted().toList());
      assertEquals(
          List.of("papi_1", "papi_2"), condition.papiPlaceholders().stream().sorted().toList());
      assertTrue(condition.hasPapiRefs());
    }
  }
}
