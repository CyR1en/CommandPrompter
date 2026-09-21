package dev.cyr1en.promptpaper.hook.geyser;

import org.geysermc.geyser.inventory.AnvilContainer;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;

public final class PatchedAnvilContainer extends AnvilContainer {
  private boolean readyForInput;

  public PatchedAnvilContainer(
      GeyserSession session, String title, int id, int size, ContainerType type) {
    super(session, title, id, size, type);
  }

  public boolean isReadyForInput() {
    return readyForInput;
  }

  public void setReadyForInput(boolean ready) {
    readyForInput = ready;
  }

  @Override
  public String checkForRename(GeyserSession session, String rename) {
    boolean authoritativeCost = isUseJavaLevelCost();
    try {
      return super.checkForRename(session, rename);
    } finally {
      // Java only resends changed properties, so renaming cannot invalidate its last cost.
      setUseJavaLevelCost(authoritativeCost);
    }
  }
}
