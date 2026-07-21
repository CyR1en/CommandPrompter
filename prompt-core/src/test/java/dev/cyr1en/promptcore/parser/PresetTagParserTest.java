package dev.cyr1en.promptcore.parser;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.PromptTag;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the {@code <@id>} and {@code <!@id>} preset tag forms (Scope 4). The legacy
 * inline forms and the existing PCM/dialog tests live in {@link CommandLineParserTest}; this class
 * focuses on the new discriminator.
 */
class PresetTagParserTest {

  private final CommandLineParser parser = new CommandLineParser();

  @Test
  void presetPromptTagIsExtracted() {
    var result = parser.parse("/give <@target_player> 64");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertTrue(tag.isPreset(), "tag should be marked as preset");
    assertEquals("target_player", tag.displayText(), "id is stored in displayText");
    assertEquals("", tag.key(), "preset tags have empty key");
    assertEquals("<@target_player>", tag.rawTag());
  }

  @Test
  void presetPromptTagStopsAtSpace() {
    // ID is parsed up to the first space.
    var result = parser.parse("/cmd <@my_prompt> more text");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertTrue(tag.isPreset());
    assertEquals("my_prompt", tag.displayText());
  }

  @Test
  void multiplePresetPromptTags() {
    var result = parser.parse("/cmd <@a> <@b> <@c>");
    assertEquals(3, result.promptTags().size());
    assertTrue(result.promptTags().stream().allMatch(PromptTag::isPreset));
    assertEquals("a", result.promptTags().get(0).displayText());
    assertEquals("b", result.promptTags().get(1).displayText());
    assertEquals("c", result.promptTags().get(2).displayText());
  }

  @Test
  void mixedPresetAndInlinePromptTags() {
    var result = parser.parse("/cmd <@a> <a:why> <@b>");
    assertEquals(3, result.promptTags().size());
    assertTrue(result.promptTags().get(0).isPreset());
    assertFalse(result.promptTags().get(1).isPreset());
    assertTrue(result.promptTags().get(2).isPreset());
  }

  @Test
  void emptyPresetIdFallsThroughToLegacy() {
    // Fallback to legacy chat prompt if ID is empty.
    var result = parser.parse("/cmd <@>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertFalse(tag.isPreset());
    assertEquals("@", tag.displayText());
  }

  @Test
  void presetPromptTagIsNotAPcm() {
    var result = parser.parse("/cmd <@my_prompt>");
    assertTrue(result.postCmds().isEmpty());
  }

  @Test
  void presetPostCommandOnComplete() {
    var result = parser.parse("/cmd <!@log_reason>");
    assertEquals(1, result.postCmds().size());
    var pcm = result.postCmds().get(0);
    assertTrue(pcm.isPreset());
    assertEquals("log_reason", pcm.command());
    assertFalse(pcm.onCancel());
    assertEquals(0, pcm.delayTicks());
  }

  @Test
  void presetPostCommandOnCancel() {
    var result = parser.parse("/cmd <!!@refund_fee>");
    assertEquals(1, result.postCmds().size());
    var pcm = result.postCmds().get(0);
    assertTrue(pcm.isPreset());
    assertEquals("refund_fee", pcm.command());
    assertTrue(pcm.onCancel());
  }

  @Test
  void presetPostCommandWithDelay() {
    var result = parser.parse("/cmd <!:20@delayed_thing>");
    assertEquals(1, result.postCmds().size());
    var pcm = result.postCmds().get(0);
    assertTrue(pcm.isPreset());
    assertEquals("delayed_thing", pcm.command());
    assertEquals(20, pcm.delayTicks());
    assertFalse(pcm.onCancel());
  }

  @Test
  void presetPostCommandOnCancelWithDelay() {
    var result = parser.parse("/cmd <!!:50@warn_admins>");
    assertEquals(1, result.postCmds().size());
    var pcm = result.postCmds().get(0);
    assertTrue(pcm.isPreset());
    assertEquals("warn_admins", pcm.command());
    assertTrue(pcm.onCancel());
    assertEquals(50, pcm.delayTicks());
  }

  @Test
  void legacyPostCommandIsNotPreset() {
    // Plain PCMs remain non-preset.
    var result = parser.parse("/cmd <!log {0}>");
    assertEquals(1, result.postCmds().size());
    var pcm = result.postCmds().get(0);
    assertFalse(pcm.isPreset());
    assertEquals("log {0}", pcm.command());
  }

  @Test
  void mixedPresetPromptAndPresetPostCommand() {
    var result = parser.parse("/cmd <@my_prompt> <!@log_thing>");
    assertEquals(1, result.promptTags().size());
    assertTrue(result.promptTags().get(0).isPreset());
    assertEquals(1, result.postCmds().size());
    assertTrue(result.postCmds().get(0).isPreset());
  }

  @Test
  void hasTagFormDetectsPromptTag() {
    assertTrue(parser.hasTagForm("/cmd <a:why>"));
  }

  @Test
  void hasTagFormDetectsPresetTag() {
    assertTrue(parser.hasTagForm("/cmd <@my_prompt>"));
  }

  @Test
  void hasTagFormDetectsPcm() {
    assertTrue(parser.hasTagForm("/cmd <!log>"));
  }

  @Test
  void hasTagFormDetectsPresetPcm() {
    assertTrue(parser.hasTagForm("/cmd <!@log_thing>"));
  }

  @Test
  void hasTagFormFalseForPlainCommand() {
    assertFalse(parser.hasTagForm("/cmd no tags here"));
  }

  @Test
  void hasTagFormFalseForNullOrBlank() {
    assertFalse(parser.hasTagForm(null));
    assertFalse(parser.hasTagForm(""));
    assertFalse(parser.hasTagForm("   "));
  }

  @Test
  void presetPromptTagRawTagPreserved() {
    // rawTag must include the @ symbol.
    var result = parser.parse("/cmd <@my_prompt>");
    assertEquals("<@my_prompt>", result.promptTags().get(0).rawTag());
  }
}
