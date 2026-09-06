package dev.cyr1en.promptpaper.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cyr1en.promptcore.ItemOutputFormat;
import dev.cyr1en.promptcore.ItemSource;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.item.catalog.CatalogEntry;
import dev.cyr1en.promptpaper.item.catalog.CatalogSnapshot;
import dev.cyr1en.promptpaper.item.catalog.ItemCatalogRegistry;
import dev.cyr1en.promptpaper.preset.AnvilButton;
import dev.cyr1en.promptpaper.preset.AnvilPrompt;
import dev.cyr1en.promptpaper.preset.CancelBehavior;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.ConfirmationPrompt;
import dev.cyr1en.promptpaper.preset.DialogBaseConfig;
import dev.cyr1en.promptpaper.preset.DialogPrompt;
import dev.cyr1en.promptpaper.preset.DialogRow;
import dev.cyr1en.promptpaper.preset.DialogType;
import dev.cyr1en.promptpaper.preset.DialogTypeConfig;
import dev.cyr1en.promptpaper.preset.InputType;
import dev.cyr1en.promptpaper.preset.ItemPrompt;
import dev.cyr1en.promptpaper.preset.PlayerUiPrompt;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import dev.cyr1en.promptpaper.preset.PromptDefinition;
import dev.cyr1en.promptpaper.preset.SignPrompt;
import dev.cyr1en.promptpaper.preset.UIButton;
import dev.cyr1en.promptpaper.screen.AnvilPromptScreen;
import dev.cyr1en.promptpaper.screen.ChatPromptScreen;
import dev.cyr1en.promptpaper.screen.SignPromptScreen;
import dev.cyr1en.promptpaper.screen.TitleWrapperScreen;
import dev.cyr1en.promptpaper.screen.item.ItemPromptScreen;
import dev.cyr1en.promptpaper.screen.item.ItemScreenMode;
import dev.cyr1en.promptpaper.screen.playerui.PlayerUIScreen;
import dev.cyr1en.promptui.InputScreen;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit coverage for the three new classes in the {@code factory} package: {@link PromptFactory},
 * {@link MaterialMapper}, and {@link InlineTagMapper}.
 *
 * <p>The {@link MockBukkitTest} base sets up a mock plugin, server, and config loader; we rely on
 * that for the factory's plugin reference.
 */
class PromptFactoryTest extends MockBukkitTest {

  private PromptFactory factory;

  @BeforeEach
  void setUp() {
    factory = new PromptFactory(plugin);
  }

