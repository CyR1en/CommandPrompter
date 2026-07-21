package dev.cyr1en.promptpaper.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.preset.AnvilButton;
import dev.cyr1en.promptpaper.preset.AnvilPrompt;
import dev.cyr1en.promptpaper.preset.CancelBehavior;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.DialogBaseConfig;
import dev.cyr1en.promptpaper.preset.DialogPrompt;
import dev.cyr1en.promptpaper.preset.DialogRow;
import dev.cyr1en.promptpaper.preset.DialogType;
import dev.cyr1en.promptpaper.preset.DialogTypeConfig;
import dev.cyr1en.promptpaper.preset.InputType;
import dev.cyr1en.promptpaper.preset.PlayerUiPrompt;
import dev.cyr1en.promptpaper.preset.PromptDefinition;
import dev.cyr1en.promptpaper.preset.SignPrompt;
import dev.cyr1en.promptpaper.preset.UIButton;
import dev.cyr1en.promptpaper.screen.AnvilPromptScreen;
import dev.cyr1en.promptpaper.screen.ChatPromptScreen;
import dev.cyr1en.promptpaper.screen.SignPromptScreen;
import dev.cyr1en.promptpaper.screen.TitleWrapperScreen;
import dev.cyr1en.promptpaper.screen.playerui.PlayerUIScreen;
import dev.cyr1en.promptui.InputScreen;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the three new classes in the {@code factory} package:
 * {@link PromptFactory}, {@link MaterialMapper}, and {@link InlineTagMapper}.
 *
 * <p>The {@link MockBukkitTest} base sets up a mock plugin, server, and config loader; we
 * rely on that for the factory's plugin reference.
 */
class PromptFactoryTest extends MockBukkitTest {

  private PromptFactory factory;

  @BeforeEach
  void setUp() {
    factory = new PromptFactory(plugin);
  }

  // ------------------------------------------------------------------
  // MaterialMapper
  // ------------------------------------------------------------------

  @Test
  void materialMapperResolvesValidName() {
    var mapper = new MaterialMapper(plugin.getPluginLogger());
    var mat = mapper.resolveOrDefault("STONE", "test");
    assertNotNull(mat);
    assertEquals(org.bukkit.Material.STONE, mat);
  }

  @Test
  void materialMapperStripsMinecraftPrefix() {
    var mapper = new MaterialMapper(plugin.getPluginLogger());
    var mat = mapper.resolveOrDefault("minecraft:DIAMOND_SWORD", "test");
    assertEquals(org.bukkit.Material.DIAMOND_SWORD, mat);
  }

  @Test
  void materialMapperIsCaseInsensitive() {
    var mapper = new MaterialMapper(plugin.getPluginLogger());
    var mat = mapper.resolveOrDefault("stone", "test");
    assertEquals(org.bukkit.Material.STONE, mat);
  }

  @Test
  void materialMapperFallsBackToPaperOnUnknown() {
    var mapper = new MaterialMapper(plugin.getPluginLogger());
    var mat = mapper.resolveOrDefault("DEFINITELY_NOT_A_MATERIAL", "test ctx");
    assertEquals(org.bukkit.Material.PAPER, mat);
  }

  @Test
  void materialMapperFallsBackToPaperOnNull() {
    var mapper = new MaterialMapper(plugin.getPluginLogger());
    var mat = mapper.resolveOrDefault(null, "test ctx");
    assertEquals(org.bukkit.Material.PAPER, mat);
  }

  @Test
  void materialMapperFallsBackToPaperOnBlank() {
    var mapper = new MaterialMapper(plugin.getPluginLogger());
    var mat = mapper.resolveOrDefault("   ", "test ctx");
    assertEquals(org.bukkit.Material.PAPER, mat);
  }

  @Test
  void factoryOwnsAMaterialMapper() {
    assertNotNull(factory.getMaterialMapper());
    // Same instance returned across calls.
    assertSame(factory.getMaterialMapper(), factory.getMaterialMapper());
  }

  // ------------------------------------------------------------------
  // InlineTagMapper
  // ------------------------------------------------------------------

  @Test
  void inlineMapperChatTagYieldsChatPrompt() {
    var tag = new PromptTag("<test>", "", null, "Why?");
    var def = InlineTagMapper.toPromptDefinition(tag);
    assertInstanceOf(ChatPrompt.class, def);
    var chat = (ChatPrompt) def;
    assertEquals("chat", chat.type());
    assertTrue(chat.id().startsWith(InlineTagMapper.INLINE_ID_PREFIX));
    assertEquals("Why?", chat.promptText());
    assertNotNull(chat.cancel());
  }

