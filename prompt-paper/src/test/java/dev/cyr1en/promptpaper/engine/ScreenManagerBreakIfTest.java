package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationOutcome;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationPromptScreen;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationView;
import dev.cyr1en.promptpaper.screen.dialog.AnswerEncoding;
import dev.cyr1en.promptpaper.util.PluginLogger;
import dev.cyr1en.promptui.DialogScreen;
import dev.cyr1en.promptui.ScreenResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScreenManagerBreakIfTest extends MockBukkitTest {

  record CapturedCommand(String command, CommandSender sender) {}

  private final List<CapturedCommand> capturedCommands = new ArrayList<>();
  private final List<String> capturedLogMessages = new ArrayList<>();
  private final List<String> capturedWarnMessages = new ArrayList<>();

  private PromptEngine engine;
  private PromptFactory factory;
  private ScreenManager screenManager;

  private static class TestPluginLogger extends PluginLogger {
    private final List<String> allLogs;
    private final List<String> warnLogs;

    TestPluginLogger(CommandPrompter plugin, List<String> allLogs, List<String> warnLogs) {
      super(plugin);
      this.allLogs = allLogs;
      this.warnLogs = warnLogs;
    }

    @Override
    public void info(String msg, Object... args) {
      allLogs.add(dev.cyr1en.promptpaper.util.FormatUtil.safeFormat(msg, args));
      super.info(msg, args);
    }

    @Override
    public void warn(String msg, Object... args) {
      var formatted = dev.cyr1en.promptpaper.util.FormatUtil.safeFormat(msg, args);
      allLogs.add(formatted);
      warnLogs.add(formatted);
      super.warn(msg, args);
    }

    @Override
    public void err(String msg, Object... args) {
      allLogs.add(dev.cyr1en.promptpaper.util.FormatUtil.safeFormat(msg, args));
      super.err(msg, args);
    }

    @Override
    public void debug(String msg, Object... args) {
      allLogs.add(dev.cyr1en.promptpaper.util.FormatUtil.safeFormat(msg, args));
      super.debug(msg, args);
    }
  }

  @BeforeEach
  void setUpBreakIfTests() {
    capturedCommands.clear();
    capturedLogMessages.clear();
    capturedWarnMessages.clear();

    var testLogger = new TestPluginLogger(plugin, capturedLogMessages, capturedWarnMessages);
    when(plugin.getPluginLogger()).thenReturn(testLogger);

    var promptConfig = mock(dev.cyr1en.promptpaper.config.PromptConfig.class);
    when(promptConfig.getScreenMappings())
        .thenReturn(
            Map.of(
                "", ScreenType.CHAT,
                "a", ScreenType.CHAT,
                "s", ScreenType.CHAT,
                "p", ScreenType.CHAT,
                "d", ScreenType.CHAT));
    lenient().when(promptConfig.sendCancelText()).thenReturn(false);
    lenient().when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
    when(configLoader.getPromptConfig()).thenReturn(promptConfig);

    engine = new PromptEngine(plugin, scheduler);
    factory = new PromptFactory(plugin);
    screenManager =
        new ScreenManager(
            plugin,
            engine,
            factory,
            scheduler,
            (p, tag, ctx) -> {
              if (tag.isCompound() || "d".equals(tag.key())) {
                int count = tag.isCompound() ? ScreenManager.answerBearingTags(tag).size() : 1;
                return new FakeDialogScreen(count);
              }
              return factory.createFromTag(p, tag, ctx);
            });

    when(plugin.getEngine()).thenReturn(engine);
    when(plugin.getPromptFactory()).thenReturn(factory);
    when(plugin.getScreenManager()).thenReturn(screenManager);

    registerCapturingCommand("custom_test");
    registerCapturingCommand("custom_ban");
    registerCapturingCommand("custom_pvp");
    registerCapturingCommand("custom_dlg");
    registerCapturingCommand("on_complete");
    registerCapturingCommand("on_cancel");
    registerCapturingCommand("error_pcm");
  }

  private void registerCapturingCommand(String name) {
    var cmd =
        new Command(name) {
          @Override
          public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            var joined = args.length > 0 ? " " + String.join(" ", args) : "";
            capturedCommands.add(new CapturedCommand(commandLabel + joined, sender));
            return true;
          }
        };
    server.getCommandMap().register("commandprompter", cmd);
  }

  private long countCapturedCommands(String prefix) {
    return capturedCommands.stream().filter(c -> c.command().contains(prefix)).count();
  }

  // =========================================================================
  // Single prompt breakIf tests
  // =========================================================================

  @Test
  @DisplayName(
      "FLOW-06: Single prompt with breakIf=true cancels with MANUAL, dispatches cancel PCM, no primary dispatch")
  void singlePromptBreakIfTrueCancelsWithManualAndDispatchesCancelPCMs() {
    when(config.showCancelled()).thenReturn(true);
    var player = createPlayer("SingleBreakUser");
    screenManager.startSession(
        player,
        "/custom_test <a:Reason -breakIf:{0} equals \"skip\"> <!on_complete {0}> <!!on_cancel>");
    assertTrue(engine.hasActiveSession(player));
    assertTrue(screenManager.hasActiveScreen(player));

    // Submit "skip" matching the condition
    screenManager.handleChatInput(player, "skip");
    performTicks(20);

    // Session must be terminated and active screen unlinked
    assertFalse(engine.hasActiveSession(player), "Session must be terminated on breakIf=true");
    assertFalse(screenManager.hasActiveScreen(player), "Screen must be unlinked");

    // Primary command and on-complete PCMs must NOT be dispatched
    assertEquals(
        0,
        countCapturedCommands("custom_test"),
        "Primary command must NOT be dispatched on breakIf");
    assertEquals(0, countCapturedCommands("on_complete"), "On-complete PCM must NOT be dispatched");

    // On-cancel PCM MUST be dispatched exactly once
    assertEquals(
        1, countCapturedCommands("on_cancel"), "On-cancel PCM must be dispatched on breakIf");
  }

  @Test
  @DisplayName("FLOW-06: Single prompt with breakIf=false proceeds to submission and completion")
  void singlePromptBreakIfFalseProceedsAndCompletes() {
    var player = createPlayer("SingleProceedUser");
    screenManager.startSession(
        player,
        "/custom_test <a:Reason -breakIf:{0} equals \"skip\"> <!on_complete {0}> <!!on_cancel>");
    assertTrue(engine.hasActiveSession(player));

    // Submit "proceed" which does NOT match the condition
    screenManager.handleChatInput(player, "proceed");
    performTicks(20);

    assertFalse(engine.hasActiveSession(player), "Session must complete");
    assertFalse(screenManager.hasActiveScreen(player));

    // Primary command and on-complete PCMs MUST be dispatched
    assertEquals(1, countCapturedCommands("custom_test"), "Primary command must be dispatched");
    assertEquals(1, countCapturedCommands("on_complete"), "On-complete PCM must be dispatched");
    assertEquals(0, countCapturedCommands("on_cancel"), "On-cancel PCM must NOT be dispatched");
  }

  // =========================================================================
  // Multi-prompt previous + new indices breakIf tests
  // =========================================================================

  @Test
  @DisplayName(
      "FLOW-06: Multi-prompt condition referencing previous and candidate indices evaluates accurately")
  void multiPromptPreviousAndNewIndicesBreakIf() {
    var player = createPlayer("MultiBreakUser");
    screenManager.startSession(
        player,
        "/custom_ban <a:Target> <a:Reason -breakIf:{0} equals \"admin\" || {1} equals \"ignore\"> <!on_complete {0} {1}> <!!on_cancel {0}>");
    assertTrue(engine.hasActiveSession(player));

    // Step 1: Prompt 0 answered with "steve"
    screenManager.handleChatInput(player, "steve");
    performTicks(20);

    assertTrue(engine.hasActiveSession(player), "Session must still be active for prompt 1");
    var session = engine.getSession(player).orElseThrow();
    assertEquals(1, session.currentIndex());
    assertEquals(List.of("steve"), session.answers());

    // Step 2: Prompt 1 answered with "ignore" -> condition {0} equals "admin" || {1} equals
    // "ignore" evaluates with ["steve", "ignore"] -> true
    screenManager.handleChatInput(player, "ignore");
    performTicks(20);

    assertFalse(engine.hasActiveSession(player), "Session must terminate on breakIf=true");
    assertFalse(screenManager.hasActiveScreen(player));

    assertEquals(0, countCapturedCommands("custom_ban"), "Primary command must NOT be dispatched");
    assertEquals(0, countCapturedCommands("on_complete"), "On-complete PCM must NOT be dispatched");

    // Cancel PCM receives already accepted answers ("steve")
    assertEquals(1, countCapturedCommands("on_cancel"), "On-cancel PCM must be dispatched");
    var cancelCmd =
        capturedCommands.stream()
            .filter(
                c -> c.command().startsWith("on_cancel") || c.command().startsWith("/on_cancel"))
            .findFirst()
            .orElseThrow();
    assertTrue(
        cancelCmd.command().contains("steve"),
        "Cancel PCM must resolve accepted answers: " + cancelCmd.command());
    assertFalse(
        cancelCmd.command().contains("ignore"),
        "Cancel PCM must NOT contain unsubmitted candidate answer: " + cancelCmd.command());
  }

  @Test
  @DisplayName(
      "FLOW-06: Multi-prompt with previous answers evaluates to false and finishes successfully")
  void multiPromptBreakIfFalseProceedsToCompletion() {
    var player = createPlayer("MultiProceedUser");
    screenManager.startSession(
        player,
        "/custom_ban <a:Target> <a:Reason -breakIf:{0} equals \"admin\" || {1} equals \"ignore\"> <!on_complete {0} {1}>");
    assertTrue(engine.hasActiveSession(player));

    screenManager.handleChatInput(player, "steve");
    performTicks(20);
    screenManager.handleChatInput(player, "griefing");
    performTicks(20);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(1, countCapturedCommands("custom_ban"));
    assertEquals(1, countCapturedCommands("on_complete"));
  }

  // =========================================================================
  // Dialog and Compound batch breakIf tests
  // =========================================================================

  @Test
  @DisplayName(
      "FLOW-06: Compound dialog batch evaluated against combined answers before submission")
  void compoundDialogPromptBatchBreakIfTrue() {
    var player = createPlayer("CompoundBreakUser");
    screenManager.startSession(
        player,
        "/custom_dlg <d:text:A && d:text:B -breakIf:{0} equals \"x\" && {1} equals \"y\"> <!!on_cancel>");
    assertTrue(engine.hasActiveSession(player));

    var screen = (FakeDialogScreen) screenManager.getActiveScreen(player);
    assertNotNull(screen);
    screen.emit(AnswerEncoding.encode(List.of("x", "y")));
    performTicks(20);

    assertFalse(engine.hasActiveSession(player), "Session must be cancelled on compound breakIf");
    assertFalse(screenManager.hasActiveScreen(player));
    assertEquals(0, countCapturedCommands("custom_dlg"), "Primary command must NOT be dispatched");
    assertEquals(1, countCapturedCommands("on_cancel"), "On-cancel PCM must be dispatched");
  }

  @Test
  @DisplayName("FLOW-06: Compound dialog batch evaluated to false finishes cleanly")
  void compoundDialogPromptBatchBreakIfFalse() {
    var player = createPlayer("CompoundProceedUser");
    screenManager.startSession(
        player, "/custom_dlg <d:text:A && d:text:B -breakIf:{0} equals \"x\" && {1} equals \"y\">");
    assertTrue(engine.hasActiveSession(player));

    var screen = (FakeDialogScreen) screenManager.getActiveScreen(player);
    assertNotNull(screen);
    screen.emit(AnswerEncoding.encode(List.of("x", "z")));
    performTicks(20);

    assertFalse(engine.hasActiveSession(player), "Session must complete");
    assertEquals(1, countCapturedCommands("custom_dlg"), "Primary command must be dispatched");
  }

  @Test
  @DisplayName("FLOW-06: Compound prompt with previous single prompt answer and new indices")
  void compoundDialogPromptWithPreviousAnswersAndNewIndices() {
    var player = createPlayer("MixedCompoundBreakUser");
    screenManager.startSession(
        player,
        "/custom_dlg <a:First> <d:text:B && d:text:C -breakIf:{0} equals \"p0\" && {2} equals \"p2\"> <!!on_cancel>");
    assertTrue(engine.hasActiveSession(player));

    // Prompt 0 answered with "p0"
    screenManager.handleChatInput(player, "p0");
    performTicks(20);

    var session = engine.getSession(player).orElseThrow();
    assertEquals(1, session.currentIndex());
    assertEquals(List.of("p0"), session.answers());

    // Prompt 1 compound answered with ["p1", "p2"]
    var screen = (FakeDialogScreen) screenManager.getActiveScreen(player);
    assertNotNull(screen);
    screen.emit(AnswerEncoding.encode(List.of("p1", "p2")));
    performTicks(20);

    assertFalse(engine.hasActiveSession(player), "Session must terminate on breakIf");
    assertEquals(0, countCapturedCommands("custom_dlg"), "Primary command must NOT be dispatched");
    assertEquals(1, countCapturedCommands("on_cancel"), "Cancel PCM must dispatch");
  }

  // =========================================================================
  // Confirmation breakIf tests
  // =========================================================================

  @Test
  @DisplayName("FLOW-06: Value-mode confirmation prompt evaluates breakIf condition")
  void confirmationPromptValueModeBreakIf() {
    var player = createPlayer("ConfBreakUser");

    var view = new TestConfirmationView();
    var screen =
        new ConfirmationPromptScreen(
            List.of(view), "true", "false", false, null, true); // valueMode = true
    var customManager = new ScreenManager(plugin, engine, factory, scheduler, (p, t, c) -> screen);

    customManager.startSession(
        player, "/custom_pvp <c:Enable? -value -breakIf:{0} equals \"true\"> <!!on_cancel>");
    assertTrue(engine.hasActiveSession(player));

    // Emit Confirmed (candidate is "true")
    view.emit(ConfirmationOutcome.confirmed());
    performTicks(20);

    assertFalse(engine.hasActiveSession(player), "Session must terminate on breakIf=true");
    assertFalse(customManager.hasActiveScreen(player));
    assertEquals(0, countCapturedCommands("custom_pvp"), "Primary command must NOT be dispatched");
    assertEquals(1, countCapturedCommands("on_cancel"), "Cancel PCM must be dispatched");
  }

  @Test
  @DisplayName("FLOW-06: Value-mode confirmation prompt breakIf=false submits value")
  void confirmationPromptValueModeBreakIfFalse() {
    var player = createPlayer("ConfProceedUser");

    var view = new TestConfirmationView();
    var screen =
        new ConfirmationPromptScreen(
            List.of(view), "true", "false", false, null, true); // valueMode = true
    var customManager = new ScreenManager(plugin, engine, factory, scheduler, (p, t, c) -> screen);

    customManager.startSession(
        player, "/custom_pvp <c:Enable? -value -breakIf:{0} equals \"false\">");
    assertTrue(engine.hasActiveSession(player));

    // Emit Confirmed (candidate is "true", condition is {0} equals "false" -> false)
    view.emit(ConfirmationOutcome.confirmed());
    performTicks(20);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(1, countCapturedCommands("custom_pvp"), "Primary command must be dispatched");
  }

  // =========================================================================
  // Error / Fail-closed runtime condition evaluation tests
  // =========================================================================

  @Test
  @DisplayName(
      "FLOW-06: Unresolvable runtime answer index fails closed with CancelReason.ERROR and bounded diagnostic")
  void unresolvableRuntimeIndexFailsClosedError() {
    var player = createPlayer("UnresolvableUser");
    // Condition references index 5, which does not exist in a single 1-answer prompt
    screenManager.startSession(
        player, "/custom_test <a:Input -breakIf:{5} equals \"test\"> <!!error_pcm>");
    assertTrue(engine.hasActiveSession(player));

    screenManager.handleChatInput(player, "hello");
    performTicks(20);

    assertFalse(engine.hasActiveSession(player), "Session must fail closed on evaluation error");
    assertFalse(screenManager.hasActiveScreen(player));
    assertEquals(
        0, countCapturedCommands("custom_test"), "Primary command must NOT be dispatched on error");
    assertEquals(1, countCapturedCommands("error_pcm"), "Error cancel PCM must be dispatched");

    // Verify warning diagnostic is logged
    assertTrue(
        capturedWarnMessages.stream()
            .anyMatch(m -> m.contains("BreakIf evaluation failed for UnresolvableUser")),
        "Warning diagnostic must be logged for breakIf evaluation failure");
  }

  @Test
  @DisplayName(
      "FLOW-06: Type mismatch during runtime evaluation fails closed with CancelReason.ERROR")
  void typeMismatchRuntimeErrorFailsClosedError() {
    var player = createPlayer("TypeMismatchUser");
    // Numeric comparison on a non-numeric string answer
    screenManager.startSession(player, "/custom_test <a:Input -breakIf:{0} <= 10> <!!error_pcm>");
    assertTrue(engine.hasActiveSession(player));

    screenManager.handleChatInput(player, "not_a_number");
    performTicks(20);

    assertFalse(engine.hasActiveSession(player), "Session must fail closed on type mismatch");
    assertEquals(
        0, countCapturedCommands("custom_test"), "Primary command must NOT be dispatched on error");
    assertEquals(1, countCapturedCommands("error_pcm"), "Error cancel PCM must be dispatched");
  }

  // =========================================================================
  // Validator rejection before breakIf
  // =========================================================================

  @Test
  @DisplayName(
      "FLOW-06: Validation failure does not evaluate breakIf condition and re-shows prompt")
  void validationFailureDoesNotEvaluateBreakIf() {
    var player = createPlayer("ValidationUser");
    // Type constraint INTEGER: "abc" will fail validation
    screenManager.startSession(player, "/custom_test <a:Age -int -breakIf:{0} == 0> <!!on_cancel>");
    assertTrue(screenManager.hasChatScreen(player));

    screenManager.handleChatInput(player, "abc");
    performTicks(20);

    // Session must still be active awaiting input
    assertTrue(
        engine.hasActiveSession(player),
        "Session must remain active when validator rejects answer");
    assertTrue(screenManager.hasChatScreen(player), "Prompt must be re-shown");
    assertEquals(0, countCapturedCommands("custom_test"), "No primary command");
    assertEquals(0, countCapturedCommands("on_cancel"), "No cancel PCM");
  }

  // =========================================================================
  // Stale Attempt / Duplicate Callback Verification
  // =========================================================================

  @Test
  @DisplayName(
      "FLOW-06: Duplicate callback is discarded by stale check and does not re-evaluate breakIf")
  void duplicateCallbackDoesNotEvaluateTwice() throws Exception {
    var player = createPlayer("DuplicateUser");
    screenManager.startSession(
        player, "/custom_test <a:Reason -breakIf:{0} equals \"skip\"> <!!on_cancel>");
    assertTrue(engine.hasActiveSession(player));

    var session = engine.getSession(player).orElseThrow();
    var handleMethod =
        ScreenManager.class.getDeclaredMethod(
            "handleResult", Player.class, ScreenResult.class, long.class);
    handleMethod.setAccessible(true);

    // First callback triggers breakIf
    handleMethod.invoke(screenManager, player, ScreenResult.answer("skip"), session.generation());
    performTicks(20);

    assertEquals(1, countCapturedCommands("on_cancel"), "Cancel PCM dispatched once");

    // Duplicate stale callback
    handleMethod.invoke(screenManager, player, ScreenResult.answer("skip"), session.generation());
    performTicks(20);

    // Cancel PCM count must remain 1, no duplicate actions
    assertEquals(1, countCapturedCommands("on_cancel"), "Stale duplicate callback must be ignored");
    assertEquals(0, countCapturedCommands("custom_test"), "Primary command must NOT be dispatched");
  }

  // =========================================================================
  // Test View Helper
  // =========================================================================

  private static class TestConfirmationView implements ConfirmationView {
    protected Consumer<ConfirmationOutcome> callback;
    protected Consumer<Throwable> failureCallback;
    private boolean open;

    @Override
    public void open(Consumer<ConfirmationOutcome> callback) {
      this.callback = callback;
      this.open = true;
    }

    @Override
    public void close() {
      this.open = false;
      this.callback = null;
      this.failureCallback = null;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void onOpenFailure(Consumer<Throwable> failureCallback) {
      this.failureCallback = failureCallback;
    }

    public void emit(ConfirmationOutcome outcome) {
      if (callback != null) {
        callback.accept(outcome);
      }
    }

    public void emitFailure(Throwable t) {
      if (failureCallback != null) {
        failureCallback.accept(t);
      }
    }
  }

  /** A Paper-free {@link DialogScreen} carrying a fixed effective arity. */
  private static final class FakeDialogScreen implements DialogScreen {

    private final int effectiveAnswerCount;
    private Consumer<ScreenResult> callback;
    private boolean open;

    FakeDialogScreen(int effectiveAnswerCount) {
      this.effectiveAnswerCount = effectiveAnswerCount;
    }

    @Override
    public int effectiveAnswerCount() {
      return effectiveAnswerCount;
    }

    @Override
    public void onResult(Consumer<ScreenResult> callback) {
      this.callback = callback;
    }

    @Override
    public void open() {
      this.open = true;
    }

    @Override
    public void close() {
      this.open = false;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    public void emit(String answer) {
      if (callback != null) {
        callback.accept(ScreenResult.answer(answer));
      }
    }
  }
}
