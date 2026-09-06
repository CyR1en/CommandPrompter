package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.plan.ExecutionPlanAdapter;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verification for Phase 5.3 Gate remediation: - Atomic compare-and-remove inception artifact
 * consumption with exact incarnation verification. - PromptEngine.cancel fail-closed behavior on
 * missing/mismatched artifacts. - Pinned captured PresetSnapshot dispatch even after live
 * configuration reload. - ScreenManager.handleSubmitted fail-closed behavior on missing/mismatched
 * artifacts.
 */
class SessionInceptionArtifactSecurityTest extends MockBukkitTest {

  record Captured(String command, CommandSender sender) {}

  private final List<Captured> captured = new ArrayList<>();
  private final Map<String, PostCommand> presetCommands = new HashMap<>();
  private PresetRegistry registry;
  private HookContainer hookContainer;
  private PromptEngine engine;
  private PromptFactory factory;
  private ScreenManager screenManager;

  @BeforeEach
  void setUp() {
    registry = mock(PresetRegistry.class);
    hookContainer = mock(HookContainer.class);
    presetCommands.clear();
    captured.clear();

    when(plugin.getPresetRegistry()).thenReturn(registry);
    when(plugin.getHookContainer()).thenReturn(hookContainer);
    when(hookContainer.getHook(PapiHook.class)).thenReturn(Optional.empty());

    when(registry.snapshot())
        .thenAnswer(
            inv -> new PresetSnapshot(Map.of(), new HashMap<>(presetCommands), Map.of(), Map.of()));
    when(registry.getSnapshot())
        .thenAnswer(
            inv -> new PresetSnapshot(Map.of(), new HashMap<>(presetCommands), Map.of(), Map.of()));
    when(registry.getPostCommand(anyString()))
        .thenAnswer(inv -> Optional.ofNullable(presetCommands.get(inv.getArgument(0))));

    promptConfig = mock(PromptConfig.class);
    when(promptConfig.getScreenMappings())
        .thenReturn(Map.of("", ScreenType.CHAT, "a", ScreenType.CHAT));
    lenient().when(promptConfig.sendCancelText()).thenReturn(false);
    lenient().when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
    lenient().when(promptConfig.textCancelMessage()).thenReturn("");
    lenient().when(promptConfig.textCancelHoverMessage()).thenReturn("");
    when(configLoader.getPromptConfig()).thenReturn(promptConfig);

    engine = new PromptEngine(plugin, scheduler);
    factory = new PromptFactory(plugin);
    screenManager = new ScreenManager(plugin, engine, factory, scheduler);

    registerCapturingCommand("say");
    registerCapturingCommand("log");
    registerCapturingCommand("primary");
    registerCapturingCommand("cancel_cmd");
  }

