package dev.cyr1en.promptpaper.command;

import dev.cyr1en.promptcore.parser.LegacyPromptMigrator;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** File operations shared by execution and completion; no server API calls. */
final class MigrationService {
  static final int MAX_BYTES = 8 * 1024 * 1024;
  private static final Set<Path> ACTIVE = ConcurrentHashMap.newKeySet();
  private static final Set<String> EXCLUDED =
      Set.of(
          "jar", "zip", "gz", "class", "png", "jpg", "jpeg", "gif", "db", "sqlite", "dat", "bak",
          "tmp", "log");
  private final Path root;
  private final Path backups;

  record Outcome(LegacyPromptMigrator.Result result, Path backup) {}

  record UndoOutcome(Path file, Path backup) {}

  static final class Failure extends IOException {
    private final String reason;
    private Path backup;

    Failure(String reason) {
      this(reason, null);
    }

    Failure(String reason, Throwable cause) {
      super(reason, cause);
      this.reason = reason;
    }

    String reason() {
      return reason;
    }

    Path backup() {
      return backup;
    }
  }

  MigrationService(Path pluginDataFolder) {
    var data = pluginDataFolder.toAbsolutePath().normalize();
    root = data.getParent();
    backups = data.resolve("migration-backups");
  }

  Outcome migrate(String input, boolean dryRun, LegacyPromptMigrator converter) throws IOException {
    Path file = resolve(input);
    Path activeFile = acquire(file);
    try {
      byte[] original = read(file);
      String source;
      try {
        source = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(original)).toString();
      } catch (CharacterCodingException e) {
        throw new Failure("unsupported_file", e);
      }
      if (source
          .codePoints()
          .anyMatch(c -> Character.isISOControl(c) && c != '\r' && c != '\n' && c != '\t')) {
        throw new Failure("unsupported_file");
      }
      var result = converter.migrate(source);
      if (dryRun || result.changes().isEmpty() || !result.diagnostics().isEmpty()) {
        return new Outcome(result, null);
      }
      Path backup = createBackup(file, original);
      replace(file, original, result.content().getBytes(StandardCharsets.UTF_8), backup);
      return new Outcome(result, backup);
    } finally {
      ACTIVE.remove(activeFile);
    }
  }

  UndoOutcome undo(String input) throws IOException {
    Path snapshot = resolve(input, backups);
    Path relative = backups.relativize(snapshot);
    if (relative.getNameCount() < 2) throw new Failure("invalid_path");
    if (!Files.exists(snapshot, LinkOption.NOFOLLOW_LINKS)) throw new Failure("not_found");
    if (!Files.isRegularFile(snapshot, LinkOption.NOFOLLOW_LINKS) || excluded(snapshot)) {
      throw new Failure("unsupported_file");
    }
    Path file = resolve(relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/'));
    Path activeFile = acquire(file);
    try {
      byte[] original = read(file);
      byte[] restored = read(snapshot);
      if (Arrays.equals(original, restored)) return new UndoOutcome(file, null);
      Path backup = createBackup(file, original);
      replace(file, original, restored, backup);
      return new UndoOutcome(file, backup);
    } finally {
      ACTIVE.remove(activeFile);
    }
  }

  String backupArgument(Path backup) {
    return backups.relativize(backup).toString().replace('\\', '/');
  }

  private Path acquire(Path file) throws IOException {
    if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new Failure("not_found");
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || excluded(file)) {
      throw new Failure("unsupported_file");
    }
    Path activeFile;
    try {
      activeFile = file.toRealPath();
    } catch (IOException e) {
      throw new Failure("read_failed", e);
    }
    if (!ACTIVE.add(activeFile)) throw new Failure("busy");
    return activeFile;
  }

  private Path createBackup(Path file, byte[] original) throws IOException {
    Path backup;
    Path temporary = null;
    try {
      createBackupDirectories(backups);
      // createTempDirectory gives each backup a private directory on POSIX filesystems.
      Path run =
          Files.createTempDirectory(backups, Instant.now().toString().replace(':', '-') + "-");
      backup = run.resolve(root.relativize(file));
      createBackupDirectories(backup.getParent());
      temporary = Files.createTempFile(backup.getParent(), ".snapshot-", ".tmp");
      Files.write(
          temporary, original, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        channel.force(true);
      }
      // Only complete snapshots become visible to undo and its file suggestions.
      Files.move(temporary, backup, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException | SecurityException e) {
      throw new Failure("backup_failed", e);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // Any partial snapshot remains hidden and excluded from undo.
        }
      }
    }
    return backup;
  }

  private void replace(Path file, byte[] original, byte[] updated, Path backup) throws IOException {
    Path temporary = null;
    try {
      checkLinks(file);
      temporary = Files.createTempFile(file.getParent(), ".cmdp-migrate-", ".tmp");
      Files.copy(
          file, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
      try (var channel =
          FileChannel.open(
              temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        var bytes = ByteBuffer.wrap(updated);
        while (bytes.hasRemaining()) channel.write(bytes);
        channel.force(true);
      }
      checkLinks(file);
      if (!Arrays.equals(original, read(file))) throw new Failure("changed");
      // No non-atomic fallback: an unsupported filesystem leaves the original intact.
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (Failure e) {
      e.backup = backup;
      throw e;
    } catch (IOException | SecurityException e) {
      var failure = new Failure("write_failed", e);
      failure.backup = backup;
      throw failure;
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // A leftover .tmp is excluded from migration and completion.
        }
      }
    }
  }

  List<String> suggestions(String input) throws IOException {
    return suggestions(input, false);
  }

  List<String> suggestions(String input, boolean undo) throws IOException {
    Path base = undo ? backups : root;
    String prefix = input.startsWith("\"") ? input.substring(1) : input;
    if (prefix.endsWith("\"")) prefix = prefix.substring(0, prefix.length() - 1);
    int slash = prefix.lastIndexOf('/');
    String parent = slash < 0 ? "" : prefix.substring(0, slash + 1);
    String name = prefix.substring(slash + 1).toLowerCase(Locale.ROOT);
    Path directory = parent.isEmpty() ? base : resolve(parent, base);
    checkLinks(directory);
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
    var values = new ArrayList<String>();
    try (var children = Files.newDirectoryStream(directory)) {
      // ponytail: cap large directories; use an index if browsing >4096 siblings is needed.
      int inspected = 0;
      for (var child : children) {
        if (++inspected > 4096) break;
        if (Files.isSymbolicLink(child) || (!undo && child.startsWith(backups)) || excluded(child))
          continue;
        if (!child.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(name)) continue;
        boolean folder = Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS);
        if (!folder && !Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) continue;
        String value = parent + child.getFileName() + (folder ? "/" : "");
        if (value.contains("\"") || value.codePoints().anyMatch(Character::isISOControl)) continue;
        values.add(input.startsWith("\"") ? "\"" + value + (folder ? "" : "\"") : value);
      }
    }
    return values.stream().sorted().limit(100).toList();
  }

  Path resolve(String input) throws IOException {
    return resolve(input, root);
  }

  private Path resolve(String input, Path base) throws IOException {
    String value = input.strip();
    if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
      value = value.substring(1, value.length() - 1);
    }
    try {
      Path relative = Path.of(value);
      if (value.isBlank()
          || relative.isAbsolute()
          || value.contains("\\")
          || value.contains("\"")
          || value.codePoints().anyMatch(Character::isISOControl))
        throw new Failure("invalid_path");
      for (var part : relative) if (part.toString().equals("..")) throw new Failure("invalid_path");
      Path file = base.resolve(relative).normalize();
      if (!file.startsWith(base)
          || file.equals(base)
          || (base.equals(root) && file.startsWith(backups))) throw new Failure("invalid_path");
      checkLinks(file);
      return file;
    } catch (InvalidPathException e) {
      throw new Failure("invalid_path", e);
    }
  }

  private void checkLinks(Path path) throws IOException {
    Path current = root;
    if (Files.isSymbolicLink(current)) throw new Failure("invalid_path");
    for (Path part : root.relativize(path)) {
      current = current.resolve(part);
      if (Files.isSymbolicLink(current)) throw new Failure("invalid_path");
    }
  }

  private void createBackupDirectories(Path directory) throws IOException {
    checkLinks(directory);
    Files.createDirectories(directory);
    checkLinks(directory);
  }

  private byte[] read(Path path) throws IOException {
    try {
      if (Files.size(path) > MAX_BYTES) throw new Failure("too_large");
      try (var stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
        byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new Failure("too_large");
        return bytes;
      }
    } catch (Failure e) {
      throw e;
    } catch (IOException | SecurityException e) {
      throw new Failure("read_failed", e);
    }
  }

  private static boolean excluded(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.startsWith(".") || EXCLUDED.contains(name.substring(name.lastIndexOf('.') + 1));
  }
}