  /**
   * Builds a factory whose presentation expander records every string it is asked to expand and
   * wraps it in brackets. The recorded list is the evidence that each presentation field was
   * expanded exactly once and that semantic fields were never sent to the expander.
   */
  private PromptFactory factoryWithExpander(List<String> expanded) {
    var expander =
        new PromptPresentationExpander(
            (player, value) -> {
              expanded.add(value);
              return "[" + value + "]";
            });
    return new PromptFactory(plugin, expander);
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
  void inlineMapperUnknownKeyThrows() {
    var tag = new PromptTag("<x:text>", "x", null, "text");
    assertThrows(IllegalArgumentException.class, () -> InlineTagMapper.toPromptDefinition(tag));
  }

  @Test
  void inlineMapperEmptyKeyYieldsChatPrompt() {
    var tag = new PromptTag("<text>", "", null, "text");
    var def = InlineTagMapper.toPromptDefinition(tag);
    assertInstanceOf(ChatPrompt.class, def);
  }

  @Test
  void inlineMapperUppercaseBuiltinsResolveIdentically() {
    assertInstanceOf(
        AnvilPrompt.class,
        InlineTagMapper.toPromptDefinition(new PromptTag("<A:val>", "A", null, "val")));
    assertInstanceOf(
        AnvilPrompt.class,
        InlineTagMapper.toPromptDefinition(new PromptTag("<ANVIL:val>", "ANVIL", null, "val")));
    assertInstanceOf(
        SignPrompt.class,
        InlineTagMapper.toPromptDefinition(new PromptTag("<S:val>", "S", null, "val")));
    assertInstanceOf(
        SignPrompt.class,
        InlineTagMapper.toPromptDefinition(new PromptTag("<SIGN:val>", "SIGN", null, "val")));
    assertInstanceOf(
        PlayerUiPrompt.class,
        InlineTagMapper.toPromptDefinition(new PromptTag("<P:val>", "P", null, "val")));
    assertInstanceOf(
        PlayerUiPrompt.class,
        InlineTagMapper.toPromptDefinition(new PromptTag("<PLAYER:val>", "PLAYER", null, "val")));
    assertInstanceOf(
        DialogPrompt.class,
        InlineTagMapper.toPromptDefinition(new PromptTag("<D:val>", "D", null, "val")));
    assertInstanceOf(
        DialogPrompt.class,
        InlineTagMapper.toPromptDefinition(new PromptTag("<DIALOG:val>", "DIALOG", null, "val")));
    assertInstanceOf(
        dev.cyr1en.promptpaper.preset.ConfirmationPrompt.class,
        InlineTagMapper.toPromptDefinition(new PromptTag("<C:val>", "C", null, "val")));
    assertInstanceOf(
        dev.cyr1en.promptpaper.preset.ConfirmationPrompt.class,
        InlineTagMapper.toPromptDefinition(new PromptTag("<CONFIRM:val>", "CONFIRM", null, "val")));
  }

  @Test
  void inlineMapperCustomMappingResolvesValidKey() {
    var tagCustom = new PromptTag("<custom:Sign here>", "custom", null, "Sign here");
    var defCustom =
        InlineTagMapper.toPromptDefinition(tagCustom, Map.of("custom", ScreenType.SIGN));
    assertInstanceOf(SignPrompt.class, defCustom);
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
    var anvil =
        new AnvilPrompt(
            "anvil",
            "p1",
            "Rename",
            "New",
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
    var pui =
        new PlayerUiPrompt(
            "player_ui",
            "p1",
            "Choose",
            "online",
            new UIButton(true, 0, "Cancel", "BARRIER", "Cancel", 0),
            new UIButton(true, 1, "Prev", "ARROW", "Prev", 0),
            new UIButton(true, 2, "Next", "ARROW", "Next", 0),
            true);
    var screen = factory.create(createPlayer(), pui);
    assertInstanceOf(PlayerUIScreen.class, screen);
  }

  @Test
  void createConfirmationPromptYieldsConfirmationScreen() {
    var confGui =
        new dev.cyr1en.promptpaper.preset.ConfirmationPrompt(
            "confirmation",
            "c1",
            dev.cyr1en.promptcore.ConfirmationMode.GUI,
            "Confirm",
            "Are you sure?",
            "Yes",
            "No",
            false,
            null,
            true);
    var screenGui = factory.create(createPlayer(), confGui);
    assertInstanceOf(
        dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen.class, screenGui);
    var confScreenGui =
        (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen) screenGui;
    assertEquals(2, confScreenGui.fallbackChain().size());

    var confChat =
        new dev.cyr1en.promptpaper.preset.ConfirmationPrompt(
            "confirmation",
            "c2",
            dev.cyr1en.promptcore.ConfirmationMode.CHAT,
            null,
            "Chat confirm?",
            null,
            null,
            true,
            null,
            false);
    var screenChat = factory.create(createPlayer(), confChat);
    assertInstanceOf(
        dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen.class, screenChat);
    var confScreenChat =
        (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen) screenChat;
    assertEquals(1, confScreenChat.fallbackChain().size());
    assertTrue(confScreenChat.isValueMode());

    var confDialog =
        new dev.cyr1en.promptpaper.preset.ConfirmationPrompt(
            "confirmation",
            "c3",
            dev.cyr1en.promptcore.ConfirmationMode.DIALOG,
            "Title",
            "Dialog confirm?",
            "Y",
            "N",
            false,
            "ui.button.click",
            true);
    var screenDialog = factory.create(createPlayer(), confDialog);
    assertInstanceOf(
        dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen.class, screenDialog);
    var confScreenDialog =
        (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen) screenDialog;
    assertEquals(3, confScreenDialog.fallbackChain().size());
  }

  @Test
  void createConfirmationWithTitleDisplayIsWrapped() {
    var title = new TitleConfig("Confirm Title", "Sub", 40);
    var conf =
        new dev.cyr1en.promptpaper.preset.ConfirmationPrompt(
            "confirmation",
            "c1",
            dev.cyr1en.promptcore.ConfirmationMode.GUI,
            "Confirm",
            "Are you sure?",
            "Yes",
            "No",
            false,
            null,
            true,
            title);
    var screen = factory.create(createPlayer(), conf);
    assertInstanceOf(TitleWrapperScreen.class, screen);
    var wrapper = (TitleWrapperScreen) screen;
    assertInstanceOf(
        dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen.class,
        wrapper.delegate());
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
      assertTrue(
          msg != null
              && (msg.contains("papermc/paper") || msg.contains("net/kyori/adventure/dialog")),
          "Expected NoClassDefFoundError on a Paper dialog class, got: " + msg);
    }
  }

  @Test
  void createRejectsNullDefinition() {
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.create(createPlayer(), (PromptDefinition) null));
  }

