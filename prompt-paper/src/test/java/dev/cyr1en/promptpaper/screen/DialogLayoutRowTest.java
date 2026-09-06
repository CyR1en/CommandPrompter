package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cyr1en.promptcore.parser.CommandLineParser;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.screen.dialog.DialogInputKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Issue #92: TITLE/BODY rows are layout-only — they never produce answers. {@link
 * ScreenManager#answerBearingTags} must classify only real input rows, and {@link
 * DialogInputKind#isAnswerBearing} must be the single source of truth used for validation pairing
 * and cancel scanning.
 */
class DialogLayoutRowTest extends MockBukkitTest {

  private final CommandLineParser parser = new CommandLineParser();

  @Test
  void layoutKindsAreNotAnswerBearing() {
    assertFalse(DialogInputKind.TITLE.isAnswerBearing());
    assertFalse(DialogInputKind.BODY.isAnswerBearing());
    assertTrue(DialogInputKind.TEXT.isAnswerBearing());
    assertTrue(DialogInputKind.NUMBER.isAnswerBearing());
    assertTrue(DialogInputKind.CHOICE.isAnswerBearing());
    assertTrue(DialogInputKind.TAB.isAnswerBearing());
  }

  @Test
  void answerBearingTagsExcludesTitleAndBodyRows() {
    var parsed =
        parser.parse("/cmd <d:title:Header && d:text:Name && d:body:Note && d:num[0,24]:Days>");
    var tag = parsed.promptTags().get(0);
    assertTrue(tag.isCompound());
    assertEquals(4, tag.subTags().size());

    var answerTags = ScreenManager.answerBearingTags(tag);
    assertEquals(2, answerTags.size(), "TITLE and BODY rows must be filtered out");
    assertEquals("Name", answerTags.get(0).displayText());
    assertEquals(DialogInputKind.TEXT, DialogInputKind.parse(answerTags.get(0).filter()));
    assertEquals("Days", answerTags.get(1).displayText());
    assertEquals(DialogInputKind.NUMBER, DialogInputKind.parse(answerTags.get(1).filter()));
  }

  @Test
  void answerBearingTagsForNonCompoundIsTheTagItself() {
    // Preset references are non-compound; validation pairing falls back to
    // the block-level tag exactly as before.
    var parsed = parser.parse("/cmd <@preset_id>");
    var tag = parsed.promptTags().get(0);
    assertEquals(List.of(tag), ScreenManager.answerBearingTags(tag));

    var single = parser.parse("/cmd <d:text:Name>");
    var singleTag = single.promptTags().get(0);
    assertEquals(List.of(singleTag), ScreenManager.answerBearingTags(singleTag));
  }
}
