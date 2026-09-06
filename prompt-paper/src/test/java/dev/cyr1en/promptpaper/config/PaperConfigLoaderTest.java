package dev.cyr1en.promptpaper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.custom.CustomScreenRegistry;
import dev.cyr1en.promptpaper.custom.ScreenKeyResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperConfigLoaderTest extends MockBukkitTest {

  @TempDir Path tempDir;

  @Test
  void preparedConfigurationIsInvisibleUntilPublished() {
    when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
    var loader = new PaperConfigLoader(plugin);
    var oldConfig = loader.getConfig();
    var oldPromptConfig = loader.getPromptConfig();
    var oldI18n = loader.getI18n();

    var prepared = loader.prepareReload();

    assertSame(oldConfig, loader.getConfig());
    assertSame(oldPromptConfig, loader.getPromptConfig());
    assertSame(oldI18n, loader.getI18n());
    loader.publishReload(prepared);
    assertSame(prepared.config(), loader.getConfig());
    assertSame(prepared.promptConfig(), loader.getPromptConfig());
    assertSame(prepared.i18n(), loader.getI18n());
  }

  @Test
  void failedReloadKeepsTheCompletePublishedState() throws Exception {
    when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
    var loader = new PaperConfigLoader(plugin);
    var oldConfig = loader.getConfig();
    var oldPromptConfig = loader.getPromptConfig();
    var oldI18n = loader.getI18n();

    Files.writeString(
        tempDir.resolve("prompt-config.yml"), "PlayerUI:\n  Size: 20\n", StandardCharsets.UTF_8);

    assertThrows(IllegalArgumentException.class, loader::reload);
    assertSame(oldConfig, loader.getConfig());
    assertSame(oldPromptConfig, loader.getPromptConfig());
    assertSame(oldI18n, loader.getI18n());
  }

  @Test
  void collidingConfiguredKeyReloadRejectsAndKeepsOldStateUntilUnregistered() throws Exception {
    when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

    var customRegistry =
        new CustomScreenRegistry(() -> true, Map::of, PromptConfig.RESERVED_SCREEN_KEYS, null);
    var resolver = new ScreenKeyResolver(customRegistry, Map.of());
    when(plugin.getScreenKeyResolver()).thenReturn(resolver);

    Plugin thirdParty = mock(Plugin.class);
    when(thirdParty.getName()).thenReturn("EcoPlugin");
    when(thirdParty.isEnabled()).thenReturn(true);
    customRegistry.registerScreen(thirdParty, "ecoitem", (player, ctx) -> null);

    var loader = new PaperConfigLoader(plugin);
    var initialConfig = loader.getConfig();
    var initialPromptConfig = loader.getPromptConfig();
    var initialI18n = loader.getI18n();

    // Write a prompt-config.yml that maps "ECOITEM" (uppercase testing case normalization)
    Files.writeString(
        tempDir.resolve("prompt-config.yml"),
        "screen-mappings:\n  ECOITEM: Anvil\n",
        StandardCharsets.UTF_8);

    IllegalStateException ex = assertThrows(IllegalStateException.class, loader::reload);
    assertTrue(ex.getMessage().contains("ecoitem"));
    assertTrue(ex.getMessage().contains("EcoPlugin"));

    // Old state must be preserved exactly
    assertSame(initialConfig, loader.getConfig());
    assertSame(initialPromptConfig, loader.getPromptConfig());
    assertSame(initialI18n, loader.getI18n());

    // After unregistering custom screen, reload succeeds
    customRegistry.unregisterScreens(thirdParty);

    loader.reload();
    assertNotSame(initialPromptConfig, loader.getPromptConfig());
    assertEquals(ScreenType.ANVIL, loader.getPromptConfig().getScreenMappings().get("ecoitem"));
  }
}
