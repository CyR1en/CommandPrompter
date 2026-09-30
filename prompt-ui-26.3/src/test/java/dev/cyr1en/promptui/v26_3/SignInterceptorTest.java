package dev.cyr1en.promptui.v26_3;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class SignInterceptorTest {

  private final BlockPos position = new BlockPos(4, 64, 8);
  private final JavaPlugin plugin = mock(JavaPlugin.class);
  private final Player player = mock(Player.class);
  private final EntityScheduler scheduler = mock(EntityScheduler.class);
  private final ScheduledTask task = mock(ScheduledTask.class);
  private final List<Consumer<ScheduledTask>> scheduledActions = new ArrayList<>();
  private final List<String[]> answers = new ArrayList<>();
  private final List<ScheduledTask> rememberedTasks = new ArrayList<>();
  private final List<Throwable> failures = new ArrayList<>();
  private final List<Object> forwarded = new ArrayList<>();
  private SignInterceptor interceptor;

  @BeforeAll
  static void initializeMinecraft() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
  }

  @BeforeEach
  void setUp() {
    when(plugin.getSLF4JLogger()).thenReturn(mock(Logger.class));
    when(player.getScheduler()).thenReturn(scheduler);
    when(scheduler.run(eq(plugin), any(), isNull()))
        .thenAnswer(
            invocation -> {
              scheduledActions.add(invocation.getArgument(1));
              return task;
            });
    interceptor =
        new SignInterceptor(
            plugin,
            player,
            position,
            "test_sign_interceptor",
            answers::add,
            rememberedTasks::add,
            failures::add);
  }

  @Test
  void matchingUpdateSchedulesAnswerPreservingOrderAndBlankLines() {
    var lines = List.of("first", "", "third", "");

    interceptor.decode(
        null, new ServerboundSignUpdatePacket(position, lines, SignTextSlot.FRONT), forwarded);

    assertTrue(forwarded.isEmpty());
    assertTrue(answers.isEmpty(), "Answers must run on the player's scheduler");
    assertEquals(List.of(task), rememberedTasks);
    assertEquals(1, scheduledActions.size());
    scheduledActions.getFirst().accept(task);
    assertEquals(1, answers.size());
    assertArrayEquals(new String[] {"first", "", "third", ""}, answers.getFirst());
    assertTrue(failures.isEmpty());
  }

  @Test
  void duplicateMatchingUpdatesAreConsumedAndSubmittedOnce() {
    var packet =
        new ServerboundSignUpdatePacket(
            position, List.of("answer", "", "", ""), SignTextSlot.FRONT);

    interceptor.decode(null, packet, forwarded);
    interceptor.decode(null, packet, forwarded);

    assertTrue(forwarded.isEmpty());
    assertEquals(1, scheduledActions.size());
    verify(scheduler).run(eq(plugin), any(), isNull());
    scheduledActions.getFirst().accept(task);
    assertEquals(1, answers.size());
    assertEquals(List.of(task), rememberedTasks);
    assertTrue(failures.isEmpty());
  }

  @Test
  void updateAtDifferentPositionIsForwardedWithoutScheduling() {
    var packet =
        new ServerboundSignUpdatePacket(
            position.above(), List.of("other", "", "", ""), SignTextSlot.FRONT);

    interceptor.decode(null, packet, forwarded);

    assertEquals(List.of(packet), forwarded);
    verifyNoInteractions(scheduler);
    assertTrue(answers.isEmpty());
    assertTrue(rememberedTasks.isEmpty());
    assertTrue(failures.isEmpty());
  }

  @Test
  void retiredSchedulerReportsFailureAndConsumesUpdate() {
    when(scheduler.run(eq(plugin), any(), isNull())).thenReturn(null);

    interceptor.decode(
        null,
        new ServerboundSignUpdatePacket(
            position, List.of("answer", "", "", ""), SignTextSlot.FRONT),
        forwarded);

    assertTrue(forwarded.isEmpty());
    assertTrue(answers.isEmpty());
    assertTrue(rememberedTasks.isEmpty());
    assertEquals(1, failures.size());
    assertInstanceOf(IllegalStateException.class, failures.getFirst());
    assertEquals("Player scheduler returned no sign-finish task", failures.getFirst().getMessage());
  }
}
