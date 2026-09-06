package dev.cyr1en.promptcore.parser;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.*;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CommandLineParserTest {

  private final CommandLineParser parser = new CommandLineParser();

  @Test
  void emptyString() {
    var result = parser.parse("");
    assertEquals("", result.templateCommand());
    assertTrue(result.promptTags().isEmpty());
    assertTrue(result.postCmds().isEmpty());
  }

  @Test
  void nullInput() {
    var result = parser.parse(null);
    assertEquals("", result.templateCommand());
    assertTrue(result.promptTags().isEmpty());
  }

  @Test
  void noPrompts() {
    var result = parser.parse("/kick Steve");
    assertEquals("/kick Steve", result.templateCommand());
    assertTrue(result.promptTags().isEmpty());
  }

  @Test
  void singleChatPrompt() {
    var result = parser.parse("/kick <>");
    assertEquals("/kick <>", result.templateCommand());
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("", tag.key());
    assertEquals("", tag.displayText());
    assertTrue(tag.sanitize());
  }

  @Test
  void singleKeyedPrompt() {
    var result = parser.parse("/ban <a:Why?>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("a", tag.key());
    assertEquals("Why?", tag.displayText());
  }

  @Test
  void keyedPromptWithFilter() {
    var result = parser.parse("/msg <p:w:Who?>");
    var tag = result.promptTags().get(0);
    assertEquals("p", tag.key());
    assertEquals("w", tag.filter());
    assertEquals("Who?", tag.displayText());
  }

  @Test
  void keyedPromptWithComplexFilter() {
    var result = parser.parse("/msg <p:r100:Who?>");
    var tag = result.promptTags().get(0);
    assertEquals("p", tag.key());
    assertEquals("r100", tag.filter());
    assertEquals("Who?", tag.displayText());
  }

  @Test
  void multiplePrompts() {
    var result = parser.parse("/kick <> <a:Why?>");
    assertEquals(2, result.promptTags().size());
    assertEquals("", result.promptTags().get(0).key());
    assertEquals("a", result.promptTags().get(1).key());
  }

  @Test
  void promptWithTextBeforeAndAfter() {
    var result = parser.parse("say <> world");
    assertEquals(1, result.promptTags().size());
    assertEquals("say <> world", result.templateCommand());
  }

  @Test
  void disableSanitization() {
    var result = parser.parse("/kick <-ds>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertFalse(tag.sanitize());
    assertEquals("", tag.displayText());
  }

  @Test
  void validatorAlias() {
    var result = parser.parse("/ban <-iv:required>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("required", tag.validatorAlias());
  }

  @Test
  void validatorAliasWithText() {
    var result = parser.parse("/ban <a:Why? -iv:required>");
    var tag = result.promptTags().get(0);
    assertEquals("required", tag.validatorAlias());
    assertEquals("Why?", tag.displayText());
  }

  @Test
  void blankValidatorAliasFailsClosed() {
    var error =
        assertThrows(IllegalArgumentException.class, () -> parser.parse("/ban <a:Why? -iv:>"));
    assertTrue(error.getMessage().contains("Blank input-validator alias"));
  }

  @Test
  void intTypeFlag() {
    var result = parser.parse("/tempban <-int>");
    var tag = result.promptTags().get(0);
    assertEquals(PromptTag.AnswerType.INTEGER, tag.type());
  }

  @Test
  void strTypeFlag() {
    var result = parser.parse("/msg <-str>");
    var tag = result.promptTags().get(0);
    assertEquals(PromptTag.AnswerType.STRING, tag.type());
  }

  @Test
  void combinedFlags() {
    var result = parser.parse("/ban <a:Enter name -str -iv:required -ds>");
    var tag = result.promptTags().get(0);
    assertEquals("a", tag.key());
    assertEquals("Enter name", tag.displayText());
    assertEquals(PromptTag.AnswerType.STRING, tag.type());
    assertEquals("required", tag.validatorAlias());
    assertFalse(tag.sanitize());
  }

  @Test
  void pcmOnComplete() {
    var result = parser.parse("/kick <> <! ban {0}>");
    assertEquals(1, result.postCmds().size());
    var pcm = result.postCmds().get(0);
    assertEquals("ban {0}", pcm.command());
    assertFalse(pcm.onCancel());
    assertEquals(DispatchTarget.PASSTHROUGH, pcm.dispatchTarget());
    assertArrayEquals(new int[] {0}, pcm.answerIndices());
  }

  @Test
  void pcmOnCancel() {
    var result = parser.parse("/kick <> <!! msg {0}>");
    assertEquals(1, result.postCmds().size());
    var pcm = result.postCmds().get(0);
    assertEquals("msg {0}", pcm.command());
    assertTrue(pcm.onCancel());
  }

  @Test
  void pcmWithDelay() {
    var result = parser.parse("/kick <> <!:20 msg {0}>");
    assertEquals(1, result.postCmds().size());
    var pcm = result.postCmds().get(0);
    assertEquals(20, pcm.delayTicks());
    assertEquals("msg {0}", pcm.command());
  }

  @Test
  void pcmWithConsoleTarget() {
    var result = parser.parse("/kick <> <! ban {0} @console>");
    var pcm = result.postCmds().get(0);
    assertEquals(DispatchTarget.CONSOLE, pcm.dispatchTarget());
  }

  @Test
  void pcmWithPlayerTarget() {
    var result = parser.parse("/kick <> <! say {0} @player>");
    var pcm = result.postCmds().get(0);
    assertEquals(DispatchTarget.PLAYER, pcm.dispatchTarget());
  }

  @Test
  void pcmWithMultipleAnswerRefs() {
    var result = parser.parse("/cmd <a:first> <a:second> <! calc {0} {1}>");
    var pcm = result.postCmds().get(0);
    assertArrayEquals(new int[] {0, 1}, pcm.answerIndices());
    assertEquals("calc {0} {1}", pcm.command());
  }

  @Test
  void pcmCombined() {
    var result = parser.parse("/kick <> <!:30 tempban {0} 7d @console>");
    var pcm = result.postCmds().get(0);
    assertEquals(DispatchTarget.CONSOLE, pcm.dispatchTarget());
    assertEquals(30, pcm.delayTicks());
    assertEquals("tempban {0} 7d", pcm.command());
    assertArrayEquals(new int[] {0}, pcm.answerIndices());
  }

  @Test
  void mixedPromptsAndPCMs() {
    var result = parser.parse("/ban <a:Why -str> <! tempban {0} 7d>");
    assertEquals(1, result.promptCount());
    assertEquals(1, result.pcmCount());

    var tag = result.promptTags().get(0);
    assertEquals("a", tag.key());
    assertEquals("Why", tag.displayText());
    assertEquals(PromptTag.AnswerType.STRING, tag.type());

    var pcm = result.postCmds().get(0);
    assertEquals("tempban {0} 7d", pcm.command());
  }

  @Test
  void emptyTag() {
    var result = parser.parse("/kick <><a:>");
    assertEquals(2, result.promptTags().size());
    assertEquals("", result.promptTags().get(0).displayText());
    assertEquals("a", result.promptTags().get(1).key());
    assertEquals("", result.promptTags().get(1).displayText());
  }

  @Test
  void specialCharsInText() {
    var result = parser.parse("/kick <a:§cHello {br} world!>");
    var tag = result.promptTags().get(0);
    assertEquals("§cHello {br} world!", tag.displayText());
  }

  @Test
  void rawTagPreservesOriginalFormat() {
    var result = parser.parse("/ban <a:Why? -ds>");
    var tag = result.promptTags().get(0);
    assertEquals("<a:Why? -ds>", tag.rawTag());
  }

  @Test
  void keyWithoutColon() {
    var result = parser.parse("/cmd <a>");
    var tag = result.promptTags().get(0);
    assertEquals("", tag.key());
    assertEquals("a", tag.displayText());
  }

  @Test
  void promptCount() {
    var result = parser.parse("/a <> <a:b> <s:c>");
    assertEquals(3, result.promptCount());
  }

  @Test
  void hasPrompts() {
    assertFalse(parser.parse("/kick Steve").hasPrompts());
    assertTrue(parser.parse("/kick <>").hasPrompts());
  }

  @Test
  void pcmCount() {
    var result = parser.parse("/a <> <! cmd> <!! cmd2>");
    assertEquals(2, result.pcmCount());
  }

  @Test
  void onCompletePCMs() {
    var result = parser.parse("/a <> <! cmd1> <!! cmd2>");
    assertEquals(1, result.onCompletePCMs().size());
    assertEquals("cmd1", result.onCompletePCMs().get(0).command());
  }

  @Test
  void onCancelPCMs() {
    var result = parser.parse("/a <> <! cmd1> <!! cmd2>");
    assertEquals(1, result.onCancelPCMs().size());
    assertEquals("cmd2", result.onCancelPCMs().get(0).command());
  }

  @ParameterizedTest
  @MethodSource("pcmSyntaxVariants")
  void pcmSyntaxVariants(
      String input,
      String expectedCommand,
      boolean onCancel,
      int delay,
      DispatchTarget target,
      int[] indices) {
    var result = parser.parse(input);
    assertEquals(1, result.postCmds().size(), "Expected exactly one PCM for: " + input);
    var pcm = result.postCmds().get(0);
    assertEquals(expectedCommand, pcm.command(), "command mismatch for: " + input);
    assertEquals(onCancel, pcm.onCancel(), "onCancel mismatch for: " + input);
    assertEquals(delay, pcm.delayTicks(), "delay mismatch for: " + input);
    assertEquals(target, pcm.dispatchTarget(), "dispatchTarget mismatch for: " + input);
    assertArrayEquals(indices, pcm.answerIndices(), "indices mismatch for: " + input);
  }

  static Stream<Arguments> pcmSyntaxVariants() {
    return Stream.of(
        Arguments.of("<! msg>", "msg", false, 0, DispatchTarget.PASSTHROUGH, new int[] {}),
        Arguments.of("<!! msg>", "msg", true, 0, DispatchTarget.PASSTHROUGH, new int[] {}),
        Arguments.of("<!:20 msg>", "msg", false, 20, DispatchTarget.PASSTHROUGH, new int[] {}),
        Arguments.of("<! msg @console>", "msg", false, 0, DispatchTarget.CONSOLE, new int[] {}),
        Arguments.of("<! msg @player>", "msg", false, 0, DispatchTarget.PLAYER, new int[] {}),
        Arguments.of("<! ban {0}>", "ban {0}", false, 0, DispatchTarget.PASSTHROUGH, new int[] {0}),
        Arguments.of(
            "<! calc {0} {1}>",
            "calc {0} {1}", false, 0, DispatchTarget.PASSTHROUGH, new int[] {0, 1}),
        Arguments.of(
            "<!!:50 warn {0} @console>",
            "warn {0}", true, 50, DispatchTarget.CONSOLE, new int[] {0}));
  }

  @Test
  void escapedTagIsNotMatched() {
    var result = parser.parse("hello \\<a:foo\\> world");
    assertTrue(result.promptTags().isEmpty(), "Escaped tag should not be a prompt tag");
    assertEquals("hello <a:foo> world", result.templateCommand());
  }

  @Test
  void escapeInsidePromptIsLiteral() {
    var result = parser.parse("<a:Why? \\<test?>>");
    assertEquals(1, result.promptTags().size());
    var display = result.promptTags().get(0).displayText();
    assertTrue(display.contains("<test?"), "Escaped opening bracket should be literal: " + display);
  }

  @Test
  void mixedEscapedAndReal() {
    var result = parser.parse("\\<a:literal\\> <a:real>");
    assertEquals(1, result.promptTags().size());
    assertEquals("a", result.promptTags().get(0).key());
    assertEquals("real", result.promptTags().get(0).displayText());
    assertEquals("<a:literal> <a:real>", result.templateCommand());
  }

  @Test
  void unifiedDialogForm_noFilterDefaultsToText() {
    // No filter defaults to text input.
    var result = parser.parse("/test <d:Title>");
    var tag = result.promptTags().get(0);
    assertEquals("d", tag.key());
    assertNull(tag.filter());
    assertEquals("Title", tag.displayText());
  }

  @Test
  void unifiedDialogForm_textExplicit() {
    var result = parser.parse("/test <d:text:Title>");
    var tag = result.promptTags().get(0);
    assertEquals("d", tag.key());
    assertEquals("text", tag.filter());
    assertEquals("Title", tag.displayText());
  }

  @Test
  void unifiedDialogForm_textMinMax() {
    // Constraint block rides inside the filter slot, same as the player-UI
    // form. The display text starts after the second colon.
    var result = parser.parse("/test <d:text[0,32]:Title>");
    var tag = result.promptTags().get(0);
    assertEquals("text[0,32]", tag.filter());
    assertEquals("Title", tag.displayText());
  }

  @Test
  void unifiedDialogForm_boolDefault() {
    var result = parser.parse("/test <d:bool:Confirm?>");
    var tag = result.promptTags().get(0);
    assertEquals("bool", tag.filter());
    assertEquals("Confirm?", tag.displayText());
  }

  @Test
  void unifiedDialogForm_boolInitialTrue() {
    var result = parser.parse("/test <d:bool[true]:Confirm?>");
    var tag = result.promptTags().get(0);
    assertEquals("bool[true]", tag.filter());
  }

  @Test
  void unifiedDialogForm_boolInitialFalse() {
    var result = parser.parse("/test <d:bool[false]:Confirm?>");
    var tag = result.promptTags().get(0);
    assertEquals("bool[false]", tag.filter());
  }

  @Test
  void unifiedDialogForm_numberMinMax() {
    var result = parser.parse("/test <d:num[1,64]:Amount>");
    var tag = result.promptTags().get(0);
    assertEquals("num[1,64]", tag.filter());
    assertEquals("Amount", tag.displayText());
  }

  @Test
  void unifiedDialogForm_numberFullSpec() {
    // Four-arg form: min, max, step, initial.
    var result = parser.parse("/test <d:num[0,100,5,42]:Amount>");
    var tag = result.promptTags().get(0);
    assertEquals("num[0,100,5,42]", tag.filter());
    assertEquals("Amount", tag.displayText());
  }

  @Test
  void unifiedDialogForm_caseInsensitive() {
    // Parser preserves case; normalization happens downstream.
    var result = parser.parse("/test <d:NUM[0,100]:Amount>");
    var tag = result.promptTags().get(0);
    assertEquals("NUM[0,100]", tag.filter(), "Parser preserves the captured case");
    assertEquals(
        "num[0,100]",
        tag.filter().toLowerCase(),
        "Downstream normalizes before matching the kind keyword");
  }

  @Test
  void unifiedDialogForm_displayPreservesKeyword() {
    // Bare keyword in display is not parsed as a filter.
    var result = parser.parse("/test <d:Confirm with bool value?>");
    var tag = result.promptTags().get(0);
    assertNull(tag.filter(), "Bare keyword in display text must not be picked up as a filter");
    assertEquals("Confirm with bool value?", tag.displayText());
  }

  @Test
  void unifiedDialogForm_displayWithBrackets() {
    // Display text brackets are preserved.
    var result = parser.parse("/test <d:num[0,100]:Items [0-99]>");
    var tag = result.promptTags().get(0);
    assertEquals("num[0,100]", tag.filter());
    assertEquals("Items [0-99]", tag.displayText());
  }

  @Test
  void unifiedDialogForm_flagsCombined() {
    // Flags after display text are parsed and stripped.
    var result = parser.parse("/test <d:text:Greeting -ds -iv:min -str>");
    var tag = result.promptTags().get(0);
    assertEquals("d", tag.key());
    assertEquals("text", tag.filter());
    assertFalse(tag.sanitize(), "-ds must flip sanitize to false");
    assertEquals("min", tag.validatorAlias());
    assertEquals(PromptTag.AnswerType.STRING, tag.type());
    assertEquals("Greeting", tag.displayText());
  }

  @Test
  void unifiedDialogForm_legacyTrailingKindIsPlainDisplay() {
    // Legacy trailing-kind form is no longer parsed.
    var result = parser.parse("/test <d:Title bool>");
    var tag = result.promptTags().get(0);
    assertNull(tag.filter(), "Legacy form must not populate filter");
    assertEquals("Title bool", tag.displayText(), "Kind keyword stays in display text");
  }

  @Test
  void unifiedDialogForm_deprecationWarningFiresForLegacyForm() {
    // Deprecation warning fires for legacy form.
    var logger = java.util.logging.Logger.getLogger(CommandLineParser.class.getName());
    var captured = new java.util.ArrayList<java.util.logging.LogRecord>();
    var handler =
        new java.util.logging.Handler() {
          @Override
          public void publish(java.util.logging.LogRecord record) {
            captured.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    logger.addHandler(handler);
    try {
      parser.parse("/test <d:Title bool>");
    } finally {
      logger.removeHandler(handler);
    }
    assertTrue(
        captured.stream()
            .anyMatch(
                r ->
                    r.getLevel() == java.util.logging.Level.WARNING
                        && r.getMessage() != null
                        && r.getMessage().contains("trailing-kind")),
        "Expected a deprecation warning for legacy trailing-kind form");
  }

  @Test
  void unifiedDialogForm_deprecationWarningSkippedForNonDialogKey() {
    // The detector is key-scoped: a sign prompt ending in the word
    // "text" must not fire the dialog deprecation warning. The
    // trailing-kind pattern would otherwise match it, but the key
    // is not "d" so the warning is suppressed.
    var logger = java.util.logging.Logger.getLogger(CommandLineParser.class.getName());
    var captured = new java.util.ArrayList<java.util.logging.LogRecord>();
    var handler =
        new java.util.logging.Handler() {
          @Override
          public void publish(java.util.logging.LogRecord record) {
            captured.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    logger.addHandler(handler);
    try {
      parser.parse("/test <s:Greeting text>");
    } finally {
      logger.removeHandler(handler);
    }
    assertTrue(
        captured.stream()
            .noneMatch(
                r ->
                    r.getLevel() == java.util.logging.Level.WARNING
                        && r.getMessage() != null
                        && r.getMessage().contains("trailing-kind")),
        "No deprecation warning for non-dialog keys");
  }

  @Test
  void titleFlagStandalone() {
    var result = parser.parse("/kick <a:Why? -t>");
    var tag = result.promptTags().get(0);
    assertNotNull(tag.title());
    assertEquals("", tag.title().main());
    assertNull(tag.title().sub());
    assertNull(tag.title().ticks());
    // -t flag stripped from display text.
    assertEquals("Why?", tag.displayText());
  }

  @Test
  void titleFlagWithQuotedMain() {
    var result = parser.parse("/kick <a:Why? -t:\"Main Title\">");
    var tag = result.promptTags().get(0);
    assertNotNull(tag.title());
    assertEquals("Main Title", tag.title().main());
    assertNull(tag.title().sub());
    assertNull(tag.title().ticks());
    assertEquals("Why?", tag.displayText());
  }

  @Test
  void titleFlagWithMainSubAndStay() {
    var result = parser.parse("/kick <a:Why? -t:Main|Sub|70>");
    var tag = result.promptTags().get(0);
    var tagTitle = tag.title();
    assertNotNull(tagTitle);
    assertEquals("Main", tagTitle.main());
    assertEquals("Sub", tagTitle.sub());
    assertEquals(70, tagTitle.ticks());
    assertEquals("Why?", tag.displayText());
  }

  @Test
  void titleFlagWithQuotedMainAndSubAndStay() {
    var result = parser.parse("/kick <a:Why? -t:\"Main Title\"|\"Sub Title\"|100>");
    var tag = result.promptTags().get(0);
    var tagTitle = tag.title();
    assertNotNull(tagTitle);
    assertEquals("Main Title", tagTitle.main());
    assertEquals("Sub Title", tagTitle.sub());
    assertEquals(100, tagTitle.ticks());
  }

  @Test
  void titleFlagSkipSubWithDoublePipe() {
    var result = parser.parse("/kick <a:Why? -t:\"Main\"||70>");
    var tag = result.promptTags().get(0);
    var tagTitle = tag.title();
    assertNotNull(tagTitle);
    assertEquals("Main", tagTitle.main());
    assertNull(tagTitle.sub());
    assertEquals(70, tagTitle.ticks());
  }

  @Test
  void titleFlagOnlyMain() {
    var result = parser.parse("/kick <a:Why? -t:MainOnly>");
    var tag = result.promptTags().get(0);
    assertNotNull(tag.title());
    assertEquals("MainOnly", tag.title().main());
    assertNull(tag.title().sub());
    assertNull(tag.title().ticks());
  }

  @Test
  void titleFlagStrippedFromDisplayText() {
    var result = parser.parse("/kick <a:Why? -t:\"Main\"|Sub|70>");
    var tag = result.promptTags().get(0);
    assertEquals("Why?", tag.displayText());
    assertFalse(tag.displayText().contains("-t"));
  }

  @Test
  void titleFlagWithCompoundDialog() {
    var result = parser.parse("/ban <d:text:Reason && d:num[0,24]:Days -t:\"Ban Form\"||50>");
    var tag = result.promptTags().get(0);
    var tagTitle = tag.title();
    assertNotNull(tagTitle);
    assertEquals("Ban Form", tagTitle.main());
    assertNull(tagTitle.sub());
    assertEquals(50, tagTitle.ticks());
    assertTrue(tag.isCompound());
    assertEquals(2, tag.subTags().size());
  }

  @Test
  void titleFlagStandaloneWithCompoundDialog() {
    var result = parser.parse("/ban <d:text:Reason && d:num[0,24]:Days -t>");
    var tag = result.promptTags().get(0);
    assertNotNull(tag.title());
    assertEquals("", tag.title().main());
    assertNull(tag.title().sub());
    assertNull(tag.title().ticks());
    assertTrue(tag.isCompound());
  }

  @Test
  void noTitleFlagYieldsNullTitle() {
    var result = parser.parse("/kick <a:Why?>");
    var tag = result.promptTags().get(0);
    assertNull(tag.title());
  }

  // ============================= Tag Filter Tests =============================

  @Test
  void tagFilterSkipsMatchingTags() {
    // Simulate a MiniMessage-style filter: skip tags whose content is a known formatting word.
    TagFilter skipRed = content -> content.equals("red") || content.equals("/red");
    var filteredParser = new CommandLineParser(ParserConfig.ANGLE_BRACKETS, skipRed);

    var result = filteredParser.parse("/say <red>Hello</red> <a:Name?>");
    // <red> and </red> are skipped; only <a:Name?> is a prompt.
    assertEquals(1, result.promptTags().size());
    assertEquals("Name?", result.promptTags().get(0).displayText());
    // Template preserves the original string (MiniMessage tags left intact).
    assertEquals("/say <red>Hello</red> <a:Name?>", result.templateCommand());
  }

  @Test
  void tagFilterSkipsClosingTags() {
    TagFilter skipBold = content -> content.equals("bold") || content.equals("/bold");
    var filteredParser = new CommandLineParser(ParserConfig.ANGLE_BRACKETS, skipBold);

    var result = filteredParser.parse("/say <bold>text</bold> <>");
    assertEquals(1, result.promptTags().size());
    assertEquals("", result.promptTags().get(0).displayText());
  }

  @Test
  void tagFilterDoesNotAffectNonMatchingTags() {
    TagFilter skipNothing = content -> false;
    var filteredParser = new CommandLineParser(ParserConfig.ANGLE_BRACKETS, skipNothing);

    var result = filteredParser.parse("/kick <a:Why?> <s:How?>");
    assertEquals(2, result.promptTags().size());
  }

  @Test
  void tagFilterAffectsHasTagForm() {
    TagFilter skipRed = content -> content.equals("red") || content.equals("/red");
    var filteredParser = new CommandLineParser(ParserConfig.ANGLE_BRACKETS, skipRed);

    // Only MiniMessage tags → hasTagForm returns false.
    assertFalse(filteredParser.hasTagForm("/say <red>Hello</red>"));
    // Mix of MiniMessage and prompt → hasTagForm returns true.
    assertTrue(filteredParser.hasTagForm("/say <red>Hello</red> <a:Name?>"));
    // No tags at all → false.
    assertFalse(filteredParser.hasTagForm("/say hello"));
  }

  @Test
  void tagFilterDoesNotAffectPCMs() {
    // PCMs (starting with !) should not be filtered.
    TagFilter skipRed = content -> content.equals("red");
    var filteredParser = new CommandLineParser(ParserConfig.ANGLE_BRACKETS, skipRed);

    var result = filteredParser.parse("/kick <> <! ban {0}>");
    assertEquals(1, result.promptTags().size());
    assertEquals(1, result.postCmds().size());
  }

  @Test
  void nullTagFilterBehavesLikeNoFilter() {
    var p = new CommandLineParser(ParserConfig.ANGLE_BRACKETS, null);
    var result = p.parse("/say <red>Hello</red> <a:Name?>");
    // Without a filter, <red>, </red>, and <a:Name?> are all treated as prompt tags.
    assertEquals(3, result.promptTags().size());
  }

  @Test
  void pcmTargetRequiresBothTokenBoundaries() {
    var result = parser.parse("/say <!hello@console>");
    var pcm = result.postCmds().get(0);

    assertEquals("hello@console", pcm.command());
    assertEquals(DispatchTarget.PASSTHROUGH, pcm.dispatchTarget());
  }

  @Test
  void flagsDoNotMatchDisplayTextSubstrings() {
    var result = parser.parse("/say <a:cost-int cost-str cost-ds cost-iv:required>");
    var tag = result.promptTags().get(0);

    assertEquals(PromptTag.AnswerType.NONE, tag.type());
    assertTrue(tag.sanitize());
    assertNull(tag.validatorAlias());
    assertEquals("cost-int cost-str cost-ds cost-iv:required", tag.displayText());
  }

  @Test
  void parsedPcmSpansRemoveOnlyTheExactParsedTag() {
    var filtered =
        new CommandLineParser(ParserConfig.ANGLE_BRACKETS, content -> content.startsWith("!"));
    var parsed = filtered.parse("/say \\<!kept> <!removed>");

    assertTrue(parsed.postCmds().isEmpty());
    assertEquals("/say <!kept> <!removed> ", ParsedCommand.buildPartialCommand(parsed, List.of()));
  }

  @Test
  void unterminatedRepeatedOpenersAreScannedInLinearTime() {
    var input = "<".repeat(200_000);

    assertTimeoutPreemptively(
        Duration.ofSeconds(2),
        () -> {
          assertFalse(parser.hasTagForm(input));
          assertTrue(parser.parse(input).promptTags().isEmpty());
        });
  }

  @Test
  void getTagFilterReturnsConfiguredFilter() {
    TagFilter filter = content -> true;
    var p = new CommandLineParser(ParserConfig.ANGLE_BRACKETS, filter);
    assertSame(filter, p.getTagFilter());
  }

  @Test
  void getTagFilterReturnsNullWhenNotSet() {
    var p = new CommandLineParser();
    assertNull(p.getTagFilter());
  }

  // ====================================================================
  // Timeout Parsing & Bounds (SEC-02)
  // ====================================================================

  @Test
  void timeoutFlagExtractedCorrectly() {
    var result = parser.parse("/test <a:Prompt -timeout:30>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("Prompt", tag.displayText());
    assertEquals(30, tag.timeout());
  }

  @Test
  void timeoutBoundsMinAndMaxAccepted() {
    var minResult = parser.parse("/test <a:Prompt -timeout:1>");
    assertEquals(1, minResult.promptTags().get(0).timeout());

    var maxResult = parser.parse("/test <a:Prompt -timeout:3600>");
    assertEquals(3600, maxResult.promptTags().get(0).timeout());
  }

  @Test
  void timeoutInCompoundTagExtracted() {
    var result = parser.parse("/test <d:text:Name && d:num[0,10]:Age -timeout:45>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertTrue(tag.isCompound());
    assertEquals(45, tag.timeout());
  }

  @Test
  void compoundDialogWithEarlyBreakIfAndFlags() {
    var result =
        parser.parse(
            "/test <d:text:Reason -ds -breakIf:{0} equals \"skip\" && d:num[0,10]:Days -timeout:30>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertTrue(tag.isCompound());
    assertEquals(2, tag.subTags().size());
    assertFalse(tag.sanitize());
    assertEquals(30, tag.timeout());
    assertTrue(tag.hasBreakIf());
    assertEquals("{0} equals \"skip\"", tag.breakIf().source());
  }

  @Test
  void sec02_timeoutZeroFailsClosed() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("/test <a:Prompt -timeout:0>"));
  }

  @Test
  void sec02_timeoutNegativeFailsClosed() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/test <a:Prompt -timeout:-5>"));
  }

  @Test
  void sec02_timeoutExcessiveFailsClosed() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/test <a:Prompt -timeout:99999>"));
  }

  @Test
  void sec02_timeoutNonNumericFailsClosed() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/test <a:Prompt -timeout:abc>"));
  }

  @Test
  void sec02_timeoutEmptyValueFailsClosed() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("/test <a:Prompt -timeout:>"));
  }

  @Test
  void sec02_timeoutBareFlagFailsClosed() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("/test <a:Prompt -timeout>"));
  }

  @Test
  void sec02_timeoutEqualsSyntaxFailsClosed() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/test <a:Prompt -timeout=10>"));
  }

  @Test
  void sec02_duplicateTimeoutFlagsFailClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/test <a:Prompt -timeout:10 -timeout:20>"));
  }

  // ====================================================================
  // Max 16 Tags Constraint
  // ====================================================================

  @Test
  void sixteenPromptTagsAllowed() {
    var sb = new StringBuilder("/cmd");
    for (int i = 0; i < 16; i++) {
      sb.append(" <a:Tag").append(i).append(">");
    }
    var result = parser.parse(sb.toString());
    assertEquals(16, result.promptTags().size());
  }

  @Test
  void seventeenPromptTagsRejected() {
    var sb = new StringBuilder("/cmd");
    for (int i = 0; i < 17; i++) {
      sb.append(" <a:Tag").append(i).append(">");
    }
    assertThrows(IllegalArgumentException.class, () -> parser.parse(sb.toString()));
  }

  // ====================================================================
  // Quote-Aware Scanning Regressions for Ordinary Tags
  // ====================================================================

  @Test
  void ordinaryTagWithQuotesAndGreaterSign_parsedCorrectly() {
    var result = parser.parse("/test <a:\"Enter value > 0\">");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("a", tag.key());
    assertEquals("\"Enter value > 0\"", tag.displayText());
  }

  @Test
  void ordinaryTagWithEscapedDelimiter_parsedCorrectly() {
    var result = parser.parse("/test <a:Why? \\>>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("Why? >", tag.displayText());
  }

  @Test
  void ordinaryTagWithEscapedQuotes_parsedCorrectly() {
    var result = parser.parse("/test <a:He said \\\"Hello\\\">");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("He said \\\"Hello\\\"", tag.displayText());
  }

  @Test
  void multipleOrdinaryTagsWithQuotes() {
    var result = parser.parse("/msg <p:\"Target > 1\"> <a:\"Message > 2\">");
    assertEquals(2, result.promptTags().size());
    assertEquals("p", result.promptTags().get(0).key());
    assertEquals("\"Target > 1\"", result.promptTags().get(0).displayText());
    assertEquals("a", result.promptTags().get(1).key());
    assertEquals("\"Message > 2\"", result.promptTags().get(1).displayText());
  }

  @Test
  void confirmationTag_preservesColonsInDisplayText() {
    var result = parser.parse("/test <c:Warning: delete town?>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("c", tag.key());
    assertNull(tag.filter(), "Confirmation tags should not treat second colon as generic filter");
    assertEquals("Warning: delete town?", tag.displayText());
  }

  // ====================================================================
  // Inline PCM Delay Bounds & Overflow Tests
  // ====================================================================

  @Test
  void pcmDelay_boundaryZero_accepted() {
    var result = parser.parse("/test <!:0 msg>");
    assertEquals(1, result.postCmds().size());
    assertEquals(0, result.postCmds().get(0).delayTicks());
    assertEquals("msg", result.postCmds().get(0).command());
  }

  @Test
  void pcmDelay_boundaryMax72000_accepted() {
    var result = parser.parse("/test <!:72000 msg>");
    assertEquals(1, result.postCmds().size());
    assertEquals(72000, result.postCmds().get(0).delayTicks());
    assertEquals("msg", result.postCmds().get(0).command());
  }

  @Test
  void pcmDelay_overMax72001_rejected() {
    var ex =
        assertThrows(IllegalArgumentException.class, () -> parser.parse("/test <!:72001 msg>"));
    assertTrue(ex.getMessage().contains("PCM delay out of bounds"));
  }

  @Test
  void pcmDelay_overflowLargeNumber_rejected() {
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse("/test <!:999999999999999999999999 msg>"));
    assertTrue(ex.getMessage().contains("PCM delay"));
  }

  @Test
  void pcmDelay_overflowIntegerMax_rejected() {
    var ex =
        assertThrows(
            IllegalArgumentException.class, () -> parser.parse("/test <!:2147483648 msg>"));
    assertTrue(ex.getMessage().contains("PCM delay"));
  }

  @Test
  void pcmDelay_nonNumeric_rejected() {
    var ex = assertThrows(IllegalArgumentException.class, () -> parser.parse("/test <!:abc msg>"));
    assertTrue(ex.getMessage().contains("Invalid PCM delay"));
  }

  @Test
  void pcmDelay_empty_rejected() {
    var ex = assertThrows(IllegalArgumentException.class, () -> parser.parse("/test <!: msg>"));
    assertTrue(ex.getMessage().contains("Invalid PCM delay"));
  }

  @Test
  void pcmDelay_spaceBeforeNumber_rejected() {
    var ex = assertThrows(IllegalArgumentException.class, () -> parser.parse("/test <!: 20 msg>"));
    assertTrue(ex.getMessage().contains("Invalid PCM delay"));
  }

  @Test
  void pcmDelay_negative_rejected() {
    var ex = assertThrows(IllegalArgumentException.class, () -> parser.parse("/test <!:-1 msg>"));
    assertTrue(ex.getMessage().contains("Invalid PCM delay"));
  }

  @Test
  void pcmDelay_presetWithBoundaries() {
    var valid = parser.parse("/test <!:72000@delayed_preset>");
    assertEquals(1, valid.postCmds().size());
    assertEquals(72000, valid.postCmds().get(0).delayTicks());
    assertEquals("delayed_preset", valid.postCmds().get(0).command());

    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/test <!:72001@delayed_preset>"));
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/test <!:abc@delayed_preset>"));
  }

  @Test
  void parserFineLogs_doNotLeakCommandPayload() {
    var logger = java.util.logging.Logger.getLogger(CommandLineParser.class.getName());
    var originalLevel = logger.getLevel();
    logger.setLevel(java.util.logging.Level.FINEST);
    var captured = new java.util.ArrayList<String>();
    var handler =
        new java.util.logging.Handler() {
          @Override
          public void publish(java.util.logging.LogRecord record) {
            captured.add(record.getMessage());
          }

          @Override
          public void flush() {}

          @Override
          public void close() throws SecurityException {}
        };
    handler.setLevel(java.util.logging.Level.FINEST);
    logger.addHandler(handler);
    try {
      parser.parse("/secretcmd <a:secret_display_text> <! secret_post_cmd>");
      for (var msg : captured) {
        assertFalse(
            msg.contains("secret_display_text"), "FINE log must not contain display text: " + msg);
        assertFalse(
            msg.contains("secret_post_cmd"), "FINE log must not contain post command: " + msg);
        assertFalse(msg.contains("secretcmd"), "FINE log must not contain raw command: " + msg);
      }
    } finally {
      logger.removeHandler(handler);
      logger.setLevel(originalLevel);
    }
  }

  // ====================================================================
  // Custom Syntax & Multi-character Delimiters
  // ====================================================================

  @Test
  void customSyntax_bracesDelimiters() {
    var customParser = new CommandLineParser(new ParserConfig("{", "}", "\\"));
    var result = customParser.parse("/kick {a:Player} {a:Reason} {! notify}");
    assertEquals(2, result.promptTags().size());
    assertEquals("Player", result.promptTags().get(0).displayText());
    assertEquals("Reason", result.promptTags().get(1).displayText());
    assertEquals(1, result.postCmds().size());
    assertEquals("notify", result.postCmds().get(0).command());
  }

  @Test
  void customSyntax_multiCharacterDelimitersAndEscapes() {
    var customParser = new CommandLineParser(new ParserConfig("{{", "}}", "%%"));
    var result = customParser.parse("/msg %%{{not_a_tag%%}} {{p:Player}} {{a:Message}}");
    assertEquals(2, result.promptTags().size());
    assertEquals("Player", result.promptTags().get(0).displayText());
    assertEquals("Message", result.promptTags().get(1).displayText());
    assertEquals("/msg {{not_a_tag}} {{p:Player}} {{a:Message}}", result.templateCommand());
  }

  @Test
  void customSyntax_quotesAndEscapedDelimiters() {
    var customParser = new CommandLineParser(new ParserConfig("{", "}", "\\"));
    var result = customParser.parse("/msg {p:\"Target } with } braces\"} \\{not_tag\\}");
    assertEquals(1, result.promptTags().size());
    assertEquals("\"Target } with } braces\"", result.promptTags().get(0).displayText());
    assertEquals("/msg {p:\"Target } with } braces\"} {not_tag}", result.templateCommand());
  }

  @Test
  void customSyntax_malformedInputs() {
    var customParser = new CommandLineParser(new ParserConfig("{{", "}}", "\\"));

    // Unclosed tag
    assertThrows(IllegalArgumentException.class, () -> customParser.parse("/msg {{c:Unclosed"));
    // Unbalanced quotes
    assertThrows(
        IllegalArgumentException.class, () -> customParser.parse("/msg {{a:\"unclosed quote}}"));
  }

  @Test
  void escapedQuotesDoNotCloseQuotedTagContent() {
    var parsed = parser.parse("/cmd <c:\"A \\\" > B\"> <a:Next>");
    assertEquals(2, parsed.promptCount());
    assertEquals(
        "A \" > B",
        dev.cyr1en.promptcore.ConfirmationGrammar.parse(parsed.promptTags().getFirst())
            .promptText());
    assertEquals("Next", parsed.promptTags().get(1).displayText());
  }

  @Test
  void standardFlagsInsideQuotedTextAreLiteral() {
    var display = "\"Keep -ds -iv:fake -int -str -timeout:bad -t:fake literal\"";
    for (var key : List.of("a", "c", "i", "customscreen")) {
      var tag =
          parser
              .parse("/cmd <" + key + ":" + display + " -iv:req -timeout:30 -t:Real>")
              .promptTags()
              .getFirst();
      assertEquals(display, tag.displayText());
      assertTrue(tag.sanitize());
      assertEquals(PromptTag.AnswerType.NONE, tag.type());
      assertEquals("req", tag.validatorAlias());
      assertEquals(30, tag.timeout());
      assertEquals("Real", tag.title().main());
    }
    var compound =
        parser
            .parse("/cmd <d:text:" + display + " && d:text:Next -timeout:30>")
            .promptTags()
            .getFirst();
    assertTrue(compound.sanitize());
    assertEquals(display, compound.subTags().getFirst().displayText());
    assertEquals(30, compound.timeout());
  }
}
