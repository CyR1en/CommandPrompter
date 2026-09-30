package dev.cyr1en.promptpaper.hook.geyser;

import dev.cyr1en.promptui.AnvilItemPresentation;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Checks client capabilities before loading the modern Paper component implementation. */
final class BedrockAnvilPresentation implements AnvilItemPresentation {
  private final Set<Material> materials;

  BedrockAnvilPresentation(Set<Material> materials) {
    this.materials = Set.copyOf(materials);
  }

  @Override
  public ItemStack present(Slot slot, ItemStack item) {
    if (slot == Slot.RESULT) return item;
    if (!materials.contains(item.getType())) {
      throw new IllegalStateException(
          "Bedrock anvil appearance "
              + item.getType()
              + " is not registered; restart or rebuild the standalone patch");
    }
    return BedrockAnvilCarrier.create(slot, item);
  }
}