  private void registerCapturingCommand(String name) {
    var cmd =
        new Command(name) {
          @Override
          public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            var joined = args.length > 0 ? " " + String.join(" ", args) : "";
            captured.add(new Captured(commandLabel + joined, sender));
            return true;
          }
        };
    server.getCommandMap().register(name, "minecraft", cmd);
  }

  private void registerPreset(PostCommand def) {
    presetCommands.put(def.id(), def);
  }

  // =========================================================================
  // PromptEngine.cancel tests
  // =========================================================================

  @Test
  @DisplayName(
      "cancel with missing artifacts fails closed: no PCM dispatched, cancellation completes")
  void cancelWithMissingArtifactsFailsClosedNoPcmDispatched() {
    var player = createPlayer("MissingArtifactUser");
    engine.intercept(player, "/cmd <a:why> <!!say cancelled_pcm>");
    assertTrue(engine.hasActiveSession(player));

    // Remove artifacts behind engine's back (simulating missing state)
    var session = engine.getSession(player).orElseThrow();
    engine.takeInceptionArtifacts(player.getUniqueId(), session.incarnation());

    // Cancel session
    engine.cancel(player, CancelReason.MANUAL);
    performTicks(20);

    // Session is cancelled
    assertFalse(engine.hasActiveSession(player), "Session cancellation itself must complete");
    // No PCM dispatched because artifacts were absent
    assertEquals(0, captured.size(), "No cancel PCM must be dispatched when artifacts are missing");
  }

  @Test
  @DisplayName(
      "cancel with mismatched incarnation fails closed and does not consume newer artifacts")
  void cancelWithMismatchedIncarnationFailsClosedAndPreservesNewerArtifacts() {
    var player = createPlayer("MismatchedArtifactUser");
    engine.intercept(player, "/cmd <a:why> <!!say cancelled_pcm>");
    assertTrue(engine.hasActiveSession(player));

    var session = engine.getSession(player).orElseThrow();
    long oldIncarnation = session.incarnation();
    long newerIncarnation = oldIncarnation + 100;

    // Simulate newer session's inception artifacts being stored
    var newerParsed = engine.getParser().parse("/cmd <a:newer> <!!say newer_pcm>");
    var newerArtifacts =
        new SessionInceptionArtifacts(
            newerIncarnation,
            0L,
            ExecutionPlanAdapter.fromParsedCommand(newerParsed),
            PresetSnapshot.empty(),
            List.copyOf(newerParsed.postCmds()));

    // Directly put newer artifacts in map (simulating a race where a newer session's artifacts are
    // in map)
    var artifactsMapField = getArtifactsMap();
    artifactsMapField.put(player.getUniqueId(), newerArtifacts);

    // Cancel the older session
    engine.cancel(player, CancelReason.MANUAL);
    performTicks(20);

    // Cancelled old session must NOT have dispatched
    assertEquals(0, captured.size(), "Mismatched artifacts must fail closed with no PCM dispatch");
    // Newer artifacts must STILL be present and untouched!
    var remainingArtifacts = engine.getInceptionArtifacts(player.getUniqueId());
    assertTrue(remainingArtifacts.isPresent());
    assertEquals(
        newerIncarnation,
        remainingArtifacts.get().incarnation(),
        "Newer session artifacts must not be consumed by stale cancellation");
  }

  @Test
  @DisplayName("cancel with mismatched generation fails closed and preserves newer artifacts")
  void cancelWithMismatchedGenerationFailsClosedAndPreservesNewerArtifacts() {
    var player = createPlayer("MismatchedGenCancelUser");
    engine.intercept(player, "/cmd <a:why> <!!say cancelled_pcm>");
    assertTrue(engine.hasActiveSession(player));

    var session = engine.getSession(player).orElseThrow();
    long incarnation = session.incarnation();
    long newerGeneration = session.generation() + 10L;

    // Put newer generation artifacts with same incarnation
    var newerParsed = engine.getParser().parse("/cmd <a:newer> <!!say newer_pcm>");
    var newerArtifacts =
        new SessionInceptionArtifacts(
            incarnation,
            newerGeneration,
            ExecutionPlanAdapter.fromParsedCommand(newerParsed),
            PresetSnapshot.empty(),
            List.copyOf(newerParsed.postCmds()));
    getArtifactsMap().put(player.getUniqueId(), newerArtifacts);

    // Cancel session (which has session.generation() != newerGeneration)
    engine.cancel(player, CancelReason.MANUAL);
    performTicks(20);

    // Cancelled session must NOT have dispatched
    assertEquals(
        0,
        captured.size(),
        "Mismatched generation artifacts must fail closed with no PCM dispatch");
    // Newer artifacts must STILL be present and untouched
    var remainingArtifacts = engine.getInceptionArtifacts(player.getUniqueId());
    assertTrue(remainingArtifacts.isPresent());
    assertEquals(
        newerGeneration,
        remainingArtifacts.get().generation(),
        "Newer generation artifacts must not be consumed by stale generation cancellation");
  }

  @Test
  @DisplayName("cancel with exact generation succeeds and consumes artifacts")
  void cancelWithExactGenerationSucceeds() {
    var player = createPlayer("ExactGenCancelUser");
    engine.intercept(player, "/cmd <a:why> <!!say exact_gen_cancel_pcm>");
    assertTrue(engine.hasActiveSession(player));

    engine.cancel(player, CancelReason.MANUAL);
    performTicks(20);

    assertEquals(1, captured.size(), "Exact generation cancel must dispatch PCM");
    assertEquals("say exact_gen_cancel_pcm", captured.get(0).command());
    assertTrue(
        engine.getInceptionArtifacts(player.getUniqueId()).isEmpty(),
        "Exact generation cancel must consume artifacts");
  }

  @Test
  @DisplayName("stale old cancellation cannot consume newer session artifacts")
  void staleCancellationCannotConsumeNewerSessionArtifacts() {
    var player = createPlayer("StaleCancelUser");
    engine.intercept(player, "/cmd <a:first> <!!say cancel_first>");
    var firstSession = engine.getSession(player).orElseThrow();
    long firstIncarnation = firstSession.incarnation();

    // Discard first session and start second session
    engine.discard(player.getUniqueId());
    engine.intercept(player, "/cmd <a:second> <!!say cancel_second>");
    var secondSession = engine.getSession(player).orElseThrow();
    long secondIncarnation = secondSession.incarnation();
    assertNotEquals(firstIncarnation, secondIncarnation);

    // Attempt to take artifacts with first (stale) incarnation
    var staleTaken = engine.takeInceptionArtifacts(player.getUniqueId(), firstIncarnation);
    assertTrue(staleTaken.isEmpty(), "Stale incarnation must not match newer artifacts");

    // Second session's artifacts are still safe in place
    var currentArtifacts = engine.getInceptionArtifacts(player.getUniqueId(), secondIncarnation);
    assertTrue(currentArtifacts.isPresent());
    assertEquals(secondIncarnation, currentArtifacts.get().incarnation());

    // Cancel second session properly
    engine.cancel(player, CancelReason.MANUAL);
    performTicks(20);

    assertEquals(1, captured.size());
    assertEquals("say cancel_second", captured.get(0).command());
  }

  @Test
  @DisplayName(
      "exact match cancel dispatches expected PCMs from captured old snapshot even after reload")
  void cancelDispatchesFromCapturedOldSnapshotEvenAfterReload() {
    var def =
        new PostCommand(
            "cancel_hook",
            "say old_snapshot_cancel",
            ExecutionPolicy.ON_CANCEL,
            ExecuteAs.CONSOLE,
            0);
    registerPreset(def);

    var player = createPlayer("SnapshotReloadUser");
    player.setOp(true);
    engine.intercept(player, "/cmd <a:why> <!!@cancel_hook>");

    // Now mutate/reload the live preset registry
    presetCommands.put(
        "cancel_hook",
        new PostCommand(
            "cancel_hook",
            "say NEW_RELOADED_CANCEL",
            ExecutionPolicy.ON_CANCEL,
            ExecuteAs.CONSOLE,
            0));

    // Cancel the session
    engine.cancel(player, CancelReason.MANUAL);
    performTicks(20);

    assertEquals(1, captured.size());
    assertEquals(
        "say old_snapshot_cancel",
        captured.get(0).command(),
        "Cancel PCM must be resolved from immutable snapshot captured at inception, not live registry");
  }

  // =========================================================================
  // ScreenManager.handleSubmitted tests
  // =========================================================================

  @Test
  @DisplayName(
      "ScreenManager handleSubmitted with missing artifacts fails closed: no primary, no PCM")
  void screenManagerHandleSubmittedMissingArtifactsFailsClosed() {
    var player = createPlayer("SMFailClosedUser");
    screenManager.startSession(player, "/primary Steve <a:Reason> <!say post_pcm>");
    assertTrue(engine.hasActiveSession(player));

    var session = engine.getSession(player).orElseThrow();
    long inc = session.incarnation();

    // Remove inception artifacts
    engine.takeInceptionArtifacts(player.getUniqueId(), inc);

    // Answer prompt through ScreenManager
    screenManager.handleChatInput(player, "Griefing");
    performTicks(20);

    assertEquals(
        0,
        captured.size(),
        "Neither primary command nor PCM must be dispatched when artifacts are missing");
  }

  @Test
  @DisplayName(
      "ScreenManager handleSubmitted with mismatched incarnation fails closed and preserves newer artifacts")
  void screenManagerHandleSubmittedMismatchedIncarnationFailsClosedAndPreservesNewer() {
    var player = createPlayer("SMMismatchUser");
    screenManager.startSession(player, "/primary Steve <a:Reason> <!say post_first>");
    assertTrue(engine.hasActiveSession(player));

    var session = engine.getSession(player).orElseThrow();
    long firstInc = session.incarnation();
    long newerInc = firstInc + 50;

    // Put newer artifacts
    var newerParsed = engine.getParser().parse("/primary Steve <a:Reason> <!say post_newer>");
    var newerArtifacts =
        new SessionInceptionArtifacts(
            newerInc,
            0L,
            ExecutionPlanAdapter.fromParsedCommand(newerParsed),
            PresetSnapshot.empty(),
            List.copyOf(newerParsed.postCmds()));
    getArtifactsMap().put(player.getUniqueId(), newerArtifacts);

    // Answer chat input for the first session (which will finish with firstInc)
    screenManager.handleChatInput(player, "Griefing");
    performTicks(20);

    assertEquals(0, captured.size(), "Stale completion must fail closed without dispatch");
    var remainingArtifacts = engine.getInceptionArtifacts(player.getUniqueId());
    assertTrue(remainingArtifacts.isPresent());
    assertEquals(
        newerInc,
        remainingArtifacts.get().incarnation(),
        "Newer artifacts must remain intact and not consumed by stale completion");
  }

  @Test
  @DisplayName(
      "ScreenManager handleSubmitted with mismatched generation fails closed and preserves newer artifacts")
  void screenManagerHandleSubmittedMismatchedGenerationFailsClosedAndPreservesNewer() {
    var player = createPlayer("SMGenMismatchUser");
    screenManager.startSession(player, "/primary Steve <a:Reason> <!say post_first>");
    assertTrue(engine.hasActiveSession(player));

    var session = engine.getSession(player).orElseThrow();
    long inc = session.incarnation();
    long newerGen = session.generation() + 5L;

    // Put newer generation artifacts with same incarnation
    var newerParsed = engine.getParser().parse("/primary Steve <a:Reason> <!say post_newer>");
    var newerArtifacts =
        new SessionInceptionArtifacts(
            inc,
            newerGen,
            ExecutionPlanAdapter.fromParsedCommand(newerParsed),
            PresetSnapshot.empty(),
            List.copyOf(newerParsed.postCmds()));
    getArtifactsMap().put(player.getUniqueId(), newerArtifacts);

    // Answer chat input for the session (which completes with expectedGeneration = 0)
    screenManager.handleChatInput(player, "Griefing");
    performTicks(20);

    assertEquals(
        0,
        captured.size(),
        "Stale generation completion must fail closed without primary or PCM dispatch");
    var remainingArtifacts = engine.getInceptionArtifacts(player.getUniqueId());
    assertTrue(remainingArtifacts.isPresent());
    assertEquals(
        newerGen,
        remainingArtifacts.get().generation(),
        "Newer generation artifacts must remain intact and not consumed by stale generation completion");
  }

  @Test
  @DisplayName(
      "ScreenManager handleSubmitted with exact generation succeeds and consumes artifacts")
  void screenManagerHandleSubmittedExactGenerationSucceeds() {
    var player = createPlayer("SMExactGenUser");
    screenManager.startSession(player, "/primary Steve <a:Reason> <!say post_exact>");
    assertTrue(engine.hasActiveSession(player));

    screenManager.handleChatInput(player, "Griefing");
    performTicks(20);

    var primaryCmd = captured.stream().filter(c -> c.command().startsWith("primary")).findFirst();
    assertTrue(
        primaryCmd.isPresent(),
        "Primary command must be dispatched on exact generation completion");

    var pcmCmd = captured.stream().filter(c -> c.command().startsWith("say")).findFirst();
    assertTrue(pcmCmd.isPresent(), "PCM must be dispatched on exact generation completion");
    assertEquals("say post_exact", pcmCmd.get().command());

    assertTrue(
        engine.getInceptionArtifacts(player.getUniqueId()).isEmpty(),
        "Exact generation completion must consume inception artifacts");
  }

  @Test
  @DisplayName(
      "ScreenManager completion dispatches from captured inception snapshot even after reload")
  void screenManagerCompletionDispatchesFromCapturedInceptionSnapshotEvenAfterReload() {
    var def =
        new PostCommand(
            "complete_hook",
            "say old_snapshot_complete",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            0);
    registerPreset(def);

    var player = createPlayer("SMReloadUser");
    player.setOp(true);

    screenManager.startSession(player, "/primary Steve <a:Reason> <!@complete_hook>");
    assertTrue(engine.hasActiveSession(player));

    // Live configuration reload occurs: preset definition changes
    presetCommands.put(
        "complete_hook",
        new PostCommand(
            "complete_hook",
            "say NEW_RELOADED_COMPLETE",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            0));

    // Submit answer through screen manager
    screenManager.handleChatInput(player, "Griefing");
    performTicks(20);

    // Primary command dispatched
    var primaryCmd = captured.stream().filter(c -> c.command().startsWith("primary")).findFirst();
    assertTrue(primaryCmd.isPresent(), "Primary command must be dispatched");

    // PCM dispatched from snapshot v1
    var pcmCmd = captured.stream().filter(c -> c.command().startsWith("say")).findFirst();
    assertTrue(pcmCmd.isPresent(), "PCM must be dispatched");
    assertEquals(
        "say old_snapshot_complete",
        pcmCmd.get().command(),
        "PCM must resolve against captured inception snapshot even after reload");
  }

  // =========================================================================
  // Non-terminal submit generation advancement & artifact security tests
  // =========================================================================

  @Test
  @DisplayName("submit non-final with exact tokens advances artifact generation")
  void submitNonFinalWithExactTokensAdvancesArtifactGeneration() {
    var player = createPlayer("ExactSubmitUser");
    engine.intercept(player, "/cmd <a:first> <a:second> <!say done>");
    assertTrue(engine.hasActiveSession(player));

    var initialSession = engine.getSession(player).orElseThrow();
    long inc = initialSession.incarnation();
    assertEquals(0L, initialSession.generation());

    var initialArtifact = engine.getInceptionArtifacts(player.getUniqueId()).orElseThrow();
    assertEquals(inc, initialArtifact.incarnation());
    assertEquals(0L, initialArtifact.generation());

    var result = engine.submit(player, "ans1");
    assertTrue(result.isEmpty(), "Non-final submit must not complete the session");

    var advancedSession = engine.getSession(player).orElseThrow();
    assertEquals(inc, advancedSession.incarnation());
    assertEquals(1L, advancedSession.generation());

    var advancedArtifact = engine.getInceptionArtifacts(player.getUniqueId()).orElseThrow();
    assertEquals(inc, advancedArtifact.incarnation());
    assertEquals(
        1L, advancedArtifact.generation(), "Artifact generation must advance on exact token match");
    assertEquals(initialArtifact.planDefinition(), advancedArtifact.planDefinition());
    assertEquals(initialArtifact.presetSnapshot(), advancedArtifact.presetSnapshot());
    assertEquals(initialArtifact.originalPostCommands(), advancedArtifact.originalPostCommands());
  }

  @Test
  @DisplayName("submit non-final with mismatched incarnation preserves existing artifact unchanged")
  void submitNonFinalWithMismatchedIncarnationPreservesExistingArtifactUnchanged() {
    var player = createPlayer("MismatchIncSubmitUser");
    engine.intercept(player, "/cmd <a:first> <a:second> <!say done>");
    assertTrue(engine.hasActiveSession(player));

    var session = engine.getSession(player).orElseThrow();
    long sessionInc = session.incarnation();
    long newerInc = sessionInc + 100L;

    var newerParsed = engine.getParser().parse("/cmd <a:newer> <!say newer_done>");
    var newerArtifact =
        new SessionInceptionArtifacts(
            newerInc,
            0L,
            ExecutionPlanAdapter.fromParsedCommand(newerParsed),
            PresetSnapshot.empty(),
            List.copyOf(newerParsed.postCmds()));
    getArtifactsMap().put(player.getUniqueId(), newerArtifact);

    var result = engine.submit(player, "ans1");
    assertTrue(result.isEmpty());

    var currentArtifact = getArtifactsMap().get(player.getUniqueId());
    assertSame(
        newerArtifact,
        currentArtifact,
        "Mismatched incarnation must preserve preinstalled artifact unchanged");
    assertEquals(newerInc, currentArtifact.incarnation());
    assertEquals(0L, currentArtifact.generation());
  }

  @Test
  @DisplayName("submit non-final with mismatched generation preserves existing artifact unchanged")
  void submitNonFinalWithMismatchedGenerationPreservesExistingArtifactUnchanged() {
    var player = createPlayer("MismatchGenSubmitUser");
    engine.intercept(player, "/cmd <a:first> <a:second> <!say done>");
    assertTrue(engine.hasActiveSession(player));

    var session = engine.getSession(player).orElseThrow();
    long sessionInc = session.incarnation();
    long mismatchedGen = 5L;

    var mismatchedArtifact =
        new SessionInceptionArtifacts(
            sessionInc,
            mismatchedGen,
            ExecutionPlanAdapter.fromParsedCommand(
                engine.getParser().parse("/cmd <a:first> <a:second> <!say done>")),
            PresetSnapshot.empty());
    getArtifactsMap().put(player.getUniqueId(), mismatchedArtifact);

    var result = engine.submit(player, "ans1");
    assertTrue(result.isEmpty());

    var currentArtifact = getArtifactsMap().get(player.getUniqueId());
    assertSame(
        mismatchedArtifact,
        currentArtifact,
        "Mismatched generation must preserve preinstalled artifact unchanged");
    assertEquals(sessionInc, currentArtifact.incarnation());
    assertEquals(mismatchedGen, currentArtifact.generation());
  }

  @Test
  @DisplayName("submitAnswers non-final with exact tokens advances artifact generation")
  void submitAnswersNonFinalWithExactTokensAdvancesArtifactGeneration() {
    var player = createPlayer("ExactSubmitAnswersUser");
    engine.intercept(player, "/cmd <d:text:A && d:text:B> <a:second> <!say done>");
    assertTrue(engine.hasActiveSession(player));

    var initialSession = engine.getSession(player).orElseThrow();
    long inc = initialSession.incarnation();
    assertEquals(0L, initialSession.generation());

    var result = engine.submitAnswers(player, List.of("ans1", "ans2"));
    assertTrue(result.isEmpty());

    var advancedSession = engine.getSession(player).orElseThrow();
    assertEquals(inc, advancedSession.incarnation());
    assertEquals(1L, advancedSession.generation());

    var advancedArtifact = engine.getInceptionArtifacts(player.getUniqueId()).orElseThrow();
    assertEquals(inc, advancedArtifact.incarnation());
    assertEquals(1L, advancedArtifact.generation());
  }

  @Test
  @DisplayName(
      "submitAnswers non-final with mismatched incarnation preserves existing artifact unchanged")
  void submitAnswersNonFinalWithMismatchedIncarnationPreservesExistingArtifactUnchanged() {
    var player = createPlayer("MismatchIncSubmitAnswersUser");
    engine.intercept(player, "/cmd <d:text:A && d:text:B> <a:second> <!say done>");
    assertTrue(engine.hasActiveSession(player));

    var session = engine.getSession(player).orElseThrow();
    long sessionInc = session.incarnation();
    long newerInc = sessionInc + 50L;

    var newerParsed =
        engine.getParser().parse("/cmd <d:text:NewA && d:text:NewB> <!say newer_done>");
    var newerArtifact =
        new SessionInceptionArtifacts(
            newerInc,
            0L,
            ExecutionPlanAdapter.fromParsedCommand(newerParsed),
            PresetSnapshot.empty(),
            List.copyOf(newerParsed.postCmds()));
    getArtifactsMap().put(player.getUniqueId(), newerArtifact);

    var result = engine.submitAnswers(player, List.of("ans1", "ans2"));
    assertTrue(result.isEmpty());

    var currentArtifact = getArtifactsMap().get(player.getUniqueId());
    assertSame(
        newerArtifact,
        currentArtifact,
        "Mismatched incarnation must preserve preinstalled artifact unchanged");
    assertEquals(newerInc, currentArtifact.incarnation());
    assertEquals(0L, currentArtifact.generation());
  }

  @Test
  @DisplayName(
      "submitAnswers non-final with mismatched generation preserves existing artifact unchanged")
  void submitAnswersNonFinalWithMismatchedGenerationPreservesExistingArtifactUnchanged() {
    var player = createPlayer("MismatchGenSubmitAnswersUser");
    engine.intercept(player, "/cmd <d:text:A && d:text:B> <a:second> <!say done>");
    assertTrue(engine.hasActiveSession(player));

    var session = engine.getSession(player).orElseThrow();
    long sessionInc = session.incarnation();
    long mismatchedGen = 8L;

    var mismatchedArtifact =
        new SessionInceptionArtifacts(
            sessionInc,
            mismatchedGen,
            ExecutionPlanAdapter.fromParsedCommand(
                engine.getParser().parse("/cmd <d:text:A && d:text:B> <a:second> <!say done>")),
            PresetSnapshot.empty());
    getArtifactsMap().put(player.getUniqueId(), mismatchedArtifact);

    var result = engine.submitAnswers(player, List.of("ans1", "ans2"));
    assertTrue(result.isEmpty());

    var currentArtifact = getArtifactsMap().get(player.getUniqueId());
    assertSame(
        mismatchedArtifact,
        currentArtifact,
        "Mismatched generation must preserve preinstalled artifact unchanged");
    assertEquals(sessionInc, currentArtifact.incarnation());
    assertEquals(mismatchedGen, currentArtifact.generation());
  }

  @Test
  @DisplayName(
      "submitAnswers explicit count non-final with exact tokens advances artifact generation")
  void submitAnswersExplicitCountNonFinalWithExactTokensAdvancesArtifactGeneration() {
    var player = createPlayer("ExactSubmitAnswersExplicitUser");
    engine.intercept(player, "/cmd <a:first> <a:second> <!say done>");
    assertTrue(engine.hasActiveSession(player));

    var initialSession = engine.getSession(player).orElseThrow();
    long inc = initialSession.incarnation();

    var result = engine.submitAnswers(player, List.of("ans1"), 1);
    assertTrue(result.isEmpty());

    var advancedArtifact = engine.getInceptionArtifacts(player.getUniqueId()).orElseThrow();
    assertEquals(inc, advancedArtifact.incarnation());
    assertEquals(1L, advancedArtifact.generation());
  }

  @Test
  @DisplayName(
      "submitAnswers explicit count non-final with mismatched tokens preserves existing artifact unchanged")
  void submitAnswersExplicitCountNonFinalWithMismatchedTokensPreservesExistingArtifactUnchanged() {
    var player = createPlayer("MismatchSubmitAnswersExplicitUser");
    engine.intercept(player, "/cmd <a:first> <a:second> <!say done>");
    assertTrue(engine.hasActiveSession(player));

    var session = engine.getSession(player).orElseThrow();
    long sessionInc = session.incarnation();
    long newerInc = sessionInc + 20L;

    var newerParsed = engine.getParser().parse("/cmd <a:newer> <!say newer_done>");
    var newerArtifact =
        new SessionInceptionArtifacts(
            newerInc,
            0L,
            ExecutionPlanAdapter.fromParsedCommand(newerParsed),
            PresetSnapshot.empty(),
            List.copyOf(newerParsed.postCmds()));
    getArtifactsMap().put(player.getUniqueId(), newerArtifact);

    var result = engine.submitAnswers(player, List.of("ans1"), 1);
    assertTrue(result.isEmpty());

    var currentArtifact = getArtifactsMap().get(player.getUniqueId());
    assertSame(
        newerArtifact,
        currentArtifact,
        "Mismatched tokens must preserve preinstalled artifact unchanged");
  }

  @Test
  @DisplayName("stale retry does not overwrite or downgrade newer artifact generation")
  void staleRetryDoesNotOverwriteOrDowngradeNewerArtifact() {
    var player = createPlayer("StaleRetryUser");
    engine.intercept(player, "/cmd <a:first> <a:second> <a:third> <!say done>");
    assertTrue(engine.hasActiveSession(player));

    var initialSession = engine.getSession(player).orElseThrow();
    long inc = initialSession.incarnation();

    // First legitimate transition advances gen 0 -> gen 1
    var res1 = engine.submit(player, "ans1");
    assertTrue(res1.isEmpty());

    var gen1Artifact = engine.getInceptionArtifacts(player.getUniqueId()).orElseThrow();
    assertEquals(1L, gen1Artifact.generation());

    // Advance artifact manually to gen 2 (simulating a concurrent advancement)
    var gen2Artifact = gen1Artifact.withGeneration(2L);
    getArtifactsMap().put(player.getUniqueId(), gen2Artifact);

    // Session currently in engine has gen 1. When submit is called, priorGeneration is 1.
    // Since artifact is at gen 2, art.generation() (2) != priorGeneration (1), so artifact remains
    // unchanged at gen 2.
    var res2 = engine.submit(player, "ans2");
    assertTrue(res2.isEmpty());

    var remainingArtifact = getArtifactsMap().get(player.getUniqueId());
    assertSame(
        gen2Artifact,
        remainingArtifact,
        "Stale transition must not modify or downgrade newer artifact");
    assertEquals(2L, remainingArtifact.generation());
  }

  @SuppressWarnings("unchecked")
  private Map<java.util.UUID, SessionInceptionArtifacts> getArtifactsMap() {
    try {
      var field = PromptEngine.class.getDeclaredField("sessionInceptionArtifacts");
      field.setAccessible(true);
      return (Map<java.util.UUID, SessionInceptionArtifacts>) field.get(engine);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
