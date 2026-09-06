package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptcore.SessionResult;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.preset.ActionButtonConfig;
import dev.cyr1en.promptpaper.preset.DialogBaseConfig;
import dev.cyr1en.promptpaper.preset.DialogPrompt;
import dev.cyr1en.promptpaper.preset.DialogRow;
import dev.cyr1en.promptpaper.preset.DialogType;
import dev.cyr1en.promptpaper.preset.DialogTypeConfig;
import dev.cyr1en.promptpaper.preset.InputType;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.screen.dialog.AnswerEncoding;
import dev.cyr1en.promptui.DialogScreen;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Issue #78 / #92: {@link ScreenManager#handleResult} must route dialog screen results through the
 * arity-aware batch path — a JSON preset dialog may submit 0, 1, or N answers even though its
 * parsed {@code PromptTag} is non-compound.
 *
 * <p>The manager detects dialog screens through the loadable UI-API {@link DialogScreen} marker
 * (whose {@code effectiveAnswerCount()} carries the arity decided at open time), so the Paper-bound
 * dialog screen class is never loaded here. A fake {@link DialogScreen} is injected into the
 * manager's active-screens map and the private {@code handleResult} is driven via reflection — no
 * {@code NoClassDefFoundError} is involved in the assertions.
 */
class DialogResultRoutingTest extends MockBukkitTest {

  private static final String CONFIRM = "Confirm";
  private static final String CANCEL = "Cancel";

  private PromptEngine engine;
  private PromptFactory factory;
  private ScreenManager screenManager;
  private PresetRegistry registry;

  @BeforeEach
  void setUpRouting() {
    registry = mock(PresetRegistry.class);
    when(plugin.getPresetRegistry()).thenReturn(registry);

    engine = new PromptEngine(plugin, scheduler);
    factory = new PromptFactory(plugin);
    screenManager = new ScreenManager(plugin, engine, factory, scheduler);
  }

  // ------------------------------------------------------------------
  // Preset dialog arity routing
  // ------------------------------------------------------------------

  @Test
  void zeroAnswerPresetConsumesPromptWithoutShiftingAnswers() throws Exception {
    registerPreset("d0", confirmation(List.of()));
    var player = createPlayer();
    engine.intercept(player, "/cmd <@d0> <next>");
    assertTrue(engine.getSession(player).isPresent());

    injectActiveScreen(player, new FakeDialogScreen(0));
    invokeHandleResult(player, ScreenResult.answer(AnswerEncoding.encode(List.of())));

    var session = engine.getSession(player).orElseThrow();
    assertEquals(1, session.currentIndex(), "zero-answer preset must advance the prompt ordinal");
    assertEquals(List.of(), session.answers(), "zero-answer preset adds no flat answers");

    SessionResult completed = engine.submit(player, "c").orElseThrow();
    // The dropped preset leaves the gap between the two tags ("/cmd <@d0> <next>"),
    // matching the existing empty-slot double-space precedent.
    assertEquals(
        "/cmd  \"c\"",
        completed.assembledCommand(),
        "preset removed, next prompt answer occupies index 0");
    assertEquals(List.of("c"), completed.answers());
  }

  @Test
  void singleAnswerPresetRoutesThroughBatch() throws Exception {
    registerPreset("d1", confirmation(List.of(new DialogRow("A", InputType.TEXT, List.of()))));
    var player = createPlayer();
    engine.intercept(player, "/cmd <@d1> <next>");

    injectActiveScreen(player, new FakeDialogScreen(1));
    invokeHandleResult(player, ScreenResult.answer(AnswerEncoding.encode(List.of("a"))));

    var session = engine.getSession(player).orElseThrow();
    assertEquals(1, session.currentIndex());
    assertEquals(List.of("a"), session.answers());

    SessionResult completed = engine.submit(player, "c").orElseThrow();
    assertEquals("/cmd \"a\" \"c\"", completed.assembledCommand());
    assertEquals(List.of("a", "c"), completed.answers());
  }