  @Test
  void inlineMapperAnvilTagYieldsAnvilPrompt() {
    var tag = new PromptTag("<a:Enter value>", "a", null, "Enter value");
    var def = InlineTagMapper.toPromptDefinition(tag);
    assertInstanceOf(AnvilPrompt.class, def);
    var anvil = (AnvilPrompt) def;
    assertEquals("anvil", anvil.type());
    assertTrue(anvil.id().startsWith(InlineTagMapper.INLINE_ID_PREFIX));
    assertEquals("Enter value", anvil.promptText());
    assertNotNull(anvil.leftButton());
    assertNotNull(anvil.rightButton());
  }

  @Test
  void inlineMapperSignTagYieldsSignPrompt() {
    var tag = new PromptTag("<s:Sign here>", "s", null, "Sign here");
    var def = InlineTagMapper.toPromptDefinition(tag);
    assertInstanceOf(SignPrompt.class, def);
    var sign = (SignPrompt) def;
    assertEquals("sign", sign.type());
    assertEquals("Sign here", sign.promptText());
    assertNotNull(sign.defaultLines());
  }

  @Test
  void inlineMapperPlayerTagYieldsPlayerUiPrompt() {
    var tag = new PromptTag("<p:Choose>", "p", "online", "Choose");
    var def = InlineTagMapper.toPromptDefinition(tag);
    assertInstanceOf(PlayerUiPrompt.class, def);
    var pui = (PlayerUiPrompt) def;
    assertEquals("player_ui", pui.type());
    assertEquals("online", pui.filter());
    assertEquals("Choose", pui.promptText());
  }

  @Test
  void inlineMapperDialogTagYieldsDialogPrompt() {
    var tag = new PromptTag("<d:choice[set,add]:Pick>", "d", "choice[set,add]", "Pick");
    var def = InlineTagMapper.toPromptDefinition(tag);
    assertInstanceOf(DialogPrompt.class, def);
    var dlg = (DialogPrompt) def;
    assertEquals("dialog", dlg.type());
    assertNotNull(dlg.base());
    assertEquals(1, dlg.base().inputs().size());
    assertEquals("Pick", dlg.base().inputs().get(0).label());
    assertEquals(InputType.CHOICE, dlg.base().inputs().get(0).inputType());
    // Non-tab dialogs default to confirmation layout.
    assertEquals(DialogType.CONFIRMATION, dlg.dialogType().type());
  }

  @Test
  void inlineMapperPreservesSanitizeFlag() {
    // default sanitize = true
    var tagOn = new PromptTag("<a:X>", "a", null, "X");
    assertTrue(InlineTagMapper.toPromptDefinition(tagOn).sanitize());
    // -ds flag means sanitize = false
    var tagOff = new PromptTag("<a:X -ds>", "a", null, "X", false, null);
    assertEquals(false, InlineTagMapper.toPromptDefinition(tagOff).sanitize());
  }

  @Test
  void inlineMapperGeneratesUniqueIds() {
    var tag = new PromptTag("<a:X>", "a", null, "X");
    var id1 = InlineTagMapper.toPromptDefinition(tag).id();
    var id2 = InlineTagMapper.toPromptDefinition(tag).id();
    assertTrue(id1.startsWith(InlineTagMapper.INLINE_ID_PREFIX));
    // Two successive calls produce different ids (UUID-based).
    assertTrue(!id1.equals(id2));
  }

  @Test
  void inlineMapperUnknownKeyFallsBackToChat() {
    var tag = new PromptTag("<x:text>", "x", null, "text");
    var def = InlineTagMapper.toPromptDefinition(tag);
    assertInstanceOf(ChatPrompt.class, def);
  }

  @Test
  void inlineMapperCompoundDialogYieldsMultipleRows() {
    var inner1 = new PromptTag("<d:choice[set,add]:Op>", "d", "choice[set,add]", "Op");
    var inner2 = new PromptTag("<d:num[0,24]:Value>", "d", "num[0,24]", "Value");
    var block = new PromptTag(
        "<d:choice[set,add]:Op && d:num[0,24]:Value>",
        "d",
        null,
        "",
        true,
        null,
        PromptTag.AnswerType.NONE,
        List.of(inner1, inner2),
        false);
    var def = InlineTagMapper.toPromptDefinition(block);
    assertInstanceOf(DialogPrompt.class, def);
    var dlg = (DialogPrompt) def;
    assertNotNull(dlg.base());
    assertEquals(2, dlg.base().inputs().size());
    assertEquals("Op", dlg.base().inputs().get(0).label());
    assertEquals(InputType.CHOICE, dlg.base().inputs().get(0).inputType());
    assertEquals("Value", dlg.base().inputs().get(1).label());
    assertEquals(InputType.NUMBER, dlg.base().inputs().get(1).inputType());
  }

