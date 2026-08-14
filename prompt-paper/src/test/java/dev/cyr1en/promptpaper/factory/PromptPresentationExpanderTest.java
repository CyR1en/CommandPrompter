package dev.cyr1en.promptpaper.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.gson.JsonPrimitive;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.preset.ActionButtonConfig;
import dev.cyr1en.promptpaper.preset.AnvilButton;
import dev.cyr1en.promptpaper.preset.AnvilPrompt;
import dev.cyr1en.promptpaper.preset.CancelBehavior;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.DialogBaseConfig;
import dev.cyr1en.promptpaper.preset.DialogBodyConfig;
import dev.cyr1en.promptpaper.preset.DialogBodyType;
import dev.cyr1en.promptpaper.preset.DialogPrompt;
import dev.cyr1en.promptpaper.preset.DialogRow;
import dev.cyr1en.promptpaper.preset.DialogType;
import dev.cyr1en.promptpaper.preset.DialogTypeConfig;
import dev.cyr1en.promptpaper.preset.InputType;
import dev.cyr1en.promptpaper.preset.PlayerUiPrompt;
import dev.cyr1en.promptpaper.preset.PromptDefinition;
import dev.cyr1en.promptpaper.preset.SignPrompt;
import dev.cyr1en.promptpaper.preset.UIButton;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Direct unit coverage for {@link PromptPresentationExpander} — the single
 * presentation-materialization boundary. Uses a deterministic non-idempotent expander
 * ({@code value -> "[" + value + "]"}) so every assertion proves a field was expanded
 * exactly once, and semantic / parser fields can be asserted byte-for-byte unchanged.
 *
 * <p>No MockBukkit inventories are involved: the expander never touches Bukkit APIs, so a
 * bare mocked {@link Player} suffices.
 */
class PromptPresentationExpanderTest {

  private Player player;
  private PromptPresentationExpander expander;

  @BeforeEach
  void setUp() {
    player = mock(Player.class);
    expander = new PromptPresentationExpander((p, value) -> "[" + value + "]");
  }

  private PromptDefinition expand(PromptDefinition raw) {
    return expander.expand(player, raw);
  }

  // ------------------------------------------------------------------
  // Chat
  // ------------------------------------------------------------------

  @Test
  void chatExpandsPresentationFieldsExactlyOnce() {
    var raw = new ChatPrompt(
        "chat", "chat_id", "prompt %a%",
        new CancelBehavior(true, "cancel %b%", false, "hover %c%"),
        true,
        new TitleConfig("main %d%", "sub %e%", 40));

    var out = (ChatPrompt) expand(raw);

    assertEquals("[prompt %a%]", out.promptText());
    assertEquals("[cancel %b%]", out.cancel().message());
    assertEquals("[hover %c%]", out.cancel().hoverMessage());
    assertEquals("[main %d%]", out.titleDisplay().main());
    assertEquals("[sub %e%]", out.titleDisplay().sub());
    // Semantic fields byte-for-byte unchanged.
    assertEquals("chat", out.type());
    assertEquals("chat_id", out.id());
    assertTrue(out.sanitize());
    assertEquals(true, out.cancel().send());
    assertEquals(false, out.cancel().clickable());
    assertEquals(40, out.titleDisplay().ticks());
  }

  @Test
  void chatNullTitleDisplayAndEmptyCancelStringsStayRaw() {
    var raw = new ChatPrompt(
        "chat", "chat_id", "prompt %a%",
        new CancelBehavior(false, "", false, ""),
        false);

    var out = (ChatPrompt) expand(raw);

    assertEquals("[prompt %a%]", out.promptText());
    assertEquals("", out.cancel().message());
    assertEquals("", out.cancel().hoverMessage());
    assertNull(out.titleDisplay());
    assertTrue(!out.sanitize());
  }

  // ------------------------------------------------------------------
  // Anvil
  // ------------------------------------------------------------------

