package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptui.AnvilInputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnvilPromptScreenTest extends MockBukkitTest {

  private List<ScreenProvider> emptyProviders;

  @BeforeEach
  void setUpAnvil() {
    emptyProviders = List.of();
    lenient().when(promptConfig.sendCancelText()).thenReturn(false);
  }

  @Test
  void constructorStoresValues() {
    var player = createPlayer();
    var screen =
        new AnvilPromptScreen(
            plugin,
            player,
            new dev.cyr1en.promptpaper.preset.AnvilPrompt(
                "anvil",
                "inline-test",
                "Anvil",
                "Enter:",
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                true),
            emptyProviders);
    assertNotNull(screen);
    assertFalse(screen.isOpen());
  }

  @Test
  void initialTextAndTitlesAreIndependentOfSanitize() {
    for (boolean sanitize : List.of(true, false)) {
      for (boolean enableTitle : List.of(true, false)) {
        when(promptConfig.enableTitle()).thenReturn(enableTitle);
        when(promptConfig.customTitle()).thenReturn("&cConfig Title");
        for (String text :
            List.of("", "BLANK", "blank", "Blank", " BLANK", "BLANK ", " ", "&aText")) {
          when(promptConfig.promptMessage()).thenReturn(text);
          var inline = testScreen("inline-test", "Ignored", sanitize);
          var inlineConfig = inline.buildConfig(promptConfig);
          assertEquals("BLANK".equals(text) ? "" : text, inlineConfig.get("promptMessage"));
          assertEquals(String.valueOf(enableTitle), inlineConfig.get("enableTitle"));
          assertEquals("&cConfig Title", inlineConfig.get("customTitle"));

          var presetConfig = testScreen("json-preset", text, sanitize).buildConfig(promptConfig);
          assertEquals(text, presetConfig.get("promptMessage"));
          assertEquals("true", presetConfig.get("enableTitle"));
          assertEquals("&aPreset Title", presetConfig.get("customTitle"));
        }
      }
    }
  }

  @Test
  void emptyAnswerIsForwardedForBothSanitizeValues() {
    for (boolean sanitize : List.of(true, false)) {
      var screen = testScreen("json-preset", "", sanitize);
      var result = new AtomicReference<ScreenResult>();
      screen.onResult(result::set);
      screen.open();
      screen.handleResult(ScreenResult.answer(""));
      assertNotNull(result.get());
      assertEquals("", result.get().answer());
      assertFalse(result.get().cancelled());
    }
  }

  private AnvilPromptScreen testScreen(String id, String text, boolean sanitize) {
    var button =
        new dev.cyr1en.promptpaper.preset.AnvilButton(true, "Not initial text", "PAPER", "", 0);
    return new AnvilPromptScreen(
        plugin,
        createPlayer(),
        new dev.cyr1en.promptpaper.preset.AnvilPrompt(
            "anvil", id, "&aPreset Title", text, button, button, sanitize),
        emptyProviders);
  }

  @Test
  void openWithEmptyProvidersFallsBackToChat() {
    var player = createPlayer();
    var screen =
        new AnvilPromptScreen(
            plugin,
            player,
            new dev.cyr1en.promptpaper.preset.AnvilPrompt(
                "anvil",
                "inline-test",
                "Anvil",
                "Enter:",
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                true),
            emptyProviders);
    screen.open();
    assertTrue(screen.isOpen());
  }

  @Test
  void openWithValidProviderDelegates() {
    var player = createPlayer();
    var mockProvider = mock(ScreenProvider.class);
    var mockAnvil = mock(AnvilInputScreen.class);
    when(mockProvider.createAnvil(any(CommandPrompter.class), any(), anyString()))
        .thenReturn(mockAnvil);

    var screen =
        new AnvilPromptScreen(
            plugin,
            player,
            new dev.cyr1en.promptpaper.preset.AnvilPrompt(
                "anvil",
                "inline-test",
                "Anvil",
                "Enter:",
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                true),
            List.of(mockProvider));
    screen.open();

    assertTrue(screen.isOpen());
    verify(mockAnvil).open();
  }

  @Test
  void asynchronousProviderFailureFallsBackToChat() {
    var player = createPlayer();
    var mockProvider = mock(ScreenProvider.class);
    var mockAnvil = mock(AnvilInputScreen.class);
    var failureCallback = new AtomicReference<Consumer<Throwable>>();
    doAnswer(
            invocation -> {
              failureCallback.set(invocation.getArgument(0));
              return null;
            })
        .when(mockAnvil)
        .onOpenFailure(any());
    when(mockProvider.createAnvil(any(CommandPrompter.class), any(), anyString()))
        .thenReturn(mockAnvil);

    var screen =
        new AnvilPromptScreen(
            plugin,
            player,
            new dev.cyr1en.promptpaper.preset.AnvilPrompt(
                "anvil",
                "inline-test",
                "Anvil",
                "Enter:",
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                true),
            List.of(mockProvider));
    screen.open();

    assertNotNull(failureCallback.get());
    failureCallback.get().accept(new IllegalStateException("async open failed"));
    assertTrue(screen.isOpen());
    verify(mockAnvil).close();
  }

  @Test
  void handleResultWithAnswerFiresCallback() {
    var player = createPlayer();
    var screen =
        new AnvilPromptScreen(
            plugin,
            player,
            new dev.cyr1en.promptpaper.preset.AnvilPrompt(
                "anvil",
                "inline-test",
                "Anvil",
                "Enter:",
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                true),
            emptyProviders);
    var resultRef = new AtomicReference<ScreenResult>();
    screen.onResult(resultRef::set);
    screen.open();

    screen.handleResult(ScreenResult.answer("  myAnswer  "));
    assertNotNull(resultRef.get());
    assertEquals("myAnswer", resultRef.get().answer());
    assertFalse(resultRef.get().cancelled());
  }

  @Test
  void handleResultWithoutSanitizePreservesColorCodes() {
    var player = createPlayer();
    var screen =
        new AnvilPromptScreen(
            plugin,
            player,
            new dev.cyr1en.promptpaper.preset.AnvilPrompt(
                "anvil",
                "inline-test",
                "Anvil",
                "Enter:",
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                false),
            emptyProviders);
    var resultRef = new AtomicReference<ScreenResult>();
    screen.onResult(resultRef::set);
    screen.open();

    screen.handleResult(ScreenResult.answer("§cHello"));
    assertNotNull(resultRef.get());
    assertEquals("§cHello", resultRef.get().answer());
    assertFalse(resultRef.get().cancelled());
  }

  @Test
  void handleResultWithCancelFiresCallback() {
    var player = createPlayer();
    var screen =
        new AnvilPromptScreen(
            plugin,
            player,
            new dev.cyr1en.promptpaper.preset.AnvilPrompt(
                "anvil",
                "inline-test",
                "Anvil",
                "Enter:",
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                true),
            emptyProviders);
    var resultRef = new AtomicReference<ScreenResult>();
    screen.onResult(resultRef::set);
    screen.open();

    screen.handleResult(ScreenResult.cancel());
    assertNotNull(resultRef.get());
    assertTrue(resultRef.get().cancelled());
  }

  @Test
  void handleResultWithCancelKeywordReturnsCancel() {
    var player = createPlayer();
    when(config.cancelKeyword()).thenReturn("cancel");
    var screen =
        new AnvilPromptScreen(
            plugin,
            player,
            new dev.cyr1en.promptpaper.preset.AnvilPrompt(
                "anvil",
                "inline-test",
                "Anvil",
                "Enter:",
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                true),
            emptyProviders);
    var resultRef = new AtomicReference<ScreenResult>();
    screen.onResult(resultRef::set);
    screen.open();

    screen.handleResult(ScreenResult.answer("  Cancel  "));
    assertNotNull(resultRef.get());
    assertTrue(resultRef.get().cancelled());
  }

  @Test
  void handleResultWithoutOpenIsNoop() {
    var player = createPlayer();
    var screen =
        new AnvilPromptScreen(
            plugin,
            player,
            new dev.cyr1en.promptpaper.preset.AnvilPrompt(
                "anvil",
                "inline-test",
                "Anvil",
                "Enter:",
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                true),
            emptyProviders);
    var resultRef = new AtomicReference<ScreenResult>();
    screen.onResult(resultRef::set);

    screen.handleResult(ScreenResult.answer("value"));
    assertNull(resultRef.get());
  }

  @Test
  void buildConfigPresetWithPromptTextMapsAllFields() {
    var player = createPlayer();
    var leftBtn =
        new dev.cyr1en.promptpaper.preset.AnvilButton(
            true, "Left Name", "DIAMOND", "Left Lore", 123);
    var rightBtn =
        new dev.cyr1en.promptpaper.preset.AnvilButton(
            true, "Cancel Name", "BARRIER", "Cancel Lore", 456);
    var preset =
        new dev.cyr1en.promptpaper.preset.AnvilPrompt(
            "anvil", "custom-anvil", "Custom Title", "Prefilled Text", leftBtn, rightBtn, true);

    var screen = new AnvilPromptScreen(plugin, player, preset, emptyProviders);
    var configMap = screen.buildConfig(promptConfig);

    assertEquals("true", configMap.get("enableTitle"));
    assertEquals("Custom Title", configMap.get("customTitle"));
    assertEquals("true", configMap.get("enableFirstItem"));
    assertEquals("Prefilled Text", configMap.get("promptMessage"));
    assertEquals("Left Lore", configMap.get("itemHoverText"));
    assertEquals("DIAMOND", configMap.get("anvilItem"));
    assertEquals("123", configMap.get("itemCustomModelData"));
    assertEquals("true", configMap.get("enableCancelItem"));
    assertEquals("BARRIER", configMap.get("anvilCancelItem"));
    assertEquals("456", configMap.get("cancelItemCustomModelData"));
    assertEquals("Cancel Name", configMap.get("cancelItemMessage"));
    assertEquals("Cancel Lore", configMap.get("cancelItemHoverText"));
  }

  @Test
  void buildConfigPresetWithEmptyPromptTextDoesNotFallBackToLeftButtonText() {
    var player = createPlayer();
    var leftBtn = new dev.cyr1en.promptpaper.preset.AnvilButton(false, "Left Name", "PAPER", "", 0);
    var rightBtn =
        new dev.cyr1en.promptpaper.preset.AnvilButton(false, "Cancel Name", "BARRIER", "", 0);
    var preset =
        new dev.cyr1en.promptpaper.preset.AnvilPrompt(
            "anvil", "custom-anvil", "Custom Title", "", leftBtn, rightBtn, true);

    var screen = new AnvilPromptScreen(plugin, player, preset, emptyProviders);
    var configMap = screen.buildConfig(promptConfig);

    assertEquals("false", configMap.get("enableFirstItem"));
    assertEquals("", configMap.get("promptMessage"));
    assertEquals("false", configMap.get("enableCancelItem"));
  }

  @Test
  void buildConfigInlineUsesPromptConfigDefaults() {
    var player = createPlayer();
    when(promptConfig.enableTitle()).thenReturn(false);
    when(promptConfig.customTitle()).thenReturn("Config Title");
    when(promptConfig.promptMessage()).thenReturn("Config Prompt");
    when(promptConfig.enableCancelItem()).thenReturn(true);
    when(promptConfig.anvilItem()).thenReturn("GOLD_INGOT");
    when(promptConfig.itemCustomModelData()).thenReturn(10);
    when(promptConfig.anvilCancelItem()).thenReturn("REDSTONE");
    when(promptConfig.cancelItemCustomModelData()).thenReturn(20);
    when(promptConfig.cancelItemHoverText()).thenReturn("Config Cancel Hover");

    var inlinePreset =
        new dev.cyr1en.promptpaper.preset.AnvilPrompt(
            "anvil",
            "inline-123",
            "Inline Title",
            "Enter:",
            new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
            new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
            true);

    var screen = new AnvilPromptScreen(plugin, player, inlinePreset, emptyProviders);
    var configMap = screen.buildConfig(promptConfig);

    assertEquals("false", configMap.get("enableTitle"));
    assertEquals("Config Title", configMap.get("customTitle"));
    assertEquals("true", configMap.get("enableFirstItem"));
    assertEquals("Config Prompt", configMap.get("promptMessage"));
    assertEquals("", configMap.get("itemHoverText"));
    assertEquals("GOLD_INGOT", configMap.get("anvilItem"));
    assertEquals("10", configMap.get("itemCustomModelData"));
    assertEquals("true", configMap.get("enableCancelItem"));
    assertEquals("REDSTONE", configMap.get("anvilCancelItem"));
    assertEquals("20", configMap.get("cancelItemCustomModelData"));
    assertEquals("", configMap.get("cancelItemMessage"));
    assertEquals("Config Cancel Hover", configMap.get("cancelItemHoverText"));
  }
}
