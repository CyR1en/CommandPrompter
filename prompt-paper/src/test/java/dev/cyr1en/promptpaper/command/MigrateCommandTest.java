package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.cyr1en.promptcore.i18n.Placeholder;
import dev.cyr1en.promptpaper.MockBukkitTest;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrateCommandTest extends MockBukkitTest {
  @TempDir Path plugins;
  private CommandSender sender;
  private MigrateCommand command;

  @BeforeEach
  void setupMigration() throws Exception {
    when(plugin.getDataFolder())
        .thenReturn(Files.createDirectory(plugins.resolve("CommandPrompterPaper")).toFile());
    Files.createDirectory(plugins.resolve("Menus"));
    sender = mock(CommandSender.class);
    when(sender.hasPermission("promptpaper.migrate")).thenReturn(true);
    when(i18n.get(anyString(), nullable(Player.class), any(Placeholder[].class)))
        .thenReturn(Component.empty());
    command = new MigrateCommand(plugin);
  }

  @Test
  void nothingToMigrateUsesLocalizedFeedbackWithoutBackup() throws Exception {
    Files.writeString(plugins.resolve("Menus/config.yml"), "commands: []");
    command.execute(sender, "Menus/config.yml", false);
    verify(i18n).get(eq("command.migrate.nothing"), isNull(), any(Placeholder[].class));
    verify(i18n, never()).get(eq("command.migrate.success"), isNull(), any(Placeholder[].class));
    assertFalse(Files.exists(plugins.resolve("CommandPrompterPaper/migration-backups")));
  }

  @Test
  void errorsAndManualAttentionUseLocalizedFeedback() throws Exception {
    command.execute(sender, "Menus/missing.yml", false);
    verify(i18n).get(eq("command.migrate.error.not_found"), isNull(), any(Placeholder[].class));
    Files.writeString(plugins.resolve("Menus/config.yml"), "<-custom Example>");
    command.execute(sender, "Menus/config.yml", false);
    verify(i18n).get(eq("command.migrate.manual"), isNull(), any(Placeholder[].class));
    verify(i18n).get(eq("command.migrate.issue.unsupported"), isNull(), any(Placeholder[].class));
  }

  @Test
  void brigadierSupportsDryRunSpacesAndActualMigration() throws Exception {
    var file =
        Files.writeString(plugins.resolve("Menus/my config.json"), "{\"cmd\":\"say <-a Name>\"}");
    var dispatcher = new CommandDispatcher<CommandSourceStack>();
    dispatcher.register(
        io.papermc.paper.command.brigadier.Commands.literal("cmdp").then(command.build()));
    var source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    assertEquals(1, dispatcher.execute("cmdp migrate --dry-run Menus/my config.json", source));
    assertTrue(Files.readString(file).contains("<-a Name>"));
    verify(i18n).get(eq("command.migrate.preview"), isNull(), any(Placeholder[].class));
    assertEquals(1, dispatcher.execute("cmdp migrate \"Menus/my config.json\"", source));
    assertEquals("{\"cmd\":\"say <a:Name>\"}", Files.readString(file));
    verify(i18n).get(eq("command.migrate.success"), isNull(), any(Placeholder[].class));
    verify(i18n).get(eq("command.migrate.backup"), isNull(), any(Placeholder[].class));
  }

  @Test
  void permissionAppliesToExecutionAndAutocomplete() throws Exception {
    Files.writeString(plugins.resolve("Menus/config.yml"), "<-a Name>");
    when(sender.hasPermission("promptpaper.migrate")).thenReturn(false);
    assertEquals(0, command.execute(sender, "Menus/config.yml", false));
    assertTrue(command.suggest(sender, new SuggestionsBuilder("Menus/", 0)).get().isEmpty());
    verify(i18n).get(eq("command.migrate.denied"), isNull(), any(Placeholder[].class));
    assertEquals("<-a Name>", Files.readString(plugins.resolve("Menus/config.yml")));
    when(sender.hasPermission("promptpaper.admin")).thenReturn(true);
    assertTrue(command.allowed(sender));
    assertEquals(
        "Menus/config.yml",
        command
            .suggest(sender, new SuggestionsBuilder("Menus/", 0))
            .get()
            .getList()
            .getFirst()
            .getText());
  }

  @Test
  void brigadierSuggestionsReplaceTheWholePathInBothBranches() throws Exception {
    Files.writeString(plugins.resolve("Menus/shop.yml"), "");
    var dispatcher = new CommandDispatcher<CommandSourceStack>();
    dispatcher.register(
        io.papermc.paper.command.brigadier.Commands.literal("cmdp").then(command.build()));
    var source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    for (String prefix : List.of("cmdp migrate ", "cmdp migrate --dry-run ")) {
      String input = prefix + "Menus/sh";
      var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).get();
      assertTrue(
          suggestions.getList().stream()
              .anyMatch(s -> s.apply(input).equals(prefix + "Menus/shop.yml")));
    }
  }

  @Test
  void bundledLocalesContainMatchingKeysAndPlaceholders() throws Exception {
    var defaults = locale("en_US");
    var keys =
        defaults.stringPropertyNames().stream()
            .filter(k -> k.startsWith("command.migrate."))
            .collect(Collectors.toSet());
    assertEquals(31, keys.size());
    for (String language :
        List.of("es_ES", "fr_FR", "ja_JP", "ko_KR", "pirate", "pl_PL", "pt_BR", "tl_PH", "zh_CN")) {
      var translated = locale(language);
      assertEquals(
          keys,
          translated.stringPropertyNames().stream()
              .filter(k -> k.startsWith("command.migrate."))
              .collect(Collectors.toSet()),
          language);
      for (String key : keys) {
        assertEquals(
            placeholders(defaults.getProperty(key)),
            placeholders(translated.getProperty(key)),
            language + ":" + key);
      }
    }
  }

  @Test
  void undoCommandCompletesRestoresAndLocalizesResults() throws Exception {
    var file = Files.writeString(plugins.resolve("Menus/my config.yml"), "<-a Name>");
    var service = new MigrationService(plugin.getDataFolder().toPath());
    var snapshot =
        service
            .migrate(
                "Menus/my config.yml",
                false,
                new dev.cyr1en.promptcore.parser.LegacyPromptMigrator(
                    dev.cyr1en.promptcore.ParserConfig.ANGLE_BRACKETS, null))
            .backup();
    String argument = service.backupArgument(snapshot);
    var dispatcher = new CommandDispatcher<CommandSourceStack>();
    dispatcher.register(
        io.papermc.paper.command.brigadier.Commands.literal("cmdp").then(command.build()));
    var source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    String input = "cmdp migrate undo " + argument.substring(0, argument.lastIndexOf('/') + 1);
    var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).get();
    assertTrue(
        suggestions.getList().stream()
            .anyMatch(s -> s.apply(input).equals("cmdp migrate undo " + argument)));
    assertEquals(1, dispatcher.execute("cmdp migrate undo \"" + argument + "\"", source));
    assertEquals("<-a Name>", Files.readString(file));
    verify(i18n).get(eq("command.migrate.undo.success"), isNull(), any(Placeholder[].class));
    command.undo(sender, argument);
    verify(i18n).get(eq("command.migrate.undo.nothing"), isNull(), any(Placeholder[].class));
    command.undo(sender, "../escape");
    verify(i18n).get(eq("command.migrate.undo.invalid_path"), isNull(), any(Placeholder[].class));
    when(sender.hasPermission("promptpaper.migrate")).thenReturn(false);
    assertEquals(0, command.undo(sender, argument));
    assertTrue(command.suggest(sender, new SuggestionsBuilder("", 0), true).get().isEmpty());
  }

  private Properties locale(String language) throws Exception {
    var properties = new Properties();
    try (var stream = getClass().getResourceAsStream("/messages_" + language + ".properties")) {
      assertNotNull(stream);
      properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    return properties;
  }

  private Set<String> placeholders(String value) {
    return Pattern.compile("%\\w+%")
        .matcher(value)
        .results()
        .map(match -> match.group())
        .collect(Collectors.toSet());
  }
}
