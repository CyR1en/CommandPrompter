package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptcore.DispatchTarget;
import dev.cyr1en.promptcore.PostCommandMeta;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.ExecutionPolicy;
import dev.cyr1en.promptpaper.preset.PostCommand;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link PostCommandResolver}. The resolver is the
 * Scope 5 core: it turns a parsed {@link PostCommandMeta} into a fully prepared
 * command string (with placeholders resolved) plus the dispatch metadata
 * (sender context, delay) loaded from the preset registry.
 */
class PostCommandResolverTest extends MockBukkitTest {

  private PostCommandResolver resolver;
  private PresetRegistry registry;
  private HookContainer hookContainer;

  @BeforeEach
  void setUp() {
    registry = mock(PresetRegistry.class);
    hookContainer = mock(HookContainer.class);
    when(plugin.getPresetRegistry()).thenReturn(registry);
    when(plugin.getHookContainer()).thenReturn(hookContainer);
    when(hookContainer.getHook(PapiHook.class)).thenReturn(Optional.empty());
    resolver = new PostCommandResolver(plugin);
  }

  private Player player() {
    return createPlayer("TestUser");
  }

  // --- Legacy PCMs ---

  @Test
  void legacyOnCompleteDispatchTargetMapsToPlayer() {
    var pcm = new PostCommandMeta(
            "say hi", new int[0], 0, false, DispatchTarget.PLAYER, false);
    var resolved = resolver.resolve(player(), pcm, false, List.of());
    assertTrue(resolved.isPresent());
    assertEquals("say hi", resolved.get().command());
    assertEquals(ExecuteAs.PLAYER, resolved.get().executeAs());
    assertEquals(0, resolved.get().delayTicks());
    assertFalse(resolved.get().preset());
  }

  @Test
  void legacyConsoleTargetMapsToConsole() {
    var pcm = new PostCommandMeta(
            "say hi", new int[0], 0, false, DispatchTarget.CONSOLE, false);
    var resolved = resolver.resolve(player(), pcm, false, List.of());
    assertEquals(ExecuteAs.CONSOLE, resolved.get().executeAs());
  }

  @Test
  void legacyPassthroughMapsToPlayer() {
    var pcm = new PostCommandMeta(
            "say hi", new int[0], 0, false, DispatchTarget.PASSTHROUGH, false);
    var resolved = resolver.resolve(player(), pcm, false, List.of());
    assertEquals(ExecuteAs.PLAYER, resolved.get().executeAs());
  }

  @Test
  void legacyEmptyCommandIsSkipped() {
    var pcm = new PostCommandMeta(
            "", new int[0], 0, false, DispatchTarget.PLAYER, false);
    assertTrue(resolver.resolve(player(), pcm, false, List.of()).isEmpty());
  }

  @Test
  void legacyKeepsParsedDelay() {
    var pcm = new PostCommandMeta(
            "say hi", new int[0], 42, false, DispatchTarget.PLAYER, false);
    var resolved = resolver.resolve(player(), pcm, false, List.of());
    assertEquals(42, resolved.get().delayTicks());
  }

  // --- Preset PCMs ---

  @Test
  void presetOnCompleteWithMatchingStateIsResolved() {
    var def = new PostCommand(
            "log_reason",
            "discord broadcast {player} {input:1}",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            0);
    when(registry.getPostCommand("log_reason")).thenReturn(Optional.of(def));
    var pcm = new PostCommandMeta(
            "log_reason", new int[0], 0, false, DispatchTarget.PASSTHROUGH, true);
    var resolved = resolver.resolve(player(), pcm, false, List.of("spamming"));
    assertTrue(resolved.isPresent());
    assertEquals("discord broadcast TestUser spamming", resolved.get().command());
    assertEquals(ExecuteAs.CONSOLE, resolved.get().executeAs());
    assertTrue(resolved.get().preset());
    assertEquals("log_reason", resolved.get().sourceId());
  }

  @Test
  void presetOnCancelWithMatchingStateIsResolved() {
    var def = new PostCommand(
            "refund_fee",
            "eco give {player} 100",
            ExecutionPolicy.ON_CANCEL,
            ExecuteAs.CONSOLE,
            20);
    when(registry.getPostCommand("refund_fee")).thenReturn(Optional.of(def));
    var pcm = new PostCommandMeta(
            "refund_fee", new int[0], 0, true, DispatchTarget.PASSTHROUGH, true);
    var resolved = resolver.resolve(player(), pcm, true, List.of());
    assertTrue(resolved.isPresent());
    assertEquals(20, resolved.get().delayTicks());
    assertEquals(ExecuteAs.CONSOLE, resolved.get().executeAs());
  }