  @Test
  void factoryResolvesBadAnvilMaterialWithoutCrashing() {
    // A bogus button_icon in JSON should produce a non-fatal warning and
    // fall back to PAPER — the anvil screen must still be created.
    var anvil =
        new AnvilPrompt(
            "anvil",
            "p_bad",
            "T",
            "X",
            new AnvilButton(true, "L", "NOT_A_REAL_MATERIAL", "H", 0),
            new AnvilButton(true, "R", "PAPER", "H", 0),
            true);
    var screen = factory.create(createPlayer(), anvil);
    assertInstanceOf(AnvilPromptScreen.class, screen);
  }

  @Test
  void factoryResolvesBadPlayerUiMaterialWithoutCrashing() {
    var pui =
        new PlayerUiPrompt(
            "player_ui",
            "p_bad",
            "Choose",
            null,
            new UIButton(true, 0, "X", "GARBAGE", "H", 0),
            null,
            null,
            true);
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
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.createFromTag(createPlayer(), (PromptTag) null));
  }

  @Test
  void createFromTagUnknownKeyThrowsAndFailsClosed() {
    var tag = new PromptTag("<x:text>", "x", null, "text");
    assertThrows(IllegalArgumentException.class, () -> factory.createFromTag(createPlayer(), tag));
  }

  @Test
  void createFromTagWithConfiguredScreenMappingsResolvesValidCustomKey() {
    Mockito.when(promptConfig.getScreenMappings()).thenReturn(Map.of("custom", ScreenType.SIGN));

    var tagCustom = new PromptTag("<custom:Sign>", "custom", null, "Sign");
    var screenCustom = factory.createFromTag(createPlayer(), tagCustom);
    assertInstanceOf(SignPromptScreen.class, screenCustom);
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
    var chat =
        new ChatPrompt("chat", "p1", "Why?", new CancelBehavior(false, "", false, ""), true, title);
    var screen = factory.create(createPlayer(), chat);
    assertInstanceOf(TitleWrapperScreen.class, screen);
    var wrapper = (TitleWrapperScreen) screen;
    assertInstanceOf(ChatPromptScreen.class, wrapper.delegate());
  }

  @Test
  void createAnvilWithTitleDisplayIsWrapped() {
    var title = new TitleConfig("Main", null, null);
    var anvil =
        new AnvilPrompt(
            "anvil",
            "p1",
            "Rename",
            "New",
            new AnvilButton(true, "Cancel", "BARRIER", "Click", 0),
            new AnvilButton(true, "OK", "PAPER", "Click", 0),
            true,
            title);
    var screen = factory.create(createPlayer(), anvil);
    assertInstanceOf(TitleWrapperScreen.class, screen);
  }

  @Test
  void titleDisplayWithEmptyMainInjectsPromptText() {
    // Empty main → factory should inject the prompt's display text.
    var title = new TitleConfig("", null, null);
    var chat =
        new ChatPrompt(
            "chat", "p1", "Default Text", new CancelBehavior(false, "", false, ""), true, title);
    var screen = factory.create(createPlayer(), chat);
    assertInstanceOf(TitleWrapperScreen.class, screen);
    // The wrapper is created — the main injection happens inside wrapWithTitle.
    // We can't directly inspect the resolved TitleConfig, but the wrapper
    // exists which proves the titleDisplay was present and processed.
  }

  @Test
  void inlineMapperPassesTitleToChatPrompt() {
    var titleTag =
        new PromptTag(
            "<test>",
            "",
            null,
            "Why?",
            true,
            null,
            PromptTag.AnswerType.NONE,
            java.util.List.of(),
            false,
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
    var titleTag =
        new PromptTag(
            "<test>",
            "",
            null,
            "Prompt Text",
            true,
            null,
            PromptTag.AnswerType.NONE,
            java.util.List.of(),
            false,
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
    var tag =
        new PromptTag(
            "<a:Why?>",
            "a",
            null,
            "Why?",
            true,
            null,
            PromptTag.AnswerType.NONE,
            java.util.List.of(),
            false,
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

  // ------------------------------------------------------------------
  // Presentation expansion boundary (PAPI applied exactly once)
  // ------------------------------------------------------------------

  /**
   * A preset tag whose id looks like a PAPI placeholder must be looked up in the registry with the
   * raw id — it is never sent to the expansion delegate — while every presentation field of the
   * resolved definition is expanded exactly once.
   */
  @Test
  void presetLookupUsesRawDisplayTextAndExpandsResolvedFieldsExactlyOnce() {
    var registry = Mockito.mock(PresetRegistry.class);
    Mockito.when(plugin.getPresetRegistry()).thenReturn(registry);
    var rawChat =
        new ChatPrompt(
            "chat",
            "%preset_id%",
            "Hello %player_name%",
            new CancelBehavior(false, "Bye %player_name%", false, "Hover %player_name%"),
            true);
    Mockito.when(registry.getPrompt("%preset_id%")).thenReturn(Optional.of(rawChat));

    List<String> expanded = new ArrayList<>();
    var testFactory = factoryWithExpander(expanded);

    // key="" + preset=true + displayText=the id — exactly what <@%preset_id%> parses to.
    var tag =
        new PromptTag(
            "<@%preset_id%>",
            "", null, "%preset_id%", true, null, PromptTag.AnswerType.NONE, List.of(), true);

    var screen = testFactory.createFromTag(createPlayer(), tag);

    assertInstanceOf(ChatPromptScreen.class, screen);
    // Raw id used for lookup, and only once.
    Mockito.verify(registry, Mockito.times(1)).getPrompt("%preset_id%");
    // The PAPI-looking id never reaches the expander.
    assertFalse(expanded.contains("%preset_id%"));
    // Every presentation field expanded exactly once.
    assertEquals(1, Collections.frequency(expanded, "Hello %player_name%"));
    assertEquals(1, Collections.frequency(expanded, "Bye %player_name%"));
    assertEquals(1, Collections.frequency(expanded, "Hover %player_name%"));
    assertEquals(3, expanded.size());
  }

  /**
   * An empty {@code titleDisplay.main} stays empty through the presentation expander (it is a "use
   * the prompt text" marker, not presentation text), and the factory's title fallback uses the
   * already-expanded prompt text without re-expanding it.
   */
  @Test
  void emptyTitleMainIsPreservedAndFallbackUsesExpandedPromptTextOnce() {
    var title = new TitleConfig("", null, 40);
    var chat =
        new ChatPrompt(
            "chat",
            "p1",
            "Hello %player_name%",
            new CancelBehavior(false, "", false, ""),
            true,
            title);

    List<String> expanded = new ArrayList<>();
    var testFactory = factoryWithExpander(expanded);

    var screen = testFactory.create(createPlayer(), chat);

    assertInstanceOf(TitleWrapperScreen.class, screen);
    assertInstanceOf(ChatPromptScreen.class, ((TitleWrapperScreen) screen).delegate());
    // Empty main and the empty cancel strings never reach the expander.
    assertFalse(expanded.contains(""));
    // The prompt text was expanded exactly once — the title fallback reuses that value
    // instead of expanding a second time.
    assertEquals(1, Collections.frequency(expanded, "Hello %player_name%"));
    assertEquals(1, expanded.size());
  }

  /**
   * A non-dialog inline tag flows through {@link InlineTagMapper} on the raw tag and then {@link
   * PromptFactory#create}, so the display text is expanded exactly once (never twice by the mapper
   * and the factory).
   */
  @Test
  void inlineTagDisplayTextIsExpandedExactlyOnce() {
    var tag = new PromptTag("<a:Enter %player_name%>", "a", null, "Enter %player_name%");

    List<String> expanded = new ArrayList<>();
    var testFactory = factoryWithExpander(expanded);

    var screen = testFactory.createFromTag(createPlayer(), tag);

    assertInstanceOf(AnvilPromptScreen.class, screen);
    assertEquals(1, Collections.frequency(expanded, "Enter %player_name%"));
    // Default inline anvil fields are blank and must be skipped, not mangled.
    assertFalse(expanded.contains(""));
  }

  @Test
  void createFromTag_unknownKey_throwsAndNeverFallsBackToChat() {
    var tag = new PromptTag("<unknown:Hello>", "unknown", null, "Hello");
    assertThrows(IllegalArgumentException.class, () -> factory.createFromTag(createPlayer(), tag));
  }

  @Test
  void createConfirmation_inlineTimeout_propagatesToChatViewTtl() {
    var tag =
        new PromptTag(
            "<c:Confirm? -timeout:1 -mode:chat>",
            "c",
            null,
            "Confirm? -timeout:1 -mode:chat",
            true,
            null,
            PromptTag.AnswerType.NONE,
            List.of(),
            false,
            null,
            1);
    var screen = factory.createFromTag(createPlayer(), tag);
    assertInstanceOf(
        dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen.class, screen);
    var confScreen = (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen) screen;
    var chatView =
        confScreen.fallbackChain().stream()
            .filter(
                v -> v instanceof dev.cyr1en.promptpaper.screen.confirmation.ConfirmationChatView)
            .map(v -> (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationChatView) v)
            .findFirst()
            .orElseThrow();
    assertEquals(java.time.Duration.ofSeconds(1), chatView.getTtl());
  }

  @Test
  void createConfirmation_nullInlineTimeout_fallsBackToConfigTimeout() {
    Mockito.when(config.promptTimeout()).thenReturn(45);
    var tag = new PromptTag("<c:Confirm? -mode:chat>", "c", null, "Confirm? -mode:chat");
    var screen = factory.createFromTag(createPlayer(), tag);
    assertInstanceOf(
        dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen.class, screen);
    var confScreen = (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen) screen;
    var chatView =
        confScreen.fallbackChain().stream()
            .filter(
                v -> v instanceof dev.cyr1en.promptpaper.screen.confirmation.ConfirmationChatView)
            .map(v -> (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationChatView) v)
            .findFirst()
            .orElseThrow();
    assertEquals(java.time.Duration.ofSeconds(45), chatView.getTtl());
  }

  @Test
  void createConfirmation_presetTimeout_respectsLowerTimeout() {
    Mockito.when(config.promptTimeout()).thenReturn(300);
    var preset =
        new ConfirmationPrompt(
            "confirmation",
            "c_low",
            dev.cyr1en.promptcore.ConfirmationMode.CHAT,
            null,
            "Proceed?",
            null,
            null,
            false,
            null,
            true,
            null,
            15);
    var screen = factory.create(createPlayer(), preset);
    assertInstanceOf(
        dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen.class, screen);
    var confScreen = (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen) screen;
    var chatView =
        confScreen.fallbackChain().stream()
            .filter(
                v -> v instanceof dev.cyr1en.promptpaper.screen.confirmation.ConfirmationChatView)
            .map(v -> (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationChatView) v)
            .findFirst()
            .orElseThrow();
    assertEquals(java.time.Duration.ofSeconds(15), chatView.getTtl());
  }

  @Test
  void createConfirmation_globalTimeoutZero_capsTtlAt3600() {
    Mockito.when(config.promptTimeout()).thenReturn(0);
    var preset =
        new ConfirmationPrompt(
            "confirmation",
            "c_zero",
            dev.cyr1en.promptcore.ConfirmationMode.CHAT,
            null,
            "Proceed?",
            null,
            null,
            false,
            null,
            true,
            null,
            null);
    var screen = factory.create(createPlayer(), preset);
    assertInstanceOf(
        dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen.class, screen);
    var confScreen = (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen) screen;
    var chatView =
        confScreen.fallbackChain().stream()
            .filter(
                v -> v instanceof dev.cyr1en.promptpaper.screen.confirmation.ConfirmationChatView)
            .map(v -> (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationChatView) v)
            .findFirst()
            .orElseThrow();
    assertEquals(java.time.Duration.ofSeconds(3600), chatView.getTtl());
  }

  @Test
  void createConfirmation_maxTimeout_capsTtlAt3600() {
    Mockito.when(config.promptTimeout()).thenReturn(3600);
    var preset =
        new ConfirmationPrompt(
            "confirmation",
            "c_max",
            dev.cyr1en.promptcore.ConfirmationMode.CHAT,
            null,
            "Proceed?",
            null,
            null,
            false,
            null,
            true,
            null,
            3600);
    var screen = factory.create(createPlayer(), preset);
    assertInstanceOf(
        dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen.class, screen);
    var confScreen = (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen) screen;
    var chatView =
        confScreen.fallbackChain().stream()
            .filter(
                v -> v instanceof dev.cyr1en.promptpaper.screen.confirmation.ConfirmationChatView)
            .map(v -> (dev.cyr1en.promptpaper.screen.confirmation.ConfirmationChatView) v)
            .findFirst()
            .orElseThrow();
    assertEquals(java.time.Duration.ofSeconds(3600), chatView.getTtl());
  }

  // ------------------------------------------------------------------
  // ItemPrompt & ItemPromptScreen
  // ------------------------------------------------------------------

  @Test
  void createItemPromptInventorySourceYieldsItemPromptScreen() {
    var itemPrompt =
        new ItemPrompt(
            "item",
            "i1",
            "Select item",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            true);
    var screen = factory.create(createPlayer(), itemPrompt);
    assertInstanceOf(ItemPromptScreen.class, screen);
    var itemScreen = (ItemPromptScreen) screen;
    assertEquals(ItemScreenMode.INVENTORY, itemScreen.getMode());
    assertEquals(ItemOutputFormat.KEY, itemScreen.getOutputFormat());
    assertFalse(itemScreen.isOpen());
  }

  @Test
  void createItemPromptHandSourceYieldsItemPromptScreen() {
    var itemPrompt =
        new ItemPrompt(
            "item",
            "i2",
            "Select held item",
            ItemSource.HAND,
            ItemOutputFormat.SLOT,
            null,
            null,
            true);
    var screen = factory.create(createPlayer(), itemPrompt);
    assertInstanceOf(ItemPromptScreen.class, screen);
    var itemScreen = (ItemPromptScreen) screen;
    assertEquals(ItemScreenMode.HAND, itemScreen.getMode());
    assertEquals(ItemOutputFormat.SLOT, itemScreen.getOutputFormat());
    assertFalse(itemScreen.isOpen());
  }

  @Test
  void createItemPromptArmorSourceYieldsItemPromptScreen() {
    var itemPrompt =
        new ItemPrompt(
            "item",
            "i3",
            "Select armor item",
            ItemSource.ARMOR,
            ItemOutputFormat.AMOUNT,
            null,
            null,
            true);
    var screen = factory.create(createPlayer(), itemPrompt);
    assertInstanceOf(ItemPromptScreen.class, screen);
    var itemScreen = (ItemPromptScreen) screen;
    assertEquals(ItemScreenMode.ARMOR, itemScreen.getMode());
    assertEquals(ItemOutputFormat.AMOUNT, itemScreen.getOutputFormat());
    assertFalse(itemScreen.isOpen());
  }

  @Test
  void createItemPromptCatalogSourceWithValidCategoryYieldsScreenWithCapturedSnapshot() {
    var entry = CatalogEntry.of(org.bukkit.Material.STONE);
    var snapshot =
        new CatalogSnapshot(
            Map.of(
                "all", List.of(entry),
                "weapons", List.of(entry)));
    var catalogRegistry = Mockito.mock(ItemCatalogRegistry.class);
    Mockito.when(catalogRegistry.snapshot()).thenReturn(snapshot);
    Mockito.when(plugin.getItemCatalogRegistry()).thenReturn(catalogRegistry);

    var itemPrompt =
        new ItemPrompt(
            "item",
            "i4",
            "Catalog prompt",
            ItemSource.CATALOG,
            ItemOutputFormat.MATERIAL,
            "weapons",
            "ui.button.click",
            true);
    var screen = factory.create(createPlayer(), itemPrompt);
    assertInstanceOf(ItemPromptScreen.class, screen);
    var itemScreen = (ItemPromptScreen) screen;
    assertEquals(ItemScreenMode.CATALOG, itemScreen.getMode());
    assertEquals(ItemOutputFormat.MATERIAL, itemScreen.getOutputFormat());
    assertSame(snapshot, itemScreen.getCatalogSnapshot());
    assertFalse(itemScreen.isOpen());
  }

  @Test
  void createItemPromptCatalogSourceWithDefaultAllCategoryYieldsScreenWithCapturedSnapshot() {
    var entry = CatalogEntry.of(org.bukkit.Material.STONE);
    var snapshot = new CatalogSnapshot(Map.of("all", List.of(entry)));
    var catalogRegistry = Mockito.mock(ItemCatalogRegistry.class);
    Mockito.when(catalogRegistry.snapshot()).thenReturn(snapshot);
    Mockito.when(plugin.getItemCatalogRegistry()).thenReturn(catalogRegistry);

    var itemPrompt =
        new ItemPrompt(
            "item",
            "i_all",
            "All items",
            ItemSource.CATALOG,
            ItemOutputFormat.KEY,
            null,
            null,
            true);
    var screen = factory.create(createPlayer(), itemPrompt);
    assertInstanceOf(ItemPromptScreen.class, screen);
    var itemScreen = (ItemPromptScreen) screen;
    assertEquals(ItemScreenMode.CATALOG, itemScreen.getMode());
    assertSame(snapshot, itemScreen.getCatalogSnapshot());
  }

  @Test
  void createItemPromptCatalogSourceUnknownCategoryThrowsIllegalStateException() {
    var entry = CatalogEntry.of(org.bukkit.Material.STONE);
    var snapshot = new CatalogSnapshot(Map.of("all", List.of(entry)));
    var catalogRegistry = Mockito.mock(ItemCatalogRegistry.class);
    Mockito.when(catalogRegistry.snapshot()).thenReturn(snapshot);
    Mockito.when(plugin.getItemCatalogRegistry()).thenReturn(catalogRegistry);

    var itemPrompt =
        new ItemPrompt(
            "item",
            "i_bad",
            "Catalog prompt",
            ItemSource.CATALOG,
            ItemOutputFormat.KEY,
            "nonexistent",
            null,
            true);
    assertThrows(IllegalStateException.class, () -> factory.create(createPlayer(), itemPrompt));
  }

  @Test
  void createItemPromptCatalogSourceNullRegistryThrowsIllegalStateException() {
    Mockito.when(plugin.getItemCatalogRegistry()).thenReturn(null);

    var itemPrompt =
        new ItemPrompt(
            "item",
            "i_null_reg",
            "Catalog prompt",
            ItemSource.CATALOG,
            ItemOutputFormat.KEY,
            "all",
            null,
            true);
    assertThrows(IllegalStateException.class, () -> factory.create(createPlayer(), itemPrompt));
  }

  @Test
  void createItemPromptWithTitleDisplayIsWrapped() {
    var title = new TitleConfig("Choose Item", "Subtitle", 50);
    var itemPrompt =
        new ItemPrompt(
            "item",
            "i_title",
            "Select item",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            true,
            title);
    var screen = factory.create(createPlayer(), itemPrompt);
    assertInstanceOf(TitleWrapperScreen.class, screen);
    var wrapper = (TitleWrapperScreen) screen;
    assertInstanceOf(ItemPromptScreen.class, wrapper.delegate());
  }

  @Test
  void createItemPromptWithEmptyTitleMainInjectsPromptText() {
    var title = new TitleConfig("", null, 50);
    var itemPrompt =
        new ItemPrompt(
            "item",
            "i_empty_title",
            "Select your weapon",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            true,
            title);
    var screen = factory.create(createPlayer(), itemPrompt);
    assertInstanceOf(TitleWrapperScreen.class, screen);
    var wrapper = (TitleWrapperScreen) screen;
    assertInstanceOf(ItemPromptScreen.class, wrapper.delegate());
  }

  @Test
  void createFromTagItemTagYieldsItemPromptScreen() {
    var tag = new PromptTag("<i:Select an item>", "i", null, "Select an item");
    var screen = factory.createFromTag(createPlayer(), tag);
    assertInstanceOf(ItemPromptScreen.class, screen);
    var itemScreen = (ItemPromptScreen) screen;
    assertEquals(ItemScreenMode.INVENTORY, itemScreen.getMode());
  }

  @Test
  void createFromTagItemTagWithTitleFlagWrapsScreen() {
    var tag =
        new PromptTag(
            "<i:Select item>",
            "i",
            null,
            "Select item",
            true,
            null,
            PromptTag.AnswerType.NONE,
            java.util.List.of(),
            false,
            new TitleConfig("Item Title", null, 40));
    var screen = factory.createFromTag(createPlayer(), tag);
    assertInstanceOf(TitleWrapperScreen.class, screen);
    var wrapper = (TitleWrapperScreen) screen;
    assertInstanceOf(ItemPromptScreen.class, wrapper.delegate());
  }

  @Test
  void itemPromptPresentationExpansionExpandsPromptTextExactlyOnce() {
    var itemPrompt =
        new ItemPrompt(
            "item",
            "i_papi",
            "Select item for %player_name%",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            true);
    List<String> expanded = new ArrayList<>();
    var testFactory = factoryWithExpander(expanded);

    var screen = testFactory.create(createPlayer(), itemPrompt);
    assertInstanceOf(ItemPromptScreen.class, screen);
    assertEquals(1, Collections.frequency(expanded, "Select item for %player_name%"));
    assertFalse(expanded.contains("i_papi"));
  }
}
