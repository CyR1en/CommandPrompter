package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptui.ScreenResult;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatPromptScreenTest extends MockBukkitTest {

  private ChatPromptScreen screen;
  private AtomicReference<ScreenResult> resultRef;

  @BeforeEach
  void setUpScreen() {
    var player = createPlayer("TestPlayer");
    lenient().when(promptConfig.sendCancelText()).thenReturn(false);
    screen =
        new ChatPromptScreen(
            plugin,
            player,
            new dev.cyr1en.promptpaper.preset.ChatPrompt(
                "chat",
                "inline-test",
                "Enter value:",
                new dev.cyr1en.promptpaper.preset.CancelBehavior(false, "", false, ""),
                true));
    resultRef = new AtomicReference<>();
    screen.onResult(resultRef::set);
  }

  @Test
  void isOpenReturnsFalseInitially() {
    assertFalse(screen.isOpen());
  }

  @Test
  void openSetsOpenToTrue() {
    screen.open();
    assertTrue(screen.isOpen());
  }

  @Test
  void closeSetsOpenToFalse() {
    screen.open();
    screen.close();
    assertFalse(screen.isOpen());
  }

  @Test
  void handleInputFiresCallbackWithAnswer() {
    screen.open();
    screen.handleInput("myAnswer");
    assertNotNull(resultRef.get());
    assertEquals("myAnswer", resultRef.get().answer());
    assertFalse(resultRef.get().cancelled());
  }

  @Test
  void handleInputWithoutOpenDoesNotFireCallback() {
    screen.handleInput("myAnswer");
    assertNull(resultRef.get());
  }

  @Test
  void handleInputClosesScreenBeforeCallback() {
    screen.open();
    screen.handleInput("test");
    assertFalse(screen.isOpen());
  }

  @Test
  void onResultReplacesPreviousCallback() {
    var secondRef = new AtomicReference<ScreenResult>();
    screen.onResult(secondRef::set);
    screen.open();
    screen.handleInput("test");
    assertNull(resultRef.get());
    assertNotNull(secondRef.get());
  }

  @Test
  void openWithLineBreaksSendsSeparateMessages() {
    var testPlayer = createPlayer("MultiLinePlayer");
    var chatPrompt =
        new dev.cyr1en.promptpaper.preset.ChatPrompt(
            "chat",
            "inline-test",
            "Line 1{br}Line 2{br}Line 3",
            new dev.cyr1en.promptpaper.preset.CancelBehavior(false, "", false, ""),
            true);
    var multiScreen = new ChatPromptScreen(plugin, testPlayer, chatPrompt);

    multiScreen.open();

    assertEquals("[Prompter] Line 1", testPlayer.nextMessage());
    assertEquals("Line 2", testPlayer.nextMessage());
    assertEquals("Line 3", testPlayer.nextMessage());
    assertNull(testPlayer.nextMessage());
  }

  @Test
  void openWithLineBreaksAndCancelSendsSeparateMessagesWithCancelOnLast() {
    var testPlayer = createPlayer("MultiLineCancelPlayer");
    lenient().when(promptConfig.sendCancelText()).thenReturn(true);
    lenient().when(promptConfig.textCancelMessage()).thenReturn(" (Cancel)");
    lenient().when(promptConfig.textCancelHoverMessage()).thenReturn("Click to cancel");

    var chatPrompt =
        new dev.cyr1en.promptpaper.preset.ChatPrompt(
            "chat",
            "inline-test",
            "Line 1{br}Line 2",
            new dev.cyr1en.promptpaper.preset.CancelBehavior(
                true, " (Cancel)", false, "Click to cancel"),
            true);
    var multiScreen = new ChatPromptScreen(plugin, testPlayer, chatPrompt);

    multiScreen.open();

    assertEquals("[Prompter] Line 1", testPlayer.nextMessage());
    String lastMsg = testPlayer.nextMessage();
    assertTrue(lastMsg.contains("Line 2"));
    assertTrue(lastMsg.contains("Cancel"));
    assertFalse(lastMsg.contains("[Prompter]"));
  }

  @Test
  void openWithClickableCancelUsesFixedCancelCommandRegardlessOfCancelKeyword() {
    var testPlayer = createPlayer("ClickableCancelPlayer");
    when(config.cancelKeyword()).thenReturn("quit_custom");
    lenient().when(promptConfig.sendCancelText()).thenReturn(true);
    lenient().when(promptConfig.textCancelMessage()).thenReturn("[Cancel]");
    lenient().when(promptConfig.textCancelHoverMessage()).thenReturn("Click to cancel");

    var chatPrompt =
        new dev.cyr1en.promptpaper.preset.ChatPrompt(
            "chat",
            "inline-test",
            "Enter value:",
            new dev.cyr1en.promptpaper.preset.CancelBehavior(
                true, "[Cancel]", true, "Click to cancel"),
            true);
    var clickableScreen = new ChatPromptScreen(plugin, testPlayer, chatPrompt);

    clickableScreen.open();

    var message = testPlayer.nextComponentMessage();
    assertNotNull(message);
    boolean foundCancelClick = false;
    for (var child : message.children()) {
      if (child.clickEvent() != null && "/cmdp cancel".equals(child.clickEvent().value())) {
        foundCancelClick = true;
        break;
      }
    }
    assertTrue(foundCancelClick, "Expected click event running '/cmdp cancel'");
  }

  @Test
  void handleInput_doesNotLeakAnswerInDiagnosticLogs() {
    var testPlayer = createPlayer("NoLeakPlayer");
    var mockLogger = org.mockito.Mockito.mock(dev.cyr1en.promptpaper.util.PluginLogger.class);
    org.mockito.Mockito.when(plugin.getPluginLogger()).thenReturn(mockLogger);

    var chatPrompt =
        new dev.cyr1en.promptpaper.preset.ChatPrompt(
            "chat",
            "inline-test",
            "Enter secret:",
            new dev.cyr1en.promptpaper.preset.CancelBehavior(false, "", false, ""),
            true);
    var chatScreen = new ChatPromptScreen(plugin, testPlayer, chatPrompt);

    chatScreen.open();
    String secretAnswer = "superSecretUserAnswer123";
    chatScreen.handleInput(secretAnswer);

    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(mockLogger, org.mockito.Mockito.atLeastOnce())
        .debug(captor.capture(), org.mockito.ArgumentMatchers.any(Object[].class));

    for (var loggedMsg : captor.getAllValues()) {
      assertFalse(
          loggedMsg.contains(secretAnswer),
          "ChatPromptScreen debug log must not contain raw answer: " + loggedMsg);
    }
  }
}
