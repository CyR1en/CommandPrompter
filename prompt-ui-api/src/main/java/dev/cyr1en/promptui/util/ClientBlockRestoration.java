package dev.cyr1en.promptui.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Restores a client-only block from its current world state without overwriting a newer prompt. */
public final class ClientBlockRestoration {
  private static final ConcurrentMap<Key, ClientBlockRestoration> OWNERS =
      new ConcurrentHashMap<>();

  private record Key(UUID player, UUID world, int x, int y, int z) {}

  private final JavaPlugin plugin;
  private final Player player;
  private final World world;
  private final Location location;
  private final Key key;
  private final AtomicBoolean restoring = new AtomicBoolean();

  /** Called on the player's thread immediately before sending the fake block. */
  public ClientBlockRestoration(JavaPlugin plugin, Player player, Location location) {
    this.plugin = plugin;
    this.player = player;
    this.location = location.clone();
    this.world = location.getWorld();
    this.key =
        new Key(
            player.getUniqueId(),
            world.getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ());
    OWNERS.put(key, this);
  }

  public void restore() {
    if (restoring.compareAndSet(false, true)) onPlayerThread(this::readCurrentBlock);
  }

  private boolean ownsBlock() {
    return OWNERS.get(key) == this;
  }

  private boolean canSend() {
    return ownsBlock() && player.isOnline() && player.getWorld() == world;
  }

  private void readCurrentBlock() {
    if (!canSend()) {
      forget();
      return;
    }
    if (plugin.getServer().isOwnedByCurrentRegion(location)) {
      readAndSend();
    } else {
      plugin
          .getServer()
          .getRegionScheduler()
          .run(plugin, location, ignored -> guarded(this::readAndSend));
    }
  }

  private void readAndSend() {
    if (!ownsBlock() || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
      forget();
      return;
    }
    BlockState snapshot = world.getBlockAt(key.x(), key.y(), key.z()).getState();
    onPlayerThread(() -> send(snapshot));
  }

  private void send(BlockState snapshot) {
    try {
      if (!canSend()) return;
      player.sendBlockChange(location, snapshot.getBlockData());
      if (snapshot instanceof TileState tile) player.sendBlockUpdate(location, tile);
    } finally {
      forget();
    }
  }

  private void onPlayerThread(Runnable action) {
    guarded(
        () -> {
          if (plugin.getServer().isOwnedByCurrentRegion(player)) {
            action.run();
          } else if (player.getScheduler().run(plugin, ignored -> guarded(action), this::forget)
              == null) {
            forget();
          }
        });
  }

  private void guarded(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException failure) {
      forget();
      plugin.getSLF4JLogger().debug("Client block restoration failed: {}", failure.getMessage());
    }
  }

  private void forget() {
    OWNERS.remove(key, this);
  }
}
