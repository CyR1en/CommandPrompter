package dev.cyr1en.promptpaper.hook.geyser;

import dev.cyr1en.promptui.AnvilItemPresentation.Slot;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Repairable;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Applies modern Paper components after the client and startup definitions have been checked. */
final class BedrockAnvilCarrier {
  private BedrockAnvilCarrier() {}

  static ItemStack create(Slot slot, ItemStack item) {
    ItemStack carrier = item.withType(slot == Slot.INPUT ? Material.PAPER : Material.STICK);
    carrier.setAmount(1);
    carrier.setData(
        DataComponentTypes.ITEM_MODEL, Key.key(BedrockAnvilItems.model(slot, item.getType())));
    carrier.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
    carrier.setData(DataComponentTypes.REPAIR_COST, 0);
    carrier.unsetData(DataComponentTypes.UNBREAKABLE);
    if (slot == Slot.INPUT) {
      carrier.setData(DataComponentTypes.MAX_DAMAGE, BedrockAnvilItems.MAX_DAMAGE);
      carrier.setData(DataComponentTypes.DAMAGE, 1);
      carrier.setData(
          DataComponentTypes.REPAIRABLE,
          Repairable.repairable(RegistrySet.keySet(RegistryKey.ITEM, ItemTypeKeys.STICK)));
    } else {
      carrier.unsetData(DataComponentTypes.MAX_DAMAGE);
      carrier.unsetData(DataComponentTypes.DAMAGE);
      carrier.unsetData(DataComponentTypes.REPAIRABLE);
    }
    return carrier;
  }
}
