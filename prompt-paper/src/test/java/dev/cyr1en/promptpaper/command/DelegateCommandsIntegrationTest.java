package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DelegateCommandsIntegrationTest extends MockBukkitTest {

  private PromptEngine engine;
  private PromptFactory factory;
  private ScreenManager screenManager;

  @BeforeEach
  void setUpDelegates() {
    var promptConfig = org.mockito.Mockito.mock(dev.cyr1en.promptpaper.config.PromptConfig.class);
    when(promptConfig.getScreenMappings()).thenReturn(Map.of("", ScreenType.CHAT));
    lenient().when(promptConfig.sendCancelText()).thenReturn(false);
    lenient().when(promptConfig.responseListenerPriority()).thenReturn("LOWEST");
    when(configLoader.getPromptConfig()).thenReturn(promptConfig);

    engine = new PromptEngine(plugin, scheduler);
    factory = new PromptFactory(plugin);
    screenManager = new ScreenManager(plugin, engine, factory, scheduler);
    lenient()
        .when(
            i18n.get(
                org.mockito.ArgumentMatchers.eq("validation.delegated_single_argument"),
                org.mockito.ArgumentMatchers.any(org.bukkit.entity.Player.class)))
        .thenReturn(net.kyori.adventure.text.Component.text("single argument only"));
  }

  @Test
  void consoleDelegateWithNoPromptsDispatchesDirectly() {
    var player = createPlayer();
    screenManager.startDelegatedSession(
        player, "/cmd no prompts", ScreenManager.DispatchMode.CONSOLE, null);
    assertFalse(screenManager.hasActiveScreen(player));
  }

  @Test
  void consoleDelegateWithPromptsCreatesScreen() {
    var player = createPlayer();
    screenManager.startDelegatedSession(
        player, "/cmd <test>", ScreenManager.DispatchMode.CONSOLE, null);
    assertTrue(screenManager.hasActiveScreen(player));
  }

  @Test
  void playerDelegateWithNoPromptsDispatchesDirectly() {
    var player = createPlayer();
    screenManager.startDelegatedSession(
        player, "/cmd no prompts", ScreenManager.DispatchMode.ATTACHMENT, "test-key");
    assertFalse(screenManager.hasActiveScreen(player));
  }

  @Test
  void playerDelegateWithPromptsCreatesScreen() {
    var player = createPlayer();
    screenManager.startDelegatedSession(
        player, "/cmd <test>", ScreenManager.DispatchMode.ATTACHMENT, "test-key");
    assertTrue(screenManager.hasActiveScreen(player));
  }

  @Test
  void normalDelegateWithPromptsCreatesScreen() {
    var player = createPlayer();
    screenManager.startDelegatedSession(
        player, "/cmd <test>", ScreenManager.DispatchMode.NORMAL, null);
    assertTrue(screenManager.hasActiveScreen(player));
  }

  @Test
  void delegatedSessionCompletesWithChatInput() {
    var player = createPlayer();
    screenManager.startDelegatedSession(
        player, "/cmd <test>", ScreenManager.DispatchMode.CONSOLE, null);
    assertTrue(screenManager.hasChatScreen(player));

    screenManager.handleChatInput(player, "myValue");
    assertFalse(screenManager.hasActiveScreen(player));
    assertFalse(engine.hasActiveSession(player));
  }

  private java.util.List<String> captureDispatches(String name) {
    var dispatched = new java.util.concurrent.CopyOnWriteArrayList<String>();
    server
        .getCommandMap()
        .register(
            name,
            new org.bukkit.command.Command(name) {
              @Override
              public boolean execute(
                  org.bukkit.command.CommandSender sender, String label, String[] args) {
                dispatched.add(sender.getName() + ":" + String.join(" ", args));
                return true;
              }
            });
    return dispatched;
  }

  private void assertRejected(ScreenManager.DispatchMode mode, String key, String answer) {
    if (key != null) when(config.getPermissionAttachment(key)).thenReturn(new String[] {"p.x"});
    var dispatched = captureDispatches("givegems");
    var player = createPlayer();
    screenManager.startDelegatedSession(player, "/givegems <amount>", mode, key);
    assertTrue(screenManager.hasChatScreen(player));

    screenManager.handleChatInput(player, answer);
    performTicks(5);

    org.mockito.Mockito.verify(i18n)
        .get(
            org.mockito.ArgumentMatchers.eq("validation.delegated_single_argument"),
            org.mockito.ArgumentMatchers.same(player));
    assertTrue(screenManager.hasActiveScreen(player), "prompt must be shown again");
    assertTrue(engine.hasActiveSession(player));
    assertTrue(dispatched.isEmpty(), "rejected answer must not be dispatched: " + dispatched);

    screenManager.handleChatInput(player, "64");
    performTicks(5);
    assertFalse(engine.hasActiveSession(player));
    assertEquals(1, dispatched.size());
    assertTrue(dispatched.get(0).endsWith(":64"), dispatched.toString());
  }

  @Test
  void consoleDelegateRejectsSelectorAnswer() {
    assertRejected(ScreenManager.DispatchMode.CONSOLE, null, "@a");
  }

  @Test
  void consoleDelegateRejectsAnswerWithExtraArgument() {
    assertRejected(ScreenManager.DispatchMode.CONSOLE, null, "1 extra");
  }

  @Test
  void consoleDelegateRejectsNonBreakingSpaceAnswer() {
    assertRejected(ScreenManager.DispatchMode.CONSOLE, null, "1\u00A0extra");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"&a@a", "$@a", "(@a)", "{@a}", "[@a]", "<@a>", "&&a@a", "1&a &b2"})
  void consoleDelegateRejectsSelectorRevealedBySanitization(String answer) {
    assertRejected(ScreenManager.DispatchMode.CONSOLE, null, answer);
  }

  @Test
  void playerDelegateRejectsSelectorAndExtraArgument() {
    assertRejected(ScreenManager.DispatchMode.ATTACHMENT, "test-key", "1 @a");
  }

  @Test
  void consoleDelegateAllowArgsFlagOptsIn() {
    var dispatched = captureDispatches("givegems");
    var player = createPlayer();
    screenManager.startDelegatedSession(
        player, "/givegems <amount -allowargs>", ScreenManager.DispatchMode.CONSOLE, null);
    assertTrue(screenManager.hasChatScreen(player));

    screenManager.handleChatInput(player, "1 extra");
    performTicks(5);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(1, dispatched.size());
    assertTrue(dispatched.get(0).endsWith(":1 extra"), dispatched.toString());
  }

  @Test
  void nonDelegatedSessionStillAcceptsSpacesAndSelectors() {
    var dispatched = captureDispatches("givegems");
    var player = createPlayer();
    screenManager.startSession(player, "/givegems <amount>");
    assertTrue(screenManager.hasChatScreen(player));

    screenManager.handleChatInput(player, "@a extra");
    performTicks(5);

    assertFalse(engine.hasActiveSession(player));
    assertEquals(java.util.List.of(player.getName() + ":@a extra"), dispatched);
  }
}
