package dev.cyr1en.promptpaper.hook.geyser;

import java.util.Map;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.translator.inventory.AnvilInventoryTranslator;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;

/** Installs the opt-in translator in memory, without restricting the Geyser version. */
public final class GeyserAnvilPatch {
  private GeyserAnvilPatch() {}

  public static Runnable install() throws ReflectiveOperationException {
    var field = InventoryTranslator.class.getDeclaredField("INVENTORY_TRANSLATORS");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    var translators =
        (Map<ContainerType, InventoryTranslator<? extends Inventory>>) field.get(null);
    var original = translators.get(ContainerType.ANVIL);
    if (original == null || original.getClass() != AnvilInventoryTranslator.class) {
      throw new IllegalStateException("Another integration already replaced the anvil translator");
    }
    var replacement = new PatchedAnvilTranslator();
    translators.put(ContainerType.ANVIL, replacement);
    return () -> translators.replace(ContainerType.ANVIL, replacement, original);
  }
}
