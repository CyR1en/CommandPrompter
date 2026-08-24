package dev.cyr1en.promptpaper.item.catalog;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemCatalogParserTest extends MockBukkitTest {

    private static final String VALID_YAML = """
            categories:
              all:
                - minecraft:stone
                - diamond_sword
                - iron_ingot
              blocks:
                - minecraft:stone
              tools:
                - diamond_sword
            """;

    @Test
    @DisplayName("Valid YAML with categories section parses successfully")
    void testValidYaml() {
        CatalogSnapshot snapshot = ItemCatalogParser.parse(VALID_YAML, "test.yml");
        assertEquals(3, snapshot.categoryCount());
        assertEquals(List.of("all", "blocks", "tools"), snapshot.getCategoryNames());
        assertEquals(3, snapshot.all().size());
        assertEquals(1, snapshot.getEntries("blocks").size());
        assertEquals(1, snapshot.getEntries("tools").size());
        assertEquals(Material.STONE, snapshot.getEntries("blocks").getFirst().material());
    }

    @Test
    @DisplayName("Valid YAML with top-level categories without categories key parses successfully")
    void testTopLevelCategoriesYaml() {
        String yaml = """
                all:
                  - stone
                  - diamond
                building_blocks:
                  - stone
                """;
        CatalogSnapshot snapshot = ItemCatalogParser.parse(yaml, "top_level.yml");
        assertEquals(2, snapshot.categoryCount());
        assertTrue(snapshot.hasCategory("all"));
        assertTrue(snapshot.hasCategory("building_blocks"));
        assertEquals(2, snapshot.all().size());
    }

    @Test
    @DisplayName("Map style entry with item/material key parses correctly")
    void testMapStyleEntry() {
        String yaml = """
                categories:
                  all:
                    - item: diamond_sword
                    - material: stone
                """;
        CatalogSnapshot snapshot = ItemCatalogParser.parse(yaml, "map_entry.yml");
        assertEquals(2, snapshot.all().size());
        assertEquals(Material.DIAMOND_SWORD, snapshot.all().get(0).material());
        assertEquals(Material.STONE, snapshot.all().get(1).material());
    }

    @Test
    @DisplayName("Enforces 1 MiB size cap")
    void testSizeCapLimit() {
        // Create a string larger than 1 MiB (1,048,576 bytes)
        StringBuilder sb = new StringBuilder();
        sb.append("categories:\n  all:\n");
        while (sb.length() < 1024 * 1024 + 100) {
            sb.append("    # ").append("a".repeat(100)).append("\n");
        }
        ItemCatalogException ex = assertThrows(ItemCatalogException.class,
                () -> ItemCatalogParser.parse(sb.toString(), "oversized.yml"));
        assertTrue(ex.getMessage().contains("1 MiB"));
    }

    @Test
    @DisplayName("Missing required 'all' category throws ItemCatalogException")
    void testMissingAllCategory() {
        String yaml = """
                categories:
                  blocks:
                    - stone
                """;
        ItemCatalogException ex = assertThrows(ItemCatalogException.class,
                () -> ItemCatalogParser.parse(yaml, "no_all.yml"));
        assertTrue(ex.getMessage().contains("Missing required 'all' category"));
    }

    @Test
    @DisplayName("Empty YAML content throws ItemCatalogException")
    void testEmptyYaml() {
        assertThrows(ItemCatalogException.class, () -> ItemCatalogParser.parse("", "empty.yml"));
        assertThrows(ItemCatalogException.class, () -> ItemCatalogParser.parse("   ", "blank.yml"));
        assertThrows(ItemCatalogException.class, () -> ItemCatalogParser.parse("{}", "empty_map.yml"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "all",
            "building_blocks",
            "food.drinks",
            "combat-gear",
            "group_123.test-abc"
    })
    @DisplayName("Valid category names matching ^[a-z0-9_.-]{1,64}$ are accepted")
    void testValidCategoryNames(String category) {
        String yaml = "categories:\n  all:\n    - stone\n  " + category + ":\n    - stone\n";
        CatalogSnapshot snapshot = ItemCatalogParser.parse(yaml, "valid_names.yml");
        assertTrue(snapshot.hasCategory(category));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "All",
            "BUILDING_BLOCKS",
            "building blocks",
            "food/drinks",
            "category@1",
            "cat!",
            "cat#name"
    })
    @DisplayName("Invalid category names violating regex ^[a-z0-9_.-]{1,64}$ are rejected")
    void testInvalidCategoryNames(String category) {
        String yaml = "categories:\n  all:\n    - stone\n  \"" + category + "\":\n    - stone\n";
        ItemCatalogException ex = assertThrows(ItemCatalogException.class,
                () -> ItemCatalogParser.parse(yaml, "invalid_name.yml"));
        assertTrue(ex.getMessage().contains("Invalid category name") || ex.getMessage().contains("regex"));
    }

    @Test
    @DisplayName("Category name exceeding 64 characters is rejected")
    void testOversizedCategoryName() {
        String longName = "a".repeat(65);
        String yaml = "categories:\n  all:\n    - stone\n  " + longName + ":\n    - stone\n";
        ItemCatalogException ex = assertThrows(ItemCatalogException.class,
                () -> ItemCatalogParser.parse(yaml, "long_name.yml"));
        assertTrue(ex.getMessage().contains("Invalid category name") || ex.getMessage().contains("regex"));
    }

    @Test
    @DisplayName("Enforces maximum 256 categories limit")
    void testMaxCategoriesLimit() {
        StringBuilder sb = new StringBuilder();
        sb.append("categories:\n  all:\n    - stone\n");
        for (int i = 1; i <= 257; i++) {
            sb.append("  cat_").append(i).append(":\n    - stone\n");
        }
        ItemCatalogException ex = assertThrows(ItemCatalogException.class,
                () -> ItemCatalogParser.parse(sb.toString(), "too_many_cats.yml"));
        assertTrue(ex.getMessage().contains("256"));
    }

    @Test
    @DisplayName("Enforces maximum 1024 entries per category limit")
    void testMaxEntriesPerCategoryLimit() {
        StringBuilder sb = new StringBuilder();
        sb.append("categories:\n  all:\n");
        // Add 1025 entries to "all" (even with duplicate or unique materials, size check happens before/during parse)
        for (int i = 0; i < 1025; i++) {
            sb.append("    - stone\n");
        }
        ItemCatalogException ex = assertThrows(ItemCatalogException.class,
                () -> ItemCatalogParser.parse(sb.toString(), "too_many_entries.yml"));
        assertTrue(ex.getMessage().contains("1024"));
    }

    @Test
    @DisplayName("Duplicate entry in same category is rejected")
    void testDuplicateEntryRejection() {
        String yaml = """
                categories:
                  all:
                    - stone
                    - minecraft:stone
                """;
        ItemCatalogException ex = assertThrows(ItemCatalogException.class,
                () -> ItemCatalogParser.parse(yaml, "duplicate.yml"));
        assertTrue(ex.getMessage().contains("Duplicate entry"));
    }

    @Test
    @DisplayName("Same item in different categories is permitted")
    void testSameItemAcrossCategories() {
        String yaml = """
                categories:
                  all:
                    - stone
                    - diamond_sword
                  blocks:
                    - stone
                  combat:
                    - diamond_sword
                """;
        CatalogSnapshot snapshot = ItemCatalogParser.parse(yaml, "multi_cat.yml");
        assertEquals(3, snapshot.categoryCount());
        assertEquals(1, snapshot.getEntries("blocks").size());
        assertEquals(1, snapshot.getEntries("combat").size());
    }

    @Test
    @DisplayName("Invalid material or non-item throws ItemCatalogException")
    void testInvalidMaterialThrows() {
        String invalidMatYaml = """
                categories:
                  all:
                    - not_a_real_minecraft_item_xyz
                """;
        assertThrows(ItemCatalogException.class,
                () -> ItemCatalogParser.parse(invalidMatYaml, "bad_mat.yml"));

        String nonItemYaml = """
                categories:
                  all:
                    - water
                """;
        ItemCatalogException ex = assertThrows(ItemCatalogException.class,
                () -> ItemCatalogParser.parse(nonItemYaml, "water.yml"));
        assertTrue(ex.getMessage().contains("not a valid item"));
    }

    @Test
    @DisplayName("Preserves category and entry order exactly as in YAML")
    void testOrderPreservation() {
        String yaml = """
                categories:
                  all:
                    - iron_sword
                    - stone
                    - golden_apple
                    - bow
                  tools:
                    - compass
                    - clock
                  alpha:
                    - bread
                """;
        CatalogSnapshot snapshot = ItemCatalogParser.parse(yaml, "ordered.yml");
        assertEquals(List.of("all", "tools", "alpha"), snapshot.getCategoryNames());
        assertEquals(List.of(
                Material.IRON_SWORD,
                Material.STONE,
                Material.GOLDEN_APPLE,
                Material.BOW
        ), snapshot.all().stream().map(CatalogEntry::material).toList());
        assertEquals(List.of(
                Material.COMPASS,
                Material.CLOCK
        ), snapshot.getEntries("tools").stream().map(CatalogEntry::material).toList());
    }
}