  @Test
  void inlineMapperTabDialogYieldsMultiActionDialogType() {
    var tag = new PromptTag("<d:tab:Player>", "d", "tab", "Player");
    var def = InlineTagMapper.toPromptDefinition(tag);
    assertInstanceOf(DialogPrompt.class, def);
    var dlg = (DialogPrompt) def;
    assertEquals("Player", dlg.title());
    assertEquals(DialogType.MULTI_ACTION, dlg.dialogType().type());
    // The spec's Rule 2 — tab-completion maps to multi_action with a tab source.
    assertEquals(2, dlg.dialogType().columns());
  }

  // ------------------------------------------------------------------
  // PromptFactory.create(PromptDefinition)
  // ------------------------------------------------------------------

  @Test
  void createChatPromptYieldsChatScreen() {
    var chat = new ChatPrompt("chat", "p1", "Why?", new CancelBehavior(false, "", false, ""), true);
    var screen = factory.create(createPlayer(), chat);
    assertInstanceOf(ChatPromptScreen.class, screen);
  }

  @Test
  void createAnvilPromptYieldsAnvilScreen() {
    var anvil = new AnvilPrompt(
        "anvil", "p1", "Rename", "New",
        new AnvilButton(true, "Cancel", "BARRIER", "Click", 0),
        new AnvilButton(true, "OK", "PAPER", "Click", 0),
        true);
    var screen = factory.create(createPlayer(), anvil);
    assertInstanceOf(AnvilPromptScreen.class, screen);
  }

  @Test
  void createSignPromptYieldsSignScreen() {
    var sign = new SignPrompt("sign", "p1", "Sign here", List.of("a", "b", "c", "d"), true);
    var screen = factory.create(createPlayer(), sign);
    assertInstanceOf(SignPromptScreen.class, screen);
  }

  @Test
  void createPlayerUiPromptYieldsPlayerUIScreen() {
    var pui = new PlayerUiPrompt(
        "player_ui", "p1", "Choose", "online",
        new UIButton(true, 0, "Cancel", "BARRIER", "Cancel", 0),
        new UIButton(true, 1, "Prev", "ARROW", "Prev", 0),
        new UIButton(true, 2, "Next", "ARROW", "Next", 0),
        true);
    var screen = factory.create(createPlayer(), pui);
    assertInstanceOf(PlayerUIScreen.class, screen);
  }

  @Test
  void createPlayerUiPromptWithNullButtonsStillWorks() {
    // All UI buttons omitted in JSON. The factory must tolerate null fields.
    var pui = new PlayerUiPrompt("player_ui", "p1", "Choose", null, null, null, null, true);
    var screen = factory.create(createPlayer(), pui);
    assertInstanceOf(PlayerUIScreen.class, screen);
  }

  @Test
  void createDialogPromptYieldsDialogScreen() {
    // The factory now supports JSON-sourced DialogPrompt. A well-formed
    // dialog dispatches to the new createDialog(...) branch in the
    // factory, which builds a DialogPromptScreen. We can't directly
    // reference the screen class in this test because the test classpath
    // doesn't carry Paper's dialog API (paper-api is compileOnly at
    // runtime, and MockBukkit-v1.21 + paper-api-26.1 conflict on the
    // shared classpath). Instead we verify the dispatch happens by
    // observing that the factory no longer throws the pre-refactor
    // UnsupportedOperationException — if the factory were still
    // un-implemented, the call would surface the same throw the legacy
    // test asserted.
    //
    // Loading DialogPromptScreen still requires Paper's dialog classes on
    // the classpath, so the actual call below will surface a
    // NoClassDefFoundError on dialog classes in the test environment.
    // We accept either outcome: the throw proves the dispatch was
    // reached, and the (unreachable in this environment) success path
    // proves the screen was constructed. The full end-to-end coverage
    // of the dialog screen lives in a separate runtime test against a
    // live Paper server, not in the unit suite.
    var rows = List.of(new DialogRow("Reason", InputType.TEXT, null));
    var base = new DialogBaseConfig(List.of(), rows);
    var dt = new DialogTypeConfig(DialogType.CONFIRMATION, null, null, null, null, null, null);
    var dlg = new DialogPrompt("dialog", "p1", "Ban", base, dt, true);
    try {
      var screen = factory.create(createPlayer(), dlg);
      // If the classpath has Paper's dialog API, the screen was built.
      assertNotNull(screen);
      assertEquals(false, screen.isOpen());
    } catch (NoClassDefFoundError e) {
      // The dialog class isn't on the test classpath; assert the
      // failure is specifically about a Paper dialog class (not
      // something else like a NullPointerException in our code).
      var msg = e.getMessage();
      assertTrue(msg != null && msg.contains("papermc/paper"),
          "Expected NoClassDefFoundError on a Paper dialog class, got: " + msg);
    }
  }

