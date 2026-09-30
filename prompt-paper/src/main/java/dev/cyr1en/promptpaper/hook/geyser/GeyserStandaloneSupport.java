package dev.cyr1en.promptpaper.hook.geyser;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptui.AnvilItemPresentation;
import dev.cyr1en.promptui.util.BedrockUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Backend capability tracking, usable without any Geyser classes on Paper's classpath. */
public final class GeyserStandaloneSupport
    implements PluginMessageListener, Listener, AutoCloseable {
  private final CommandPrompter plugin;
  private final StandaloneJarPatcher patcher;
  private final Map<UUID, AnvilItemPresentation> players = new ConcurrentHashMap<>();
  private volatile byte[] token;
  private volatile boolean enabled;

  public GeyserStandaloneSupport(CommandPrompter plugin) {
    this.plugin = plugin;
    patcher = new StandaloneJarPatcher(plugin.getDataFolder().toPath());
  }

  public void enable() {
    try {
      if (Files.isSymbolicLink(patcher.directory()))
        throw new IOException("compat cannot be a symbolic link");
      Files.createDirectories(patcher.directory());
      if (!plugin.getConfigLoader().getConfig().geyserAnvilPatch()) return;
      Path key = keyPath();
      if (Files.exists(key, LinkOption.NOFOLLOW_LINKS)) token = readToken(key);
      plugin
          .getServer()
          .getMessenger()
          .registerIncomingPluginChannel(plugin, AnvilPatchProtocol.CHANNEL, this);
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
      enabled = true;
    } catch (IOException | RuntimeException failure) {
      plugin
          .getLogger()
          .log(Level.WARNING, "Standalone anvil support could not initialize", failure);
    }
  }

  public StandaloneJarPatcher patcher() {
    return patcher;
  }

  public StandaloneJarPatcher.Assets assets() throws IOException {
    patcher.prepareDirectory();
    var selected = BedrockAnvilAppearance.selected(plugin);
    var definitions = new TreeMap<String, String>();
    selected.forEach(
        (material, appearance) ->
            definitions.put(
                material.getKey().getKey(),
                appearance.serialized() + "|" + material.translationKey()));
    Path pack =
        BedrockAnvilAppearance.writePack(
            plugin.getDataFolder().toPath().resolve("geyser"), selected);
    return new StandaloneJarPatcher.Assets(
        definitions,
        pack == null ? new byte[0] : Files.readAllBytes(pack),
        AnvilPatchProtocol.encode(backendToken(), definitions.keySet()));
  }

  private Path keyPath() {
    return patcher.directory().resolve("standalone.key");
  }

  private synchronized byte[] backendToken() throws IOException {
    if (token != null) return token.clone();
    Files.createDirectories(patcher.directory());
    Path key = keyPath();
    if (!Files.exists(key, LinkOption.NOFOLLOW_LINKS)) {
      byte[] generated = new byte[AnvilPatchProtocol.TOKEN_BYTES];
      new SecureRandom().nextBytes(generated);
      Files.write(key, generated, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    token = readToken(key);
    return token.clone();
  }

  private static byte[] readToken(Path key) throws IOException {
    if (!Files.isRegularFile(key, LinkOption.NOFOLLOW_LINKS)
        || Files.size(key) != AnvilPatchProtocol.TOKEN_BYTES) {
      throw new IOException("Invalid standalone backend key");
    }
    return Files.readAllBytes(key);
  }

  @Override
  public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    byte[] currentToken = token;
    if (!enabled || currentToken == null || !AnvilPatchProtocol.CHANNEL.equals(channel)) return;
    try {
      var materials = EnumSet.noneOf(Material.class);
      for (String name : AnvilPatchProtocol.decode(message, currentToken)) {
        var material = Material.matchMaterial(name);
        if (material == null || material.isAir() || !material.isItem()) return;
        materials.add(material);
      }
      players.put(player.getUniqueId(), new BedrockAnvilPresentation(materials));
      BedrockUtil.setProxyBedrockPlayer(player.getUniqueId(), true);
    } catch (IOException ignored) {
      // Ignore stale or unrelated proxy announcements without enabling their item presentation.
    }
  }

  public Optional<AnvilItemPresentation> itemPresentation(UUID playerId) {
    return Optional.ofNullable(players.get(playerId));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    forget(event.getPlayer().getUniqueId());
  }

  private void forget(UUID playerId) {
    if (players.remove(playerId) != null) BedrockUtil.setProxyBedrockPlayer(playerId, false);
  }

  @Override
  public void close() {
    enabled = false;
    plugin
        .getServer()
        .getMessenger()
        .unregisterIncomingPluginChannel(plugin, AnvilPatchProtocol.CHANNEL, this);
    HandlerList.unregisterAll(this);
    Set.copyOf(players.keySet()).forEach(this::forget);
  }
}
