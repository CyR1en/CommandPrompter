package dev.cyr1en.promptcore.parser;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.logic.condition.ConditionBindings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BreakIfParserTest {

  private final CommandLineParser parser = new CommandLineParser();

  // =========================================================================
  // FLOW-06 Parse Model Tests
  // =========================================================================

  @Test
  @DisplayName("FLOW-06: Parses basic -breakIf condition on anvil prompt")
  void parsesBasicBreakIf() {
    var result = parser.parse("/test <a:Reason -breakIf:{0}==\"skip\">");
    assertEquals(1, result.promptCount());
    var tag = result.promptTags().get(0);
    assertEquals("a", tag.key());
    assertEquals("Reason", tag.displayText());
    assertTrue(tag.hasBreakIf());
    assertNotNull(tag.breakIf());
    assertEquals("{0}==\"skip\"", tag.breakIf().source());

    // Evaluated via string equals
    var strResult = parser.parse("/test <a:Reason -breakIf:{0} equals \"skip\">");
    var strTag = strResult.promptTags().get(0);
    var trueBindings = ConditionBindings.ofAnswers("skip");
    var falseBindings = ConditionBindings.ofAnswers("continue");
    assertTrue(strTag.breakIf().evaluate(trueBindings));
    assertFalse(strTag.breakIf().evaluate(falseBindings));
  }

  // =========================================================================
  // Valid Precedence, Quotes, and Spaces Tests
  // =========================================================================

  @Test
  @DisplayName("Parses -breakIf with spaces around comparison operators")
  void parsesBreakIfWithSpacesAroundOperators() {
    var result = parser.parse("/test <a:Reason -breakIf:{0} == \"skip\">");
    var tag = result.promptTags().get(0);
    assertEquals("Reason", tag.displayText());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} == \"skip\"", tag.breakIf().source());
  }

  @Test
  @DisplayName("Parses -breakIf with space after colon")
  void parsesBreakIfWithSpaceAfterColon() {
    var result = parser.parse("/test <a:Reason -breakIf: {0} == \"skip\">");
    var tag = result.promptTags().get(0);
    assertEquals("Reason", tag.displayText());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} == \"skip\"", tag.breakIf().source());
  }

  @Test
  @DisplayName("Parses -breakIf with complex parentheses and logical operators")
  void parsesBreakIfComplexPrecedence() {
    var result =
        parser.parse(
            "/test <a:Reason -breakIf:({0} equals \"skip\" || {0} equals \"none\") && {1} == 5>");
    var tag = result.promptTags().get(0);
    assertEquals("Reason", tag.displayText());
    assertTrue(tag.hasBreakIf());
    assertEquals(
        "({0} equals \"skip\" || {0} equals \"none\") && {1} == 5", tag.breakIf().source());

    assertTrue(tag.breakIf().evaluate(ConditionBindings.ofAnswers("skip", "5")));
    assertFalse(tag.breakIf().evaluate(ConditionBindings.ofAnswers("skip", "3")));
    assertFalse(tag.breakIf().evaluate(ConditionBindings.ofAnswers("other", "5")));
  }

  @Test
  @DisplayName("Parses -breakIf with quoted string containing spaces, colons, hyphens, and escapes")
  void parsesBreakIfQuotedSpecialCharacters() {
    var result =
        parser.parse(
            "/test <a:Reason -breakIf:{0} equals \"skip -ds -iv:req \\\"quoted\\\"\" -ds>");
    var tag = result.promptTags().get(0);
    assertEquals("Reason", tag.displayText());
    assertFalse(tag.sanitize());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"skip -ds -iv:req \\\"quoted\\\"\"", tag.breakIf().source());

    assertTrue(tag.breakIf().evaluate(ConditionBindings.ofAnswers("skip -ds -iv:req \"quoted\"")));
  }

  @Test
  @DisplayName("Parses -breakIf with negative numbers inside expression")
  void parsesBreakIfWithNegativeNumbers() {
    var result = parser.parse("/test <a:Amount -breakIf:{0} <= -10.5 -ds>");
    var tag = result.promptTags().get(0);
    assertEquals("Amount", tag.displayText());
    assertFalse(tag.sanitize());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} <= -10.5", tag.breakIf().source());

    assertTrue(tag.breakIf().evaluate(ConditionBindings.ofAnswers("-15")));
    assertFalse(tag.breakIf().evaluate(ConditionBindings.ofAnswers("0")));
  }

  @Test
  @DisplayName("Parses -breakIf mixed with other flags in various positions")
  void parsesBreakIfMixedFlagPositions() {
    // breakIf in middle
    var res1 = parser.parse("/test <a:Reason -ds -breakIf:{0} == \"skip\" -timeout:30 -iv:req>");
    var tag1 = res1.promptTags().get(0);
    assertEquals("Reason", tag1.displayText());
    assertFalse(tag1.sanitize());
    assertEquals(30, tag1.timeout());
    assertEquals("req", tag1.validatorAlias());
    assertTrue(tag1.hasBreakIf());

    // breakIf at end
    var res2 = parser.parse("/test <a:Reason -iv:req -breakIf:{0} == \"skip\">");
    var tag2 = res2.promptTags().get(0);
    assertEquals("Reason", tag2.displayText());
    assertEquals("req", tag2.validatorAlias());
    assertTrue(tag2.hasBreakIf());
  }

  @Test
  @DisplayName("Case-insensitive -breakif flag syntax")
  void parsesCaseInsensitiveFlag() {
    var res1 = parser.parse("/test <a:Why? -breakif:{0}==\"a\">");
    assertTrue(res1.promptTags().get(0).hasBreakIf());

    var res2 = parser.parse("/test <a:Why? -BREAKIF:{0}==\"a\">");
    assertTrue(res2.promptTags().get(0).hasBreakIf());

    var res3 = parser.parse("/test <a:Why? -BreakIf:{0}==\"a\">");
    assertTrue(res3.promptTags().get(0).hasBreakIf());
  }

  // =========================================================================
  // All Built-In Tags Support Tests
  // =========================================================================

  @Test
  @DisplayName("Parses -breakIf on chat prompt")
  void parsesChatPromptBreakIf() {
    var res = parser.parse("/msg <Chat text -breakIf:{0} equals \"exit\">");
    var tag = res.promptTags().get(0);
    assertEquals("", tag.key());
    assertEquals("Chat text", tag.displayText());
    assertTrue(tag.hasBreakIf());

    var bareChat = parser.parse("/msg <-breakIf:{0} == 1>");
    var bareTag = bareChat.promptTags().get(0);
    assertEquals("", bareTag.key());
    assertEquals("", bareTag.displayText());
    assertTrue(bareTag.hasBreakIf());
  }

  @Test
  @DisplayName("Parses -breakIf on sign prompt")
  void parsesSignPromptBreakIf() {
    var res = parser.parse("/sign <s:Sign prompt -breakIf:{0} equals \"cancel\">");
    var tag = res.promptTags().get(0);
    assertEquals("s", tag.key());
    assertEquals("Sign prompt", tag.displayText());
    assertTrue(tag.hasBreakIf());
  }

  @Test
  @DisplayName("Parses -breakIf on player UI prompt with filter")
  void parsesPlayerUiPromptBreakIf() {
    var res = parser.parse("/pui <p:r100:Pick player -breakIf:{0} equals \"nobody\">");
    var tag = res.promptTags().get(0);
    assertEquals("p", tag.key());
    assertEquals("r100", tag.filter());
    assertEquals("Pick player", tag.displayText());
    assertTrue(tag.hasBreakIf());
  }

  @Test
  @DisplayName("Parses -breakIf on single dialog prompt")
  void parsesDialogPromptBreakIf() {
    var res = parser.parse("/dlg <d:text:Enter username -breakIf:{0} equals \"admin\">");
    var tag = res.promptTags().get(0);
    assertEquals("d", tag.key());
    assertEquals("text", tag.filter());
    assertEquals("Enter username", tag.displayText());
    assertTrue(tag.hasBreakIf());
  }

  @Test
  @DisplayName("Parses -breakIf on compound dialog prompt at block level (trailing)")
  void parsesCompoundDialogPromptBreakIf() {
    var res =
        parser.parse(
            "/dlg <d:text:User && d:text:Pass -breakIf:{0} equals \"admin\" || {1} equals"
                + " \"root\">");
    var tag = res.promptTags().get(0);
    assertTrue(tag.isCompound());
    assertEquals(2, tag.subTags().size());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"admin\" || {1} equals \"root\"", tag.breakIf().source());
  }

  @Test
  @DisplayName("Parses early -breakIf on first compound sub-segment")
  void parsesEarlyBreakIfOnCompoundTag() {
    var res = parser.parse("/dlg <d:text:A -breakIf:{0} equals \"x\" && d:text:B>");
    assertEquals(1, res.promptCount());
    var tag = res.promptTags().get(0);
    assertTrue(tag.isCompound());
    assertEquals(2, tag.subTags().size());
    assertEquals("d", tag.subTags().get(0).key());
    assertEquals("text", tag.subTags().get(0).filter());
    assertEquals("A", tag.subTags().get(0).displayText());
    assertEquals("d", tag.subTags().get(1).key());
    assertEquals("text", tag.subTags().get(1).filter());
    assertEquals("B", tag.subTags().get(1).displayText());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"x\"", tag.breakIf().source());
    assertTrue(tag.breakIf().evaluate(ConditionBindings.ofAnswers("x")));
    assertFalse(tag.breakIf().evaluate(ConditionBindings.ofAnswers("y")));
  }

  @Test
  @DisplayName("Parses early -breakIf containing logical && condition on compound tag")
  void parsesEarlyBreakIfWithLogicalAndOnCompoundTag() {
    var res =
        parser.parse("/dlg <d:text:A -breakIf:{0} equals \"x\" && {1} equals \"y\" && d:text:B>");
    assertEquals(1, res.promptCount());
    var tag = res.promptTags().get(0);
    assertTrue(tag.isCompound());
    assertEquals(2, tag.subTags().size());
    assertEquals("A", tag.subTags().get(0).displayText());
    assertEquals("B", tag.subTags().get(1).displayText());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"x\" && {1} equals \"y\"", tag.breakIf().source());
    assertTrue(tag.breakIf().evaluate(ConditionBindings.ofAnswers("x", "y")));
    assertFalse(tag.breakIf().evaluate(ConditionBindings.ofAnswers("x", "n")));
  }

  @Test
  @DisplayName("Parses middle -breakIf on multi-row compound dialog tag")
  void parsesMiddleBreakIfOnCompoundTag() {
    var res =
        parser.parse(
            "/dlg <d:text:First && d:num[0,10]:Second -breakIf:{0} == 1 && d:choice[a,b]:Third>");
    assertEquals(1, res.promptCount());
    var tag = res.promptTags().get(0);
    assertTrue(tag.isCompound());
    assertEquals(3, tag.subTags().size());
    assertEquals("First", tag.subTags().get(0).displayText());
    assertEquals("Second", tag.subTags().get(1).displayText());
    assertEquals("Third", tag.subTags().get(2).displayText());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} == 1", tag.breakIf().source());
  }

  @Test
  @DisplayName("Parses middle -breakIf containing logical && condition on compound tag")
  void parsesMiddleBreakIfWithLogicalAndOnCompoundTag() {
    var res =
        parser.parse(
            "/dlg <d:text:A && d:text:B -breakIf:{0} equals \"x\" && {1} equals \"y\" && d:text:C>");
    assertEquals(1, res.promptCount());
    var tag = res.promptTags().get(0);
    assertTrue(tag.isCompound());
    assertEquals(3, tag.subTags().size());
    assertEquals("A", tag.subTags().get(0).displayText());
    assertEquals("B", tag.subTags().get(1).displayText());
    assertEquals("C", tag.subTags().get(2).displayText());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"x\" && {1} equals \"y\"", tag.breakIf().source());
  }

  @Test
  @DisplayName("Parses trailing -breakIf containing logical && condition on compound tag")
  void parsesTrailingBreakIfWithLogicalAndOnCompoundTag() {
    var res =
        parser.parse(
            "/dlg <d:text:User && d:text:Pass -breakIf:{0} equals \"admin\" && {1} equals \"secret\">");
    assertEquals(1, res.promptCount());
    var tag = res.promptTags().get(0);
    assertTrue(tag.isCompound());
    assertEquals(2, tag.subTags().size());
    assertEquals("User", tag.subTags().get(0).displayText());
    assertEquals("Pass", tag.subTags().get(1).displayText());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"admin\" && {1} equals \"secret\"", tag.breakIf().source());
  }

  @Test
  @DisplayName("Parses single dialog tag with logical && inside -breakIf")
  void parsesSingleDialogWithLogicalAndInBreakIf() {
    var res = parser.parse("/dlg <d:text:Enter -breakIf:{0} equals \"a\" && {1} equals \"b\">");
    assertEquals(1, res.promptCount());
    var tag = res.promptTags().get(0);
    assertFalse(tag.isCompound());
    assertEquals("d", tag.key());
    assertEquals("text", tag.filter());
    assertEquals("Enter", tag.displayText());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"a\" && {1} equals \"b\"", tag.breakIf().source());
  }

  @Test
  @DisplayName("Parses -breakIf containing quoted string with &&")
  void parsesBreakIfQuotedAmpersands() {
    var res = parser.parse("/dlg <d:text:A -breakIf:{0} equals \"foo && d:text:B\" && d:text:B>");
    assertEquals(1, res.promptCount());
    var tag = res.promptTags().get(0);
    assertTrue(tag.isCompound());
    assertEquals(2, tag.subTags().size());
    assertEquals("A", tag.subTags().get(0).displayText());
    assertEquals("B", tag.subTags().get(1).displayText());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"foo && d:text:B\"", tag.breakIf().source());
    assertTrue(tag.breakIf().evaluate(ConditionBindings.ofAnswers("foo && d:text:B")));
  }

  @Test
  @DisplayName("Parses compound sub-tags with various valid key grammars")
  void parsesCompoundWithDifferentKeys() {
    var res1 = parser.parse("/dlg <d:text:A -breakIf:{0} equals \"x\" && dialog:num[0,10]:B>");
    assertEquals(1, res1.promptCount());
    var tag1 = res1.promptTags().get(0);
    assertTrue(tag1.isCompound());
    assertEquals(2, tag1.subTags().size());
    assertEquals("dialog", tag1.subTags().get(1).key());

    var res2 = parser.parse("/dlg <d:text:A -breakIf:{0} equals \"x\" && customscreen:text:B>");
    assertEquals(1, res2.promptCount());
    var tag2 = res2.promptTags().get(0);
    assertTrue(tag2.isCompound());
    assertEquals(2, tag2.subTags().size());
    assertEquals("customscreen", tag2.subTags().get(1).key());
  }

  @Test
  @DisplayName("Parses compound tag where next subsegment starts with a flag")
  void parsesCompoundWithFlagInNextSubSegment() {
    var res = parser.parse("/dlg <d:text:A -breakIf:{0} equals \"x\" && -ds d:text:B>");
    assertEquals(1, res.promptCount());
    var tag = res.promptTags().get(0);
    assertTrue(tag.isCompound());
    assertFalse(tag.sanitize());
    assertEquals(2, tag.subTags().size());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"x\"", tag.breakIf().source());
  }

  @Test
  @DisplayName("Parses -breakIf on confirmation prompt")
  void parsesConfirmationPromptBreakIf() {
    var res = parser.parse("/confirm <c:Are you sure? -mode:chat -breakIf:{0} equals \"no\">");
    var tag = res.promptTags().get(0);
    assertEquals("c", tag.key());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"no\"", tag.breakIf().source());
  }

  @Test
  @DisplayName("Parses -breakIf on item selector prompt")
  void parsesItemPromptBreakIf() {
    var res =
        parser.parse("/item <i:Pick weapon -source:hand -breakIf:{0} equals \"minecraft:air\">");
    var tag = res.promptTags().get(0);
    assertEquals("i", tag.key());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"minecraft:air\"", tag.breakIf().source());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "a",
        "anvil",
        "ANVIL",
        "s",
        "sign",
        "SIGN",
        "p",
        "player",
        "PLAYER",
        "d",
        "dialog",
        "DIALOG",
        "c",
        "confirm",
        "CONFIRM",
        "confirmation",
        "i",
        "item",
        "ITEM"
      })
  @DisplayName("Parses -breakIf for all built-in key variations")
  void parsesAllBuiltInKeys(String key) {
    var res = parser.parse("/test <" + key + ":Prompt text -breakIf:{0} equals \"quit\">");
    var tag = res.promptTags().get(0);
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"quit\"", tag.breakIf().source());
  }

  // =========================================================================
  // Malformed, Duplicate, PAPI, Depth Rejection (Fail-Closed) Tests
  // =========================================================================

  @Test
  @DisplayName("Rejects malformed -breakIf without colon")
  void rejectsMalformedWithoutColon() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("/test <a:Reason -breakIf>"));
  }

  @Test
  @DisplayName("Rejects malformed -breakIf with empty condition")
  void rejectsEmptyCondition() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("/test <a:Reason -breakIf:>"));
  }

  @Test
  @DisplayName("Rejects malformed -breakIf with blank whitespace condition")
  void rejectsBlankCondition() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/test <a:Reason -breakIf:   >"));
  }

  @Test
  @DisplayName("Rejects duplicate -breakIf flags in single tag")
  void rejectsDuplicateBreakIf() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/test <a:Reason -breakIf:{0} == 1 -breakIf:{0} == 2>"));
  }

  @Test
  @DisplayName("Rejects duplicate -breakIf flags across compound dialog sub-segments")
  void rejectsDuplicateBreakIfAcrossCompoundSegments() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/test <d:text:A -breakIf:{0} == 1 && d:text:B -breakIf:{1} == 2>"));
  }

  @Test
  @DisplayName("Rejects dangling && in -breakIf condition")
  void rejectsDanglingAndInBreakIf() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/test <a:Reason -breakIf:{0} == 1 &&>"));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/test <d:text:A -breakIf:{0} == 1 &&>"));
  }

  @Test
  @DisplayName("Rejects invalid syntax following && in -breakIf condition")
  void rejectsInvalidSyntaxFollowingAndInBreakIf() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/test <a:Reason -breakIf:{0} == 1 && 123>"));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/test <d:text:A -breakIf:{0} == 1 && bad_syntax>"));
  }

  @Test
  @DisplayName("Rejects PAPI references in inline -breakIf condition")
  void rejectsPapiReferences() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/test <a:Reason -breakIf:%player_name% equals \"Steve\">"));
  }

  @Test
  @DisplayName("Rejects syntax error in -breakIf condition")
  void rejectsSyntaxError() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/test <a:Reason -breakIf:{0} == >"));
  }

  @Test
  @DisplayName("Rejects unclosed quoted string in -breakIf condition")
  void rejectsUnclosedQuote() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/test <a:Reason -breakIf:{0} equals \"unclosed>"));
  }

  @Test
  @DisplayName("Rejects unclosed parenthesis in -breakIf condition")
  void rejectsUnclosedParen() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/test <a:Reason -breakIf:({0} equals \"skip\">"));
  }

  @Test
  @DisplayName("Rejects -breakIf condition exceeding AST depth limit")
  void rejectsExceededDepth() {
    // 11 levels of nested NOT > MAX_AST_DEPTH (10)
    String deepCondition = "!(!(!(!(!(!(!(!(!(!(!{0} == 1)))))))))))";
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/test <a:Reason -breakIf:" + deepCondition + ">"));
  }

  // =========================================================================
  // Custom Flag Regression Tests
  // =========================================================================

  @Test
  @DisplayName("Preserves -breakIf as custom flag for custom screens without compiling")
  void preservesCustomScreenBreakIfFlag() {
    var res = parser.parse("/give <ecoitem:Pick weapon -breakIf:customValue -glow>");
    var tag = res.promptTags().get(0);
    assertEquals("ecoitem", tag.key());
    assertEquals("Pick weapon", tag.displayText());
    assertNull(tag.breakIf());
    assertFalse(tag.hasBreakIf());
    assertEquals("customValue", tag.flags().get("breakif"));
    assertEquals("true", tag.flags().get("glow"));
  }

  @Test
  @DisplayName("Preserves arbitrary custom flags on custom screen alongside breakif")
  void preservesArbitraryCustomFlags() {
    var res = parser.parse("/custom <myscreen:Prompt -breakif:\"quoted : value\" -tier:epic>");
    var tag = res.promptTags().get(0);
    assertEquals("myscreen", tag.key());
    assertNull(tag.breakIf());
    assertEquals("quoted : value", tag.flags().get("breakif"));
    assertEquals("epic", tag.flags().get("tier"));
  }

  // =========================================================================
  // PromptTag Model & Constructor Compatibility Tests
  // =========================================================================

  @Test
  @DisplayName("PromptTag backward compatibility with legacy constructors")
  void promptTagConstructorCompatibility() {
    var tag4 = new PromptTag("<raw>", "a", null, "disp");
    assertNull(tag4.breakIf());
    assertFalse(tag4.hasBreakIf());

    var tag6 = new PromptTag("<raw>", "a", null, "disp", false, "alias");
    assertNull(tag6.breakIf());

    var tag9 =
        new PromptTag(
            "<raw>",
            "a",
            null,
            "disp",
            true,
            "alias",
            PromptTag.AnswerType.STRING,
            List.of(),
            false);
    assertNull(tag9.breakIf());

    var tag10 =
        new PromptTag(
            "<raw>",
            "a",
            null,
            "disp",
            true,
            "alias",
            PromptTag.AnswerType.STRING,
            List.of(),
            false,
            null);
    assertNull(tag10.breakIf());

    var tag11 =
        new PromptTag(
            "<raw>",
            "a",
            null,
            "disp",
            true,
            "alias",
            PromptTag.AnswerType.STRING,
            List.of(),
            false,
            null,
            60);
    assertNull(tag11.breakIf());

    var tag12 =
        new PromptTag(
            "<raw>",
            "a",
            null,
            "disp",
            true,
            "alias",
            PromptTag.AnswerType.STRING,
            List.of(),
            false,
            null,
            60,
            Map.of("custom", "val"));
    assertNull(tag12.breakIf());
    assertEquals("val", tag12.flags().get("custom"));
  }
}
