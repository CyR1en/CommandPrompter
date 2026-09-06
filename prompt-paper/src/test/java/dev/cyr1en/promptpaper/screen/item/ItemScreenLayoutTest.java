package dev.cyr1en.promptpaper.screen.item;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ItemScreenLayout Tests")
class ItemScreenLayoutTest {

  @Test
  @DisplayName("Verify layout constants")
  void testLayoutConstants() {
    assertEquals(54, ItemScreenLayout.FULL_SIZE);
    assertEquals(0, ItemScreenLayout.STORAGE_START);
    assertEquals(26, ItemScreenLayout.STORAGE_END);
    assertEquals(27, ItemScreenLayout.HOTBAR_START);
    assertEquals(35, ItemScreenLayout.HOTBAR_END);
    assertEquals(36, ItemScreenLayout.DIVIDER_START);
    assertEquals(44, ItemScreenLayout.DIVIDER_END);
    assertEquals(45, ItemScreenLayout.OFFHAND_SLOT);
    assertEquals(46, ItemScreenLayout.BOOTS_SLOT);
    assertEquals(47, ItemScreenLayout.LEGGINGS_SLOT);
    assertEquals(48, ItemScreenLayout.CHEST_SLOT);
    assertEquals(49, ItemScreenLayout.HELMET_SLOT);
    assertEquals(50, ItemScreenLayout.INFO_SLOT);
    assertEquals(51, ItemScreenLayout.FILLER_START);
    assertEquals(52, ItemScreenLayout.FILLER_END);
    assertEquals(53, ItemScreenLayout.CANCEL_SLOT);

    assertEquals(9, ItemScreenLayout.HAND_SIZE);
    assertEquals(4, ItemScreenLayout.HAND_ITEM_SLOT);
    assertEquals(8, ItemScreenLayout.HAND_CANCEL_SLOT);

    assertEquals(54, ItemScreenLayout.CATALOG_SIZE);
    assertEquals(0, ItemScreenLayout.CATALOG_CONTENT_START);
    assertEquals(44, ItemScreenLayout.CATALOG_CONTENT_END);
    assertEquals(45, ItemScreenLayout.CATALOG_PAGE_SIZE);
    assertEquals(45, ItemScreenLayout.CATALOG_PREV_SLOT);
    assertEquals(49, ItemScreenLayout.CATALOG_PAGE_INFO_SLOT);
    assertEquals(52, ItemScreenLayout.CATALOG_NEXT_SLOT);
    assertEquals(53, ItemScreenLayout.CATALOG_CANCEL_SLOT);
    assertEquals(22, ItemScreenLayout.CATALOG_EMPTY_SLOT);

    assertEquals(3, ItemScreenLayout.MAX_MISMATCH_STRIKES);
  }

  @Test
  @DisplayName("isCancelSlot identifies cancel slot correctly for each mode")
  void testIsCancelSlot() {
    assertTrue(ItemScreenLayout.isCancelSlot(ItemScreenMode.HAND, 8));
    assertFalse(ItemScreenLayout.isCancelSlot(ItemScreenMode.HAND, 4));
    assertFalse(ItemScreenLayout.isCancelSlot(ItemScreenMode.HAND, 53));

    for (ItemScreenMode mode :
        new ItemScreenMode[] {
          ItemScreenMode.INVENTORY, ItemScreenMode.ARMOR, ItemScreenMode.CATALOG
        }) {
      assertTrue(
          ItemScreenLayout.isCancelSlot(mode, 53), "Slot 53 should be cancel slot for " + mode);
      assertFalse(
          ItemScreenLayout.isCancelSlot(mode, 8), "Slot 8 should not be cancel slot for " + mode);
      assertFalse(
          ItemScreenLayout.isCancelSlot(mode, 0), "Slot 0 should not be cancel slot for " + mode);
    }
  }

