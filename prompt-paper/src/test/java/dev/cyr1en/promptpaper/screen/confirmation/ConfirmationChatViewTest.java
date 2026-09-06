package dev.cyr1en.promptpaper.screen.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ConfirmationChatViewTest extends MockBukkitTest {

  private PlayerMock player;
  private NonceResponseRegistry registry;
  private ConfirmationChatView view;
  private AtomicReference<ConfirmationOutcome> outcomeRef;
  private AtomicInteger outcomeCount;

  private static final long INCARNATION = 10L;
  private static final long GENERATION = 3L;
  private static final int PROMPT_INDEX = 2;
  private static final Duration TTL = Duration.ofSeconds(30);

  @BeforeEach
  void setUpConfirmationChat() {
    player = createPlayer("ChatConfirmUser");
    registry = new NonceResponseRegistry();
    outcomeRef = new AtomicReference<>();
    outcomeCount = new AtomicInteger();

    view =
        new ConfirmationChatView(
            plugin,
            player,
            Component.text("Proceed with transfer?"),
            Component.text("[Yes]"),
            Component.text("[No]"),
            registry,
            INCARNATION,
            GENERATION,
            PROMPT_INDEX,
            TTL);
  }

  @Test
  void openRendersInteractiveComponentsWithClickCommands() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    assertTrue(view.isOpen());
    assertTrue(view.getActiveNonce().isPresent());
    var nonce = view.getActiveNonce().get();

    var message = player.nextComponentMessage();
    assertNotNull(message, "Player should receive a chat message");

    // Inspect all click events within the component tree
    var clickEvents = extractClickEvents(message);
    assertEquals(2, clickEvents.size(), "Should have exactly 2 click events (confirm and decline)");

    var expectedConfirmCmd = "/commandprompter:response " + nonce + " confirm";
    var expectedDeclineCmd = "/commandprompter:response " + nonce + " decline";

    assertTrue(
        clickEvents.stream().anyMatch(e -> expectedConfirmCmd.equals(e.value())),
        "Must contain confirm command: " + expectedConfirmCmd);
    assertTrue(
        clickEvents.stream().anyMatch(e -> expectedDeclineCmd.equals(e.value())),
        "Must contain decline command: " + expectedDeclineCmd);
  }

  @Test
  void nonceBindingFieldsInRegistry() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });

    var nonce = view.getActiveNonce().orElseThrow();
    var bindingOpt = registry.get(nonce);
    assertTrue(bindingOpt.isPresent(), "Binding must be registered in NonceResponseRegistry");

    var binding = bindingOpt.get();
    assertEquals(nonce, binding.nonce());
    assertEquals(player.getUniqueId(), binding.playerUuid());
    assertEquals(INCARNATION, binding.incarnation());
    assertEquals(GENERATION, binding.generation());
    assertEquals(PROMPT_INDEX, binding.promptIndex());
    assertNotNull(binding.expiresAt());
    assertNotNull(binding.callback());
  }

  @Test
  void confirmDecisionMapsToConfirmedExactlyOnce() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    var nonce = view.getActiveNonce().orElseThrow();

    // Simulate successful registry consumption
    var result =
        registry.consume(
            nonce, player.getUniqueId(), INCARNATION, GENERATION, PROMPT_INDEX, "confirm");
    assertTrue(result.isSuccess());
    assertEquals(ConfirmationDecision.CONFIRM, result.optDecision().orElse(null));

    // Invoke binding callback as integration would
    result.optBinding().orElseThrow().callback().accept(result.optDecision().orElseThrow());

    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Confirmed.class, outcomeRef.get());
    assertFalse(view.isOpen());
    assertFalse(registry.contains(nonce));
  }

  @Test
  void declineDecisionMapsToDeclinedExactlyOnce() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    var nonce = view.getActiveNonce().orElseThrow();

    // Simulate successful registry consumption
    var result =
        registry.consume(
            nonce, player.getUniqueId(), INCARNATION, GENERATION, PROMPT_INDEX, "decline");
    assertTrue(result.isSuccess());
    assertEquals(ConfirmationDecision.DECLINE, result.optDecision().orElse(null));

    result.optBinding().orElseThrow().callback().accept(result.optDecision().orElseThrow());

    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Declined.class, outcomeRef.get());
    assertFalse(view.isOpen());
    assertFalse(registry.contains(nonce));
  }

  @Test
  void replaySuppressionGuardsDuplicateDeliveries() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    var nonce = view.getActiveNonce().orElseThrow();

    var result =
        registry.consume(
            nonce, player.getUniqueId(), INCARNATION, GENERATION, PROMPT_INDEX, "confirm");
    assertTrue(result.isSuccess());

    var callback = result.optBinding().orElseThrow().callback();
    callback.accept(ConfirmationDecision.CONFIRM);

    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Confirmed.class, outcomeRef.get());

    // Second delivery to callback is suppressed
    callback.accept(ConfirmationDecision.CONFIRM);
    callback.accept(ConfirmationDecision.DECLINE);
    assertEquals(1, outcomeCount.get());

    // Replay attempt on registry is rejected
    var replay =
        registry.consume(
            nonce, player.getUniqueId(), INCARNATION, GENERATION, PROMPT_INDEX, "confirm");
    assertFalse(replay.isSuccess());
  }

  @Test
  void programmaticCloseInvalidatesNonceAndClearsCallbacksWithoutEmittingOutcome() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    var nonce = view.getActiveNonce().orElseThrow();
    assertTrue(registry.contains(nonce));

    view.close();

    assertFalse(view.isOpen());
    assertFalse(registry.contains(nonce), "Nonce must be invalidated on programmatic close");
    assertEquals(0, outcomeCount.get(), "Programmatic close must not deliver any outcome");

    // Subsequent handleDecision is ignored
    view.handleDecision(ConfirmationDecision.CONFIRM);
    assertEquals(0, outcomeCount.get());
  }

  @Test
  void repeatedOpenAndRepeatedCloseAreSafe() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    var firstNonce = view.getActiveNonce().orElseThrow();
    assertTrue(view.isOpen());

    // Second open is a no-op
    view.open(outcome -> outcomeCount.incrementAndGet());
    assertEquals(firstNonce, view.getActiveNonce().orElse(null));
    assertEquals(1, registry.size());

    view.close();
    assertFalse(view.isOpen());
    assertFalse(registry.contains(firstNonce));

    // Repeated close is a no-op
    assertDoesNotThrow(() -> view.close());
    assertFalse(view.isOpen());
    assertEquals(0, outcomeCount.get());
  }

  @Test
  void openFailureCleansNonceAndNotifiesFailureCallback() {
    var failureRef = new AtomicReference<Throwable>();
    view.onOpenFailure(failureRef::set);

    // Player scheduler or registration error simulation
    // Create an un-openable view with a closed/retired entity or null player uuid
    var badView =
        new ConfirmationChatView(
            plugin,
            player,
            Component.text("Test"),
            Component.text("Y"),
            Component.text("N"),
            registry,
            INCARNATION,
            GENERATION,
            PROMPT_INDEX,
            TTL) {
          @Override
          public Component renderMessage(String nonce) {
            throw new IllegalStateException("Render simulation failed");
          }
        };

    var badOutcomeRef = new AtomicReference<ConfirmationOutcome>();
    badView.onOpenFailure(failureRef::set);

    badView.open(badOutcomeRef::set);

    assertFalse(badView.isOpen());
    assertNotNull(failureRef.get());
    assertInstanceOf(IllegalStateException.class, failureRef.get());
    assertEquals("Render simulation failed", failureRef.get().getMessage());
    assertNull(badOutcomeRef.get());
    assertEquals(0, registry.size(), "Nonce must be cleaned up on open failure");
  }

  @Test
  void playerQuitCancelsPromptWithManualReason() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    var nonce = view.getActiveNonce().orElseThrow();
    assertTrue(registry.contains(nonce));

    var quitEvent =
        new PlayerQuitEvent(
            player, Component.text("Quit"), PlayerQuitEvent.QuitReason.DISCONNECTED);
    server.getPluginManager().callEvent(quitEvent);

    assertFalse(view.isOpen());
    assertFalse(registry.contains(nonce));
    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Cancelled.class, outcomeRef.get());
    var cancelled = (ConfirmationOutcome.Cancelled) outcomeRef.get();
    assertEquals(CancelReason.MANUAL, cancelled.reason());
  }

  @Test
  void constructorValidationAndGetters() {
    assertEquals(plugin, view.getPlugin());
    assertEquals(player, view.getPlayer());
    assertEquals(Component.text("Proceed with transfer?"), view.getPrompt());
    assertEquals(Component.text("[Yes]"), view.getConfirmLabel());
    assertEquals(Component.text("[No]"), view.getCancelLabel());
    assertEquals(registry, view.getRegistry());
    assertEquals(INCARNATION, view.getIncarnation());
    assertEquals(GENERATION, view.getGeneration());
    assertEquals(PROMPT_INDEX, view.getPromptIndex());
    assertEquals(TTL, view.getTtl());

    // Default labels constructor
    var defaultView =
        new ConfirmationChatView(
            plugin, player, "Are you sure?", registry, INCARNATION, GENERATION, PROMPT_INDEX, TTL);
    assertNotNull(defaultView.getPrompt());
    assertNotNull(defaultView.getConfirmLabel());
    assertNotNull(defaultView.getCancelLabel());

    // Null validation
    assertThrows(
        NullPointerException.class,
        () ->
            new ConfirmationChatView(
                null,
                player,
                Component.empty(),
                Component.empty(),
                Component.empty(),
                registry,
                1,
                1,
                0,
                TTL));
    assertThrows(
        NullPointerException.class,
        () ->
            new ConfirmationChatView(
                plugin,
                null,
                Component.empty(),
                Component.empty(),
                Component.empty(),
                registry,
                1,
                1,
                0,
                TTL));
    assertThrows(
        NullPointerException.class,
        () ->
            new ConfirmationChatView(
                plugin,
                player,
                Component.empty(),
                Component.empty(),
                Component.empty(),
                null,
                1,
                1,
                0,
                TTL));
    assertThrows(
        NullPointerException.class,
        () ->
            new ConfirmationChatView(
                plugin,
                player,
                Component.empty(),
                Component.empty(),
                Component.empty(),
                registry,
                1,
                1,
                0,
                null));
  }

  private List<ClickEvent> extractClickEvents(Component component) {
    var list = new ArrayList<ClickEvent>();
    extractClickEventsRecursive(component, list);
    return list;
  }

  private void extractClickEventsRecursive(Component component, List<ClickEvent> list) {
    if (component == null) return;
    if (component.clickEvent() != null) {
      list.add(component.clickEvent());
    }
    for (var child : component.children()) {
      extractClickEventsRecursive(child, list);
    }
  }
}
