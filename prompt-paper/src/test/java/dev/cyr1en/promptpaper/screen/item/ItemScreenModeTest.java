package dev.cyr1en.promptpaper.screen.item;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.ItemSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ItemScreenMode Tests")
class ItemScreenModeTest {

  @Test
  @DisplayName("fromSource maps all ItemSource enum values correctly")
  void testFromSource() {
    assertEquals(ItemScreenMode.INVENTORY, ItemScreenMode.fromSource(ItemSource.INVENTORY));
    assertEquals(ItemScreenMode.HAND, ItemScreenMode.fromSource(ItemSource.HAND));
    assertEquals(ItemScreenMode.ARMOR, ItemScreenMode.fromSource(ItemSource.ARMOR));
    assertEquals(ItemScreenMode.CATALOG, ItemScreenMode.fromSource(ItemSource.CATALOG));
    assertEquals(ItemScreenMode.INVENTORY, ItemScreenMode.fromSource(null));
  }

  @Test
  @DisplayName("isPhysical returns true for inventory, hand, and armor, false for catalog")
  void testIsPhysical() {
    assertTrue(ItemScreenMode.INVENTORY.isPhysical());
    assertTrue(ItemScreenMode.HAND.isPhysical());
    assertTrue(ItemScreenMode.ARMOR.isPhysical());
    assertFalse(ItemScreenMode.CATALOG.isPhysical());
  }

  @Test
  @DisplayName("size returns 54 for full layouts and 9 for hand layout")
  void testSize() {
    assertEquals(54, ItemScreenMode.INVENTORY.size());
    assertEquals(54, ItemScreenMode.ARMOR.size());
    assertEquals(54, ItemScreenMode.CATALOG.size());
    assertEquals(9, ItemScreenMode.HAND.size());
  }
}
