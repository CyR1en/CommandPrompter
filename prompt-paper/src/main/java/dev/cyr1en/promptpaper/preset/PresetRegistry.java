package dev.cyr1en.promptpaper.preset;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import dev.cyr1en.promptpaper.CommandPrompter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * In-memory cache for the prompt and post-command definitions loaded from {@code presets.json}.
 *
 * <p>The registry is the <i>single source of truth</i> for preset lookups at runtime: {@code
 * <@id>}, {@code <!@id>}, and {@code <#@id>} tag resolution all go through the active {@link
 * PresetSnapshot}. It is loaded once during plugin enable, refreshed in place by {@link
 * #reload()}, and safe to query concurrently from any thread.
 *
 * <h2>Thread safety</h2>
 *
 * <p>Preset definitions across all four categories (prompts, post commands, approval gates,
 * conditional post commands) are loaded and validated locally and atomically published via a
 * single {@code volatile PresetSnapshot} reference. Readers always see a consistent generation,
 * and a failed reload leaves the previous snapshot intact.
 *
 * <h2>Disk layout & limits</h2>
 *
 * <p>The file is {@code <plugin.getDataFolder()>/presets.json}. Maximum allowed file size is 1 MiB,
 * and each category permits at most 256 definitions. Duplicate IDs within a category and cross-kind
 * ID collisions across categories are strictly rejected.
 */
public class PresetRegistry {

  /** Name of the JSON file on disk and as a bundled resource. */
  public static final String FILE_NAME = "presets.json";

  /** Maximum allowed file size in bytes (1 MiB) before parsing. */
  public static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;

  /** Maximum allowed definitions per array/kind. */
  public static final int MAX_ENTRIES_PER_KIND = 256;

  private final JavaPlugin plugin;
  private final java.util.function.Supplier<InputStream> defaultResource;
  private final java.util.function.Supplier<TemplateSyntax> syntaxSupplier;
  private final File promptsFile;
  private volatile PresetSnapshot snapshot = PresetSnapshot.empty();

  /**
   * Builds a registry that reads from {@code <plugin.getDataFolder()>/presets.json} and uses
   * {@link PresetGson#presetGson()} for parsing.
   */
  public PresetRegistry(JavaPlugin plugin) {
    this(plugin, () -> {
      if (plugin instanceof CommandPrompter cp && cp.getConfigLoader() != null && cp.getConfigLoader().getConfig() != null) {
        return cp.getConfigLoader().getConfig().templateSyntax();
      }
      return TemplateSyntax.DEFAULT;
    });
  }

  public PresetRegistry(JavaPlugin plugin, java.util.function.Supplier<TemplateSyntax> syntaxSupplier) {
    this.plugin = plugin;
    this.defaultResource = null;
    this.syntaxSupplier = syntaxSupplier != null ? syntaxSupplier : () -> TemplateSyntax.DEFAULT;
    this.promptsFile = new File(plugin.getDataFolder(), FILE_NAME);
  }

  /**
   * Convenience constructor for tests and non-Bukkit embedding: lets the caller specify the
   * file path and the default-resource supplier directly.
   *
   * @param promptsFile the on-disk JSON file
   * @param defaultResource supplier for the bundled default (may return {@code null} to skip
   *     extraction when the file is missing)
   */
  public PresetRegistry(File promptsFile, java.util.function.Supplier<InputStream> defaultResource) {
    this(promptsFile, defaultResource, () -> TemplateSyntax.DEFAULT);
  }

  public PresetRegistry(
      File promptsFile,
      java.util.function.Supplier<InputStream> defaultResource,
      java.util.function.Supplier<TemplateSyntax> syntaxSupplier) {
    this.plugin = null;
    this.defaultResource = defaultResource;
    this.syntaxSupplier = syntaxSupplier != null ? syntaxSupplier : () -> TemplateSyntax.DEFAULT;
    this.promptsFile = promptsFile;
  }

  /**
   * Loads (or reloads) the registry from disk atomically.
   *
   * <p>Steps:
   * <ol>
   *   <li>If missing, extract the bundled default resource if available.
   *   <li>Enforce file size bounds (&lt;= 1 MiB) without unbounded memory reads.
   *   <li>Parse the JSON document and validate all four categories: prompts, post_commands,
   *       approval_gates, and conditional_post_commands.
   *   <li>Enforce category limits (&lt;= 256 entries each), validate non-blank IDs, and reject
   *       both duplicate IDs and cross-kind ID collisions.
   *   <li>Atomically swap in a new {@link PresetSnapshot} reference.
   * </ol>
   *
   * @throws PresetLoadException if the file cannot be read, parsed, or validated. The previous
   *     snapshot is left untouched.
   */
  @SuppressWarnings("null")
  public void reload() {
    publishReload(prepareReload());
  }

  /** Parses and validates the on-disk registry without changing the active snapshot. */
  public PresetSnapshot prepareReload() {
    return prepareReload(syntaxSupplier.get());
  }

  /**
   * Parses and validates the on-disk registry using the supplied staged template syntax without
   * changing the active snapshot.
   */
  @SuppressWarnings("null")
  public PresetSnapshot prepareReload(TemplateSyntax syntax) {
    try {
      ensureFileExists();
      byte[] bytes;
      try (var in = Files.newInputStream(promptsFile.toPath())) {
        bytes = in.readNBytes((int) MAX_FILE_SIZE_BYTES + 1);
      }
      if (bytes.length > MAX_FILE_SIZE_BYTES) {
        throw new PresetLoadException(
            "File '" + sourcePath() + "' exceeds maximum allowed size of 1 MiB ("
                + MAX_FILE_SIZE_BYTES + " bytes)",
            null);
      }

      String content = new String(bytes, StandardCharsets.UTF_8).trim();
      if (content.isEmpty()) {
        return new PresetSnapshot(
            Map.of(), Map.of(), Map.of(), Map.of(), this.snapshot.generation() + 1);
      }

      var document = JsonParser.parseString(content);
      if (document == null || document.isJsonNull()) {
        return new PresetSnapshot(
            Map.of(), Map.of(), Map.of(), Map.of(), this.snapshot.generation() + 1);
      }

      if (!document.isJsonObject()) {
        throw malformed("document", "<root>", "root",
            new IllegalArgumentException("expected a JSON object"));
      }

      var root = document.getAsJsonObject();
      Map<String, String> seenIds = new LinkedHashMap<>();
      Gson gson = PresetGson.presetGson(syntax != null ? syntax : TemplateSyntax.DEFAULT);

      var newPrompts =
          loadDefinitions(gson, root, "prompts", "prompt", PromptDefinition.class, PromptDefinition::id, seenIds);
      var newPostCommands =
          loadDefinitions(gson, root, "post_commands", "post-command", PostCommand.class, PostCommand::id, seenIds);
      var newApprovalGates =
          loadDefinitions(
              gson,
              root,
              "approval_gates",
              "approval gate",
              ApprovalGateDefinition.class,
              ApprovalGateDefinition::id,
              seenIds);
      var newConditionalPostCommands =
          loadDefinitions(
              gson,
              root,
              "conditional_post_commands",
              "conditional post-command",
              ConditionalPostCommandDefinition.class,
              ConditionalPostCommandDefinition::id,
              seenIds);

      long nextGeneration = this.snapshot.generation() + 1;
      return new PresetSnapshot(
          newPrompts, newPostCommands, newApprovalGates, newConditionalPostCommands, nextGeneration);
    } catch (PresetLoadException e) {
      throw e;
    } catch (IOException | JsonParseException | IllegalArgumentException | NullPointerException e) {
      throw new PresetLoadException(
          "Failed to load presets from '" + sourcePath() + "': " + safeMessage(e), e);
    } catch (RuntimeException e) {
      throw new PresetLoadException(
          "Failed to load presets from '" + sourcePath() + "': " + safeMessage(e), e);
    }
  }

  /** Atomically publishes a snapshot returned by {@link #prepareReload()}. */
  public void publishReload(PresetSnapshot prepared) {
    if (prepared == null) throw new IllegalArgumentException("prepared snapshot must not be null");
    this.snapshot = prepared;
  }

  /**
   * Returns the current immutable snapshot of all registered presets.
   */
  public PresetSnapshot getSnapshot() {
    return this.snapshot;
  }

  /**
   * Alias for {@link #getSnapshot()} providing concise access to the current snapshot.
   */
  public PresetSnapshot snapshot() {
    return this.snapshot;
  }

  /** Looks up a prompt by its id. Returns empty if not registered. */
  public Optional<PromptDefinition> getPrompt(String id) {
    var s = this.snapshot;
    return s.getPrompt(id);
  }

  /** Looks up a post-command by its id. Returns empty if not registered. */
  public Optional<PostCommand> getPostCommand(String id) {
    var s = this.snapshot;
    return s.getPostCommand(id);
  }

  /** Looks up an approval gate by its id. Returns empty if not registered. */
  public Optional<ApprovalGateDefinition> getApprovalGate(String id) {
    var s = this.snapshot;
    return s.getApprovalGate(id);
  }

  /** Looks up a conditional post-command by its id. Returns empty if not registered. */
  public Optional<ConditionalPostCommandDefinition> getConditionalPostCommand(String id) {
    var s = this.snapshot;
    return s.getConditionalPostCommand(id);
  }

  /** Number of currently-registered prompt definitions. */
  public int promptCount() {
    var s = this.snapshot;
    return s.promptCount();
  }

  /** Number of currently-registered post-command definitions. */
  public int postCommandCount() {
    var s = this.snapshot;
    return s.postCommandCount();
  }

  /** Number of currently-registered approval gate definitions. */
  public int approvalGateCount() {
    var s = this.snapshot;
    return s.approvalGateCount();
  }

  /** Number of currently-registered conditional post-command definitions. */
  public int conditionalPostCommandCount() {
    var s = this.snapshot;
    return s.conditionalPostCommandCount();
  }

  /** Returns an unmodifiable set of all registered prompt ids. */
  public Set<String> getPromptIds() {
    var s = this.snapshot;
    return s.getPromptIds();
  }

  /** Returns an unmodifiable set of all registered post-command ids. */
  public Set<String> getPostCommandIds() {
    var s = this.snapshot;
    return s.getPostCommandIds();
  }

  /** Returns an unmodifiable set of all registered approval gate ids. */
  public Set<String> getApprovalGateIds() {
    var s = this.snapshot;
    return s.getApprovalGateIds();
  }

  /** Returns an unmodifiable set of all registered conditional post-command ids. */
  public Set<String> getConditionalPostCommandIds() {
    var s = this.snapshot;
    return s.getConditionalPostCommandIds();
  }

  /** The on-disk file this registry reads from. */
  public File getPromptsFile() {
    return promptsFile;
  }

  // ------------------------------------------------------------------
  // Internals
  // ------------------------------------------------------------------

  private <T> Map<String, T> loadDefinitions(
      Gson gson,
      JsonObject root,
      String arrayName,
      String kindLabel,
      Class<T> type,
      java.util.function.Function<T, String> idExtractor,
      Map<String, String> seenIds) {
    var array = readArray(root, arrayName);
    if (array == null || array.isEmpty()) {
      return Map.of();
    }

    if (array.size() > MAX_ENTRIES_PER_KIND) {
      throw new PresetLoadException(
          "Array '" + arrayName + "' in '" + sourcePath() + "' contains " + array.size()
              + " entries, exceeding maximum allowed limit of " + MAX_ENTRIES_PER_KIND,
          null);
    }

    Map<String, T> map = new LinkedHashMap<>(array.size());
    for (int index = 0; index < array.size(); index++) {
      var element = array.get(index);
      var location = arrayName + "[" + index + "]";
      var id = readId(element);

      if (element == null || !element.isJsonObject()) {
        throw malformed(kindLabel, id, location, new IllegalArgumentException("expected a JSON object"));
      }

      T definition;
      try {
        definition = gson.fromJson(element, type);
        if (definition == null) {
          throw new NullPointerException(kindLabel + " definition deserialized to null");
        }
      } catch (RuntimeException e) {
        throw malformed(kindLabel, id, location, e);
      }

      String defId = idExtractor.apply(definition);
      if (defId == null || defId.isBlank()) {
        throw malformed(
            kindLabel,
            defId == null ? "<null>" : defId,
            location,
            new IllegalArgumentException("id must not be null or blank"));
      }

      if (seenIds.containsKey(defId)) {
        var existingLocation = seenIds.get(defId);
        if (map.containsKey(defId)) {
          throw malformed(
              kindLabel,
              defId,
              location,
              new IllegalArgumentException(
                  "Duplicate " + kindLabel + " id '" + defId + "' already defined at " + existingLocation));
        } else {
          throw malformed(
              kindLabel,
              defId,
              location,
              new IllegalArgumentException(
                  "Preset id '" + defId + "' collides with existing definition at " + existingLocation));
        }
      }

      seenIds.put(defId, location);
      map.put(defId, definition);
    }

    return Collections.unmodifiableMap(new LinkedHashMap<>(map));
  }

  private JsonArray readArray(JsonObject root, String name) {
    var element = root.get(name);
    if (element == null || element.isJsonNull()) return null;
    if (!element.isJsonArray()) {
      throw malformed(name, "<unknown>", name,
          new IllegalArgumentException("expected an array"));
    }
    return element.getAsJsonArray();
  }

  private static String readId(JsonElement element) {
    if (element == null || !element.isJsonObject()) return "<unknown>";
    try {
      var id = element.getAsJsonObject().get("id");
      return id != null && id.isJsonPrimitive() ? id.getAsString() : "<unknown>";
    } catch (RuntimeException ignored) {
      return "<unknown>";
    }
  }

  private PresetLoadException malformed(String kind, String id, String location, Throwable cause) {
    return new PresetLoadException(
        "Invalid " + kind + " preset id '" + id + "' at '" + sourcePath() + "' ("
            + location + "): " + safeMessage(cause),
        cause);
  }

  private String sourcePath() {
    return promptsFile.toPath().toAbsolutePath().normalize().toString();
  }

  private static String safeMessage(Throwable throwable) {
    if (throwable == null) return "unknown";
    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String msg = root.getMessage();
    if (msg != null && !msg.isBlank()) {
      return msg;
    }
    return throwable.getMessage() == null
        ? throwable.getClass().getSimpleName()
        : throwable.getMessage();
  }

  private void ensureFileExists() throws IOException {
    if (promptsFile.exists()) return;
    if (plugin != null) {
      plugin.saveResource(FILE_NAME, false);
      return;
    }
    if (defaultResource != null) {
      try (InputStream in = defaultResource.get()) {
        if (in == null) {
          throw new IOException(
              "Prompts file is missing and no default resource was provided: " + promptsFile);
        }
        var parent = promptsFile.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.copy(in, promptsFile.toPath());
      }
    } else {
      throw new IOException("Prompts file is missing: " + promptsFile);
    }
  }

  private void logInfo(String msg) {
    if (plugin != null) plugin.getLogger().info(msg);
  }

  /** Thrown when the prompts file cannot be read, parsed, or validated. The previous cache is preserved. */
  public static class PresetLoadException extends RuntimeException {
    public PresetLoadException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