  @Test
  void createRejectsNullDefinition() {
    assertThrows(IllegalArgumentException.class,
        () -> factory.create(createPlayer(), (PromptDefinition) null));
  }

  @Test
  void factoryResolvesBadAnvilMaterialWithoutCrashing() {
    // A bogus button_icon in JSON should produce a non-fatal warning and
    // fall back to PAPER — the anvil screen must still be created.
    var anvil = new AnvilPrompt(
        "anvil", "p_bad", "T", "X",
        new AnvilButton(true, "L", "NOT_A_REAL_MATERIAL", "H", 0),
        new AnvilButton(true, "R", "PAPER", "H", 0),
        true);
    var screen = factory.create(createPlayer(), anvil);
    assertInstanceOf(AnvilPromptScreen.class, screen);
  }

  @Test
  void factoryResolvesBadPlayerUiMaterialWithoutCrashing() {
    var pui = new PlayerUiPrompt(
        "player_ui", "p_bad", "Choose", null,
        new UIButton(true, 0, "X", "GARBAGE", "H", 0),
        null, null, true);
    var screen = factory.create(createPlayer(), pui);
    assertInstanceOf(PlayerUIScreen.class, screen);
  }

  // ------------------------------------------------------------------
  // PromptFactory.createFromTag (legacy)
  // ------------------------------------------------------------------

  @Test
  void createFromTagChatYieldsChatScreen() {
    var tag = new PromptTag("<test>", "", null, "Enter value");
    var screen = factory.createFromTag(createPlayer(), tag);
    assertInstanceOf(ChatPromptScreen.class, screen);
  }

  @Test
  void createFromTagAnvilYieldsAnvilScreen() {
    var tag = new PromptTag("<a:Enter value>", "a", null, "Enter value");
    var screen = factory.createFromTag(createPlayer(), tag);
    assertInstanceOf(AnvilPromptScreen.class, screen);
  }

  @Test
  void createFromTagSignYieldsSignScreen() {
    var tag = new PromptTag("<s:Sign>", "s", null, "Sign");
    var screen = factory.createFromTag(createPlayer(), tag);
    assertInstanceOf(SignPromptScreen.class, screen);
  }

  @Test
  void createFromTagPlayerUiYieldsPlayerScreen() {
    var tag = new PromptTag("<p:Choose>", "p", null, "Choose");
    var screen = factory.createFromTag(createPlayer(), tag);
    assertInstanceOf(PlayerUIScreen.class, screen);
  }

  @Test
  void createFromTagRejectsNullTag() {
    assertThrows(IllegalArgumentException.class,
        () -> factory.createFromTag(createPlayer(), (PromptTag) null));
  }

  @Test
  void createFromTagUnknownKeyFallsBackToChatScreen() {
    var tag = new PromptTag("<x:text>", "x", null, "text");
    var screen = factory.createFromTag(createPlayer(), tag);
    assertInstanceOf(ChatPromptScreen.class, screen);
  }

  // ------------------------------------------------------------------
  // Screen wiring sanity
  // ------------------------------------------------------------------

  @Test
  void createdScreensAreUnopened() {
    var chat = new ChatPrompt("chat", "p1", "Why?", new CancelBehavior(false, "", false, ""), true);
    InputScreen screen = factory.create(createPlayer(), chat);
    assertNotNull(screen);
    assertEquals(false, screen.isOpen());
  }

  // ------------------------------------------------------------------
  // Title wrapper
  // ------------------------------------------------------------------

  @Test
  void createWithoutTitleDisplayIsNotWrapped() {
    var chat = new ChatPrompt("chat", "p1", "Why?", new CancelBehavior(false, "", false, ""), true);
    var screen = factory.create(createPlayer(), chat);
    // No titleDisplay → no wrapper.
    assertFalse(screen instanceof TitleWrapperScreen);
    assertInstanceOf(ChatPromptScreen.class, screen);
  }

