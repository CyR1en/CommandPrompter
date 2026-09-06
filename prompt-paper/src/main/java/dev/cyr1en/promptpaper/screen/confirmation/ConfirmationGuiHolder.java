package dev.cyr1en.promptpaper.screen.confirmation;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Dedicated {@link InventoryHolder} for {@link ConfirmationGuiView} inventories. */
public final class ConfirmationGuiHolder implements InventoryHolder {

  private final ConfirmationGuiView view;
  private Inventory inventory;

  public ConfirmationGuiHolder(ConfirmationGuiView view) {
    this.view = view;
  }

  public ConfirmationGuiView getView() {
    return view;
  }

  public void setInventory(Inventory inventory) {
    this.inventory = inventory;
  }

  @Override
  public @NotNull Inventory getInventory() {
    return inventory;
  }
}
