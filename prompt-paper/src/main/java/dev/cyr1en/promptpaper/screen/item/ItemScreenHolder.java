package dev.cyr1en.promptpaper.screen.item;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Dedicated {@link InventoryHolder} for {@link ItemPromptScreen} inventories. Carries the screen
 * mode so event handlers can identify and route interactions without touching shared or cached
 * holders.
 */
public final class ItemScreenHolder implements InventoryHolder {

  private final ItemScreenMode mode;
  private final ItemPromptScreen screen;
  private Inventory inventory;

  public ItemScreenHolder(ItemScreenMode mode, ItemPromptScreen screen) {
    this.mode = mode;
    this.screen = screen;
  }

  public ItemScreenMode getMode() {
    return mode;
  }

  public ItemPromptScreen getScreen() {
    return screen;
  }

  public void setInventory(Inventory inventory) {
    this.inventory = inventory;
  }

  @Override
  public @NotNull Inventory getInventory() {
    return inventory;
  }
}