  @Test
  void twoAnswerPresetRoutesAllAnswersThroughBatch() throws Exception {
    registerPreset(
        "d2",
        confirmation(
            List.of(
                new DialogRow("A", InputType.TEXT, List.of()),
                new DialogRow("B", InputType.TEXT, List.of()))));
    var player = createPlayer();
    engine.intercept(player, "/cmd <@d2> <next>");

    injectActiveScreen(player, new FakeDialogScreen(2));
    invokeHandleResult(player, ScreenResult.answer(AnswerEncoding.encode(List.of("a", "b"))));

    var session = engine.getSession(player).orElseThrow();
    assertEquals(1, session.currentIndex());
    assertEquals(List.of("a", "b"), session.answers());

    SessionResult completed = engine.submit(player, "c").orElseThrow();
    assertEquals(
        "/cmd \"a\" \"b\" \"c\"",
        completed.assembledCommand(),
        "two preset answers join, then the next prompt answer follows at the next index");
    assertEquals(List.of("a", "b", "c"), completed.answers());
  }

  @Test
  void multiAnswerPresetWithPostCommandKeepsRealAnswerIndexes() throws Exception {
    registerPreset(
        "d3",
        confirmation(
            List.of(
                new DialogRow("A", InputType.TEXT, List.of()),
                new DialogRow("B", InputType.TEXT, List.of()),
                new DialogRow("C", InputType.TEXT, List.of()))));
    var player = createPlayer();
    engine.intercept(player, "/cmd <@d3> <next> <!log {0} {1} {2} {3}>");

    injectActiveScreen(player, new FakeDialogScreen(3));
    invokeHandleResult(player, ScreenResult.answer(AnswerEncoding.encode(List.of("a", "b", "c"))));

    SessionResult completed = engine.submit(player, "d").orElseThrow();
    assertEquals("/cmd \"a\" \"b\" \"c\" \"d\"", completed.assembledCommand());
    assertEquals(List.of("a", "b", "c", "d"), completed.answers());
    assertEquals(
        "log {0} {1} {2} {3}",
        completed.onCompleteCmds().get(0).command(),
        "PCM {N} references are preserved in SessionResult for deferred binding");
    assertArrayEquals(new int[] {0, 1, 2, 3}, completed.onCompleteCmds().get(0).answerIndices());
  }

  @Test
  void dialogCancelKeywordInsidePayloadCancelsSession() throws Exception {
    registerPreset(
        "d2c",
        confirmation(
            List.of(
                new DialogRow("A", InputType.TEXT, List.of()),
                new DialogRow("B", InputType.TEXT, List.of()))));
    var player = createPlayer();
    engine.intercept(player, "/cmd <@d2c> <next>");

    injectActiveScreen(player, new FakeDialogScreen(2));
    invokeHandleResult(player, ScreenResult.answer(AnswerEncoding.encode(List.of("cancel", "b"))));

    assertFalse(
        engine.hasActiveSession(player),
        "a cancel keyword in any decoded dialog answer must cancel the session");
    assertFalse(screenManager.hasActiveScreen(player));
  }

  /**
   * A malformed payload (wrong framing for the effective arity) is rejected and the prompt is
   * re-shown. With a Paper-free fake dialog screen factory injected, the replacement screen opens
   * cleanly, prompt remains unconsumed at current index, session stays active, and subsequent valid
   * payload advances.
   */
  @Test
  void malformedDialogPayloadIsRejectedAndPromptReshownWithoutError() throws Exception {
    registerPreset(
        "d2m",
        confirmation(
            List.of(
                new DialogRow("A", InputType.TEXT, List.of()),
                new DialogRow("B", InputType.TEXT, List.of()))));
    var player = createPlayer();

    var replacementScreensOpened = new java.util.concurrent.atomic.AtomicInteger();
    var customScreenManager =
        new ScreenManager(
            plugin,
            engine,
            factory,
            scheduler,
            (p, tag, ctx) -> {
              replacementScreensOpened.incrementAndGet();
              return new FakeDialogScreen(2);
            });

    engine.intercept(player, "/cmd <@d2m> <next>");
    var initialScreen = new FakeDialogScreen(2);
    injectActiveScreen(customScreenManager, player, initialScreen);

    // Unframed single answer is not a valid two-answer payload.
    // It is rejected and showPrompt() re-shows a replacement screen.
    invokeHandleResult(customScreenManager, player, ScreenResult.answer("a"));

    // Prompt remains unconsumed at index 0, session remains active, replacement screen is opened
    var session = engine.getSession(player).orElseThrow();
    assertEquals(
        0, session.currentIndex(), "Prompt must remain unconsumed after malformed payload");
    assertTrue(engine.hasActiveSession(player), "Session must remain active for retry");
    assertTrue(customScreenManager.hasActiveScreen(player), "Replacement screen must be active");
    assertEquals(1, replacementScreensOpened.get(), "Replacement screen must have been opened");

    // Now provide valid payload to verify session progresses normally
    invokeHandleResult(
        customScreenManager,
        player,
        ScreenResult.answer(AnswerEncoding.encode(List.of("valA", "valB"))));

    var sessionAfterValid = engine.getSession(player).orElseThrow();
    assertEquals(
        1,
        sessionAfterValid.currentIndex(),
        "Session must advance to next prompt after valid payload");
    assertEquals(List.of("valA", "valB"), sessionAfterValid.answers());
  }

