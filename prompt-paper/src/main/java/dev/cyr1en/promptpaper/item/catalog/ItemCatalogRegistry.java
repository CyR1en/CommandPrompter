package dev.cyr1en.promptpaper.item.catalog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * In-memory registry and lifecycle manager for item catalogs loaded from {@code item-catalogs.yml}.
 *
 * <p>Serves as the single source of truth for catalog lookups at runtime. All readers obtain
 * an immutable {@link CatalogSnapshot} via {@link #getSnapshot()} or {@link #snapshot()},
 * ensuring thread-safety and consistent state across in-flight GUI screens.
 *
 * <h2>Failure semantics</h2>
 * <p>I/O, YAML syntax, category constraint, or material validation failures throw
 * {@link ItemCatalogException}. The previous snapshot is left untouched so that a broken edit
 * on disk does not disrupt live servers.
 */
public class ItemCatalogRegistry {

    /** Default file name for the item catalog configuration. */
    public static final String FILE_NAME = "item-catalogs.yml";

    private final JavaPlugin plugin;
    private final File catalogFile;
    private final Supplier<InputStream> defaultResource;
    private final AtomicReference<CatalogSnapshot> snapshot = new AtomicReference<>(CatalogSnapshot.empty());

    /**
     * Constructs a registry using the plugin's data folder and bundled resource.
     *
     * @param plugin the hosting {@link JavaPlugin}
     */
    public ItemCatalogRegistry(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.catalogFile = new File(plugin.getDataFolder(), FILE_NAME);
        this.defaultResource = () -> plugin.getResource(FILE_NAME);
    }

    /**
     * Constructs a registry for a specific file path without a default resource supplier.
     *
     * @param catalogFile the on-disk file
     */
    public ItemCatalogRegistry(File catalogFile) {
        this(catalogFile, null);
    }

    /**
     * Constructs a registry for testing or non-standard paths with a custom default resource supplier.
     *
     * @param catalogFile the on-disk file
     * @param defaultResource supplier for the bundled default resource (may return null to skip extraction)
     */
    public ItemCatalogRegistry(File catalogFile, Supplier<InputStream> defaultResource) {
        this.plugin = null;
        this.catalogFile = Objects.requireNonNull(catalogFile, "catalogFile must not be null");
        this.defaultResource = defaultResource;
    }

    /**
     * Loads (or reloads) the catalog from disk.
     *
     * <ol>
     *   <li>Ensures the catalog file exists, extracting the default resource if missing.</li>
     *   <li>Parses and validates the YAML document off-side against all limits and invariants.</li>
     *   <li>Atomically swaps in the newly constructed {@link CatalogSnapshot}.</li>
     * </ol>
     *
     * @throws ItemCatalogException if the file cannot be read, exceeds 1 MiB, is malformed,
     *                              violates category limits/patterns, lacks required {@code "all"},
     *                              contains invalid materials, or contains duplicate items
     */
    public void reload() {
        publishReload(prepareReload());
    }

    /** Parses and validates the catalog without changing the active snapshot. */
    public CatalogSnapshot prepareReload() {
        ensureFileExists();
        return ItemCatalogParser.parse(catalogFile);
    }

    /** Atomically publishes a snapshot returned by {@link #prepareReload()}. */
    public void publishReload(CatalogSnapshot prepared) {
        snapshot.set(Objects.requireNonNull(prepared, "prepared snapshot must not be null"));
    }

    /**
     * Returns the currently active immutable {@link CatalogSnapshot}.
     *
     * @return the active snapshot (never null)
     */
    public CatalogSnapshot getSnapshot() {
        return snapshot.get();
    }

    /**
     * Alias for {@link #getSnapshot()}.
     *
     * @return the active snapshot (never null)
     */
    public CatalogSnapshot snapshot() {
        return snapshot.get();
    }

    /**
     * Returns the underlying configuration file on disk.
     */
    public File getCatalogFile() {
        return catalogFile;
    }

    /**
     * Looks up category entries in the active snapshot.
     *
     * @param category the category name
     * @return an {@link Optional} containing the entry list, or empty if not present
     */
    public Optional<List<CatalogEntry>> getCategory(String category) {
        return snapshot.get().getCategory(category);
    }

    /**
     * Returns the list of entries for the given category from the active snapshot, or an empty list.
     *
     * @param category the category name
     * @return unmodifiable list of entries, or empty list
     */
    public List<CatalogEntry> getEntries(String category) {
        return snapshot.get().getEntries(category);
    }

    /**
     * Returns the entries of the required {@code "all"} category from the active snapshot.
     */
    public List<CatalogEntry> all() {
        return snapshot.get().all();
    }

    /**
     * Checks if a category exists in the active snapshot.
     */
    public boolean hasCategory(String category) {
        return snapshot.get().hasCategory(category);
    }

    /**
     * Returns the number of categories in the active snapshot.
     */
    public int categoryCount() {
        return snapshot.get().categoryCount();
    }

    private void ensureFileExists() {
        if (catalogFile.exists()) {
            return;
        }
        if (plugin != null) {
            try {
                plugin.saveResource(FILE_NAME, false);
                if (catalogFile.exists()) {
                    return;
                }
            } catch (Exception ignored) {
                // In test mocks or non-standard environments, fall back to defaultResource supplier
            }
        }
        if (defaultResource != null) {
            try (InputStream in = defaultResource.get()) {
                if (in == null) {
                    throw new ItemCatalogException(
                            "Catalog file is missing and no default resource was available: " + catalogFile);
                }
                File parent = catalogFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                Files.copy(in, catalogFile.toPath());
            } catch (IOException e) {
                throw new ItemCatalogException(
                        "Failed to copy default catalog resource to '" + catalogFile + "': " + e.getMessage(), e);
            }
        } else {
            throw new ItemCatalogException("Catalog file is missing: " + catalogFile);
        }
    }
}
