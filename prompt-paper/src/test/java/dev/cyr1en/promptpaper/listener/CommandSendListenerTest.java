package dev.cyr1en.promptpaper.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommandSendListenerTest extends MockBukkitTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void tabCompletionSettingIncludesResponseAndNamespacedAliases(boolean enabled) {
    when(config.commandTabComplete()).thenReturn(enabled);
    var original =
        List.of(
            "cmdp",
            "commandprompterpaper:cmdp",
            "commandprompter:response",
            "commandprompterpaper:commandprompter:response",
            "other:cmdp",
            "help");
    var commands = new ArrayList<>(original);
    var event = new PlayerCommandSendEvent(createPlayer(), commands);
    new CommandSendListener(plugin).onCommandSend(event);
    assertEquals(
        enabled ? original : List.of("other:cmdp", "help"), List.copyOf(event.getCommands()));
  }
}