  @Test
  void anvilExpandsPresentationFieldsExactlyOnce() {
    var raw = new AnvilPrompt(
        "anvil", "anvil_id", "anvil %a%", "prompt %b%",
        new AnvilButton(true, "left %c%", "STONE", "left hover %d%", 7),
        new AnvilButton(true, "right %e%", "minecraft:DIAMOND", "right hover %f%", 9),
        false,
        new TitleConfig("main %g%", null, 50));

    var out = (AnvilPrompt) expand(raw);

    assertEquals("[anvil %a%]", out.title());
    assertEquals("[prompt %b%]", out.promptText());
    assertEquals("[left %c%]", out.leftButton().buttonText());
    assertEquals("[left hover %d%]", out.leftButton().buttonHoverText());
    assertEquals("[right %e%]", out.rightButton().buttonText());
    assertEquals("[right hover %f%]", out.rightButton().buttonHoverText());
    assertEquals("[main %g%]", out.titleDisplay().main());
    assertNull(out.titleDisplay().sub());
    // Semantic fields byte-for-byte unchanged.
    assertEquals("anvil", out.type());
    assertEquals("anvil_id", out.id());
    assertTrue(!out.sanitize());
    assertEquals("STONE", out.leftButton().buttonIcon());
    assertEquals("minecraft:DIAMOND", out.rightButton().buttonIcon());
    assertEquals(true, out.leftButton().show());
    assertEquals(7, out.leftButton().customModelData());
    assertEquals(9, out.rightButton().customModelData());
    assertEquals(50, out.titleDisplay().ticks());
  }

  // ------------------------------------------------------------------
  // Sign
  // ------------------------------------------------------------------

  @Test
  void signExpandsPromptTextAndEveryDefaultLine() {
    var raw = new SignPrompt(
        "sign", "sign_id", "prompt %a%",
        List.of("line one %b%", "line two %c%"),
        true,
        new TitleConfig("", "sub %d%", null));

    var out = (SignPrompt) expand(raw);

    assertEquals("[prompt %a%]", out.promptText());
    assertEquals(List.of("[line one %b%]", "[line two %c%]"), out.defaultLines());
    // Empty title main is preserved for the factory fallback; sub is expanded once.
    assertEquals("", out.titleDisplay().main());
    assertEquals("[sub %d%]", out.titleDisplay().sub());
    assertNull(out.titleDisplay().ticks());
    // Semantic fields byte-for-byte unchanged.
    assertEquals("sign", out.type());
    assertEquals("sign_id", out.id());
    assertTrue(out.sanitize());
  }

  @Test
  void signEmptyDefaultLinesStayEmpty() {
    var raw = new SignPrompt("sign", "sign_id", "prompt %a%", List.of(), true);
    var out = (SignPrompt) expand(raw);
    assertEquals("[prompt %a%]", out.promptText());
    assertTrue(out.defaultLines().isEmpty());
  }

  // ------------------------------------------------------------------
  // Player UI
  // ------------------------------------------------------------------

  @Test
  void playerUiExpandsPromptTextAndButtonsButNeverFilterOrIcons() {
    var raw = new PlayerUiPrompt(
        "player_ui", "pui_id", "prompt %a%", "world %filter%",
        new UIButton(true, 3, "cancel %b%", "BARRIER", "cancel hover %c%", 5),
        new UIButton(true, 4, "prev %d%", "ARROW", "prev hover %e%", 6),
        null,
        false,
        new TitleConfig("main %f%", null, 20));

    var out = (PlayerUiPrompt) expand(raw);

    assertEquals("[prompt %a%]", out.promptText());
    assertEquals("[cancel %b%]", out.cancelButton().buttonText());
    assertEquals("[cancel hover %c%]", out.cancelButton().buttonHoverText());
    assertEquals("[prev %d%]", out.previousButton().buttonText());
    assertEquals("[prev hover %e%]", out.previousButton().buttonHoverText());
    assertEquals("[main %f%]", out.titleDisplay().main());
    // Semantic fields byte-for-byte unchanged.
    assertEquals("world %filter%", out.filter());
    assertEquals("BARRIER", out.cancelButton().buttonIcon());
    assertEquals(3, out.cancelButton().slot());
    assertEquals(5, out.cancelButton().customModelData());
    assertEquals(true, out.cancelButton().show());
    assertEquals("ARROW", out.previousButton().buttonIcon());
    assertEquals(4, out.previousButton().slot());
    assertEquals(6, out.previousButton().customModelData());
    // Null buttons stay null.
    assertNull(out.nextButton());
    assertTrue(!out.sanitize());
    assertEquals(20, out.titleDisplay().ticks());
  }