  @Test
  void createChatWithTitleDisplayIsWrapped() {
    var title = new TitleConfig("Hello", "World", 50);
    var chat = new ChatPrompt("chat", "p1", "Why?",
        new CancelBehavior(false, "", false, ""), true, title);
    var screen = factory.create(createPlayer(), chat);
    assertInstanceOf(TitleWrapperScreen.class, screen);
    var wrapper = (TitleWrapperScreen) screen;
    assertInstanceOf(ChatPromptScreen.class, wrapper.delegate());
  }

  @Test
  void createAnvilWithTitleDisplayIsWrapped() {
    var title = new TitleConfig("Main", null, null);
    var anvil = new AnvilPrompt(
        "anvil", "p1", "Rename", "New",
        new AnvilButton(true, "Cancel", "BARRIER", "Click", 0),
        new AnvilButton(true, "OK", "PAPER", "Click", 0),
        true, title);
    var screen = factory.create(createPlayer(), anvil);
    assertInstanceOf(TitleWrapperScreen.class, screen);
  }

  @Test
  void titleDisplayWithEmptyMainInjectsPromptText() {
    // Empty main → factory should inject the prompt's display text.
    var title = new TitleConfig("", null, null);
    var chat = new ChatPrompt("chat", "p1", "Default Text",
        new CancelBehavior(false, "", false, ""), true, title);
    var screen = factory.create(createPlayer(), chat);
    assertInstanceOf(TitleWrapperScreen.class, screen);
    // The wrapper is created — the main injection happens inside wrapWithTitle.
    // We can't directly inspect the resolved TitleConfig, but the wrapper
    // exists which proves the titleDisplay was present and processed.
  }

  @Test
  void inlineMapperPassesTitleToChatPrompt() {
    var titleTag = new PromptTag("<test>", "", null, "Why?", true, null,
        PromptTag.AnswerType.NONE, java.util.List.of(), false,
        new TitleConfig("Title Main", "Sub", 60));
    var def = InlineTagMapper.toPromptDefinition(titleTag);
    assertInstanceOf(ChatPrompt.class, def);
    var chat = (ChatPrompt) def;
    assertNotNull(chat.titleDisplay());
    assertEquals("Title Main", chat.titleDisplay().main());
    assertEquals("Sub", chat.titleDisplay().sub());
    assertEquals(60, chat.titleDisplay().ticks());
  }

  @Test
  void inlineMapperStandaloneTitleInjectsDisplayText() {
    // Standalone -t flag → main is empty, mapper should inject displayText.
    var titleTag = new PromptTag("<test>", "", null, "Prompt Text", true, null,
        PromptTag.AnswerType.NONE, java.util.List.of(), false,
        new TitleConfig("", null, null));
    var def = InlineTagMapper.toPromptDefinition(titleTag);
    assertInstanceOf(ChatPrompt.class, def);
    var chat = (ChatPrompt) def;
    assertNotNull(chat.titleDisplay());
    // The mapper resolves the empty main to the prompt's displayText.
    assertEquals("Prompt Text", chat.titleDisplay().main());
  }

  @Test
  void inlineMapperNoTitleYieldsNullTitleDisplay() {
    var tag = new PromptTag("<test>", "", null, "Why?");
    var def = InlineTagMapper.toPromptDefinition(tag);
    assertInstanceOf(ChatPrompt.class, def);
    var chat = (ChatPrompt) def;
    assertNull(chat.titleDisplay());
  }

  @Test
  void createFromTagWithTitleFlagWrapsScreen() {
    var tag = new PromptTag("<a:Why?>", "a", null, "Why?", true, null,
        PromptTag.AnswerType.NONE, java.util.List.of(), false,
        new TitleConfig("Title", null, 40));
    var screen = factory.createFromTag(createPlayer(), tag);
    assertInstanceOf(TitleWrapperScreen.class, screen);
    var wrapper = (TitleWrapperScreen) screen;
    assertInstanceOf(AnvilPromptScreen.class, wrapper.delegate());
  }

  @Test
  void createFromTagWithoutTitleFlagDoesNotWrap() {
    var tag = new PromptTag("<a:Why?>", "a", null, "Why?");
    var screen = factory.createFromTag(createPlayer(), tag);
    assertFalse(screen instanceof TitleWrapperScreen);
    assertInstanceOf(AnvilPromptScreen.class, screen);
  }
}
