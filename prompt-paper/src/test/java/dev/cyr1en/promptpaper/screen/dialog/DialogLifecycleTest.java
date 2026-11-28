package dev.cyr1en.promptpaper.screen.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptui.ScreenResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class DialogLifecycleTest {

  @Test
  void dialogActionsAreBoundToThePlayersUuidAndAnUnexpectedAudienceCannotExhaustUses() {
    var owner = mock(Player.class);
    var samePlayer = mock(Player.class);
    var otherPlayer = mock(Player.class);
    var playerId = UUID.randomUUID();
    when(owner.getUniqueId()).thenReturn(playerId);
    when(samePlayer.getUniqueId()).thenReturn(playerId);
    when(otherPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

    assertFalse(DialogLifecycle.isOwner(owner, Audience.empty()));
    assertFalse(DialogLifecycle.isOwner(owner, otherPlayer));
    assertTrue(DialogLifecycle.isOwner(owner, samePlayer));
    assertEquals(
        ClickCallback.UNLIMITED_USES, DialogLifecycle.clickOptions(Duration.ofMinutes(5)).uses());
  }

  @Test
  void callbackLifetimeSupportsTheMaxPromptTimeoutAndDisabledTimeout() {
    assertEquals(
        Duration.ofHours(1),
        DialogLifecycle.clickOptions(DialogLifecycle.responseLifetime(3600)).lifetime());
    assertTrue(DialogLifecycle.responseLifetime(0).compareTo(Duration.ofHours(1)) > 0);
  }

  @Test
  void terminalResultClosesDialogBeforeNotifyingSessionManager() {
    var lifecycle = new DialogLifecycle();
    var events = new ArrayList<String>();
    var result = ScreenResult.answer("answer");
    lifecycle.onResult(received -> events.add("result:" + received.answer()));
    lifecycle.opened();

    assertTrue(lifecycle.finish(result, () -> events.add("close")));

    assertEquals(List.of("close", "result:answer"), events);
    assertFalse(lifecycle.isOpen());

    assertFalse(lifecycle.finish(result, () -> events.add("duplicate-close")));
    assertEquals(List.of("close", "result:answer"), events);
  }

  @Test
  void externalCloseDoesNotDeliverAResult() {
    var lifecycle = new DialogLifecycle();
    var events = new ArrayList<String>();
    lifecycle.onResult(result -> events.add("result"));
    lifecycle.opened();

    assertTrue(lifecycle.close(() -> events.add("close")));

    assertEquals(List.of("close"), events);
    assertFalse(lifecycle.isOpen());
  }
}
