package dev.cyr1en.promptui;

import java.util.Arrays;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/** Applies the same anvil presentation metadata in each NMS provider. */
public final class AnvilItemUtil {
  private AnvilItemUtil() {}

  public static void apply(ItemStack item, String name, String lore, int damage) {
    var meta = item.getItemMeta();
    if (meta == null) return;
    if (name != null) {
      meta.displayName(ComponentUtil.mini("<!italic>" + name));
    }
    if (lore != null && !lore.isEmpty()) {
      meta.lore(
          Arrays.stream(lore.split("\\\\n|\\R|\\{br\\}|<br>|<newline>", -1))
              .map(line -> ComponentUtil.mini("<!italic>" + line))
              .toList());
    }
    if (damage < 0) throw new IllegalArgumentException("damage must not be negative");
    if (damage > 0) {
      if (!(meta instanceof Damageable damageable) || item.getType().getMaxDurability() == 0) {
        throw new IllegalArgumentException("damage requires a damageable anvil item");
      }
      damageable.setDamage(Math.min(damage, item.getType().getMaxDurability()));
    }
    item.setItemMeta(meta);
  }
}