  @Test
  @DisplayName("isSelectableGuiSlot for INVENTORY mode covers storage, hotbar, offhand, and armor")
  void testIsSelectableInventoryMode() {
    // Storage: 0..26
    for (int slot = 0; slot <= 26; slot++) {
      assertTrue(
          ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.INVENTORY, slot),
          "Slot " + slot + " should be selectable in INVENTORY");
    }
    // Hotbar: 27..35
    for (int slot = 27; slot <= 35; slot++) {
      assertTrue(
          ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.INVENTORY, slot),
          "Slot " + slot + " should be selectable in INVENTORY");
    }
    // Divider: 36..44
    for (int slot = 36; slot <= 44; slot++) {
      assertFalse(
          ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.INVENTORY, slot),
          "Slot " + slot + " should NOT be selectable in INVENTORY");
    }
    // Equipment: 45..49
    for (int slot = 45; slot <= 49; slot++) {
      assertTrue(
          ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.INVENTORY, slot),
          "Slot " + slot + " should be selectable in INVENTORY");
    }
    // Controls / filler: 50..53
    for (int slot = 50; slot <= 53; slot++) {
      assertFalse(
          ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.INVENTORY, slot),
          "Slot " + slot + " should NOT be selectable in INVENTORY");
    }
  }

  @Test
  @DisplayName("isSelectableGuiSlot for ARMOR mode allows only slots 45..49")
  void testIsSelectableArmorMode() {
    for (int slot = 0; slot <= 44; slot++) {
      assertFalse(
          ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.ARMOR, slot),
          "Slot " + slot + " should NOT be selectable in ARMOR");
    }
    for (int slot = 45; slot <= 49; slot++) {
      assertTrue(
          ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.ARMOR, slot),
          "Slot " + slot + " should be selectable in ARMOR");
    }
    for (int slot = 50; slot <= 53; slot++) {
      assertFalse(
          ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.ARMOR, slot),
          "Slot " + slot + " should NOT be selectable in ARMOR");
    }
  }

  @Test
  @DisplayName("isSelectableGuiSlot for HAND mode allows only slot 4")
  void testIsSelectableHandMode() {
    for (int slot = 0; slot < 9; slot++) {
      if (slot == 4) {
        assertTrue(ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.HAND, slot));
      } else {
        assertFalse(ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.HAND, slot));
      }
    }
  }

  @Test
  @DisplayName("isSelectableGuiSlot for CATALOG mode allows slots 0..44")
  void testIsSelectableCatalogMode() {
    for (int slot = 0; slot <= 44; slot++) {
      assertTrue(
          ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.CATALOG, slot),
          "Slot " + slot + " should be selectable in CATALOG");
    }
    for (int slot = 45; slot <= 53; slot++) {
      assertFalse(
          ItemScreenLayout.isSelectableGuiSlot(ItemScreenMode.CATALOG, slot),
          "Slot " + slot + " should NOT be selectable in CATALOG");
    }
  }

  @Test
  @DisplayName("Display item factories construct expected materials and metadata")
  void testDisplayItemFactories() {
    ItemStack divider = ItemScreenLayout.dividerItem();
    assertNotNull(divider);
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, divider.getType());

    ItemStack filler = ItemScreenLayout.fillerItem();
    assertNotNull(filler);
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, filler.getType());

    ItemStack cancel = ItemScreenLayout.cancelItem();
    assertNotNull(cancel);
    assertEquals(Material.BARRIER, cancel.getType());

    ItemStack prev = ItemScreenLayout.previousPageItem();
    assertNotNull(prev);
    assertEquals(Material.ARROW, prev.getType());

    ItemStack next = ItemScreenLayout.nextPageItem();
    assertNotNull(next);
    assertEquals(Material.ARROW, next.getType());

    ItemStack emptyCatalog = ItemScreenLayout.emptyCatalogItem();
    assertNotNull(emptyCatalog);
    assertEquals(Material.GRAY_DYE, emptyCatalog.getType());

    ItemStack info = ItemScreenLayout.infoItem("Header", Component.text("Prompt text"));
    assertNotNull(info);
    assertEquals(Material.PAPER, info.getType());
    assertNotNull(info.getItemMeta());
    assertNotNull(info.getItemMeta().lore());
    assertFalse(info.getItemMeta().lore().isEmpty());

    ItemStack emptyInfo = ItemScreenLayout.infoItem("Header", Component.empty());
    assertNotNull(emptyInfo);
    assertEquals(Material.PAPER, emptyInfo.getType());

    ItemStack pageInfo = ItemScreenLayout.pageInfoItem(0, 3, 120);
    assertNotNull(pageInfo);
    assertEquals(Material.PAPER, pageInfo.getType());
    assertNotNull(pageInfo.getItemMeta());
    assertNotNull(pageInfo.getItemMeta().lore());

    ItemStack emptyPageInfo = ItemScreenLayout.pageInfoItem(0, 0, 0);
    assertNotNull(emptyPageInfo);
    assertEquals(Material.PAPER, emptyPageInfo.getType());
  }
}
