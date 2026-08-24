package dev.cyr1en.promptpaper.item.catalog;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemCatalogRegistryTest extends MockBukkitTest {

    private static final String SAMPLE_YAML = """
            categories:
              all:
                - minecraft:stone
                - diamond_sword
                - bread
              blocks:
                - minecraft:stone
              combat:
                - diamond_sword
            """;

    @TempDir
    File tempDir;

    private File catalogFile;

    @BeforeEach
    void setUpRegistry() {
        catalogFile = new File(tempDir, ItemCatalogRegistry.FILE_NAME);
    }

    private ItemCatalogRegistry createRegistry(String defaultContent) {
        Supplier<InputStream> supplier = defaultContent == null
                ? () -> null
                : () -> new ByteArrayInputStream(defaultContent.getBytes(StandardCharsets.UTF_8));
        return new ItemCatalogRegistry(catalogFile, supplier);
    }

    @Test
    @DisplayName("Loads successfully from an existing YAML file")
    void testLoadExistingFile() throws IOException {
        Files.writeString(catalogFile.toPath(), SAMPLE_YAML, StandardCharsets.UTF_8);

        ItemCatalogRegistry registry = createRegistry(null);
        registry.reload();

        CatalogSnapshot snapshot = registry.getSnapshot();
        assertNotNull(snapshot);
        assertEquals(3, snapshot.categoryCount());
        assertTrue(registry.hasCategory("all"));
        assertTrue(registry.hasCategory("blocks"));
        assertTrue(registry.hasCategory("combat"));
        assertEquals(3, registry.all().size());
        assertEquals(1, registry.getEntries("blocks").size());
        assertEquals(1, registry.getEntries("combat").size());
    }

    @Test
    @DisplayName("Reload is idempotent")
    void testReloadIdempotence() throws IOException {
        Files.writeString(catalogFile.toPath(), SAMPLE_YAML, StandardCharsets.UTF_8);

        ItemCatalogRegistry registry = createRegistry(null);
        registry.reload();
        CatalogSnapshot snap1 = registry.getSnapshot();

        registry.reload();
        CatalogSnapshot snap2 = registry.getSnapshot();

        assertEquals(snap1, snap2);
        assertEquals(3, registry.categoryCount());
    }

    @Test
    @DisplayName("Prepared catalog is invisible until published")
    void testPreparedReloadIsInvisibleUntilPublished() throws IOException {
        Files.writeString(catalogFile.toPath(), SAMPLE_YAML, StandardCharsets.UTF_8);
        ItemCatalogRegistry registry = createRegistry(null);
        CatalogSnapshot oldSnapshot = registry.getSnapshot();

        CatalogSnapshot prepared = registry.prepareReload();

        assertSame(oldSnapshot, registry.getSnapshot());
        assertEquals(3, prepared.categoryCount());
        registry.publishReload(prepared);
        assertSame(prepared, registry.getSnapshot());
    }

    @Test
    @DisplayName("Failed reload preserves previous snapshot intact")
    void testFailedReloadPreservesPreviousSnapshot() throws IOException {
        Files.writeString(catalogFile.toPath(), SAMPLE_YAML, StandardCharsets.UTF_8);

        ItemCatalogRegistry registry = createRegistry(null);
        registry.reload();
        CatalogSnapshot previousSnapshot = registry.getSnapshot();
        assertEquals(3, previousSnapshot.categoryCount());
        assertEquals(3, previousSnapshot.all().size());

        // Overwrite disk file with corrupted / invalid YAML (e.g. invalid material)
        String invalidYaml = """
                categories:
                  all:
                    - completely_invalid_material_xyz
                """;
        Files.writeString(catalogFile.toPath(), invalidYaml, StandardCharsets.UTF_8);

        ItemCatalogException ex = assertThrows(ItemCatalogException.class, registry::reload);
        assertTrue(ex.getMessage().contains("Unknown material"));

        // Prior snapshot MUST remain active and untouched
        CatalogSnapshot currentSnapshot = registry.getSnapshot();
        assertSame(previousSnapshot, currentSnapshot);
        assertEquals(3, currentSnapshot.categoryCount());
        assertTrue(registry.hasCategory("blocks"));
        assertEquals(Material.STONE, registry.getEntries("blocks").getFirst().material());
    }

    @Test
    @DisplayName("Failed reload due to YAML syntax preserves previous snapshot")
    void testFailedReloadSyntaxPreservesSnapshot() throws IOException {
        Files.writeString(catalogFile.toPath(), SAMPLE_YAML, StandardCharsets.UTF_8);

        ItemCatalogRegistry registry = createRegistry(null);
        registry.reload();
        CatalogSnapshot previousSnapshot = registry.getSnapshot();

        Files.writeString(catalogFile.toPath(), "categories: [ this is invalid syntax", StandardCharsets.UTF_8);
        assertThrows(ItemCatalogException.class, registry::reload);

        assertSame(previousSnapshot, registry.getSnapshot());
    }

    @Test
    @DisplayName("Missing file is automatically extracted from default resource")
    void testDefaultResourceExtraction() {
        assertFalse(catalogFile.exists());

        ItemCatalogRegistry registry = createRegistry(SAMPLE_YAML);
        registry.reload();

        assertTrue(catalogFile.exists());
        assertEquals(3, registry.categoryCount());
        assertTrue(registry.hasCategory("all"));
    }

    @Test
    @DisplayName("Missing file with null default supplier throws ItemCatalogException")
    void testMissingFileWithoutDefaultThrows() {
        assertFalse(catalogFile.exists());

        ItemCatalogRegistry registry = createRegistry(null);
        ItemCatalogException ex = assertThrows(ItemCatalogException.class, registry::reload);
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    @DisplayName("File exceeding 1 MiB on disk is rejected")
    void testOversizedFileOnDisk() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("categories:\n  all:\n");
        while (sb.length() < 1024 * 1024 + 50) {
            sb.append("    # ").append("b".repeat(100)).append("\n");
        }
        Files.writeString(catalogFile.toPath(), sb.toString(), StandardCharsets.UTF_8);

        ItemCatalogRegistry registry = createRegistry(null);
        ItemCatalogException ex = assertThrows(ItemCatalogException.class, registry::reload);
        assertTrue(ex.getMessage().contains("1 MiB"));
    }

    @Test
    @DisplayName("Direct bundled resource in src/main/resources/item-catalogs.yml is valid")
    void testBundledResourceIsValid() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("item-catalogs.yml")) {
            assertNotNull(in, "item-catalogs.yml must exist as a classpath resource");
            CatalogSnapshot snapshot = ItemCatalogParser.parse(in, "item-catalogs.yml");
            assertTrue(snapshot.categoryCount() > 0);
            assertTrue(snapshot.hasCategory("all"));
            assertFalse(snapshot.all().isEmpty());
        }
    }
}
