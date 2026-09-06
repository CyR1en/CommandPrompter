package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.preset.AnvilButton;
import dev.cyr1en.promptpaper.preset.AnvilPrompt;
import dev.cyr1en.promptpaper.preset.SignPrompt;
import dev.cyr1en.promptui.InputScreen;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatFallbackRoutingTest extends MockBukkitTest {

  @ParameterizedTest
  @ValueSource(strings = {"anvil", "sign", "titled-sign"})
  void fallbackCapturesChatAndAdvancesSession(String kind) {
    when(promptConfig.getScreenMappings()).thenReturn(Map.of("", ScreenType.CHAT));
    when(promptConfig.inputFieldLocation()).thenReturn("bottom");
    var player = createPlayer();
    var engine = new PromptEngine(plugin, scheduler);
    var manager =
        new ScreenManager(
            plugin,
            engine,
            null,
            scheduler,
            (p, tag, context) -> {
              InputScreen screen;
              if (kind.equals("anvil")) {
                var button = new AnvilButton(true, "", "PAPER", "", 0);
                screen =
                    new AnvilPromptScreen(
                        plugin,
                        p,
                        new AnvilPrompt("anvil", "inline-test", "", "Enter:", button, button, true),
                        List.of());
              } else {
                screen =
                    new SignPromptScreen(
                        plugin,
                        p,
                        new SignPrompt("sign", "inline-test", "Enter:", List.of(), true),
                        List.of());
              }
              return kind.equals("titled-sign")
                  ? new TitleWrapperScreen(
                      screen, new TitleConfig("Enter", "", 1), p, scheduler, plugin)
                  : screen;
            });

    manager.startSession(player, "/cmd <first> <second>");
    server.getScheduler().performTicks(2);
    assertTrue(manager.hasChatScreen(player));

    manager.handleChatInput(player, "myValue");

    assertEquals(List.of("myValue"), engine.getSession(player).orElseThrow().answers());
    assertTrue(manager.hasActiveScreen(player));
  }
}