  // ------------------------------------------------------------------
  // Dialog (JSON preset model)
  // ------------------------------------------------------------------

  @Test
  void dialogExpandsPresentationFieldsButNeverConstraintsOrReturnValues() {
    var body = new DialogBodyConfig(DialogBodyType.PLAIN_MESSAGE, "body %a%", null, 1);
    var item = new DialogBodyConfig(DialogBodyType.ITEM, null, "STONE", 3, 64);
    var textRow = new DialogRow("label %b%", InputType.TEXT, null);
    var numRow = new DialogRow(
        "num %c%", InputType.NUMBER,
        List.of(new JsonPrimitive(1), new JsonPrimitive(10)), 50, 2, 300);
    var base = new DialogBaseConfig(List.of(body, item), List.of(textRow, numRow));

    var action = new ActionButtonConfig("act %d%", "act tip %e%", "ret %f%");
    var exit = new ActionButtonConfig("exit %g%", null, "exit ret %h%");
    var confirm = new ActionButtonConfig("confirm %i%", "confirm tip %j%", null);
    var cancel = new ActionButtonConfig("cancel %k%", null, null);
    var dt = new DialogTypeConfig(DialogType.MULTI_ACTION, 3, List.of(action), null,
        exit, confirm, cancel);

    var raw = new DialogPrompt(
        "dialog", "dialog_id", "title %z%", base, dt, true,
        new TitleConfig("t %l%", null, 70));

    var out = (DialogPrompt) expand(raw);

    assertEquals("[title %z%]", out.title());
    assertEquals("[body %a%]", out.base().body().get(0).content());
    assertNull(out.base().body().get(1).content());
    assertEquals("STONE", out.base().body().get(1).material());
    assertEquals(3, out.base().body().get(1).amount());
    assertEquals(64, out.base().body().get(1).width());
    assertEquals("[label %b%]", out.base().inputs().get(0).label());
    assertEquals("[num %c%]", out.base().inputs().get(1).label());
    assertEquals("[act %d%]", out.dialogType().actions().get(0).label());
    assertEquals("[act tip %e%]", out.dialogType().actions().get(0).tooltip());
    assertEquals("[exit %g%]", out.dialogType().exitAction().label());
    assertNull(out.dialogType().exitAction().tooltip());
    assertEquals("[confirm %i%]", out.dialogType().confirmAction().label());
    assertEquals("[confirm tip %j%]", out.dialogType().confirmAction().tooltip());
    assertEquals("[cancel %k%]", out.dialogType().cancelAction().label());
    assertNull(out.dialogType().cancelAction().tooltip());
    assertEquals("[t %l%]", out.titleDisplay().main());
    // Semantic / parser fields byte-for-byte unchanged.
    assertEquals("dialog", out.type());
    assertEquals("dialog_id", out.id());
    assertTrue(out.sanitize());
    // Dialog constraints / numeric limits / dimensions.
    assertEquals("1", out.base().inputs().get(1).constraintsAsStrings().get(0));
    assertEquals("10", out.base().inputs().get(1).constraintsAsStrings().get(1));
    assertEquals(50, out.base().inputs().get(1).maxLength());
    assertEquals(2, out.base().inputs().get(1).maxLines());
    assertEquals(300, out.base().inputs().get(1).width());
    assertEquals(InputType.NUMBER, out.base().inputs().get(1).inputType());
    // Action return values.
    assertEquals("ret %f%", out.dialogType().actions().get(0).returnValue());
    assertEquals("exit ret %h%", out.dialogType().exitAction().returnValue());
    assertNull(out.dialogType().confirmAction().returnValue());
    assertNull(out.dialogType().cancelAction().returnValue());
    // Dialog-type layout semantics.
    assertEquals(DialogType.MULTI_ACTION, out.dialogType().type());
    assertEquals(3, out.dialogType().columns());
    assertNull(out.dialogType().actionsSource());
    assertEquals(70, out.titleDisplay().ticks());
  }

