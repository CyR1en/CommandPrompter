package dev.cyr1en.promptpaper.preset;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * In-memory cache for the prompt and post-command definitions loaded from {@code presets.json}.
 *
 * <p>The registry is the <i>single source of truth</i> for preset lookups at runtime: {@code
 * <@id>} and {@code <!@id>} tag resolution both go through {@link #getPrompt(String)} and
 * {@link #getPostCommand(String)}. It is loaded once during plugin enable, refreshed in place by
 * {@link #reload()}, and safe to query from any thread.
 *
 * <h2>Thread safety</h2>
 *
 * <p>Internal maps are rebuilt as <b>immutable</b> snapshots on every successful reload and
 * swapped in via a {@code volatile} reference. Readers never see a partially-built map, and a
 * failed reload leaves the previous snapshot intact.
 *
 * <h2>Disk layout</h2>
 *
 * <p>The file is {@code <plugin.getDataFolder()>/presets.json}. On first load, if the file is
 * missing, the registry extracts the default {@code presets.json} resource bundled with the
 * plugin via {@link JavaPlugin#saveResource(String, boolean)}.
 *
 * <h2>Failure semantics</h2>
 *
 * <p>I/O and parse failures throw {@link PresetLoadException}. The previous cache is left
 * untouched so a broken edit on disk does not silently wipe live presets.
 */
public class PresetRegistry {

  /** Name of the JSON file on disk and as a bundled resource. */
  public static final String FILE_NAME = "presets.json";

  private final JavaPlugin plugin;
  private final java.util.function.Supplier<InputStream> defaultResource;
  private final Gson gson;
  private final File promptsFile;
  private volatile Map<String, PromptDefinition> prompts = Map.of();
  private volatile Map<String, PostCommand> postCommands = Map.of();

  /**
   * Builds a registry that reads from {@code <plugin.getDataFolder()>/presets.json} and uses
   * {@link PresetGson#presetGson()} for parsing.
   */
  public PresetRegistry(JavaPlugin plugin) {
    this.plugin = plugin;
    this.defaultResource = null;
    this.gson = PresetGson.presetGson();
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
    this.plugin = null;
    this.defaultResource = defaultResource;
    this.gson = PresetGson.presetGson();
    this.promptsFile = promptsFile;
  }

  /**
   * Loads (or reloads) the registry from disk.
   *
   * <p>Steps:
   *
   * <ol>
   *   <li>If the file is missing, extract the bundled default resource (no-op when this
   *       instance was built via the test-only constructor and the supplier is {@code null}).
   *   <li>Read and parse the file as a {@link PresetConfig}.
   *   <li>Build immutable prompt and post-command maps keyed by {@code id}.
   *   <li>Atomically swap them in via the {@code volatile} references.
   * </ol>
   *
   * @throws PresetLoadException if the file cannot be read or parsed
   */
  @SuppressWarnings("null")
  public void reload() {
    try {
      ensureFileExists();
      try (var reader = Files.newBufferedReader(promptsFile.toPath(), StandardCharsets.UTF_8)) {
        var config = gson.fromJson(reader, PresetConfig.class);
        if (config == null) {
          // Gson returns null for an empty document. Treat as cleared.
          this.prompts = Map.of();
          this.postCommands = Map.of();
          logInfo("presets.json is empty; registry cleared");
          return;
        }
        var newPrompts = buildIdMap(
            config.prompts(), PromptDefinition::id, "prompt", this::logWarning);
        var newPostCommands = buildIdMap(
            config.postCommands(), PostCommand::id, "post-command", this::logWarning);
        this.prompts = newPrompts;
        this.postCommands = newPostCommands;
      }
    } catch (IOException | JsonParseException e) {
      throw new PresetLoadException("Failed to load " + FILE_NAME + ": " + e.getMessage(), e);
    }
  }

  /** Looks up a prompt by its id. Returns empty if not registered. */
  public Optional<PromptDefinition> getPrompt(String id) {
    if (id == null) return Optional.empty();
    return Optional.ofNullable(prompts.get(id));
  }

  /** Looks up a post-command by its id. Returns empty if not registered. */
  public Optional<PostCommand> getPostCommand(String id) {
    if (id == null) return Optional.empty();
    return Optional.ofNullable(postCommands.get(id));
  }

  /** Number of currently-registered prompt definitions. */
  public int promptCount() {
    return prompts.size();
  }

  /** Number of currently-registered post-command definitions. */
  public int postCommandCount() {
    return postCommands.size();
  }

  /** Returns an unmodifiable set of all registered prompt ids. */
  public Set<String> getPromptIds() {
    return prompts.keySet();
  }

  /** Returns an unmodifiable set of all registered post-command ids. */
  public Set<String> getPostCommandIds() {
    return postCommands.keySet();
  }

  /** The on-disk file this registry reads from. */
  public File getPromptsFile() {
    return promptsFile;
  }

  // ------------------------------------------------------------------
  // Internals
  // ------------------------------------------------------------------

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

  private static <T> Map<String, T> buildIdMap(
      List<T> items,
      java.util.function.Function<T, String> idExtractor,
      String label,
      java.util.function.Consumer<String> warn) {
    if (items == null || items.isEmpty()) return Map.of();
    Map<String, T> map = new LinkedHashMap<>(items.size());
    for (var item : items) {
      var id = idExtractor.apply(item);
      if (id == null || id.isEmpty()) {
        warn.accept("Skipping " + label + " with empty id");
        continue;
      }
      var prev = map.put(id, item);
      if (prev != null) {
        warn.accept("Duplicate " + label + " id '" + id + "' (keeping the last definition)");
      }
    }
    return Map.copyOf(map);
  }

  private void logInfo(String msg) {
    if (plugin != null) plugin.getLogger().info(msg);
  }

  private void logWarning(String msg) {
    if (plugin != null) plugin.getLogger().warning(msg);
  }

  /** Thrown when the prompts file cannot be read or parsed. The previous cache is preserved. */
  public static class PresetLoadException extends RuntimeException {
    public PresetLoadException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
