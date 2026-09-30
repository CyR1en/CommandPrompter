package dev.cyr1en.promptpaper.hook.geyser;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptui.AnvilItemPresentation.Slot;
import dev.cyr1en.promptui.util.BedrockUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeyserStandaloneSupportTest extends MockBukkitTest {
  @TempDir Path data;
  private final byte[] key = new byte[AnvilPatchProtocol.TOKEN_BYTES];

  @BeforeEach
  void setupStandalone() throws Exception {
    when(plugin.getDataFolder()).thenReturn(data.toFile());
    Files.createDirectories(data.resolve("compat"));
    Files.write(data.resolve("compat/standalone.key"), key);
    when(config.geyserAnvilPatch()).thenReturn(true);
    BedrockUtil.reset();
  }

  @Test
  void onlyPairedClientsReceivePresentationAndQuitClearsTheirCapability() throws Exception {
    var player = createPlayer();
    var other = createPlayer();
    try (var support = new GeyserStandaloneSupport(plugin)) {
      support.enable();
      assertFalse(BedrockUtil.isGeyserInstalled());
      assertFalse(BedrockUtil.isBedrockPlayer(player));
      var message = AnvilPatchProtocol.encode(key, Set.of("paper", "barrier"));
      support.onPluginMessageReceived(AnvilPatchProtocol.CHANNEL, player, message);
      assertTrue(BedrockUtil.isBedrockPlayer(player));
      assertFalse(BedrockUtil.isBedrockPlayer(other));
      var presentation = support.itemPresentation(player.getUniqueId()).orElseThrow();
      assertTrue(support.itemPresentation(other.getUniqueId()).isEmpty());
      var result = mock(ItemStack.class);
      assertSame(result, presentation.present(Slot.RESULT, result));
      when(result.getType()).thenReturn(Material.DIAMOND);
      assertThrows(IllegalStateException.class, () -> presentation.present(Slot.INPUT, result));
      var quit = mock(PlayerQuitEvent.class);
      when(quit.getPlayer()).thenReturn(player);
      support.onQuit(quit);
      assertFalse(BedrockUtil.isBedrockPlayer(player));
      assertTrue(support.itemPresentation(player.getUniqueId()).isEmpty());
      support.onPluginMessageReceived(AnvilPatchProtocol.CHANNEL, player, message);
    }
    assertFalse(BedrockUtil.isBedrockPlayer(player));
    assertFalse(
        server.getMessenger().isIncomingChannelRegistered(plugin, AnvilPatchProtocol.CHANNEL));
  }

  @Test
  void rejectsStaleOrMalformedAnnouncementsAndHonorsOptOut() throws Exception {
    var player = createPlayer();
    try (var support = new GeyserStandaloneSupport(plugin)) {
      support.enable();
      byte[] wrongKey = key.clone();
      wrongKey[0] = 1;
      support.onPluginMessageReceived(
          AnvilPatchProtocol.CHANNEL, player, AnvilPatchProtocol.encode(wrongKey, Set.of("paper")));
      support.onPluginMessageReceived(AnvilPatchProtocol.CHANNEL, player, new byte[0]);
      support.onPluginMessageReceived(
          AnvilPatchProtocol.CHANNEL,
          player,
          AnvilPatchProtocol.encode(key, Set.of("nonexistent")));
      support.onPluginMessageReceived(
          "unrelated:channel", player, AnvilPatchProtocol.encode(key, Set.of("paper")));
      assertTrue(support.itemPresentation(player.getUniqueId()).isEmpty());
      assertFalse(BedrockUtil.isBedrockPlayer(player));
    }
    when(config.geyserAnvilPatch()).thenReturn(false);
    try (var support = new GeyserStandaloneSupport(plugin)) {
      support.enable();
      support.onPluginMessageReceived(
          AnvilPatchProtocol.CHANNEL, player, AnvilPatchProtocol.encode(key, Set.of("paper")));
      assertTrue(support.itemPresentation(player.getUniqueId()).isEmpty());
      assertFalse(BedrockUtil.isBedrockPlayer(player));
    }
  }
}