  @Test
  void presetOnCompleteMismatchedWithCancelledStateIsSkipped() {
    var def = new PostCommand(
            "log_reason",
            "log {input:1}",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            0);
    when(registry.getPostCommand("log_reason")).thenReturn(Optional.of(def));
    var pcm = new PostCommandMeta(
            "log_reason", new int[0], 0, true, DispatchTarget.PASSTHROUGH, true);
    // session was cancelled, but preset says on_complete → skip
    assertTrue(resolver.resolve(player(), pcm, true, List.of()).isEmpty());
  }

  @Test
  void presetOnCancelMismatchedWithCompletedStateIsSkipped() {
    var def = new PostCommand(
            "refund",
            "eco give {player} 100",
            ExecutionPolicy.ON_CANCEL,
            ExecuteAs.CONSOLE,
            0);
    when(registry.getPostCommand("refund")).thenReturn(Optional.of(def));
    var pcm = new PostCommandMeta(
            "refund", new int[0], 0, false, DispatchTarget.PASSTHROUGH, true);
    // session was completed, but preset says on_cancel → skip
    assertTrue(resolver.resolve(player(), pcm, false, List.of()).isEmpty());
  }

  @Test
  void presetWithMissingIdIsSkipped() {
    // Defensive: the fail-fast check in PromptEngine.intercept should have
    // caught this, but if the registry is hot-reloaded after a session starts
    // the resolver must not NPE.
    when(registry.getPostCommand("missing")).thenReturn(Optional.empty());
    var pcm = new PostCommandMeta(
            "missing", new int[0], 0, false, DispatchTarget.PASSTHROUGH, true);
    assertTrue(resolver.resolve(player(), pcm, false, List.of()).isEmpty());
  }

  @Test
  void presetWithNullRegistryIsSkipped() {
    when(plugin.getPresetRegistry()).thenReturn(null);
    resolver = new PostCommandResolver(plugin);
    var pcm = new PostCommandMeta(
            "log_reason", new int[0], 0, false, DispatchTarget.PASSTHROUGH, true);
    assertTrue(resolver.resolve(player(), pcm, false, List.of()).isEmpty());
  }

  @Test
  void presetUsesResolvedPlayerExecuteAs() {
    var def = new PostCommand(
            "ping", "msg {player}", ExecutionPolicy.ON_COMPLETE, ExecuteAs.PLAYER, 0);
    when(registry.getPostCommand("ping")).thenReturn(Optional.of(def));
    var pcm = new PostCommandMeta(
            "ping", new int[0], 0, false, DispatchTarget.PASSTHROUGH, true);
    var resolved = resolver.resolve(player(), pcm, false, List.of());
    assertEquals(ExecuteAs.PLAYER, resolved.get().executeAs());
  }

  @Test
  void presetEmptyCommandAfterResolutionIsSkipped() {
    var def = new PostCommand(
            "empty", "", ExecutionPolicy.ON_COMPLETE, ExecuteAs.CONSOLE, 0);
    when(registry.getPostCommand("empty")).thenReturn(Optional.of(def));
    var pcm = new PostCommandMeta(
            "empty", new int[0], 0, false, DispatchTarget.PASSTHROUGH, true);
    assertTrue(resolver.resolve(player(), pcm, false, List.of()).isEmpty());
  }

  @Test
  void presetCommandIsResolvedWithMultipleInputs() {
    var def = new PostCommand(
            "multi",
            "msg {input:1} about {input:2} for {player}",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            0);
    when(registry.getPostCommand("multi")).thenReturn(Optional.of(def));
    var pcm = new PostCommandMeta(
            "multi", new int[0], 0, false, DispatchTarget.PASSTHROUGH, true);
    var resolved = resolver.resolve(player(), pcm, false, List.of("first", "second"));
    assertEquals("msg first about second for TestUser", resolved.get().command());
  }

  @Test
  void presetPolicyOverridesParserHint() {
    // Preset says on_complete, parser hint was onCancel (from <!!@id>).
    // Session is completed. The preset wins → the command is dispatched.
    var def = new PostCommand(
            "misconfigured",
            "log {input:1}",
            ExecutionPolicy.ON_COMPLETE,
            ExecuteAs.CONSOLE,
            0);
    when(registry.getPostCommand("misconfigured")).thenReturn(Optional.of(def));
    var pcm = new PostCommandMeta(
            "misconfigured", new int[0], 0, true, DispatchTarget.PASSTHROUGH, true);
    var resolved = resolver.resolve(player(), pcm, false, List.of("value"));
    assertTrue(resolved.isPresent(),
            "Preset's on_complete policy must win over the <!!@id> onCancel hint");
  }

  @Test
  void nullPcmReturnsEmpty() {
    assertTrue(resolver.resolve(player(), null, false, List.of()).isEmpty());
  }
}
