package dev.cyr1en.promptpaper.screen.item;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ItemScreenHolder Tests")
class ItemScreenHolderTest {

  @Test
  @DisplayName("ItemScreenHolder holds screen, mode, and inventory reference")
  void testHolderFields() {
    ItemPromptScreen screen = mock(ItemPromptScreen.class);
    ItemScreenHolder holder = new ItemScreenHolder(ItemScreenMode.ARMOR, screen);

    assertEquals(ItemScreenMode.ARMOR, holder.getMode());
    assertSame(screen, holder.getScreen());
    assertNull(holder.getInventory());

    Inventory inv = mock(Inventory.class);
    holder.setInventory(inv);
    assertSame(inv, holder.getInventory());
  }
}
