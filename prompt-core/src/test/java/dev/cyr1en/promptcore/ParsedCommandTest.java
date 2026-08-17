package dev.cyr1en.promptcore;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cyr1en.promptcore.parser.CommandLineParser;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Direct unit coverage for {@link ParsedCommand#buildPartialCommand}.
 *
 * <p>{@code buildPartialCommand} is the bridge from session answers to Brigadier input. The {@code
 * d:tab} dialog relies on it to reconstruct a parseable command so the NMS dispatcher can produce
 * completions. The other tests ({@code CommandLineParserTest}, {@code PromptSessionTest}) exercise
 * the method only indirectly.
 */
class ParsedCommandTest {

  private final CommandLineParser parser = new CommandLineParser();

  @Test
  void emptyCommandAndNoAnswers() {
    var parsed = parser.parse("");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of());
    assertEquals(" ", partial);
  }

  @Test
  void noPromptsCommandReturnedWithTrailingSpace() {
    var parsed = parser.parse("/kick Steve");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of());
    assertEquals("/kick Steve ", partial);
  }

  @Test
  void singleTagReplacedByAnswer() {
    var parsed = parser.parse("/ban <a:Why?>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("spamming"));
    assertEquals("/ban spamming ", partial);
  }

  @Test
  void multipleTagsReplacedByAnswers() {
    var parsed = parser.parse("/give <a:Player> <a:Amount>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("Steve", "64"));
    assertEquals("/give Steve 64 ", partial);
  }

  @Test
  void fewerAnswersRemovesUnansweredTags() {
    // First tag answered, second tag discarded from partial command.
    var parsed = parser.parse("/give <a:Player> <a:Amount>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("Steve"));
    assertEquals("/give Steve ", partial);
    assertFalse(partial.contains("<"));
    assertFalse(partial.contains(">"));
  }

  @Test
  void moreAnswersThanTagsKeepsAllReplacements() {
    // Extra answers are ignored.
    var parsed = parser.parse("/give <a:Player> <a:Amount>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("Steve", "64", "extra"));
    assertEquals("/give Steve 64 ", partial);
    assertFalse(partial.contains("extra"));
  }

  @Test
  void compoundTagReplacedSubtagBySubtag() {
    // Compound tag answers slot into corresponding spaces.
    var parsed = parser.parse("/set <d:choice[set,add]:Op && d:num[0,24]:Value>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("set", "5"));
    assertEquals("/set set 5 ", partial);
  }

  @Test
  void compoundTagWithPartialAnswers() {
    var parsed = parser.parse("/set <d:choice[set,add]:Op && d:num[0,24]:Value>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("set"));
    assertEquals("/set set ", partial);
  }

  @Test
  void postCommandMetaIsStripped() {
    var parsed = parser.parse("/ban <a:Why?> <!log to console>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("spamming"));
    assertEquals("/ban spamming ", partial);
    assertFalse(partial.contains("<!"));
    assertFalse(partial.contains("log to console"));
  }

  @Test
  void cancelPcmStripped() {
    var parsed = parser.parse("/ban <a:Why?> <!!notify mods>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("spamming"));
    assertEquals("/ban spamming ", partial);
  }

  @Test
  void pcmAndAnswerLeaveTrailingSpace() {
    // PCM adjacent to trailing space does not affect trailing space.
    var parsed = parser.parse("/ban <a:Why?><!log>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("spamming"));
    assertEquals("/ban spamming ", partial);
  }

  @Test
  void emptyAnswerProducesEmptySlot() {
    // Empty answer collapses slot without literal empty quotes.
    var parsed = parser.parse("/give <a:Player> <a:Amount>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("", "64"));
    assertEquals("/give  64 ", partial);
  }

  @Test
  void sanitizationIsNotApplied() {
    // Partial command builder preserves color codes.
    var parsed = parser.parse("/say <a:Msg>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("&#aa00ffhello"));
    assertEquals("/say &#aa00ffhello ", partial);
  }

  @Test
  void firstUnansweredSingleTagTruncatesTokensAfter() {
    // Discard tokens after unanswered tag to position cursor for completion.
    var parsed = parser.parse("gamemode <d:tab:select> CyR1en");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of());
    assertEquals("gamemode ", partial);
    assertFalse(partial.contains("CyR1en"));
  }

  @Test
  void firstUnansweredSingleTagTruncatesAfterPriorAnswer() {
    // Discard trailing tokens after first unanswered tag.
    var parsed = parser.parse("gamemode <a:Mode> <a:Target> extra");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("survival"));
    assertEquals("gamemode survival ", partial);
    assertFalse(partial.contains("extra"));
  }

  @Test
  void firstUnansweredSingleTagTruncatesAtPositionBeforeReplacement() {
    // Truncation uses modified command with replacements.
    var parsed = parser.parse("/give <a:Player> <a:Amount>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("Steve"));
    assertEquals("/give Steve ", partial);
  }

  @Test
  void nullTemplateReturnsEmpty() {
    // Ensure command without tags remains unmodified.
    var parsed = parser.parse("/no_prompts no_tags_here");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of());
    assertEquals("/no_prompts no_tags_here ", partial);
    assertTrue(partial.length() > 0);
  }

  @Test
  void duplicateRawTagsUseDistinctAnswers() {
    var parsed = parser.parse("/say <a:value> <a:value>");

    assertEquals(
        "/say first second ",
        ParsedCommand.buildPartialCommand(parsed, List.of("first", "second")));
  }

  @Test
  void answerTextIsNotSearchedForAnotherPrompt() {
    var parsed = parser.parse("/say <a:first> <a:second>");

    assertEquals(
        "/say <a:second> literal ",
        ParsedCommand.buildPartialCommand(parsed, List.of("<a:second>", "literal")));
  }

  @Test
  void parsedModelRetainsRawTemplateAndExactSpans() {
    var parsed = parser.parse("/say <a:Why? \\>> <!log>");

    assertEquals("/say <a:Why? \\>> <!log>", parsed.rawTemplateCommand());
    assertEquals(2, parsed.templateSpans().size());
    assertEquals("<a:Why? \\>>", parsed.templateSpans().get(0).rawText());
    assertEquals("<!log>", parsed.templateSpans().get(1).rawText());
  }

  @Test
  void postCommandArraysAreDefensivelyCopiedAndComparedByContent() {
    var source = new int[] {1, 2};
    var first = new PostCommandMeta("log", source, 0, false, DispatchTarget.PASSTHROUGH, false);
    source[0] = 99;
    var returned = first.answerIndices();
    returned[1] = 99;

    var second =
        new PostCommandMeta("log", new int[] {1, 2}, 0, false, DispatchTarget.PASSTHROUGH, false);
    assertArrayEquals(new int[] {1, 2}, first.answerIndices());
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }

  // ====================================================================
  // Arity-aware assembly (Issue #78 / #92)
  // ====================================================================

  @Test
  void zeroCountDropsTagWithoutConsumingAnswer() {
    var parsed = parser.parse("/cmd <@p><a:next>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("c"), List.of(0, 1));
    assertEquals("/cmd c ", partial);
  }

  @Test
  void zeroCountAllowsFollowingPromptToConsumeFirstAnswer() {
    // The preset consumed zero answers, so the flat list index 0 belongs to
    // the next prompt — the preset must not shift it.
    var parsed = parser.parse("/cmd <@p><a:next>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("only"), List.of(0, 1));
    assertEquals("/cmd only ", partial);
  }

  @Test
  void multiCountJoinsAnswersUsingCompoundBehavior() {
    var parsed = parser.parse("/cmd <@p> <a:next>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("a", "b", "c"), List.of(2, 1));
    assertEquals("/cmd a b c ", partial);
  }

  @Test
  void multiCountIgnoresEmptyValuesWhenJoining() {
    var parsed = parser.parse("/cmd <@p> <a:next>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("a", "", "c"), List.of(2, 1));
    assertEquals("/cmd a c ", partial);
  }

  @Test
  void arityAwareAssemblyStopsAtFirstPromptWithoutCount() {
    var parsed = parser.parse("/cmd <@p> <a:next> tail");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of(), List.of(0));
    assertEquals("/cmd ", partial);
    assertFalse(partial.contains("tail"));
  }

  @Test
  void arityAwareAssemblyTruncatesTrailingTokensAtUnansweredPrompt() {
    var parsed = parser.parse("/cmd <@p><a:next> tail");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("x"), List.of(0, 1));
    // The preset is dropped, the next prompt answered, and the rest retained.
    assertEquals("/cmd x tail ", partial);
  }

  @Test
  void arityAwareAssemblyRejectsNegativeCount() {
    var parsed = parser.parse("/cmd <@p>");
    assertThrows(
        IllegalArgumentException.class,
        () -> ParsedCommand.buildPartialCommand(parsed, List.of(), List.of(-1)));
  }

  @Test
  void arityAwareAssemblyRejectsAnswerOverrun() {
    var parsed = parser.parse("/cmd <@p>");
    assertThrows(
        IllegalArgumentException.class,
        () -> ParsedCommand.buildPartialCommand(parsed, List.of("a"), List.of(2)));
  }

  @Test
  void twoArgumentOverloadStillInfersCompoundArity() {
    // Legacy inference: compound tags consume one answer per sub-tag.
    var parsed = parser.parse("/set <d:choice[set,add]:Op && d:num[0,24]:Value>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("set", "5"));
    assertEquals("/set set 5 ", partial);
  }
}
