package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mojang.brigadier.CommandDispatcher;
import dev.cyr1en.promptcore.ParserConfig;
import dev.cyr1en.promptcore.parser.CommandLineParser;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.factory.InlineTagMapper;
import dev.cyr1en.promptpaper.preset.*;
import dev.cyr1en.promptpaper.screen.ChatPromptScreen;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresetCommandTest extends MockBukkitTest {
  @TempDir Path directory;
  PresetRegistry registry;
  PresetCommand command;
  CommandDispatcher<CommandSourceStack> dispatcher;
  CommandSourceStack source;
  PromptEngine engine;

  @BeforeEach
  void prepare() throws Exception {
    when(promptConfig.textCancelMessage()).thenReturn(" [Cancel]");
    when(promptConfig.textCancelHoverMessage()).thenReturn("Cancel this prompt");
    var file = directory.resolve("presets.json");
    Files.writeString(file, "{\"prompts\":[]}");
    registry = new PresetRegistry(file.toFile(), null);
    registry.reload();
    when(plugin.getPresetRegistry()).thenReturn(registry);
    engine = mock(PromptEngine.class);
    when(engine.getParser()).thenReturn(new CommandLineParser());
    when(plugin.getEngine()).thenReturn(engine);
    source = mock(CommandSourceStack.class);
    var sender = mock(CommandSender.class);
    when(sender.hasPermission("promptpaper.preset")).thenReturn(true);
    when(source.getSender()).thenReturn(sender);
    command = new PresetCommand(plugin);
    dispatcher = new CommandDispatcher<>();
    dispatcher.getRoot().addChild(new PromptRootCommand(plugin).build());
  }

  private int run(String arguments) throws Exception {
    return dispatcher.execute("commandprompter preset " + arguments, source);
  }

  @Test
  void addUpdateRemovePersistStructuredJson() throws Exception {
    assertEquals(1, run("add rename <a:Enter your new name -ds>"));
    var saved = assertInstanceOf(AnvilPrompt.class, registry.getPrompt("rename").orElseThrow());
    assertEquals("Enter your new name", saved.promptText());
    assertFalse(saved.sanitize());
    registry.reload();
    assertEquals(saved, registry.getPrompt("rename").orElseThrow());
    assertEquals(1, run("update rename <Enter your name>"));
    assertInstanceOf(ChatPrompt.class, registry.getPrompt("rename").orElseThrow());
    assertEquals(1, run("remove rename"));
    registry.reload();
    assertTrue(registry.getPromptIds().isEmpty());
  }

  @Test
  void savedChatPreservesConfiguredCancellationControls() throws Exception {
    var player = createPlayer();
    var tag = new CommandLineParser().parse("<What is your name?>").promptTags().getFirst();
    for (var enabled : new boolean[] {true, false}) {
      when(promptConfig.sendCancelText()).thenReturn(enabled);
      var inline = assertInstanceOf(ChatPrompt.class, InlineTagMapper.toPromptDefinition(tag));
      new ChatPromptScreen(plugin, player, inline).open();
      var inlineMessage = player.nextComponentMessage();

      var id = "chat" + enabled;
      assertEquals(1, run("add " + id + " <What is your name?>"));
      registry.reload();
      var saved = assertInstanceOf(ChatPrompt.class, registry.getPrompt(id).orElseThrow());
      assertEquals(
          new CancelBehavior(enabled, " [Cancel]", enabled, "Cancel this prompt"), saved.cancel());
      new ChatPromptScreen(plugin, player, saved).open();
      assertEquals(inlineMessage, player.nextComponentMessage());
    }
  }

  @Test
  void rejectsInvalidInputWithoutChangingFileOrSnapshot() throws Exception {
    var bytes = Files.readAllBytes(registry.getPromptsFile().toPath());
    var snapshot = registry.getSnapshot();
    for (var input :
        new String[] {
          "plain text",
          "<one> <two>",
          "say <one>",
          "<one> trailing",
          "<@missing>",
          "<!say hello>",
          "<a:unfinished",
          "<unknown:hello>",
          "<first && second>",
          "<text -iv:missing>"
        }) {
      assertEquals(0, run("add bad " + input), input);
      assertArrayEquals(bytes, Files.readAllBytes(registry.getPromptsFile().toPath()));
      assertSame(snapshot, registry.getSnapshot());
    }
    assertEquals(0, run("update absent <text>"));
    assertEquals(0, run("remove absent"));
  }

  @Test
  void duplicateAndMissingArgumentsAreRejected() throws Exception {
    assertEquals(1, run("add existing <text>"));
    assertEquals(0, run("add existing <replacement>"));
    assertThrows(
        com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> run("add missing"));
    assertThrows(
        com.mojang.brigadier.exceptions.CommandSyntaxException.class,
        () -> run("remove existing <extra>"));
  }

  @Test
  void permissionAndReloadGatePreventEdits() throws Exception {
    var sender = source.getSender();
    when(sender.hasPermission("promptpaper.preset")).thenReturn(false);
    assertThrows(
        com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> run("add nope <text>"));
    when(sender.hasPermission("promptpaper.admin")).thenReturn(true);
    assertEquals(1, run("add admin <text>"));
    when(engine.isReloadInProgress()).thenReturn(true);
    assertEquals(0, run("remove admin"));
    assertTrue(registry.getPrompt("admin").isPresent());
  }

  @Test
  void completesExistingPromptIdsAndAcceptsConfiguredDelimiters() throws Exception {
    when(engine.getParser()).thenReturn(new CommandLineParser(new ParserConfig("[", "]", "\\")));
    assertEquals(1, run("add reason [Why?]"));
    var results =
        dispatcher
            .getCompletionSuggestions(dispatcher.parse("commandprompter preset update re", source))
            .get();
    assertEquals(
        java.util.List.of("reason"), results.getList().stream().map(s -> s.getText()).toList());
  }
}
