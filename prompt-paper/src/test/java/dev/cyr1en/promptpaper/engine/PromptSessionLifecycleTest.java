package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.session.PromptSession;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromptSessionLifecycleTest extends MockBukkitTest {

  private PromptEngine engine;
  private PromptFactory factory;
  private ScreenManager screenManager;

  @BeforeEach
  void setUp() {
    promptConfig = org.mockito.Mockito.mock(dev.cyr1en.promptpaper.config.PromptConfig.class);
    when(promptConfig.getScreenMappings()).thenReturn(Map.of("", ScreenType.CHAT));
    lenient().when(promptConfig.sendCancelText()).thenReturn(false);
    lenient().when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
    lenient().when(promptConfig.textCancelMessage()).thenReturn("");
    lenient().when(promptConfig.textCancelHoverMessage()).thenReturn("");
    when(configLoader.getPromptConfig()).thenReturn(promptConfig);

    engine = new PromptEngine(plugin, scheduler);
    factory = new PromptFactory(plugin);
    screenManager = new ScreenManager(plugin, engine, factory, scheduler);
  }

  @Test
  void doubleCallbackExecutionDispatchesCommandExactlyOnce() {
    var player = createPlayer("AtomicUser");
    var primaryDispatches = new AtomicInteger(0);
    var pcmDispatches = new AtomicInteger(0);

    server
        .getCommandMap()
        .register(
            "give",
            "minecraft",
            new Command("give") {
              @Override
              public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                primaryDispatches.incrementAndGet();
                return true;
              }
            });
    server
        .getCommandMap()
        .register(
            "log",
            "minecraft",
            new Command("log") {
              @Override
              public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                pcmDispatches.incrementAndGet();
                return true;
              }
            });

    screenManager.startSession(player, "/give AtomicUser <item> <!log {0}>");
    assertTrue(engine.hasActiveSession(player));

    // Invoke chat input twice simulating duplicate racing callbacks
    screenManager.handleChatInput(player, "diamond");
    screenManager.handleChatInput(player, "gold");

    assertFalse(engine.hasActiveSession(player));
    assertEquals(1, primaryDispatches.get(), "Primary command must be dispatched exactly once");
    assertEquals(1, pcmDispatches.get(), "PCM must be dispatched exactly once");
  }

  @Test
  void timeoutAndCallbackRaceYieldsExactlyOneCancellationAndPcm() throws Exception {
    var player = createPlayer("RaceUser");
    var cancelPcmDispatches = new AtomicInteger(0);

    server
        .getCommandMap()
        .register(
            "audit",
            "minecraft",
            new Command("audit") {
              @Override
              public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                cancelPcmDispatches.incrementAndGet();
                return true;
              }
            });

    screenManager.startSession(player, "/cmd <test> <!!audit cancelled>");
    assertTrue(engine.hasActiveSession(player));

    // Trigger concurrent timeout teardown and manual cancel
    screenManager.cancelAll(player, false);
    screenManager.cancelAll(player, false);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(1, cancelPcmDispatches.get(), "Cancel PCM must be dispatched exactly once");
  }

  @Test
  void timeoutReasonObservedOnSessionCancel() {
    var player = createPlayer("TimeoutUser");
    engine.intercept(player, "/cmd <test>");
    assertTrue(engine.hasActiveSession(player));

    var sessionBefore = engine.getSession(player).orElseThrow();
    var cancelled = sessionBefore.cancel(CancelReason.TIMEOUT);

    assertEquals(CancelReason.TIMEOUT, cancelled.cancelReason().orElseThrow());
    assertEquals(PromptSession.SessionState.CANCELLED, cancelled.state());
  }

  @Test
  void cancelBeforeCloseGuaranteesCloseListenerSeesTerminalState() throws Exception {
    var player = createPlayer("CloseGuardUser");
    var programmaticCloseInvoked = new AtomicBoolean(false);
    var sessionStateDuringClose = new AtomicReference<PromptSession.SessionState>();
    var hasActiveScreenDuringClose = new AtomicBoolean(true);
    var cancelPcmCount = new AtomicInteger(0);

    server
        .getCommandMap()
        .register(
            "oncancel",
            "minecraft",
            new Command("oncancel") {
              @Override
              public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                cancelPcmCount.incrementAndGet();
                return true;
              }
            });

    var customScreen =
        new InputScreen() {
          private Consumer<ScreenResult> callback;
          private boolean open;

          @Override
          public void open() {
            open = true;
          }

          @Override
          public void close() {
            programmaticCloseInvoked.set(true);
            open = false;
            // Capture state during screen.close()
            var currentSession = engine.getSession(player);
            sessionStateDuringClose.set(currentSession.map(PromptSession::state).orElse(null));
            hasActiveScreenDuringClose.set(screenManager.hasActiveScreen(player));

            // Simulates an inventory close event firing synchronously during programmatic close
            if (callback != null) {
              callback.accept(ScreenResult.cancel(CancelReason.GUI_EXIT));
            }
          }

          @Override
          public boolean isOpen() {
            return open;
          }

          @Override
          public void onResult(Consumer<ScreenResult> callback) {
            this.callback = callback;
          }
        };

    engine.intercept(player, "/cmd <test> <!!oncancel cancelled>");
    assertTrue(engine.hasActiveSession(player));

    // Inject custom screen
    var field = ScreenManager.class.getDeclaredField("activeScreens");
    field.setAccessible(true);
    ((Map<UUID, InputScreen>) field.get(screenManager)).put(player.getUniqueId(), customScreen);

    // Teardown
    screenManager.cancelAll(player, false);

    assertTrue(programmaticCloseInvoked.get(), "screen.close() must be invoked");
    assertNull(
        sessionStateDuringClose.get(),
        "Session must already be removed/inactive during screen.close()");
    assertFalse(
        hasActiveScreenDuringClose.get(),
        "activeScreens must already be cleared during screen.close()");
    assertEquals(1, cancelPcmCount.get(), "Cancel PCM must execute exactly once");
  }

  @Test
  void oldSessionStaleCallbackNeverMatchesNewSessionAba() throws Exception {
    var player = createPlayer("AbaSessionUser");
    screenManager.startSession(player, "/cmd <first>");
    var session1 = engine.getSession(player).orElseThrow();
    long inc1 = session1.incarnation();
    long gen1 = session1.generation();

    // Complete session 1
    screenManager.handleChatInput(player, "ans1");
    assertFalse(engine.hasActiveSession(player));

    // Start session 2 for the same player
    screenManager.startSession(player, "/cmd <second>");
    var session2 = engine.getSession(player).orElseThrow();
    long inc2 = session2.incarnation();
    long gen2 = session2.generation();

    assertNotEquals(inc1, inc2, "New session must receive a distinct monotonic incarnation");
    assertEquals(0L, gen1, "Both sessions start at generation 0");
    assertEquals(0L, gen2, "Both sessions start at generation 0");

    // Stale callback from session 1 arrives
    Method handleMethod =
        ScreenManager.class.getDeclaredMethod(
            "handleResult", Player.class, ScreenResult.class, long.class, long.class, long.class);
    handleMethod.setAccessible(true);
    handleMethod.invoke(screenManager, player, ScreenResult.answer("stale_ans"), inc1, gen1, 1L);

    // Assert session 2 was NOT modified or completed by stale callback from session 1
    var currentSession = engine.getSession(player).orElseThrow();
    assertEquals(inc2, currentSession.incarnation());
    assertEquals(0L, currentSession.generation());
    assertTrue(currentSession.isActive());
    assertEquals(List.of(), currentSession.answers());
  }

  @Test
  void validationRetryScreenAttemptTokenDiscardsOldScreenCallbackAba() throws Exception {
    var player = createPlayer("AbaScreenUser");
    // Tag with INTEGER validation: only valid integers will be accepted
    screenManager.startSession(player, "/cmd <amount -int>");
    var session = engine.getSession(player).orElseThrow();
    assertEquals(0L, session.generation());
    long inc = session.incarnation();

    // Attempt 1: submit non-integer string -> validation fails, showPrompt re-shows prompt (creates
    // attempt 2)
    screenManager.handleChatInput(player, "invalid_number");

    // Session generation is still 0 (not advanced because validation failed)
    var sessionAfterFailedValidation = engine.getSession(player).orElseThrow();
    assertEquals(0L, sessionAfterFailedValidation.generation());
    assertTrue(sessionAfterFailedValidation.isActive());

    // An old delayed callback from Attempt 1 arrives with attempt token 1
    Method handleMethod =
        ScreenManager.class.getDeclaredMethod(
            "handleResult", Player.class, ScreenResult.class, long.class, long.class, long.class);
    handleMethod.setAccessible(true);
    handleMethod.invoke(screenManager, player, ScreenResult.answer("123"), inc, 0L, 1L);

    // Attempt 1 callback is discarded because the current attempt token is 2
    var sessionStillAwaiting = engine.getSession(player).orElseThrow();
    assertEquals(0L, sessionStillAwaiting.generation());
    assertTrue(sessionStillAwaiting.isActive());

    // Now Attempt 2 callback arrives (chat input via screenManager)
    screenManager.handleChatInput(player, "456");

    // Session is completed with attempt 2 answer
    assertFalse(engine.hasActiveSession(player));
  }

  @Test
  void monotonicSessionGenerationIncrementsPerTransition() {
    var session = PromptSession.start("user-1", engine.getParser().parse("/cmd <p1> <p2> <p3>"));
    assertEquals(0L, session.generation());
    assertEquals(PromptSession.SessionState.AWAITING_INPUT, session.state());

    var gen1 = session.submitAnswer("ans1");
    assertEquals(1L, gen1.generation());
    assertEquals(PromptSession.SessionState.AWAITING_INPUT, gen1.state());

    var gen2 = gen1.submitAnswer("ans2");
    assertEquals(2L, gen2.generation());
    assertEquals(PromptSession.SessionState.AWAITING_INPUT, gen2.state());

    var gen3 = gen2.submitAnswer("ans3");
    assertEquals(3L, gen3.generation());
    assertEquals(PromptSession.SessionState.COMPLETED, gen3.state());
  }

  @Test
  void sessionCancellationAdvancesGeneration() {
    var session = PromptSession.start("user-1", engine.getParser().parse("/cmd <p1>"));
    assertEquals(0L, session.generation());

    var cancelled = session.cancel(CancelReason.TIMEOUT);
    assertEquals(1L, cancelled.generation());
    assertEquals(PromptSession.SessionState.CANCELLED, cancelled.state());
    assertEquals(CancelReason.TIMEOUT, cancelled.cancelReason().orElseThrow());
  }
}
