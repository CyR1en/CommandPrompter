package dev.cyr1en.promptcore;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.parser.CommandLineParser;
import dev.cyr1en.promptcore.session.PromptSession;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfirmationGrammarTest {

  private final CommandLineParser parser = new CommandLineParser();

  // ====================================================================
  // Key Aliases & Case Normalization
  // ====================================================================

  @ParameterizedTest
  @ValueSource(strings = {"c", "confirm", "C", "CONFIRM", "Confirm", "CoNfIrM"})
  void isConfirmationKey_matchesCaseInsensitively(String key) {
    assertTrue(ConfirmationGrammar.isConfirmationKey(key));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "a", "anvil", "d", "dialog", "p", "player", "s", "sign", "item", "custom"})
  void isConfirmationKey_rejectsNonConfirmationKeys(String key) {
    assertFalse(ConfirmationGrammar.isConfirmationKey(key));
  }

  @Test
  void isConfirmationKey_handlesNull() {
    assertFalse(ConfirmationGrammar.isConfirmationKey(null));
  }

  @Test
  void parsePromptTag_withValidAliases() {
    var tagC = new PromptTag("<c:Prompt>", "c", null, "Prompt");
    var syntaxC = ConfirmationGrammar.parse(tagC);
    assertEquals("Prompt", syntaxC.promptText());

    var tagConfirm = new PromptTag("<confirm:Prompt>", "confirm", null, "Prompt");
    var syntaxConfirm = ConfirmationGrammar.parse(tagConfirm);
    assertEquals("Prompt", syntaxConfirm.promptText());

    var tagUpper = new PromptTag("<CONFIRM:Prompt>", "CONFIRM", null, "Prompt");
    var syntaxUpper = ConfirmationGrammar.parse(tagUpper);
    assertEquals("Prompt", syntaxUpper.promptText());
  }

  @Test
  void parsePromptTag_withNonConfirmationKey_throws() {
    var tag = new PromptTag("<a:Prompt>", "a", null, "Prompt");
    assertThrows(IllegalArgumentException.class, () -> ConfirmationGrammar.parse(tag));
  }

  @Test
  void parsePromptTag_withNull_throws() {
    assertThrows(NullPointerException.class, () -> ConfirmationGrammar.parse((PromptTag) null));
  }

  // ====================================================================
  // All Fields & Segment Variants
  // ====================================================================

  @Test
  void singleSegment_defaultsLabelsToNull() {
    var syntax = ConfirmationGrammar.parse("Transfer ${1} to {0}?");
    assertEquals("Transfer ${1} to {0}?", syntax.promptText());
    assertNull(syntax.confirmLabel());
    assertNull(syntax.cancelLabel());
    assertNull(syntax.mode());
    assertFalse(syntax.valueMode());
    assertNull(syntax.soundKey());
    assertTrue(syntax.optionalConfirmLabel().isEmpty());
    assertTrue(syntax.optionalCancelLabel().isEmpty());
    assertTrue(syntax.optionalMode().isEmpty());
    assertTrue(syntax.optionalSoundKey().isEmpty());
  }

  @Test
  void twoSegments_parsesConfirmLabel() {
    var syntax = ConfirmationGrammar.parse("Transfer funds? | Yes");
    assertEquals("Transfer funds?", syntax.promptText());
    assertEquals("Yes", syntax.confirmLabel());
    assertNull(syntax.cancelLabel());
    assertEquals("Yes", syntax.optionalConfirmLabel().orElseThrow());
    assertTrue(syntax.optionalCancelLabel().isEmpty());
  }

  @Test
  void threeSegments_parsesBothLabels() {
    var syntax = ConfirmationGrammar.parse("Disband town? | Disband | Keep");
    assertEquals("Disband town?", syntax.promptText());
    assertEquals("Disband", syntax.confirmLabel());
    assertEquals("Keep", syntax.cancelLabel());
    assertEquals("Disband", syntax.optionalConfirmLabel().orElseThrow());
    assertEquals("Keep", syntax.optionalCancelLabel().orElseThrow());
  }

  @Test
  void fourSegments_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ConfirmationGrammar.parse("Prompt | Confirm | Cancel | Extra"));
  }

  @Test
  void nullContent_yieldsEmptyPromptText() {
    var syntax = ConfirmationGrammar.parse((String) null);
    assertEquals("", syntax.promptText());
    assertNull(syntax.confirmLabel());
    assertNull(syntax.cancelLabel());
    assertNull(syntax.mode());
    assertFalse(syntax.valueMode());
    assertNull(syntax.soundKey());
  }

  // ====================================================================
  // Quoted Pipes & Escaped Quotes
  // ====================================================================

  @Test
  void pipeInsideQuotes_treatedAsData() {
    var syntax = ConfirmationGrammar.parse("\"Choose A | B?\" | \"Option | 1\" | \"Option | 2\"");
    assertEquals("Choose A | B?", syntax.promptText());
    assertEquals("Option | 1", syntax.confirmLabel());
    assertEquals("Option | 2", syntax.cancelLabel());
  }

  @Test
  void escapedQuote_unescapedInOutput() {
    var syntax =
        ConfirmationGrammar.parse(
            "\"He said \\\"Delete\\\"\" | \"Yes \\\"Sure\\\"\" | \"No \\\"Never\\\"\"");
    assertEquals("He said \"Delete\"", syntax.promptText());
    assertEquals("Yes \"Sure\"", syntax.confirmLabel());
    assertEquals("No \"Never\"", syntax.cancelLabel());
  }

  @Test
  void mixedQuotedAndUnquotedSegments() {
    var syntax = ConfirmationGrammar.parse("\"<red>Delete town?</red>\" | Confirm | Cancel");
    assertEquals("<red>Delete town?</red>", syntax.promptText());
    assertEquals("Confirm", syntax.confirmLabel());
    assertEquals("Cancel", syntax.cancelLabel());
  }

  @Test
  void miniMessagePreservedInDisplayText() {
    var syntax =
        ConfirmationGrammar.parse(
            "\"<gradient:gold:yellow>Transfer</gradient> ${1}?\" | \"<green>Send</green>\" | \"<red>Abort</red>\"");
    assertEquals("<gradient:gold:yellow>Transfer</gradient> ${1}?", syntax.promptText());
    assertEquals("<green>Send</green>", syntax.confirmLabel());
    assertEquals("<red>Abort</red>", syntax.cancelLabel());
  }

  // ====================================================================
  // Flag Parsing: -mode, -value, -sound
  // ====================================================================

  @ParameterizedTest
  @ValueSource(strings = {"gui", "GUI", "Gui"})
  void modeFlag_gui(String modeStr) {
    var syntax = ConfirmationGrammar.parse("Confirm? -mode:" + modeStr);
    assertEquals(ConfirmationMode.GUI, syntax.mode());
    assertEquals("Confirm?", syntax.promptText());
  }

  @ParameterizedTest
  @ValueSource(strings = {"dialog", "DIALOG", "Dialog"})
  void modeFlag_dialog(String modeStr) {
    var syntax = ConfirmationGrammar.parse("Confirm? -mode:" + modeStr);
    assertEquals(ConfirmationMode.DIALOG, syntax.mode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"chat", "CHAT", "Chat"})
  void modeFlag_chat(String modeStr) {
    var syntax = ConfirmationGrammar.parse("Confirm? -mode:" + modeStr);
    assertEquals(ConfirmationMode.CHAT, syntax.mode());
  }

  @Test
  void valueFlag_setsValueMode() {
    var syntax = ConfirmationGrammar.parse("Enable PvP? -value");
    assertTrue(syntax.valueMode());
    assertEquals("Enable PvP?", syntax.promptText());
  }

  @Test
  void soundFlag_validNamespacedKey() {
    var syntax = ConfirmationGrammar.parse("Delete town? -sound:minecraft:block.note_block.bell");
    assertEquals("minecraft:block.note_block.bell", syntax.soundKey());
    assertEquals("minecraft:block.note_block.bell", syntax.optionalSoundKey().orElseThrow());
    assertEquals("Delete town?", syntax.promptText());
  }

  @Test
  void soundFlag_validBareKey() {
    var syntax = ConfirmationGrammar.parse("Delete town? -sound:entity.player.levelup");
    assertEquals("entity.player.levelup", syntax.soundKey());
  }

  @Test
  void allFlagsCombined() {
    var syntax =
        ConfirmationGrammar.parse(
            "\"Disband town?\" | \"Disband\" | \"Keep\" -mode:gui -value -sound:minecraft:block.note_block.bell");
    assertEquals("Disband town?", syntax.promptText());
    assertEquals("Disband", syntax.confirmLabel());
    assertEquals("Keep", syntax.cancelLabel());
    assertEquals(ConfirmationMode.GUI, syntax.mode());
    assertTrue(syntax.valueMode());
    assertEquals("minecraft:block.note_block.bell", syntax.soundKey());
  }

  // ====================================================================
  // Error Handling & Validation
  // ====================================================================

  @Test
  void invalidMode_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> ConfirmationGrammar.parse("Prompt -mode:telepathy"));
  }

  @Test
  void emptyModeValue_throws() {
    assertThrows(IllegalArgumentException.class, () -> ConfirmationGrammar.parse("Prompt -mode:"));
  }

  @Test
  void malformedModeFlag_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> ConfirmationGrammar.parse("Prompt -mode=gui"));
    assertThrows(IllegalArgumentException.class, () -> ConfirmationGrammar.parse("Prompt -mode"));
  }

  @Test
  void duplicateModeFlag_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ConfirmationGrammar.parse("Prompt -mode:gui -mode:chat"));
  }

  @Test
  void duplicateValueFlag_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> ConfirmationGrammar.parse("Prompt -value -value"));
  }

  @Test
  void malformedValueFlag_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> ConfirmationGrammar.parse("Prompt -value:true"));
    assertThrows(
        IllegalArgumentException.class, () -> ConfirmationGrammar.parse("Prompt -value=yes"));
  }

  @Test
  void duplicateSoundFlag_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ConfirmationGrammar.parse("Prompt -sound:minecraft:bell -sound:minecraft:chime"));
  }

  @Test
  void blankSoundKey_throws() {
    assertThrows(IllegalArgumentException.class, () -> ConfirmationGrammar.parse("Prompt -sound:"));
  }

  @Test
  void malformedSoundFlag_throws() {
    assertThrows(IllegalArgumentException.class, () -> ConfirmationGrammar.parse("Prompt -sound"));
    assertThrows(
        IllegalArgumentException.class, () -> ConfirmationGrammar.parse("Prompt -sound=bell"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Prompt -sound:foo$bar",
        "Prompt -sound:foo@bar",
        "Prompt -sound:a:b:c",
        "Prompt -sound:minecraft:",
        "Prompt -sound::bell",
        "Prompt -sound:invalid_sound!",
        "Prompt -sound:invalid#sound"
      })
  void invalidSoundKeySyntax_throws(String input) {
    assertThrows(IllegalArgumentException.class, () -> ConfirmationGrammar.parse(input));
  }

  @Test
  void unbalancedQuotes_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> ConfirmationGrammar.parse("\"Unbalanced prompt"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ConfirmationGrammar.parse("Prompt | \"Unbalanced confirm | Cancel"));
  }

  @Test
  void trailingEscape_throws() {
    assertThrows(IllegalArgumentException.class, () -> ConfirmationGrammar.parse("Prompt \\"));
  }

  // ====================================================================
  // CommandLineParser Integration & Fail-Closed Behavior
  // ====================================================================

  @Test
  void commandLineParser_parsesValidConfirmationTag() {
    var result = parser.parse("/town disband <c:Disband town? | Yes | No>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("c", tag.key());

    var syntax = ConfirmationGrammar.parse(tag);
    assertEquals("Disband town?", syntax.promptText());
    assertEquals("Yes", syntax.confirmLabel());
    assertEquals("No", syntax.cancelLabel());
  }

  @Test
  void commandLineParser_parsesConfirmAliasTag() {
    var result = parser.parse("/pay Steve 100 <confirm:Send 100 to Steve? -mode:dialog>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("confirm", tag.key());

    var syntax = ConfirmationGrammar.parse(tag);
    assertEquals("Send 100 to Steve?", syntax.promptText());
    assertEquals(ConfirmationMode.DIALOG, syntax.mode());
  }

  @Test
  void commandLineParser_preservesAuthoritativeTimeoutAndTitle() {
    var result =
        parser.parse(
            "/town disband <c:Disband? | Yes | No -timeout:30 -t:\"Warning\"|\"Are you sure?\"|50>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals(30, tag.timeout());
    assertNotNull(tag.title());
    assertEquals("Warning", tag.title().main());
    assertEquals("Are you sure?", tag.title().sub());
    assertEquals(50, tag.title().ticks());

    var syntax = ConfirmationGrammar.parse(tag);
    assertEquals("Disband?", syntax.promptText());
    assertEquals("Yes", syntax.confirmLabel());
    assertEquals("No", syntax.cancelLabel());
  }

  @Test
  void commandLineParser_quotedTagContainingDelimiter_parsedCorrectly() {
    var result = parser.parse("/pay <c:\"<red>Delete?</red>\" | \"Yes > No\" | \"Cancel\">");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    var syntax = ConfirmationGrammar.parse(tag);
    assertEquals("<red>Delete?</red>", syntax.promptText());
    assertEquals("Yes > No", syntax.confirmLabel());
    assertEquals("Cancel", syntax.cancelLabel());
  }

  @Test
  void commandLineParser_unbalancedQuoteInConfirmationTag_failsClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/town disband <c:\"Unbalanced quote | Yes | No>"));
  }

  @Test
  void commandLineParser_invalidModeFlag_failsClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/town disband <c:Disband? -mode:telepathy>"));
  }

  @Test
  void commandLineParser_invalidSoundFlag_failsClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/town disband <c:Disband? -sound:invalid$key>"));
  }

  @Test
  void commandLineParser_duplicateFlags_failsClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/town disband <c:Disband? -mode:gui -mode:chat>"));
  }

  // ====================================================================
  // Specification Test Cases (CONF-01, CONF-02, CONF-09, CONF-11)
  // ====================================================================

  @Test
  void conf01_gatingConfirmationAccept_advancesWithArityZero() {
    var parsed = parser.parse("/pay Steve 100 <c:Transfer 100 to Steve?>");
    var session = PromptSession.start("user1", parsed);
    assertTrue(session.isActive());

    // Gating mode confirmation advances with 0 answers
    var completed = session.submitAnswers(List.of(), 0);
    assertTrue(completed.isComplete());
    assertTrue(completed.answers().isEmpty());
    assertEquals(List.of(0), completed.submittedAnswerCounts());

    var finish = completed.finish();
    assertEquals("/pay Steve 100", finish.assembledCommand());
  }

  @Test
  void conf02_valueModeConfirmation_submitsTrueOnAccept_submitsFalseOnDecline() {
    var parsed = parser.parse("/togglepvp <c:Enable PvP? -value>");
    var tag = parsed.promptTags().get(0);
    var syntax = ConfirmationGrammar.parse(tag);
    assertTrue(syntax.valueMode());

    // Accept in value mode submits "true"
    var sessionAccept = PromptSession.start("user1", parsed).submitAnswer("true");
    assertTrue(sessionAccept.isComplete());
    assertEquals(List.of("true"), sessionAccept.answers());
    assertEquals("/togglepvp \"true\"", sessionAccept.finish().assembledCommand());

    // Decline in value mode submits "false"
    var sessionDecline = PromptSession.start("user1", parsed).submitAnswer("false");
    assertTrue(sessionDecline.isComplete());
    assertEquals(List.of("false"), sessionDecline.answers());
    assertEquals("/togglepvp \"false\"", sessionDecline.finish().assembledCommand());
  }

  @Test
  void conf09_invalidFlagsFailClosedAtParse() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("/test <c:Prompt -timeout:0>"));
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/test <c:Prompt -timeout:99999>"));
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/test <c:Prompt -timeout:nonnumeric>"));
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/test <c:Prompt -mode:telepathy>"));
  }

  @Test
  void conf11_valueModeOutputTokenExactLiterals() {
    var parsed = parser.parse("/zone set <c:Allow entry? -value>");
    var sessionTrue = PromptSession.start("user1", parsed).submitAnswer("true");
    var resultTrue = sessionTrue.finish();
    assertEquals("true", resultTrue.answers().get(0));
    assertEquals("/zone set \"true\"", resultTrue.assembledCommand());

    var sessionFalse = PromptSession.start("user1", parsed).submitAnswer("false");
    var resultFalse = sessionFalse.finish();
    assertEquals("false", resultFalse.answers().get(0));
    assertEquals("/zone set \"false\"", resultFalse.assembledCommand());
  }

  // ====================================================================
  // Colon & URL Preservation
  // ====================================================================

  @Test
  void confirmationColonParsing_colonInPromptTextPreserved() {
    var result = parser.parse("/town delete <c:Warning: delete town?>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("c", tag.key());
    assertNull(tag.filter(), "Confirmation tags must not parse second colon as generic filter");
    assertEquals("Warning: delete town?", tag.displayText());

    var syntax = ConfirmationGrammar.parse(tag);
    assertEquals("Warning: delete town?", syntax.promptText());
    assertNull(syntax.confirmLabel());
    assertNull(syntax.cancelLabel());
  }

  @Test
  void confirmationColonParsing_urlInPromptTextPreserved() {
    var result =
        parser.parse(
            "/verify <c:Visit https://example.com/confirm?id=1: Please click | Confirm | Cancel>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("c", tag.key());
    assertNull(tag.filter());
    assertEquals(
        "Visit https://example.com/confirm?id=1: Please click | Confirm | Cancel",
        tag.displayText());

    var syntax = ConfirmationGrammar.parse(tag);
    assertEquals("Visit https://example.com/confirm?id=1: Please click", syntax.promptText());
    assertEquals("Confirm", syntax.confirmLabel());
    assertEquals("Cancel", syntax.cancelLabel());
  }

  @Test
  void confirmationColonParsing_multipleColonsAndMiniMessage() {
    var result =
        parser.parse(
            "/town delete <confirm:\"<red>Warning:</red> Delete town?\" | \"Yes: confirm\" | \"No: cancel\">");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("confirm", tag.key());
    assertNull(tag.filter());

    var syntax = ConfirmationGrammar.parse(tag);
    assertEquals("<red>Warning:</red> Delete town?", syntax.promptText());
    assertEquals("Yes: confirm", syntax.confirmLabel());
    assertEquals("No: cancel", syntax.cancelLabel());
  }

  @Test
  void confirmationColonParsing_genericFilterOnOtherScreenTypesUnchanged() {
    var result = parser.parse("/give <d:num[0,10]:Enter amount>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("d", tag.key());
    assertEquals("num[0,10]", tag.filter());
    assertEquals("Enter amount", tag.displayText());
  }
}
