package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
 * Issue #78 / #92: {@link ScreenManager#handleResult} must route dialog screen
 * results through the arity-aware batch path — a JSON preset dialog may submit
 * 0, 1, or N answers even though its parsed {@code PromptTag} is non-compound.
 *
 * <p>The manager detects dialog screens through the loadable UI-API
 * {@link DialogScreen} marker (whose {@code effectiveAnswerCount()} carries the
 * arity decided at open time), so the Paper-bound dialog screen class is never
 * loaded here. A fake {@link DialogScreen} is injected into the manager's
 * active-screens map and the private {@code handleResult} is driven via
 * reflection — no {@code NoClassDefFoundError} is involved in the assertions.
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
        engine.intercept(player, "/cmd <@d0> <x:next>");
        assertTrue(engine.getSession(player).isPresent());

        injectActiveScreen(player, new FakeDialogScreen(0));
        invokeHandleResult(player, ScreenResult.answer(AnswerEncoding.encode(List.of())));

        var session = engine.getSession(player).orElseThrow();
        assertEquals(1, session.currentIndex(),
                "zero-answer preset must advance the prompt ordinal");
        assertEquals(List.of(), session.answers(), "zero-answer preset adds no flat answers");

        SessionResult completed = engine.submit(player, "c").orElseThrow();
        // The dropped preset leaves the gap between the two tags ("/cmd <@d0> <x:next>"),
        // matching the existing empty-slot double-space precedent.
        assertEquals("/cmd  c", completed.assembledCommand(),
                "preset removed, next prompt answer occupies index 0");
        assertEquals(List.of("c"), completed.answers());
    }

    @Test
    void singleAnswerPresetRoutesThroughBatch() throws Exception {
        registerPreset("d1", confirmation(List.of(new DialogRow("A", InputType.TEXT, List.of()))));
        var player = createPlayer();
        engine.intercept(player, "/cmd <@d1> <x:next>");

        injectActiveScreen(player, new FakeDialogScreen(1));
        invokeHandleResult(player, ScreenResult.answer(AnswerEncoding.encode(List.of("a"))));

        var session = engine.getSession(player).orElseThrow();
        assertEquals(1, session.currentIndex());
        assertEquals(List.of("a"), session.answers());

        SessionResult completed = engine.submit(player, "c").orElseThrow();
        assertEquals("/cmd a c", completed.assembledCommand());
        assertEquals(List.of("a", "c"), completed.answers());
    }

    @Test
    void twoAnswerPresetRoutesAllAnswersThroughBatch() throws Exception {
        registerPreset(
                "d2",
                confirmation(List.of(
                        new DialogRow("A", InputType.TEXT, List.of()),
                        new DialogRow("B", InputType.TEXT, List.of()))));
        var player = createPlayer();
        engine.intercept(player, "/cmd <@d2> <x:next>");

        injectActiveScreen(player, new FakeDialogScreen(2));
        invokeHandleResult(player, ScreenResult.answer(AnswerEncoding.encode(List.of("a", "b"))));

        var session = engine.getSession(player).orElseThrow();
        assertEquals(1, session.currentIndex());
        assertEquals(List.of("a", "b"), session.answers());

        SessionResult completed = engine.submit(player, "c").orElseThrow();
        assertEquals("/cmd a b c", completed.assembledCommand(),
                "two preset answers join, then the next prompt answer follows at the next index");
        assertEquals(List.of("a", "b", "c"), completed.answers());
    }

    @Test
    void multiAnswerPresetWithPostCommandKeepsRealAnswerIndexes() throws Exception {
        registerPreset(
                "d3",
                confirmation(List.of(
                        new DialogRow("A", InputType.TEXT, List.of()),
                        new DialogRow("B", InputType.TEXT, List.of()),
                        new DialogRow("C", InputType.TEXT, List.of()))));
        var player = createPlayer();
        engine.intercept(player, "/cmd <@d3> <x:next> <!log {0} {1} {2} {3}>");

        injectActiveScreen(player, new FakeDialogScreen(3));
        invokeHandleResult(
                player, ScreenResult.answer(AnswerEncoding.encode(List.of("a", "b", "c"))));

        SessionResult completed = engine.submit(player, "d").orElseThrow();
        assertEquals("/cmd a b c d", completed.assembledCommand());
        assertEquals("log a b c d", completed.onCompleteCmds().get(0).command(),
                "PCM {N} references resolve against real answers only");
    }

    @Test
    void dialogCancelKeywordInsidePayloadCancelsSession() throws Exception {
        registerPreset(
                "d2c",
                confirmation(List.of(
                        new DialogRow("A", InputType.TEXT, List.of()),
                        new DialogRow("B", InputType.TEXT, List.of()))));
        var player = createPlayer();
        engine.intercept(player, "/cmd <@d2c> <x:next>");

        injectActiveScreen(player, new FakeDialogScreen(2));
        invokeHandleResult(player, ScreenResult.answer(AnswerEncoding.encode(List.of("cancel", "b"))));

        assertFalse(engine.hasActiveSession(player),
                "a cancel keyword in any decoded dialog answer must cancel the session");
        assertFalse(screenManager.hasActiveScreen(player));
    }

    /**
     * A malformed payload (wrong framing for the effective arity) is rejected
     * and the prompt is re-shown. In MockBukkit the re-show attempt fails
     * because Paper's dialog classes are absent, tearing the session down —
     * the routing decision (reject, then re-show) happened before that. The
     * decisive assertions are that the session was not half-consumed.
     */
    @Test
    void malformedDialogPayloadIsRejectedAndSessionDiscarded() throws Exception {
        registerPreset(
                "d2m",
                confirmation(List.of(
                        new DialogRow("A", InputType.TEXT, List.of()),
                        new DialogRow("B", InputType.TEXT, List.of()))));
        var player = createPlayer();
        engine.intercept(player, "/cmd <@d2m> <x:next>");

        injectActiveScreen(player, new FakeDialogScreen(2));

        // Unframed single answer is not a valid two-answer payload. The decode
        // rejection happens first; the subsequent re-show needs Paper dialog
        // classes MockBukkit does not provide (an Error, not a routing result).
        assertThrows(
                Error.class, () -> invokeHandleResult(player, ScreenResult.answer("a")));

        assertFalse(engine.hasActiveSession(player),
                "rejected payload must not leave a half-consumed session");
        assertFalse(screenManager.hasActiveScreen(player));
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

    @SuppressWarnings("unchecked")
    private void injectActiveScreen(Player player, InputScreen screen) throws Exception {
        var field = ScreenManager.class.getDeclaredField("activeScreens");
        field.setAccessible(true);
        ((Map<UUID, InputScreen>) field.get(screenManager))
                .put(player.getUniqueId(), screen);
    }

    private void invokeHandleResult(Player player, ScreenResult result) throws Exception {
        Method method =
                ScreenManager.class.getDeclaredMethod("handleResult", Player.class, ScreenResult.class);
        method.setAccessible(true);
        try {
            method.invoke(screenManager, player, result);
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
