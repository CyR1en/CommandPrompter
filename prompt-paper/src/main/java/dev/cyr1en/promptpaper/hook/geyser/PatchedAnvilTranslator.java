package dev.cyr1en.promptpaper.hook.geyser;

import org.geysermc.geyser.inventory.AnvilContainer;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.AnvilInventoryTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;

public final class PatchedAnvilTranslator extends AnvilInventoryTranslator {
  @Override
  public PatchedAnvilContainer createInventory(
      GeyserSession session, String name, int id, ContainerType type) {
    return new PatchedAnvilContainer(session, name, id, size, type);
  }

  @Override
  public void openInventory(GeyserSession session, AnvilContainer inventory) {
    var container = (PatchedAnvilContainer) inventory;
    container.setReadyForInput(false);
    super.openInventory(session, container);
    session.sendNetworkLatencyStackPacket(
        -container.getBedrockId(),
        true,
        () -> {
          var current = session.getInventoryHolder();
          if (current == null || current.inventory() != container || !container.isDisplayed())
            return;
          container.setReadyForInput(true);
          updateInventory(session, container);
        });
  }

  @Override
  public boolean canReuseInventory(GeyserSession session, Inventory inventory, Inventory previous) {
    if (!(inventory instanceof PatchedAnvilContainer next)
        || !(previous instanceof PatchedAnvilContainer old)
        || !old.isReadyForInput()
        || !super.canReuseInventory(session, inventory, previous)) return false;
    next.setReadyForInput(true);
    return true;
  }

  @Override
  public void updateInventory(GeyserSession session, AnvilContainer container) {
    PatchedAnvilUpdater.INSTANCE.updateInventory(this, session, container);
  }

  @Override
  public void updateSlot(GeyserSession session, AnvilContainer container, int slot) {
    PatchedAnvilUpdater.INSTANCE.updateSlot(this, session, container, slot);
  }
}
