package dev.cyr1en.promptpaper.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link PostCommandPlaceholderResolver}. The class is a pure
 * placeholder-substitution utility; the PAPI hook is mocked via
 * {@link HookContainer} so we can verify the {@code %…%} branch in isolation.
 */
class PostCommandPlaceholderResolverTest extends MockBukkitTest {

  private HookContainer hookContainer;
  private PostCommandPlaceholderResolver resolver;

  @BeforeEach
  void setUp() {
    hookContainer = mock(HookContainer.class);
    when(hookContainer.getHook(PapiHook.class)).thenReturn(java.util.Optional.empty());
    resolver = new PostCommandPlaceholderResolver(hookContainer);
  }

  /** A real player with a known name, for placeholder substitution tests. */
  private Player player() {
    return createPlayer("TestUser");
  }

  // --- {player} ---

  @Test
  void playerPlaceholderIsReplaced() {
    var result = resolver.resolve("say hello {player}", player(), List.of());
    assertEquals("say hello TestUser", result);
  }

  @Test
  void playerPlaceholderAbsentLeavesTemplateUntouched() {
    var result = resolver.resolve("say hello world", player(), List.of());
    assertEquals("say hello world", result);
  }

  // --- {input} / {input:N} ---

  @Test
  void bareInputPlaceholderUsesFirstAnswer() {
    var result = resolver.resolve("ban {input} 7d", player(), List.of("spamming"));
    assertEquals("ban spamming 7d", result);
  }

  @Test
  void inputOnePlaceholderUsesFirstAnswer() {
    var result = resolver.resolve("ban {input:1}", player(), List.of("spamming"));
    assertEquals("ban spamming", result);
  }

  @Test
  void inputNPlaceholderUsesNthAnswer() {
    var result = resolver.resolve(
        "{input:1} {input:2} {input:3}", player(), List.of("a", "b", "c"));
    assertEquals("a b c", result);
  }

  @Test
  void inputOutOfRangeIsLeftInPlace() {
    // Unresolved {input:N} references stay visible so operators can spot
    // a misconfigured template.
    var result = resolver.resolve("{input:5}", player(), List.of("a"));
    assertEquals("{input:5}", result);
  }

  @Test
  void inputPlaceholderWithEmptyAnswersIsLeftInPlace() {
    var result = resolver.resolve("{input}", player(), List.of());
    assertEquals("{input}", result);
  }

  @Test
  void multipleInputPlaceholdersInOneTemplate() {
    var result = resolver.resolve(
        "msg {input:2} about {input:1}", player(), List.of("first", "second"));
    assertEquals("msg second about first", result);
  }

  // --- PAPI ---

  @Test
  void papiPlaceholderIsResolvedWhenHookPresent() {
    var papi = mock(PapiHook.class);
    when(papi.setPlaceholder(org.mockito.ArgumentMatchers.any(Player.class),
            org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(inv -> "promote rank: VIP");
    when(hookContainer.getHook(PapiHook.class)).thenReturn(java.util.Optional.of(papi));

    var result = resolver.resolve("promote %player_rank%", player(), List.of());
    assertEquals("promote rank: VIP", result);
  }

  @Test
  void papiAbsentLeavesPercentPlaceholdersUntouched() {
    // No PAPI hook installed — %…% should be passed through verbatim.
    var result = resolver.resolve("promote %player_rank%", player(), List.of());
    assertEquals("promote %player_rank%", result);
  }

  @Test
  void papiNotInvokedWhenTemplateHasNoPercentSign() {
    // The fast path must not call into the hook for ordinary text.
    var papi = mock(PapiHook.class);
    when(hookContainer.getHook(PapiHook.class)).thenReturn(java.util.Optional.of(papi));
    var result = resolver.resolve("plain {player}", player(), List.of());
    assertEquals("plain TestUser", result);
    verify(papi, never()).setPlaceholder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
  }

  // --- Combined ---

  @Test
  void playerAndInputAndPapiAreAllResolvedInOnePass() {
    var papi = mock(PapiHook.class);
    when(papi.setPlaceholder(org.mockito.ArgumentMatchers.any(Player.class),
            org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(inv -> "tag answer1 TestUser VIP");
    when(hookContainer.getHook(PapiHook.class)).thenReturn(java.util.Optional.of(papi));
    resolver = new PostCommandPlaceholderResolver(hookContainer);

    var result = resolver.resolve("tag {input:1} {player} %rank%", player(), List.of("answer1"));
    assertEquals("tag answer1 TestUser VIP", result);
    verify(papi, times(1)).setPlaceholder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
  }

  // --- Edge cases ---

  @Test
  void nullTemplateReturnsNull() {
    assertNull(resolver.resolve(null, player(), List.of()));
  }

  @Test
  void emptyTemplateReturnsEmpty() {
    assertEquals("", resolver.resolve("", player(), List.of()));
  }
}