  @Test
  void dialogPreservesNullBaseAndNullActions() {
    var dt = new DialogTypeConfig(DialogType.CONFIRMATION, null, null, null, null,
        new ActionButtonConfig("confirm %a%", null, null), null);
    var raw = new DialogPrompt("dialog", "dialog_id", "title %b%", null, dt, true);

    var out = (DialogPrompt) expand(raw);

    assertNull(out.base());
    assertEquals("[title %b%]", out.title());
    assertTrue(out.dialogType().actions().isEmpty());
    assertEquals("[confirm %a%]", out.dialogType().confirmAction().label());
    assertNull(out.dialogType().cancelAction());
  }

  // ------------------------------------------------------------------
  // Inline (compound) dialog tags
  // ------------------------------------------------------------------

  @Test
  void inlineDialogExpandsEverySubTagDisplayTextAndTitle() {
    var titleRow = new PromptTag("<d:title:Header %a%>", "d", "title", "Header %a%");
    var bodyRow = new PromptTag("<d:body:Note %b%>", "d", "body", "Note %b%");
    var textRow = new PromptTag("<d:text:Enter %c%>", "d", "text", "Enter %c%");
    var numRow = new PromptTag("<d:num[0,24]:Value %d%>", "d", "num[0,24]", "Value %d%");
    var choiceRow = new PromptTag("<d:choice[x,y]:Pick %e%>", "d", "choice[x,y]", "Pick %e%");
    var block = new PromptTag(
        "<d:title:Header %a% && d:text:Enter %c%>",
        "d",
        null,
        "",
        true,
        "req",
        PromptTag.AnswerType.INTEGER,
        List.of(titleRow, bodyRow, textRow, numRow, choiceRow),
        false,
        new TitleConfig("Main %f%", "Sub %g%", 60));

    var out = expander.expandInlineDialog(player, block);

    assertNotSame(block, out);
    assertEquals("[Header %a%]", out.subTags().get(0).displayText());
    assertEquals("[Note %b%]", out.subTags().get(1).displayText());
    assertEquals("[Enter %c%]", out.subTags().get(2).displayText());
    assertEquals("[Value %d%]", out.subTags().get(3).displayText());
    assertEquals("[Pick %e%]", out.subTags().get(4).displayText());
    assertEquals("[Main %f%]", out.title().main());
    assertEquals("[Sub %g%]", out.title().sub());
    assertEquals(60, out.title().ticks());
    // Compound block displayText is empty and stays empty (never mangled by expansion).
    assertEquals("", out.displayText());
    // Parser / semantic components byte-for-byte unchanged.
    assertEquals("<d:title:Header %a% && d:text:Enter %c%>", out.rawTag());
    assertEquals("d", out.key());
    assertNull(out.filter());
    assertEquals(true, out.sanitize());
    assertEquals("req", out.validatorAlias());
    assertEquals(PromptTag.AnswerType.INTEGER, out.type());
    assertTrue(!out.preset());
    // Sub-tag parser components unchanged.
    assertEquals("title", out.subTags().get(0).filter());
    assertEquals("num[0,24]", out.subTags().get(3).filter());
    assertEquals("choice[x,y]", out.subTags().get(4).filter());
  }

  @Test
  void inlineDialogWithoutTitleConfigKeepsNullTitle() {
    var tag = new PromptTag("<d:text:Enter %a%>", "d", "text", "Enter %a%");
    var out = expander.expandInlineDialog(player, tag);
    assertEquals("[Enter %a%]", out.displayText());
    assertNull(out.title());
    assertEquals("<d:text:Enter %a%>", out.rawTag());
  }

  @Test
  void inlineDialogEmptyMainIsPreservedForFactoryFallback() {
    var tag = new PromptTag("<d:text:Enter %a%>", "d", "text", "Enter %a%", true, null,
        PromptTag.AnswerType.NONE, List.of(), false, new TitleConfig("", "sub %b%", 30));
    var out = expander.expandInlineDialog(player, tag);
    assertEquals("[Enter %a%]", out.displayText());
    assertEquals("", out.title().main());
    assertEquals("[sub %b%]", out.title().sub());
    assertEquals(30, out.title().ticks());
  }
}
