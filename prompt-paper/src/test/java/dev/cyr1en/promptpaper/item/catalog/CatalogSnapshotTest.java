package dev.cyr1en.promptpaper.item.catalog;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogSnapshotTest extends MockBukkitTest {

  private CatalogSnapshot snapshot;
  private CatalogEntry stone;
  private CatalogEntry grass;
  private CatalogEntry sword;
  private CatalogEntry bow;

  @BeforeEach
  void setUpSnapshot() {
    stone = CatalogEntry.of(Material.STONE);
    grass = CatalogEntry.of(Material.GRASS_BLOCK);
    sword = CatalogEntry.of(Material.DIAMOND_SWORD);
    bow = CatalogEntry.of(Material.BOW);

    Map<String, List<CatalogEntry>> map = new LinkedHashMap<>();
    map.put("all", List.of(stone, grass, sword, bow));
    map.put("blocks", List.of(stone, grass));
    map.put("weapons", List.of(sword, bow));

    snapshot = new CatalogSnapshot(map);
  }

  @Test
  @DisplayName("Category lookup returns correct immutable lists and preserved order")
  void testCategoryLookup() {
    assertTrue(snapshot.hasCategory("all"));
    assertTrue(snapshot.hasCategory("blocks"));
    assertTrue(snapshot.hasCategory("weapons"));
    assertFalse(snapshot.hasCategory("tools"));
    assertFalse(snapshot.hasCategory(null));

    Optional<List<CatalogEntry>> blocksOpt = snapshot.getCategory("blocks");
    assertTrue(blocksOpt.isPresent());
    assertEquals(List.of(stone, grass), blocksOpt.get());

    assertEquals(List.of(stone, grass, sword, bow), snapshot.all());
    assertEquals(List.of(sword, bow), snapshot.getEntries("weapons"));
    assertEquals(List.of(), snapshot.getEntries("nonexistent"));
    assertEquals(List.of(), snapshot.getEntries(null));

    assertEquals(List.of("all", "blocks", "weapons"), snapshot.getCategoryNames());
    assertEquals(List.of("all", "blocks", "weapons"), snapshot.categories());
    assertEquals(3, snapshot.categoryCount());
    assertEquals(4, snapshot.entryCount("all"));
    assertEquals(2, snapshot.entryCount("blocks"));
    assertEquals(0, snapshot.entryCount("missing"));
    assertEquals(0, snapshot.entryCount(null));
    assertEquals(8, snapshot.totalEntryCount());
  }

  @Test
  @DisplayName("CatalogSnapshot is deeply immutable")
  void testDeepImmutability() {
    List<CatalogEntry> allEntries = snapshot.all();
    assertThrows(
        UnsupportedOperationException.class, () -> allEntries.add(CatalogEntry.of(Material.APPLE)));

    List<String> names = snapshot.getCategoryNames();
    assertThrows(UnsupportedOperationException.class, () -> names.add("food"));

    Map<String, List<CatalogEntry>> map = snapshot.asMap();
    assertThrows(UnsupportedOperationException.class, () -> map.put("food", List.of()));
  }

  @Test
  @DisplayName("Pagination slices entries accurately with zero-based indexing")
  void testPaginationSlicing() {
    // "all" has 4 entries: stone (0), grass (1), sword (2), bow (3)
    List<CatalogEntry> page0 = snapshot.getPage("all", 0, 2);
    assertEquals(List.of(stone, grass), page0);

    List<CatalogEntry> page1 = snapshot.getPage("all", 1, 2);
    assertEquals(List.of(sword, bow), page1);

    List<CatalogEntry> page2 = snapshot.getPage("all", 2, 2);
    assertTrue(page2.isEmpty());

    // Page size larger than total
    List<CatalogEntry> singlePage = snapshot.getPage("all", 0, 10);
    assertEquals(List.of(stone, grass, sword, bow), singlePage);

    // Page size odd partition
    List<CatalogEntry> oddPage0 = snapshot.getPage("all", 0, 3);
    assertEquals(List.of(stone, grass, sword), oddPage0);

    List<CatalogEntry> oddPage1 = snapshot.getPage("all", 1, 3);
    assertEquals(List.of(bow), oddPage1);
  }

  @Test
  @DisplayName("Pagination computes total page counts accurately")
  void testPageCountComputation() {
    assertEquals(2, snapshot.getPageCount("all", 2));
    assertEquals(1, snapshot.getPageCount("all", 4));
    assertEquals(1, snapshot.getPageCount("all", 10));
    assertEquals(2, snapshot.getPageCount("all", 3));
    assertEquals(1, snapshot.getPageCount("blocks", 2));
    assertEquals(2, snapshot.getPageCount("blocks", 1));
    assertEquals(0, snapshot.getPageCount("nonexistent", 5));
  }

  @Test
  @DisplayName("Pagination validates arguments and throws on invalid inputs")
  void testPaginationValidation() {
    assertThrows(IllegalArgumentException.class, () -> snapshot.getPage("all", -1, 10));
    assertThrows(IllegalArgumentException.class, () -> snapshot.getPage("all", 0, 0));
    assertThrows(IllegalArgumentException.class, () -> snapshot.getPage("all", 0, -5));

    assertThrows(IllegalArgumentException.class, () -> snapshot.getPageCount("all", 0));
    assertThrows(IllegalArgumentException.class, () -> snapshot.getPageCount("all", -1));
  }

  @Test
  @DisplayName("Empty snapshot behaves gracefully")
  void testEmptySnapshot() {
    CatalogSnapshot empty = CatalogSnapshot.empty();
    assertEquals(0, empty.categoryCount());
    assertTrue(empty.getCategoryNames().isEmpty());
    assertTrue(empty.all().isEmpty());
    assertEquals(0, empty.getPageCount("all", 10));
    assertTrue(empty.getPage("all", 0, 10).isEmpty());
  }
}