  @Test
  void dialogAnswerOverLimitRejectsAndKeepsSessionActive() throws Exception {
    registerPreset(
        "d2len",
        confirmation(
            List.of(
                new DialogRow("A", InputType.TEXT, List.of()),
                new DialogRow("B", InputType.TEXT, List.of()))));
    when(config.maxAnswerLength()).thenReturn(10);
    var player = createPlayer();

    var customScreenManager =
        new ScreenManager(
            plugin, engine, factory, scheduler, (p, tag, ctx) -> new FakeDialogScreen(2));

    engine.intercept(player, "/cmd <@d2len>");
    injectActiveScreen(customScreenManager, player, new FakeDialogScreen(2));

    // Sub-answer 2 exceeds maxAnswerLength (10)
    invokeHandleResult(
        customScreenManager,
        player,
        ScreenResult.answer(AnswerEncoding.encode(List.of("short", "this_is_longer_than_10"))));

    verify(i18n)
        .get(
            eq("validation.answer_too_long"),
            same(player),
            any(dev.cyr1en.promptcore.i18n.Placeholder[].class));
    var session = engine.getSession(player).orElseThrow();
    assertEquals(
        0, session.currentIndex(), "Prompt must remain unconsumed after length validation failure");
    assertTrue(engine.hasActiveSession(player), "Session must remain active for retry");
    assertTrue(customScreenManager.hasActiveScreen(player), "Screen must be re-shown");
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private void registerPreset(String id, DialogPrompt prompt) {
    when(registry.getPrompt(id)).thenReturn(Optional.of(prompt));
  }

  private static DialogPrompt confirmation(List<DialogRow> inputs) {
    return new DialogPrompt(
        "dialog",
        "test-" + inputs.size(),
        "Title",
        new DialogBaseConfig(List.of(), inputs),
        new DialogTypeConfig(
            DialogType.CONFIRMATION,
            null,
            List.of(),
            null,
            null,
            new ActionButtonConfig(CONFIRM, null, null),
            new ActionButtonConfig(CANCEL, null, null)),
        true);
  }

  private void injectActiveScreen(Player player, InputScreen screen) throws Exception {
    injectActiveScreen(screenManager, player, screen);
  }

  @SuppressWarnings("unchecked")
  private void injectActiveScreen(ScreenManager sm, Player player, InputScreen screen)
      throws Exception {
    var field = ScreenManager.class.getDeclaredField("activeScreens");
    field.setAccessible(true);
    ((Map<UUID, InputScreen>) field.get(sm)).put(player.getUniqueId(), screen);
  }

  private void invokeHandleResult(Player player, ScreenResult result) throws Exception {
    invokeHandleResult(screenManager, player, result);
  }

  private void invokeHandleResult(ScreenManager sm, Player player, ScreenResult result)
      throws Exception {
    Method method =
        ScreenManager.class.getDeclaredMethod("handleResult", Player.class, ScreenResult.class);
    method.setAccessible(true);
    try {
      method.invoke(sm, player, result);
    } catch (java.lang.reflect.InvocationTargetException e) {
      var cause = e.getCause();
      if (cause instanceof RuntimeException re) throw re;
      if (cause instanceof Error err) throw err;
      throw e;
    }
  }

  /** A Paper-free {@link DialogScreen} carrying a fixed effective arity. */
  private static final class FakeDialogScreen implements DialogScreen {

    private final int effectiveAnswerCount;

    FakeDialogScreen(int effectiveAnswerCount) {
      this.effectiveAnswerCount = effectiveAnswerCount;
    }

    @Override
    public int effectiveAnswerCount() {
      return effectiveAnswerCount;
    }

    @Override
    public void onResult(Consumer<ScreenResult> callback) {}

    @Override
    public void open() {}

    @Override
    public void close() {}

    @Override
    public boolean isOpen() {
      return true;
    }
  }
}
