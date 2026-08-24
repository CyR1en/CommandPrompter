package dev.cyr1en.promptpaper.item.catalog;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogEntryTest extends MockBukkitTest {

    @Test
    @DisplayName("Valid material creates CatalogEntry with canonical NamespacedKey")
    void testValidEntry() {
        CatalogEntry entry = CatalogEntry.of(Material.DIAMOND_SWORD);
        assertEquals(Material.DIAMOND_SWORD, entry.material());
        assertEquals(Material.DIAMOND_SWORD, entry.getMaterial());
        assertEquals(NamespacedKey.minecraft("diamond_sword"), entry.key());
        assertEquals(NamespacedKey.minecraft("diamond_sword"), entry.getKey());
        assertEquals(NamespacedKey.minecraft("diamond_sword"), entry.namespacedKey());
        assertEquals("minecraft:diamond_sword", entry.canonicalKey());
        assertEquals("minecraft:diamond_sword", entry.getCanonicalKey());
    }

    @Test
    @DisplayName("CatalogEntry constructor rejects null arguments")
    void testNullArguments() {
        assertThrows(NullPointerException.class, () -> new CatalogEntry(null, NamespacedKey.minecraft("stone"), "minecraft:stone"));
        assertThrows(NullPointerException.class, () -> new CatalogEntry(Material.STONE, null, "minecraft:stone"));
        assertThrows(NullPointerException.class, () -> new CatalogEntry(Material.STONE, NamespacedKey.minecraft("stone"), null));
    }

    @Test
    @DisplayName("CatalogEntry constructor rejects non-item materials")
    void testNonItemMaterial() {
        assertFalse(Material.WATER.isItem());
        assertThrows(IllegalArgumentException.class, () -> CatalogEntry.of(Material.WATER));
        assertTrue(Material.AIR.isAir());
        assertThrows(IllegalArgumentException.class, () -> CatalogEntry.of(Material.AIR));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "stone",
            "minecraft:stone",
            "STONE",
            "diamond_sword",
            "minecraft:diamond_sword",
            "DIAMOND_SWORD",
            "golden_apple",
            "minecraft:golden_apple"
    })
    @DisplayName("CatalogEntry.resolve successfully parses various string formats")
    void testResolveValid(String raw) {
        CatalogEntry entry = CatalogEntry.resolve(raw);
        assertNotNull(entry);
        assertTrue(entry.material().isItem());
        assertTrue(entry.canonicalKey().startsWith("minecraft:"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "water",
            "minecraft:water",
            "air",
            "minecraft:air",
            "cave_air",
            "void_air",
            "lava",
            "minecraft:lava",
            "piston_head"
    })
    @DisplayName("CatalogEntry.resolve rejects non-item materials")
    void testResolveNonItem(String nonItem) {
        ItemCatalogException ex = assertThrows(ItemCatalogException.class, () -> CatalogEntry.resolve(nonItem));
        assertTrue(ex.getMessage().contains("not a valid item") || ex.getMessage().contains("isItem() == false"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "invalid_unknown_item_xyz",
            "minecraft:does_not_exist_123"
    })
    @DisplayName("CatalogEntry.resolve rejects invalid, unknown, or blank strings")
    void testResolveInvalid(String invalid) {
        assertThrows(ItemCatalogException.class, () -> CatalogEntry.resolve(invalid));
    }

    @Test
    @DisplayName("CatalogEntry.resolve rejects null")
    void testResolveNull() {
        assertThrows(ItemCatalogException.class, () -> CatalogEntry.resolve(null));
    }
}
