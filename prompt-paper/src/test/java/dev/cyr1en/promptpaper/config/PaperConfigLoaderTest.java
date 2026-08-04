package dev.cyr1en.promptpaper.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperConfigLoaderTest extends MockBukkitTest {

  @TempDir Path tempDir;

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
}
