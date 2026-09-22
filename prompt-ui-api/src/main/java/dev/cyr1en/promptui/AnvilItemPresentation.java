package dev.cyr1en.promptui;

import org.bukkit.inventory.ItemStack;

/** Adapts a configured button for a client before the GUI attaches its callback identity. */
@FunctionalInterface
public interface AnvilItemPresentation {
  AnvilItemPresentation IDENTITY = (slot, item) -> item;

  enum Slot {
    INPUT,
    CANCEL,
    RESULT
  }

  ItemStack present(Slot slot, ItemStack item);
}
