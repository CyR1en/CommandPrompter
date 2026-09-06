package dev.cyr1en.promptpaper.screen.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.sub.DialogConfig;
import dev.cyr1en.promptui.ComponentUtil;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmationDialogViewTest extends MockBukkitTest {

  private Player player;
  private AtomicReference<ConfirmationOutcome> outcomeRef;
  private AtomicInteger outcomeCount;
  private AtomicReference<Runnable> confirmCallbackRef;
  private AtomicReference<Runnable> declineCallbackRef;
  private AtomicBoolean launcherClosed;
  private ConfirmationDialogView.DialogLauncher testLauncher;

  @BeforeEach
  void setUpConfirmationDialog() {
    player = createPlayer("DialogUser");
    outcomeRef = new AtomicReference<>();
    outcomeCount = new AtomicInteger();
    confirmCallbackRef = new AtomicReference<>();
    declineCallbackRef = new AtomicReference<>();
    launcherClosed = new AtomicBoolean(false);

    testLauncher =
        new ConfirmationDialogView.DialogLauncher() {
          @Override
          public void show(Player p, Runnable onConfirm, Runnable onDecline) {
            confirmCallbackRef.set(onConfirm);
            declineCallbackRef.set(onDecline);
          }

          @Override
          public void close(Player p) {
            launcherClosed.set(true);
          }
        };
  }

  private ConfirmationDialogView createView() {
    return new ConfirmationDialogView(
        plugin,
        player,
        Component.text("Confirm Action"),
        Component.text("Are you sure?"),
        Component.text("Yes"),
        Component.text("Confirm tooltip"),
        Component.text("No"),
        Component.text("Decline tooltip"),
        testLauncher);
  }

  @Test
  void initialConfigurationAndAccessors() {
    var view = createView();

    assertEquals(Component.text("Confirm Action"), view.getTitle());
    assertEquals(Component.text("Are you sure?"), view.getBody());
    assertEquals(Component.text("Yes"), view.getConfirmLabel());
    assertEquals(Component.text("Confirm tooltip"), view.getConfirmTooltip());
    assertEquals(Component.text("No"), view.getDeclineLabel());
    assertEquals(Component.text("Decline tooltip"), view.getDeclineTooltip());
    assertSame(plugin, view.getPlugin());
    assertSame(player, view.getPlayer());
    assertSame(testLauncher, view.getLauncher());
    assertFalse(view.isOpen());
  }

  @Test
  void stringAndMiniMessageConstructors() {
    var view =
        new ConfirmationDialogView(
            plugin,
            player,
            "<gold>Title</gold>",
            "<gray>Body text</gray>",
            "<green>Confirm</green>",
            "Tooltip 1",
            "<red>Cancel</red>",
            "Tooltip 2");

    assertEquals(ComponentUtil.mini("<gold>Title</gold>"), view.getTitle());
    assertEquals(ComponentUtil.mini("<gray>Body text</gray>"), view.getBody());
    assertEquals(ComponentUtil.mini("<green>Confirm</green>"), view.getConfirmLabel());
    assertEquals(ComponentUtil.mini("Tooltip 1"), view.getConfirmTooltip());
    assertEquals(ComponentUtil.mini("<red>Cancel</red>"), view.getDeclineLabel());
    assertEquals(ComponentUtil.mini("Tooltip 2"), view.getDeclineTooltip());
  }

  @Test
  void dialogConfigConstructor() {
    var dialogConfig =
        DialogConfig.legacy(
            "<gold>Config Title</gold>",
            "<green>Yes</green>",
            "Confirm action",
            "<red>No</red>",
            "Decline action");

    var view =
        new ConfirmationDialogView(plugin, player, dialogConfig, Component.text("Config Body"));

    assertEquals(ComponentUtil.mini("<gold>Config Title</gold>"), view.getTitle());
    assertEquals(Component.text("Config Body"), view.getBody());
    assertEquals(ComponentUtil.mini("<green>Yes</green>"), view.getConfirmLabel());
    assertEquals(ComponentUtil.mini("Confirm action"), view.getConfirmTooltip());
    assertEquals(ComponentUtil.mini("<red>No</red>"), view.getDeclineLabel());
    assertEquals(ComponentUtil.mini("Decline action"), view.getDeclineTooltip());
  }

  @Test
  void openShowsDialogAndMarksOpen() {
    var view = createView();

    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    assertTrue(view.isOpen());
    assertNotNull(confirmCallbackRef.get());
    assertNotNull(declineCallbackRef.get());
    assertEquals(0, outcomeCount.get());
  }

  @Test
  void confirmCallbackEmitsConfirmedAndCloses() {
    var view = createView();

    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    assertTrue(view.isOpen());
    assertNotNull(confirmCallbackRef.get());

    // Simulate confirm button click
    confirmCallbackRef.get().run();
    server.getScheduler().performOneTick();

    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Confirmed.class, outcomeRef.get());
    assertFalse(view.isOpen());
    assertTrue(launcherClosed.get());
  }

  @Test
  void declineCallbackEmitsDeclinedAndCloses() {
    var view = createView();

    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    assertTrue(view.isOpen());
    assertNotNull(declineCallbackRef.get());

    // Simulate decline button click
    declineCallbackRef.get().run();
    server.getScheduler().performOneTick();

    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Declined.class, outcomeRef.get());
    assertFalse(view.isOpen());
    assertTrue(launcherClosed.get());
  }

  @Test
  void programmaticCloseEmitsNoOutcome() {
    var view = createView();

    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    assertTrue(view.isOpen());

    view.close();
    server.getScheduler().performOneTick();

    assertFalse(view.isOpen());
    assertEquals(0, outcomeCount.get());
    assertTrue(launcherClosed.get());

    // Any subsequent callback from client must be ignored
    if (confirmCallbackRef.get() != null) {
      confirmCallbackRef.get().run();
      server.getScheduler().performOneTick();
    }
    if (declineCallbackRef.get() != null) {
      declineCallbackRef.get().run();
      server.getScheduler().performOneTick();
    }
    assertEquals(0, outcomeCount.get());
  }

  @Test
  void duplicateConfirmCallbacksEmitOnlyOnce() {
    var view = createView();

    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    var confirmCb = confirmCallbackRef.get();
    assertNotNull(confirmCb);

    confirmCb.run();
    confirmCb.run();
    server.getScheduler().performOneTick();

    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Confirmed.class, outcomeRef.get());
    assertFalse(view.isOpen());
  }

  @Test
  void confirmThenDeclineEmitsOnlyFirstOutcome() {
    var view = createView();

    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    var confirmCb = confirmCallbackRef.get();
    var declineCb = declineCallbackRef.get();

    confirmCb.run();
    declineCb.run();
    server.getScheduler().performOneTick();

    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Confirmed.class, outcomeRef.get());
  }

  @Test
  void repeatedOpenCallsAreIgnored() {
    var view = createView();

    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    assertTrue(view.isOpen());

    // Second open call with a different callback
    AtomicBoolean secondCallbackFired = new AtomicBoolean(false);
    view.open(outcome -> secondCallbackFired.set(true));
    server.getScheduler().performOneTick();

    assertTrue(view.isOpen());

    confirmCallbackRef.get().run();
    server.getScheduler().performOneTick();

    assertEquals(1, outcomeCount.get());
    assertFalse(secondCallbackFired.get());
  }

  @Test
  void repeatedCloseCallsAreIdempotent() {
    var view = createView();

    assertFalse(view.isOpen());
    view.close();
    assertFalse(view.isOpen());

    view.open(outcome -> {});
    server.getScheduler().performOneTick();
    assertTrue(view.isOpen());

    view.close();
    assertFalse(view.isOpen());
    view.close();
    assertFalse(view.isOpen());
  }

  @Test
  void synchronousLauncherFailureInvokesOnOpenFailure() {
    var failureRef = new AtomicReference<Throwable>();
    var expectedError = new RuntimeException("Dialog show failed");

    ConfirmationDialogView.DialogLauncher failingLauncher =
        (p, onConfirm, onDecline) -> {
          throw expectedError;
        };

    var view =
        new ConfirmationDialogView(
            plugin,
            player,
            Component.text("Title"),
            (Component) null,
            Component.text("Confirm"),
            null,
            Component.text("Cancel"),
            null,
            failingLauncher);

    view.onOpenFailure(failureRef::set);

    view.open(outcome -> outcomeCount.incrementAndGet());
    server.getScheduler().performOneTick();

    assertFalse(view.isOpen());
    assertEquals(expectedError, failureRef.get());
    assertEquals(0, outcomeCount.get());
  }

  @Test
  void asyncCallbackHopsToPlayerScheduler() throws Exception {
    var view = createView();

    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    assertTrue(view.isOpen());
    var confirmCb = confirmCallbackRef.get();
    assertNotNull(confirmCb);

    // Fire confirm callback from a background thread
    var latch = new CountDownLatch(1);
    Thread.ofVirtual()
        .start(
            () -> {
              confirmCb.run();
              latch.countDown();
            });
    assertTrue(latch.await(2, TimeUnit.SECONDS));

    // Outcome should not be delivered until player scheduler runs
    server.getScheduler().performOneTick();

    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Confirmed.class, outcomeRef.get());
    assertFalse(view.isOpen());
  }

  @Test
  void coordinatorIntegrationWithFallbackChain() {
    var failureRef = new AtomicReference<Throwable>();
    var failingLauncher =
        new ConfirmationDialogView.DialogLauncher() {
          @Override
          public void show(Player p, Runnable onConfirm, Runnable onDecline) {
            throw new RuntimeException("Primary dialog failed to show");
          }
        };

    var failingDialogView =
        new ConfirmationDialogView(
            plugin,
            player,
            Component.text("Primary Dialog"),
            null,
            Component.text("Confirm"),
            null,
            Component.text("Cancel"),
            null,
            failingLauncher);

    var fallbackView = createView();

    // Chain failing dialog view -> fallback dialog view
    var screen = new ConfirmationPromptScreen(failingDialogView, fallbackView);
    var screenResultRef = new AtomicReference<dev.cyr1en.promptui.ScreenResult>();

    screen.onResult(screenResultRef::set);
    screen.open();

    server.getScheduler().performOneTick();

    // Coordinator should have advanced to fallbackView
    assertTrue(screen.isOpen());
    assertTrue(fallbackView.isOpen());
    assertNotNull(confirmCallbackRef.get());

    confirmCallbackRef.get().run();
    server.getScheduler().performOneTick();

    assertNotNull(screenResultRef.get());
    assertFalse(screenResultRef.get().cancelled());
    assertEquals("confirm", screenResultRef.get().answer());
    assertFalse(screen.isOpen());
  }

  @Test
  void nullCallbackThrowsNullPointerException() {
    var view = createView();
    assertThrows(NullPointerException.class, () -> view.open(null));
  }
}
