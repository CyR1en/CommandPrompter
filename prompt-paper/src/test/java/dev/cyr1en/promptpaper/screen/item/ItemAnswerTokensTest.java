package dev.cyr1en.promptpaper.screen.item;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.ItemOutputFormat;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.item.catalog.CatalogEntry;
import dev.cyr1en.promptpaper.item.snapshot.ItemFingerprintEncoder;
import dev.cyr1en.promptpaper.item.snapshot.ItemSnapshot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("ItemAnswerTokens Tests")
class ItemAnswerTokensTest extends MockBukkitTest {

  @Test
  @DisplayName("fromSelection generates correct tokens for all formats")
  void testFromSelectionAllFormats() {
    ItemStack item = new ItemStack(Material.DIAMOND_SWORD, 1);
    ItemSnapshot snapshot = ItemSnapshot.of(9, item, ItemFingerprintEncoder.encode(item));

    // KEY
    assertEquals(
        "minecraft:diamond_sword",
        ItemAnswerTokens.fromSelection(ItemOutputFormat.KEY, snapshot, item));

    // MATERIAL
    assertEquals(
        "DIAMOND_SWORD", ItemAnswerTokens.fromSelection(ItemOutputFormat.MATERIAL, snapshot, item));

    // SLOT
    assertEquals("9", ItemAnswerTokens.fromSelection(ItemOutputFormat.SLOT, snapshot, item));

    // AMOUNT
    ItemStack stack64 = new ItemStack(Material.APPLE, 64);
    ItemSnapshot snap64 = ItemSnapshot.of(0, stack64, ItemFingerprintEncoder.encode(stack64));
    assertEquals("64", ItemAnswerTokens.fromSelection(ItemOutputFormat.AMOUNT, snap64, stack64));
  }

  @Test
  @DisplayName("fromSelection enforces non-null parameters")
  void testFromSelectionNullChecks() {
    ItemStack item = new ItemStack(Material.STONE);
    ItemSnapshot snap = ItemSnapshot.of(0, item, ItemFingerprintEncoder.encode(item));

    assertThrows(
        NullPointerException.class, () -> ItemAnswerTokens.fromSelection(null, snap, item));
    assertThrows(
        NullPointerException.class,
        () -> ItemAnswerTokens.fromSelection(ItemOutputFormat.KEY, null, item));
    assertThrows(
        NullPointerException.class,
        () -> ItemAnswerTokens.fromSelection(ItemOutputFormat.KEY, snap, null));
  }

  @Test
  @DisplayName("fromCatalogEntry generates correct tokens for valid formats")
  void testFromCatalogEntryValidFormats() {
    CatalogEntry entry = CatalogEntry.of(Material.GOLDEN_APPLE);

    // KEY
    assertEquals(
        "minecraft:golden_apple", ItemAnswerTokens.fromCatalogEntry(ItemOutputFormat.KEY, entry));

    // MATERIAL
    assertEquals(
        "GOLDEN_APPLE", ItemAnswerTokens.fromCatalogEntry(ItemOutputFormat.MATERIAL, entry));

    // AMOUNT
    assertEquals("1", ItemAnswerTokens.fromCatalogEntry(ItemOutputFormat.AMOUNT, entry));
  }

  @Test
  @DisplayName("fromCatalogEntry throws IllegalArgumentException for SLOT format")
  void testFromCatalogEntrySlotThrows() {
    CatalogEntry entry = CatalogEntry.of(Material.IRON_INGOT);
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemAnswerTokens.fromCatalogEntry(ItemOutputFormat.SLOT, entry));
  }

  @Test
  @DisplayName("fromCatalogEntry enforces non-null parameters")
  void testFromCatalogEntryNullChecks() {
    CatalogEntry entry = CatalogEntry.of(Material.OAK_LOG);
    assertThrows(NullPointerException.class, () -> ItemAnswerTokens.fromCatalogEntry(null, entry));
    assertThrows(
        NullPointerException.class,
        () -> ItemAnswerTokens.fromCatalogEntry(ItemOutputFormat.KEY, null));
  }

  @ParameterizedTest
  @EnumSource(ItemOutputFormat.class)
  @DisplayName(
      "fromSelection throws IllegalArgumentException for empty snapshots or items across all formats")
  void testFromSelectionRejectsEmptyAcrossAllFormats(ItemOutputFormat format) {
    ItemSnapshot emptySnap = ItemSnapshot.empty(0);
    ItemStack airItem = new ItemStack(Material.AIR);
    ItemStack emptyItem = ItemStack.empty();
    ItemStack validItem = new ItemStack(Material.DIAMOND, 1);
    ItemSnapshot validSnap =
        ItemSnapshot.of(0, validItem, ItemFingerprintEncoder.encode(validItem));

    assertThrows(
        IllegalArgumentException.class,
        () -> ItemAnswerTokens.fromSelection(format, emptySnap, validItem));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemAnswerTokens.fromSelection(format, emptySnap, airItem));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemAnswerTokens.fromSelection(format, validSnap, airItem));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemAnswerTokens.fromSelection(format, validSnap, emptyItem));
  }
}
