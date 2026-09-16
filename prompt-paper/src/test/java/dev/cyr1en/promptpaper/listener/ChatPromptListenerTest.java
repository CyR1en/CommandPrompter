package dev.cyr1en.promptpaper.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatPromptListenerTest extends MockBukkitTest {

  private ScreenManager screenManager;
  private ChatPromptListener listener;

  @BeforeEach
  void setUpListener() {
    screenManager = org.mockito.Mockito.mock(ScreenManager.class);
    listener = new ChatPromptListener(plugin, screenManager);
  }

  @Test
  void priorityDefaultsToLowest() {
    when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
    assertEquals(EventPriority.LOWEST, listener.resolvePriority());
  }

  @Test
  void priorityParsesHigh() {
    when(promptConfig.responseListenerPriority()).thenReturn("HIGH");
    assertEquals(EventPriority.HIGH, listener.resolvePriority());
  }

  @Test
  void invalidPriorityFallsBackToLowest() {
    when(promptConfig.responseListenerPriority()).thenReturn("INVALID");
    assertEquals(EventPriority.LOWEST, listener.resolvePriority());
  }

  @Test
  void nullPriorityFallsBackToLowest() {
    when(promptConfig.responseListenerPriority()).thenReturn(null);
    assertEquals(EventPriority.LOWEST, listener.resolvePriority());
  }

  @Test
  void chatEventWithNoChatScreenIsNotCancelled() {
    when(screenManager.hasChatScreen(any())).thenReturn(false);
    var player = createPlayer();
    var event = chatEvent(player, "hello");
    listener.onPlayerChat(event);

    assertFalse(event.isCancelled());
  }

  @Test
  void queuedChatCannotAnswerReplacementSession() {
    when(config.promptTimeout()).thenReturn(0);
    when(promptConfig.getScreenMappings()).thenReturn(Map.of("", ScreenType.CHAT));
    var player = spy(createPlayer());
    var queued = new ArrayList<Consumer<ScheduledTask>>();
    var entityScheduler = mock(EntityScheduler.class);
    doReturn(entityScheduler).when(player).getScheduler();
    when(entityScheduler.run(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              queued.add(invocation.getArgument(1));
              return mock(ScheduledTask.class);
            });
    var engine = new PromptEngine(plugin, scheduler);
    var factory = new PromptFactory(plugin);
    var manager =
        new ScreenManager(
            plugin,
            engine,
            factory,
            scheduler,
            factory::createFromTag,
            p -> PlayerExecutor.forPlayer(plugin, p, ignored -> false));
    var realListener = new ChatPromptListener(plugin, manager);
    manager.startSession(player, "/old <value>");
    var event = chatEvent(player, "old-answer");

    realListener.onPlayerChat(event);
    assertTrue(event.isCancelled());
    assertEquals(1, queued.size());
    manager.cancelAll(player);
    manager.startSession(player, "/new <first> <second>");
    while (!queued.isEmpty()) queued.removeFirst().accept(mock(ScheduledTask.class));

    assertEquals(List.of(), engine.getSession(player).orElseThrow().answers());
    realListener.onPlayerChat(chatEvent(player, "fresh-answer"));
    while (!queued.isEmpty()) queued.removeFirst().accept(mock(ScheduledTask.class));
    assertEquals(List.of("fresh-answer"), engine.getSession(player).orElseThrow().answers());
  }

  private static AsyncChatEvent chatEvent(Player player, String message) {
    return new AsyncChatEvent(
        true,
        player,
        Set.of(player),
        ChatRenderer.defaultRenderer(),
        Component.text(message),
        Component.text(message),
        SignedMessage.system(message, Component.text(message)));
  }
}
