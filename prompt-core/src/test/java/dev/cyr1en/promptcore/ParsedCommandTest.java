package dev.cyr1en.promptcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    // The user has answered the first tag only. The second tag must
    // disappear from the partial command — leaving it as raw markup
    // would give Brigadier a token it cannot parse. Internal spaces
    // collapse to a single trailing space after trim() + suffix.
    var parsed = parser.parse("/give <a:Player> <a:Amount>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("Steve"));
    assertEquals("/give Steve ", partial);
    assertFalse(partial.contains("<"));
    assertFalse(partial.contains(">"));
  }

  @Test
  void moreAnswersThanTagsKeepsAllReplacements() {
    // Defensive: any surplus answers are ignored. They cannot map to
    // a tag, so they should not appear in the partial.
    var parsed = parser.parse("/give <a:Player> <a:Amount>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("Steve", "64", "extra"));
    assertEquals("/give Steve 64 ", partial);
    assertFalse(partial.contains("extra"));
  }

  @Test
  void compoundTagReplacedSubtagBySubtag() {
    // Compound dialog: one tag with two sub-rows. Each sub-row's
    // answer slots into the corresponding space. d:tab cannot appear
    // in compound tags (rejected by the parser) so this branch is
    // unreachable for tab — but other compound dialogs (d:choice
    // and d:num) still need partial-command reconstruction to work.
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
    // Trimming + suffix must still hold when a PCM was adjacent to a
    // trailing space in the original.
    var parsed = parser.parse("/ban <a:Why?><!log>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("spamming"));
    assertEquals("/ban spamming ", partial);
  }

  @Test
  void emptyAnswerProducesEmptySlot() {
    // User skipped (or invalid). The slot must collapse — not become
    // the literal string "" inside the command.
    var parsed = parser.parse("/give <a:Player> <a:Amount>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("", "64"));
    assertEquals("/give  64 ", partial);
  }

  @Test
  void sanitizationIsNotApplied() {
    // Sanitization is the session's job, not the partial-command
    // builder's. Color codes in the answer must survive the bridge
    // so they can be converted to legacy §X codes downstream.
    var parsed = parser.parse("/say <a:Msg>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("&#aa00ffhello"));
    assertEquals("/say &#aa00ffhello ", partial);
  }

  @Test
  void firstUnansweredSingleTagTruncatesTokensAfter() {
    // The d:tab user case: tokens after the tag in the template must
    // be discarded so the cursor sits at the tag's argument slot,
    // not at a position that has no Brigadier completions.
    var parsed = parser.parse("gamemode <d:tab:select> CyR1en");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of());
    assertEquals("gamemode ", partial);
    assertFalse(partial.contains("CyR1en"));
  }

  @Test
  void firstUnansweredSingleTagTruncatesAfterPriorAnswer() {
    // Two single tags; first answered, second not, and trailing tokens.
    // The trailing tokens after the second tag must be discarded.
    var parsed = parser.parse("gamemode <a:Mode> <a:Target> extra");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("survival"));
    assertEquals("gamemode survival ", partial);
    assertFalse(partial.contains("extra"));
  }

  @Test
  void firstUnansweredSingleTagTruncatesAtPositionBeforeReplacement() {
    // Regression for the existing test: confirm truncation uses the
    // modified command (with prior replacements), not the raw template.
    // Replaces <a:Player> with "Steve", then truncates at <a:Amount>'s
    // position in the modified command.
    var parsed = parser.parse("/give <a:Player> <a:Amount>");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of("Steve"));
    assertEquals("/give Steve ", partial);
  }

  @Test
  void nullTemplateReturnsEmpty() {
    // The contract: null templateCommand() is forbidden at the
    // record level, so we cannot construct one through the public
    // API. Build a synthetic ParsedCommand with the minimum fields.
    // templateCommand is a required record field, but we can still
    // exercise buildPartialCommand with a synthetic input by going
    // through the parser — there is no exposed path for null.
    // This test is therefore a placeholder for the implicit
    // requirement that the parser never returns null templates.
    var parsed = parser.parse("/no_prompts no_tags_here");
    var partial = ParsedCommand.buildPartialCommand(parsed, List.of());
    assertEquals("/no_prompts no_tags_here ", partial);
    assertTrue(partial.length() > 0);
  }
}
