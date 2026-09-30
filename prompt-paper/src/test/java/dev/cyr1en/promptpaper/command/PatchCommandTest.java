package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.hook.geyser.GeyserStandaloneSupport;
import dev.cyr1en.promptpaper.hook.geyser.StandaloneJarPatcher;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchCommandTest extends MockBukkitTest {
  @TempDir Path data;
  private CommandSender sender;
  private GeyserStandaloneSupport support;
  private PatchCommand command;

  @BeforeEach
  void setupPatch() throws Exception {
    when(plugin.getDataFolder()).thenReturn(data.toFile());
    sender = mock(CommandSender.class);
    when(sender.hasPermission("promptpaper.patch")).thenReturn(true);
    when(i18n.get(anyString(), nullable(Player.class), any(Placeholder[].class)))
        .thenReturn(Component.empty());
    support = mock(GeyserStandaloneSupport.class);
    when(plugin.getGeyserStandaloneSupport()).thenReturn(support);
    when(support.patcher()).thenReturn(new StandaloneJarPatcher(data));
    when(support.assets())
        .thenReturn(new StandaloneJarPatcher.Assets(Map.of(), new byte[0], new byte[0]));
    command = new PatchCommand(plugin);
  }

  @Test
  void permissionDeniesExecutionAndCompletionWithoutAccessingFiles() {
    when(sender.hasPermission("promptpaper.patch")).thenReturn(false);
    assertEquals(0, command.execute(sender, "Geyser.jar"));
    assertTrue(command.suggest(sender, new SuggestionsBuilder("", 0)).join().isEmpty());
    verifyNoInteractions(support);
    verify(i18n).get(eq("command.patch.denied"), isNull(), any(Placeholder[].class));
    assertFalse(Files.exists(data.resolve("compat")));
    when(sender.hasPermission("promptpaper.admin")).thenReturn(true);
    assertTrue(command.allowed(sender));
  }

  @Test
  void completionReplacesTheWholeFilenameIncludingSpaces() throws Exception {
    Files.createDirectories(data.resolve("compat"));
    Files.writeString(data.resolve("compat/Geyser Standalone.jar"), "jar");
    Files.writeString(data.resolve("compat/Geyser-commandprompter.jar"), "output");
    var dispatcher = dispatcher();
    var source = source();
    String input = "cmdp patch Geyser St";
    var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).join();
    assertEquals(1, suggestions.getList().size());
    assertEquals("cmdp patch Geyser Standalone.jar", suggestions.getList().getFirst().apply(input));
  }

  @Test
  void brigadierPassesSpacedNamesAndReportsSuccessOrFailure() throws Exception {
    var patcher = mock(StandaloneJarPatcher.class);
    when(support.patcher()).thenReturn(patcher);
    when(patcher.patch(eq("Geyser Standalone.jar"), any(), anyBoolean()))
        .thenReturn(data.resolve("compat/Geyser Standalone-commandprompter.jar"));
    assertEquals(1, dispatcher().execute("cmdp patch Geyser Standalone.jar", source()));
    verify(patcher).patch(eq("Geyser Standalone.jar"), any(), anyBoolean());
    verify(i18n).get(eq("command.patch.success"), isNull(), any(Placeholder[].class));
    verify(i18n).get(eq("command.patch.setup"), isNull(), any(Placeholder[].class));

    when(support.patcher()).thenReturn(new StandaloneJarPatcher(data));
    assertEquals(1, command.execute(sender, "../outside.jar"));
    verify(i18n).get(eq("command.patch.error.invalid_path"), isNull(), any(Placeholder[].class));
    assertEquals(0, dispatcher().execute("cmdp patch", source()));
    verify(i18n).get(eq("command.patch.usage"), isNull(), any(Placeholder[].class));
  }

  @Test
  void forceFlagBypassesGateAndVersionFailureCarriesDetails() throws Exception {
    var patcher = mock(StandaloneJarPatcher.class);
    when(support.patcher()).thenReturn(patcher);
    when(patcher.patch(eq("Geyser Standalone.jar"), any(), eq(true)))
        .thenReturn(data.resolve("compat/Geyser Standalone-commandprompter.jar"));
    assertEquals(1, dispatcher().execute("cmdp patch Geyser Standalone.jar --force", source()));
    verify(patcher).patch(eq("Geyser Standalone.jar"), any(), eq(true));
    verify(patcher, never()).patch(any(), any(), eq(false));

    when(patcher.patch(any(), any(), anyBoolean()))
        .thenThrow(
            new StandaloneJarPatcher.Failure(
                "version", Map.of("expected", "2.11.3", "found", "2.10.0")));
    assertEquals(1, dispatcher().execute("cmdp patch Geyser.jar", source()));
    verify(i18n).get(eq("command.patch.error.version"), isNull(), any(Placeholder[].class));

    assertEquals(0, dispatcher().execute("cmdp patch --force", source()));
    verify(patcher, never()).patch(eq(""), any(), anyBoolean());
  }

  private CommandDispatcher<CommandSourceStack> dispatcher() {
    var dispatcher = new CommandDispatcher<CommandSourceStack>();
    dispatcher.register(Commands.literal("cmdp").then(command.build()));
    return dispatcher;
  }

  private CommandSourceStack source() {
    var source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    return source;
  }
}
