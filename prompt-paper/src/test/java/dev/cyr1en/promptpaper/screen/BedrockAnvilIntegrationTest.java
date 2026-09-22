package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptui.AnvilInputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.util.BedrockUtil;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BedrockAnvilIntegrationTest extends MockBukkitTest {

  @BeforeEach
  @AfterEach
  void resetBedrock() {
    BedrockUtil.reset();
    try {
      org.geysermc.api.Geyser.set(null);
    } catch (Throwable ignored) {
    }
    FloodgateApi.setInstance(null);
  }

  @Test
  void bedrockPlayerDetectionInMockBukkitEnvironment() {
    var player = createPlayer();
    // In MockBukkit without Geyser, player is not detected as Bedrock
    assertFalse(BedrockUtil.isBedrockPlayer(player));
    assertFalse(BedrockUtil.isGeyserInstalled());

    // When mocked as Bedrock
    BedrockUtil.setBedrockChecker(uuid -> uuid.equals(player.getUniqueId()));
    assertTrue(BedrockUtil.isBedrockPlayer(player));
  }

  @Test
  void anvilScreenWorksForBedrockPlayer() {
    var player = createPlayer();
    BedrockUtil.setBedrockChecker(uuid -> uuid.equals(player.getUniqueId()));
    assertTrue(BedrockUtil.isBedrockPlayer(player));

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
                "bedrock-test",
                "Anvil Title",
                "Prompt message",
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
                true),
            List.of(mockProvider));

    screen.open();
    assertTrue(screen.isOpen());
    verify(mockAnvil).open();

    var resultRef = new AtomicReference<ScreenResult>();
    screen.onResult(resultRef::set);
    screen.handleResult(ScreenResult.answer("bedrock_input"));

    assertNotNull(resultRef.get());
    assertEquals("bedrock_input", resultRef.get().answer());
    assertFalse(resultRef.get().cancelled());
  }

  @Test
  void bedrockAnvilUsesOneRenameableInputInsteadOfAnInvalidItemCombination() {
    var player = createPlayer();
    BedrockUtil.setBedrockChecker(player.getUniqueId()::equals);
    when(promptConfig.anvilResultItem()).thenReturn("PAPER");
    when(promptConfig.resultItemCustomModelData()).thenReturn(12);
    var prompt =
        new dev.cyr1en.promptpaper.preset.AnvilPrompt(
            "anvil",
            "test_anvil_empty",
            "Test Anvil Empty",
            "",
            new dev.cyr1en.promptpaper.preset.AnvilButton(true, "Cancel", "BARRIER", "", 0),
            new dev.cyr1en.promptpaper.preset.AnvilButton(true, "Confirm", "PAPER", "", 0),
            false);
    var screen = new AnvilPromptScreen(plugin, player, prompt, List.of());

    var settings = screen.buildConfig(promptConfig);

    assertEquals("PAPER", settings.get("anvilItem"), "Bedrock must rename the submit item");
    assertEquals("12", settings.get("itemCustomModelData"));
    assertEquals("true", settings.get("enableFirstItem"));
    assertEquals(
        "false",
        settings.get("enableCancelItem"),
        "An unrelated material item prevents Bedrock from producing a clickable result");
    assertEquals("", settings.get("promptMessage"));
  }

  @Test
  void installedPatchPreservesConfiguredItemsAndFailedInstallationKeepsFallback() {
    var player = createPlayer();
    BedrockUtil.setBedrockChecker(player.getUniqueId()::equals);
    var hooks = mock(dev.cyr1en.promptpaper.hook.HookContainer.class);
    var hook = mock(dev.cyr1en.promptpaper.hook.hooks.GeyserHook.class);
    when(plugin.getHookContainer()).thenReturn(hooks);
    when(hooks.getHook(dev.cyr1en.promptpaper.hook.hooks.GeyserHook.class))
        .thenReturn(java.util.Optional.of(hook));
    when(hook.isAnvilPatchEnabled()).thenReturn(true);
    when(promptConfig.anvilResultItem()).thenReturn("PAPER");
    var prompt =
        new dev.cyr1en.promptpaper.preset.AnvilPrompt(
            "anvil",
            "patched",
            "Title",
            "test",
            new dev.cyr1en.promptpaper.preset.AnvilButton(
                true, "Input", "IRON_SWORD", "Input lore", 0, 1),
            new dev.cyr1en.promptpaper.preset.AnvilButton(
                true, "Cancel", "IRON_INGOT", "Cancel lore", 0),
            false);
    var screen = new AnvilPromptScreen(plugin, player, prompt, List.of());

    var settings = screen.buildConfig(promptConfig);
    assertEquals("true", settings.get("geyserAnvilPatch"));
    assertEquals("IRON_SWORD", settings.get("anvilItem"));
    assertEquals("1", settings.get("itemDamage"));
    assertEquals("Input lore", settings.get("itemHoverText"));
    assertEquals("true", settings.get("enableCancelItem"));
    assertEquals("IRON_INGOT", settings.get("anvilCancelItem"));
    assertEquals("Cancel", settings.get("cancelItemMessage"));

    when(hook.isAnvilPatchEnabled()).thenReturn(false);
    settings = screen.buildConfig(promptConfig);
    assertEquals("false", settings.get("geyserAnvilPatch"));
    assertEquals("PAPER", settings.get("anvilItem"));
    assertEquals("0", settings.get("itemDamage"));
    assertEquals("false", settings.get("enableCancelItem"));
  }

  @Test
  void suppliesTransportPresentationOnlyToBedrockScreens() {
    var bedrock = createPlayer();
    var javaPlayer = createPlayer();
    BedrockUtil.setBedrockChecker(bedrock.getUniqueId()::equals);
    var hooks = mock(dev.cyr1en.promptpaper.hook.HookContainer.class);
    var hook = mock(dev.cyr1en.promptpaper.hook.hooks.GeyserHook.class);
    when(plugin.getHookContainer()).thenReturn(hooks);
    when(hooks.getHook(dev.cyr1en.promptpaper.hook.hooks.GeyserHook.class))
        .thenReturn(java.util.Optional.of(hook));
    when(hook.isAnvilPatchEnabled()).thenReturn(true);
    var presentation = dev.cyr1en.promptui.AnvilItemPresentation.IDENTITY;
    when(hook.itemPresentation()).thenReturn(presentation);
    var provider = mock(ScreenProvider.class);
    var bedrockScreen = mock(AnvilInputScreen.class);
    var javaScreen = mock(AnvilInputScreen.class);
    when(provider.createAnvil(plugin, bedrock, "Prompt")).thenReturn(bedrockScreen);
    when(provider.createAnvil(plugin, javaPlayer, "Prompt")).thenReturn(javaScreen);
    var prompt =
        new dev.cyr1en.promptpaper.preset.AnvilPrompt(
            "anvil",
            "transport",
            "Title",
            "Prompt",
            new dev.cyr1en.promptpaper.preset.AnvilButton(true, "", "PAPER", "", 0),
            new dev.cyr1en.promptpaper.preset.AnvilButton(true, "Cancel", "BARRIER", "", 0),
            false);
    new AnvilPromptScreen(plugin, bedrock, prompt, List.of(provider)).open();
    new AnvilPromptScreen(plugin, javaPlayer, prompt, List.of(provider)).open();
    verify(bedrockScreen).setItemPresentation(presentation);
    verify(javaScreen, org.mockito.Mockito.never()).setItemPresentation(any());
    verify(bedrockScreen).open();
    verify(javaScreen).open();
  }

  @Test
  void simulatedGeyserDetection() {
    var player = createPlayer();
    var otherPlayer = createPlayer();

    MockBukkit.createMockPlugin("Geyser-Spigot");

    GeyserApi mockGeyser =
        (GeyserApi)
            Proxy.newProxyInstance(
                GeyserApi.class.getClassLoader(),
                new Class<?>[] {GeyserApi.class},
                (proxy, method, args) -> {
                  if ("isBedrockPlayer".equals(method.getName()) && args.length == 1) {
                    return player.getUniqueId().equals(args[0]);
                  }
                  return null;
                });
    org.geysermc.api.Geyser.set(mockGeyser);

    assertTrue(BedrockUtil.isGeyserInstalled());
    assertTrue(BedrockUtil.isBedrockPlayer(player));
    assertFalse(BedrockUtil.isBedrockPlayer(otherPlayer));
  }

  @Test
  void simulatedFloodgateDetection() {
    var player = createPlayer();
    var otherPlayer = createPlayer();

    MockBukkit.createMockPlugin("floodgate");

    FloodgateApi floodgate = new FloodgateApi();
    floodgate.setPlayerChecker(player.getUniqueId()::equals);
    FloodgateApi.setInstance(floodgate);

    assertTrue(BedrockUtil.isBedrockPlayer(player));
    assertFalse(BedrockUtil.isBedrockPlayer(otherPlayer));
  }
}
