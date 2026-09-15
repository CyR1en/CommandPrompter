package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptcore.ParserConfig;
import dev.cyr1en.promptcore.parser.LegacyPromptMigrator;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationServiceTest {
  @TempDir Path root;
  private Path data;
  private MigrationService service;
  private final LegacyPromptMigrator converter =
      new LegacyPromptMigrator(ParserConfig.ANGLE_BRACKETS, null);

  @BeforeEach
  void setup() throws Exception {
    data = Files.createDirectory(root.resolve("CommandPrompterPaper"));
    Files.createDirectories(root.resolve("Menus/menus"));
    service = new MigrationService(data);
  }

  @Test
  void backsUpExactBytesAndPreservesFileFormatting() throws Exception {
    var file = root.resolve("Menus/menus/test.yml");
    var source = "\uFEFF# 日本語\r\ncommands:\r\n  - 'say <-a Name>'\r\n";
    Files.writeString(file, source);
    var outcome = service.migrate("Menus/menus/test.yml", false, converter);
    assertArrayEquals(
        source.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(outcome.backup()));
    assertTrue(outcome.backup().startsWith(data.resolve("migration-backups")));
    assertEquals(source.replace("<-a Name>", "<a:Name>"), Files.readString(file));
    assertNull(service.migrate("Menus/menus/test.yml", false, converter).backup());
  }

  @Test
  void dryRunNothingAndDiagnosticsNeverWriteOrCreateBackups() throws Exception {
    var file = root.resolve("Menus/config.yml");
    for (var source : List.of("hello", "<a:Name>", "<-a Name>", "<-a Name> <-custom bad>")) {
      Files.writeString(file, source);
      boolean dryRun = source.equals("<-a Name>");
      var result = service.migrate("Menus/config.yml", dryRun, converter);
      assertNull(result.backup());
      assertEquals(source, Files.readString(file));
      assertFalse(Files.exists(data.resolve("migration-backups")));
    }
  }

  @Test
  void rejectsInvalidPathsSymlinksAndNonText() throws Exception {
    for (var input :
        List.of(
            "../server.properties",
            root.resolve("Menus/config.yml").toString(),
            "Menus/../../escape",
            "CommandPrompterPaper/migration-backups/a",
            "")) {
      assertEquals(
          "invalid_path",
          assertThrows(MigrationService.Failure.class, () -> service.resolve(input)).reason());
    }
    Files.writeString(root.resolve("Menus/config.yml"), "<-a Name>");
    Files.createSymbolicLink(root.resolve("link"), root.resolve("Menus"));
    assertEquals(
        "invalid_path",
        assertThrows(MigrationService.Failure.class, () -> service.resolve("link/config.yml"))
            .reason());
    for (var bytes : List.of(new byte[] {0, 1, 2}, new byte[] {(byte) 0xff, (byte) 0xfe})) {
      Files.write(root.resolve("Menus/binary.cfg"), bytes);
      assertEquals(
          "unsupported_file",
          assertThrows(
                  MigrationService.Failure.class,
                  () -> service.migrate("Menus/binary.cfg", false, converter))
              .reason());
    }
    assertEquals(
        "not_found",
        assertThrows(
                MigrationService.Failure.class,
                () -> service.migrate("Menus/missing.yml", false, converter))
            .reason());
  }

  @Test
  void backupFailureLeavesOriginalUntouched() throws Exception {
    Files.writeString(data.resolve("migration-backups"), "obstruction");
    var file = Files.writeString(root.resolve("Menus/test.yml"), "<-a Name>");
    assertEquals(
        "backup_failed",
        assertThrows(
                MigrationService.Failure.class,
                () -> service.migrate("Menus/test.yml", false, converter))
            .reason());
    assertEquals("<-a Name>", Files.readString(file));
  }

  @Test
  void detectsExternalWriteAndKeepsSnapshotBackup() throws Exception {
    var file = Files.writeString(root.resolve("Menus/test.yml"), "<-a Name>");
    var changing = mock(LegacyPromptMigrator.class);
    when(changing.migrate(anyString()))
        .thenAnswer(
            invocation -> {
              Files.writeString(file, "externally edited");
              return converter.migrate(invocation.getArgument(0));
            });
    var failure =
        assertThrows(
            MigrationService.Failure.class,
            () -> service.migrate("Menus/test.yml", false, changing));
    assertEquals("changed", failure.reason());
    assertEquals("externally edited", Files.readString(file));
    assertEquals("<-a Name>", Files.readString(failure.backup()));
  }

  @Test
  void atomicMoveFailureKeepsOriginalAndBackup() throws Exception {
    var file = Files.writeString(root.resolve("Menus/test.yml"), "<-a Name>");
    try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
      files
          .when(
              () ->
                  Files.move(
                      any(Path.class),
                      eq(file),
                      eq(StandardCopyOption.ATOMIC_MOVE),
                      eq(StandardCopyOption.REPLACE_EXISTING)))
          .thenThrow(new AtomicMoveNotSupportedException("temporary", "target", "test"));
      var failure =
          assertThrows(
              MigrationService.Failure.class,
              () -> service.migrate("Menus/test.yml", false, converter));
      assertEquals("write_failed", failure.reason());
      assertEquals("<-a Name>", Files.readString(file));
      assertEquals("<-a Name>", Files.readString(failure.backup()));
    }
    try (var children = Files.list(file.getParent())) {
      assertTrue(children.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
    }
  }

  @Test
  void rejectsConcurrentMigrationsAcrossServiceInstances() throws Exception {
    Files.writeString(root.resolve("Menus/test.yml"), "<-a Name>");
    Path snapshot = service.migrate("Menus/test.yml", false, converter).backup();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var blocking = mock(LegacyPromptMigrator.class);
    when(blocking.migrate(anyString()))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              assertTrue(release.await(5, TimeUnit.SECONDS));
              return converter.migrate(invocation.getArgument(0));
            });
    try (var executor = Executors.newSingleThreadExecutor()) {
      var task = executor.submit(() -> service.migrate("Menus/test.yml", true, blocking));
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertEquals(
            "busy",
            assertThrows(
                    MigrationService.Failure.class,
                    () -> new MigrationService(data).migrate("Menus/test.yml", false, converter))
                .reason());
        assertEquals(
            "busy",
            assertThrows(
                    MigrationService.Failure.class,
                    () -> service.undo(service.backupArgument(snapshot)))
                .reason());
      } finally {
        release.countDown();
      }
      assertNotNull(task.get(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void completesNestedPathsSpacesAndQuotesWithoutLeakingExcludedFiles() throws Exception {
    Files.writeString(root.resolve("Menus/config.yml"), "content");
    Files.writeString(root.resolve("Menus/menus/my shop.json"), "{}");
    Files.writeString(root.resolve("Menus/archive.jar"), "binary");
    Files.writeString(root.resolve("Menus/old.bak"), "backup");
    Files.createSymbolicLink(root.resolve("Menus/link"), data);
    Files.createDirectories(data.resolve("migration-backups"));
    assertTrue(service.suggestions("").contains("Menus/"));
    assertEquals(List.of("Menus/config.yml", "Menus/menus/"), service.suggestions("Menus/"));
    assertEquals(List.of("Menus/menus/my shop.json"), service.suggestions("Menus/menus/my"));
    assertEquals(List.of("\"Menus/menus/my shop.json\""), service.suggestions("\"Menus/menus/my"));
    assertEquals(
        root.resolve("Menus/menus/my shop.json"), service.resolve("\"Menus/menus/my shop.json\""));
    assertTrue(service.suggestions("CommandPrompterPaper/").isEmpty());
    assertThrows(MigrationService.Failure.class, () -> service.suggestions("../"));
  }

  @Test
  void convertedUnquotedYamlAndQuotedJsonRemainValid() throws Exception {
    String yaml = "command: say <-s Name:{br}Age:>\nvalidated: say <-a Name -iv:required>\n";
    Files.writeString(root.resolve("Menus/plain.yml"), yaml);
    service.migrate("Menus/plain.yml", false, converter);
    var parsed =
        new org.yaml.snakeyaml.Yaml()
            .<java.util.Map<String, String>>load(Files.readString(root.resolve("Menus/plain.yml")));
    assertEquals("say <s::Name:{br}Age:>", parsed.get("command"));
    assertEquals("say <a:Name -iv:required>", parsed.get("validated"));

    String json = "{\"command\":\"say <-a Enter \\\"name\\\">\"}";
    Files.writeString(root.resolve("Menus/quoted.json"), json);
    service.migrate("Menus/quoted.json", false, converter);
    var object =
        com.google.gson.JsonParser.parseString(Files.readString(root.resolve("Menus/quoted.json")))
            .getAsJsonObject();
    assertEquals("say <a:Enter \"name\">", object.get("command").getAsString());
  }

  @Test
  void oversizedFileIsRejectedBeforeBackup() throws Exception {
    Files.write(root.resolve("Menus/large.yml"), new byte[MigrationService.MAX_BYTES + 1]);
    assertEquals(
        "too_large",
        assertThrows(
                MigrationService.Failure.class,
                () -> service.migrate("Menus/large.yml", false, converter))
            .reason());
    assertFalse(Files.exists(data.resolve("migration-backups")));
  }

  @Test
  void undoRestoresExactBytesAndPreservesLaterEditsAndOriginalBackup() throws Exception {
    String original = "\uFEFF# 日本語\r\ncommand: say <-a Name>\r\n";
    Path file = Files.writeString(root.resolve("Menus/my config.yml"), original);
    Path snapshot = service.migrate("Menus/my config.yml", false, converter).backup();
    Files.writeString(file, "later user edits");
    String argument = service.backupArgument(snapshot);
    var undone = service.undo("\"" + argument + "\"");
    assertArrayEquals(original.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
    assertEquals(original, Files.readString(snapshot));
    assertEquals("later user edits", Files.readString(undone.backup()));
    assertNull(service.undo(argument).backup());
    service.undo(service.backupArgument(undone.backup()));
    assertEquals("later user edits", Files.readString(file));
  }

  @Test
  void undoCompletionBrowsesOnlySnapshotsAndRejectsEscapesAndSymlinks() throws Exception {
    assertTrue(service.suggestions("", true).isEmpty());
    Path file = Files.writeString(root.resolve("Menus/my config.yml"), "<-a Name>");
    Path snapshot = service.migrate("Menus/my config.yml", false, converter).backup();
    String argument = service.backupArgument(snapshot);
    String run = argument.substring(0, argument.indexOf('/'));
    assertEquals(List.of(run + "/"), service.suggestions("", true));
    assertEquals(List.of(argument), service.suggestions(run + "/Menus/my", true));
    assertEquals(
        List.of("\"" + argument + "\""), service.suggestions("\"" + run + "/Menus/my", true));
    for (String input : List.of("../Menus/my config.yml", snapshot.toString(), run)) {
      assertEquals(
          "invalid_path",
          assertThrows(MigrationService.Failure.class, () -> service.undo(input)).reason());
    }
    Files.createSymbolicLink(snapshot.getParent().resolve("linked.yml"), file);
    assertThrows(MigrationService.Failure.class, () -> service.undo(run + "/Menus/linked.yml"));
    Files.delete(file);
    Files.createSymbolicLink(file, snapshot);
    assertThrows(MigrationService.Failure.class, () -> service.undo(argument));
    assertEquals("<-a Name>", Files.readString(snapshot));
  }

  @Test
  void failedUndoRetainsBothSnapshotsAndCurrentFile() throws Exception {
    Path file = Files.writeString(root.resolve("Menus/test.yml"), "<-a Name>");
    Path snapshot = service.migrate("Menus/test.yml", false, converter).backup();
    try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
      files
          .when(
              () ->
                  Files.move(
                      any(Path.class),
                      eq(file),
                      eq(StandardCopyOption.ATOMIC_MOVE),
                      eq(StandardCopyOption.REPLACE_EXISTING)))
          .thenThrow(new AtomicMoveNotSupportedException("temporary", "target", "test"));
      var failure =
          assertThrows(
              MigrationService.Failure.class, () -> service.undo(service.backupArgument(snapshot)));
      assertEquals("write_failed", failure.reason());
      assertEquals("<a:Name>", Files.readString(file));
      assertEquals("<a:Name>", Files.readString(failure.backup()));
      assertEquals("<-a Name>", Files.readString(snapshot));
    }
  }

  @Test
  void failedSnapshotPublicationCannotBeSelectedForUndo() throws Exception {
    Path file = Files.writeString(root.resolve("Menus/test.yml"), "<-a Name>");
    try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
      files
          .when(
              () ->
                  Files.move(any(Path.class), any(Path.class), eq(StandardCopyOption.ATOMIC_MOVE)))
          .thenThrow(new java.io.IOException("snapshot publication failed"));
      assertEquals(
          "backup_failed",
          assertThrows(
                  MigrationService.Failure.class,
                  () -> service.migrate("Menus/test.yml", false, converter))
              .reason());
    }
    assertEquals("<-a Name>", Files.readString(file));
    try (var paths = Files.walk(data.resolve("migration-backups"))) {
      assertTrue(paths.noneMatch(Files::isRegularFile));
    }
  }
}
