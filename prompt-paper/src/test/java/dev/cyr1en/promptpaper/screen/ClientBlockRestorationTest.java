package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptui.util.ClientBlockRestoration;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class ClientBlockRestorationTest {
  private final JavaPlugin plugin = mock(JavaPlugin.class);
  private final Server server = mock(Server.class);
  private final Player player = mock(Player.class);
  private final World world = mock(World.class);
  private final Location location = new Location(world, 4, 65, 8);
  private final Block block = mock(Block.class);
  private final BlockState snapshot = mock(BlockState.class);
  private final BlockData data = mock(BlockData.class);
  private final EntityScheduler entityScheduler = mock(EntityScheduler.class);
  private final RegionScheduler regionScheduler = mock(RegionScheduler.class);
  private final AtomicBoolean playerThread = new AtomicBoolean(true);
  private final AtomicBoolean blockThread = new AtomicBoolean(true);
  private final ArrayDeque<Consumer<ScheduledTask>> playerTasks = new ArrayDeque<>();
  private final ArrayDeque<Consumer<ScheduledTask>> regionTasks = new ArrayDeque<>();

  @BeforeEach
  void setUp() {
    when(plugin.getServer()).thenReturn(server);
    when(plugin.getSLF4JLogger()).thenReturn(mock(Logger.class));
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(world.getUID()).thenReturn(UUID.randomUUID());
    when(player.isOnline()).thenReturn(true);
    when(player.getWorld())
        .thenAnswer(
            ignored -> {
              assertTrue(playerThread.get(), "Player state requires its owning thread");
              return world;
            });
    when(player.getScheduler()).thenReturn(entityScheduler);
    when(server.getRegionScheduler()).thenReturn(regionScheduler);
    when(server.isOwnedByCurrentRegion(player)).thenAnswer(ignored -> playerThread.get());
    when(server.isOwnedByCurrentRegion(location)).thenAnswer(ignored -> blockThread.get());
    when(world.isChunkLoaded(0, 0)).thenReturn(true);
    when(world.getBlockAt(4, 65, 8))
        .thenAnswer(
            ignored -> {
              assertTrue(blockThread.get(), "Block reads require the location's owning thread");
              return block;
            });
    when(block.getState()).thenReturn(snapshot);
    when(snapshot.getBlockData()).thenReturn(data);
    when(entityScheduler.run(eq(plugin), any(), any()))
        .thenAnswer(
            invocation -> {
              playerTasks.add(invocation.getArgument(1));
              return mock(ScheduledTask.class);
            });
    when(regionScheduler.run(eq(plugin), eq(location), any()))
        .thenAnswer(
            invocation -> {
              regionTasks.add(invocation.getArgument(2));
              return mock(ScheduledTask.class);
            });
  }

  @Test
  void restoresTheCurrentBlockAndTileSnapshotExactlyOnce() {
    var restoration = new ClientBlockRestoration(plugin, player, location);
    var current = mock(TileState.class);
    var currentData = mock(BlockData.class);
    when(current.getBlockData()).thenReturn(currentData);
    when(block.getState()).thenReturn(current);

    restoration.restore();
    restoration.restore();

    verify(player).sendBlockChange(location, currentData);
    verify(player).sendBlockUpdate(location, current);
    verify(block).getState();
    verifyNoInteractions(snapshot);
    assertTrue(playerTasks.isEmpty());
    assertTrue(regionTasks.isEmpty());
  }

  @Test
  void replacedTileIsNotReplayedAfterItBecomesAnOrdinaryBlock() {
    when(block.getState()).thenReturn(mock(TileState.class));
    var restoration = new ClientBlockRestoration(plugin, player, location);
    when(block.getState()).thenReturn(snapshot);

    restoration.restore();

    verify(player).sendBlockChange(location, data);
    verify(player, never()).sendBlockUpdate(any(), any());
  }

  @Test
  void worldChangeSkipsRestorationAndWorldReads() {
    var restoration = new ClientBlockRestoration(plugin, player, location);
    when(player.getWorld()).thenReturn(mock(World.class));

    restoration.restore();

    verifyNoInteractions(block);
    verify(player, never()).sendBlockChange(any(), any());
  }

  @Test
  void restorationUsesTheBlockRegionThenReturnsToThePlayerThread() {
    var restoration = new ClientBlockRestoration(plugin, player, location);
    blockThread.set(false);
    restoration.restore();
    verifyNoInteractions(block);

    playerThread.set(false);
    blockThread.set(true);
    regionTasks.removeFirst().accept(mock(ScheduledTask.class));
    verify(block).getState();
    verify(player, never()).sendBlockChange(any(), any());

    playerThread.set(true);
    blockThread.set(false);
    playerTasks.removeFirst().accept(mock(ScheduledTask.class));
    verify(player).sendBlockChange(location, data);
  }

  @Test
  void worldChangeDuringTheRegionHandoffSkipsTheQueuedPackets() {
    var restoration = new ClientBlockRestoration(plugin, player, location);
    blockThread.set(false);
    restoration.restore();
    playerThread.set(false);
    blockThread.set(true);
    regionTasks.removeFirst().accept(mock(ScheduledTask.class));
    playerThread.set(true);
    when(player.getWorld()).thenReturn(mock(World.class));

    playerTasks.removeFirst().accept(mock(ScheduledTask.class));

    verify(player, never()).sendBlockChange(any(), any());
  }

  @Test
  void pendingRestorationDoesNotOverwriteANewerPromptAtTheSamePosition() {
    var old = new ClientBlockRestoration(plugin, player, location);
    blockThread.set(false);
    old.restore();
    playerThread.set(false);
    blockThread.set(true);
    regionTasks.removeFirst().accept(mock(ScheduledTask.class));
    playerThread.set(true);
    var replacement = new ClientBlockRestoration(plugin, player, location);

    playerTasks.removeFirst().accept(mock(ScheduledTask.class));
    verify(player, never()).sendBlockChange(any(), any());
    replacement.restore();
    verify(player).sendBlockChange(location, data);
  }

  @Test
  void retiredPlayerSkipsWorldReadsAndPackets() {
    var restoration = new ClientBlockRestoration(plugin, player, location);
    playerThread.set(false);
    when(entityScheduler.run(eq(plugin), any(), any())).thenReturn(null);

    restoration.restore();

    verifyNoInteractions(block);
    verify(player, never()).sendBlockChange(any(), any());
  }

  @Test
  void unloadedChunksAreNotLoadedForClientCleanup() {
    var restoration = new ClientBlockRestoration(plugin, player, location);
    when(world.isChunkLoaded(0, 0)).thenReturn(false);

    restoration.restore();

    verifyNoInteractions(block);
    verify(player, never()).sendBlockChange(any(), any());
  }
}
