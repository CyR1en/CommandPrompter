package dev.cyr1en.promptpaper.custom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PlayerExecutor Ownership Fast-Path and Scheduled Fallback Tests")
class PlayerExecutorTest extends MockBukkitTest {

  @Test
  @DisplayName("Executes task inline when ownership predicate is true")
  void executesInlineWhenOwned() {
    var player = createPlayer();
    var executed = new AtomicBoolean(false);
    var retired = new AtomicBoolean(false);

    PlayerExecutor executor = PlayerExecutor.forPlayer(plugin, player, p -> true);

    executor.execute(() -> executed.set(true), () -> retired.set(true));

    assertTrue(executed.get(), "Task must execute immediately inline when owned");
    assertFalse(retired.get(), "Retirement callback must not run on successful inline execution");
  }

  @Test
  @DisplayName("Schedules on entity scheduler when ownership predicate is false")
  void schedulesFallbackWhenNotOwned() {
    Player mockPlayer = mock(Player.class);
    EntityScheduler scheduler = mock(EntityScheduler.class);
    when(mockPlayer.getScheduler()).thenReturn(scheduler);

    var executed = new AtomicBoolean(false);
    var retired = new AtomicBoolean(false);

    doAnswer(
            invocation -> {
              Consumer<ScheduledTask> consumer = invocation.getArgument(1);
              ScheduledTask task = mock(ScheduledTask.class);
              consumer.accept(task);
              return task;
            })
        .when(scheduler)
        .run(any(Plugin.class), any(), any());

    PlayerExecutor executor = PlayerExecutor.forPlayer(plugin, mockPlayer, p -> false);

    executor.execute(() -> executed.set(true), () -> retired.set(true));

    verify(scheduler).run(eq(plugin), any(), any());
    assertTrue(executed.get(), "Scheduled task must run via entity scheduler");
    assertFalse(retired.get(), "Retirement callback must not run when scheduler accepted task");
  }

  @Test
  @DisplayName("Invokes retirement callback when scheduled task returns null")
  void invokesRetirementWhenSchedulerReturnsNull() {
    Player mockPlayer = mock(Player.class);
    EntityScheduler scheduler = mock(EntityScheduler.class);
    when(mockPlayer.getScheduler()).thenReturn(scheduler);
    when(scheduler.run(any(Plugin.class), any(), any())).thenReturn(null);

    var executed = new AtomicBoolean(false);
    var retired = new AtomicBoolean(false);

    PlayerExecutor executor = PlayerExecutor.forPlayer(plugin, mockPlayer, p -> false);

    executor.execute(() -> executed.set(true), () -> retired.set(true));

    assertFalse(executed.get(), "Task must not execute if scheduling failed");
    assertTrue(retired.get(), "Retirement callback must be invoked when scheduler returns null");
  }

  @Test
  @DisplayName("Invokes retirement callback when scheduler throws exception")
  void invokesRetirementWhenSchedulerThrows() {
    Player mockPlayer = mock(Player.class);
    EntityScheduler scheduler = mock(EntityScheduler.class);
    when(mockPlayer.getScheduler()).thenReturn(scheduler);
    when(scheduler.run(any(Plugin.class), any(), any()))
        .thenThrow(new IllegalStateException("Scheduler retired"));

    var executed = new AtomicBoolean(false);
    var retired = new AtomicBoolean(false);

    PlayerExecutor executor = PlayerExecutor.forPlayer(plugin, mockPlayer, p -> false);

    executor.execute(() -> executed.set(true), () -> retired.set(true));

    assertFalse(executed.get(), "Task must not execute if scheduler threw");
    assertTrue(retired.get(), "Retirement callback must be invoked when scheduler throws");
  }

  @Test
  @DisplayName("Invokes retirement callback when player is null")
  void handlesNullPlayerGracefully() {
    var executed = new AtomicBoolean(false);
    var retired = new AtomicBoolean(false);

    PlayerExecutor executor = PlayerExecutor.forPlayer(plugin, null, p -> true);

    executor.execute(() -> executed.set(true), () -> retired.set(true));

    assertFalse(executed.get(), "Task must not run for null player");
    assertTrue(retired.get(), "Retirement callback must run for null player");
  }

  @Test
  @DisplayName("Inline execution does not catch or suppress runtime exceptions in task")
  void inlineExecutionPropagatesTaskExceptions() {
    var player = createPlayer();
    PlayerExecutor executor = PlayerExecutor.forPlayer(plugin, player, p -> true);

    assertThrows(
        IllegalStateException.class,
        () ->
            executor.execute(
                () -> {
                  throw new IllegalStateException("Test exception");
                },
                () -> fail("Retirement callback must not run for task runtime exception")));
  }

  @Test
  @DisplayName("Default forPlayer factory checks Bukkit isOwnedByCurrentRegion")
  void defaultFactoryUsesBukkitOwnershipCheck() {
    var player = createPlayer();
    // In MockBukkit single-threaded server, isOwnedByCurrentRegion(player) evaluates
    boolean owned = PlayerExecutor.isCurrentThreadOwningPlayer(player);

    var count = new AtomicInteger(0);
    PlayerExecutor executor = PlayerExecutor.forPlayer(plugin, player);

    executor.execute(count::incrementAndGet);

    if (owned) {
      assertEquals(1, count.get(), "Should have executed inline when Bukkit reported owned");
    }
  }
}
