package dev.cyr1en.promptui.v26_2;

import dev.cyr1en.promptui.gui.AnvilInventory;
import dev.cyr1en.promptui.gui.TextHolder;
import dev.cyr1en.promptui.util.BedrockUtil;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * MC 26.2 NMS implementation of {@link AnvilInventory}.
 *
 * <p>Manages the lifecycle of an NMS {@link AnvilMenu}, including packet-based opening and closing,
 * rename text interception, and inventory creation.
 */
public final class AnvilInventoryImpl extends AnvilInventory {

  private static final int INPUT_SLOT = 0;
  private static final int OUTPUT_SLOT = 2;

  private final CraftPlayer craftPlayer;
  private final org.bukkit.entity.Player player;
  private NMSAnvilContainer container;
  private Consumer<String> nameChangeCallback;
  private boolean opened;
  private boolean experienceFaked;
  private final boolean geyserAnvilPatch;

  public AnvilInventoryImpl(@NotNull org.bukkit.entity.Player player) {
    this(player, false);
  }

  public AnvilInventoryImpl(@NotNull org.bukkit.entity.Player player, boolean geyserAnvilPatch) {
    this.geyserAnvilPatch = geyserAnvilPatch;
    this.player = Objects.requireNonNull(player, "player");
    this.craftPlayer = (CraftPlayer) player;
  }

  /** Creates the NMS {@link AnvilMenu} container and returns its Bukkit inventory view. */
  @NotNull
  @Override
  public org.bukkit.inventory.Inventory createInventory(@NotNull TextHolder title) {
    Component nmsTitle =
        io.papermc.paper.adventure.PaperAdventure.asVanillaNullToEmpty(
            title == null ? null : title.getComponent());
    container = new NMSAnvilContainer(player, nmsTitle, geyserAnvilPatch);
    container.setParent(this);
    return container.getBukkitView().getTopInventory();
  }

  /** Opens the anvil screen using NMS packets. */
  public void open() {
    if (container == null) {
      throw new IllegalStateException("createInventory() must be called before open()");
    }
    var nmsPlayer = craftPlayer.getHandle();
    if (opened && nmsPlayer.containerMenu == container) {
      return;
    }
    try {
      closeExistingMenu(nmsPlayer);
      Component title = container.getTitle();
      int id = container.containerId;
      sendPacket(nmsPlayer, new ClientboundOpenScreenPacket(id, MenuType.ANVIL, title));
      nmsPlayer.containerMenu = container;
      nmsPlayer.initMenu(container);
      opened = true;
      if (container.bedrock) {
        sendPacket(nmsPlayer, new ClientboundSetExperiencePacket(0.0f, 0, 20));
        experienceFaked = true;
      }
    } catch (Throwable failure) {
      opened = false;
      restoreInventoryMenu(nmsPlayer);
      if (experienceFaked) {
        experienceFaked = false;
        restoreExperience(nmsPlayer);
      }
      if (failure instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (failure instanceof Error error) {
        throw error;
      }
      throw new RuntimeException(failure);
    }
  }

  /** Closes the anvil screen using NMS packets. */
  public void close() {
    opened = false;
    if (container == null) return;
    var nmsPlayer = craftPlayer.getHandle();
    boolean isCurrent = nmsPlayer.containerMenu == container;
    try {
      if (isCurrent) {
        sendPacket(nmsPlayer, new ClientboundContainerClosePacket(container.containerId));
        nmsPlayer.doCloseContainer();
        restoreInventoryMenu(nmsPlayer);
      }
    } finally {
      if (experienceFaked) {
        experienceFaked = false;
        if (isCurrent) {
          restoreExperience(nmsPlayer);
        }
      }
    }
  }

  /** Returns whether this NMS container is currently installed for the player. */
  public boolean isOpened() {
    return opened;
  }

  /** Returns whether fake experience was sent for Bedrock edition workaround. */
  public boolean isExperienceFaked() {
    return experienceFaked;
  }

  /** Clears callbacks and parent links after the screen reaches a terminal state. */
  public void clearCallbacks() {
    nameChangeCallback = null;
    if (container != null) {
      container.setParent(null);
    }
  }

  /** Handles container removal by Minecraft (e.g. replaced by another menu or closed). */
  void onContainerRemoved(NMSAnvilContainer removedContainer) {
    if (this.container != removedContainer) {
      return;
    }
    opened = false;
    if (experienceFaked) {
      experienceFaked = false;
      var nmsPlayer = craftPlayer.getHandle();
      if (nmsPlayer.containerMenu == container) {
        restoreExperience(nmsPlayer);
      }
    }
  }

  private void closeExistingMenu(net.minecraft.server.level.ServerPlayer nmsPlayer) {
    var active = nmsPlayer.containerMenu;
    if (active == null || active == nmsPlayer.inventoryMenu || active == container) {
      return;
    }
    // The client must receive the id of the menu it actually has open;
    // container id 0 is only the player's inventory menu.
    sendPacket(nmsPlayer, new ClientboundContainerClosePacket(active.containerId));
    nmsPlayer.doCloseContainer();
    restoreInventoryMenu(nmsPlayer);
  }

  private void restoreInventoryMenu(net.minecraft.server.level.ServerPlayer nmsPlayer) {
    if (nmsPlayer.containerMenu != nmsPlayer.inventoryMenu
        && nmsPlayer.containerMenu == container) {
      nmsPlayer.doCloseContainer();
    }
  }

  private void restoreExperience(net.minecraft.server.level.ServerPlayer nmsPlayer) {
    sendPacket(
        nmsPlayer,
        new ClientboundSetExperiencePacket(
            nmsPlayer.experienceProgress, nmsPlayer.totalExperience, nmsPlayer.experienceLevel));
  }

  private static void sendPacket(
      net.minecraft.server.level.ServerPlayer nmsPlayer,
      net.minecraft.network.protocol.Packet<?> packet) {
    if (nmsPlayer == null || nmsPlayer.connection == null) {
      return;
    }
    try {
      nmsPlayer.connection.send(packet);
    } catch (Throwable ignored) {
      // network/packet failures must not abort container closure or state reset
    }
  }

  /** {@inheritDoc} */
  @Override
  public void subscribeToNameInputChanges(@NotNull Consumer<String> callback) {
    this.nameChangeCallback = callback;
  }

  @Override
  @NotNull
  public String getRenameText() {
    if (container != null) {
      return container.itemName;
    }
    return "";
  }

  /** Relays rename-text changes from the NMS container to the subscribed callback. */
  void onNameChanged(String text) {
    this.renameText = text;
    if (nameChangeCallback != null) {
      nameChangeCallback.accept(text);
    }
  }

  /** An anvil menu that forwards rename changes and keeps the prompt result available. */
  static final class NMSAnvilContainer extends AnvilMenu {

    private AnvilInventoryImpl parent;
    private final boolean bedrock;

    NMSAnvilContainer(org.bukkit.entity.Player bukkitPlayer, Component title) {
      this(bukkitPlayer, title, false);
    }

    NMSAnvilContainer(
        org.bukkit.entity.Player bukkitPlayer, Component title, boolean geyserAnvilPatch) {
      super(
          ((CraftPlayer) bukkitPlayer).getHandle().nextContainerCounter(),
          ((CraftPlayer) bukkitPlayer).getHandle().getInventory(),
          ContainerLevelAccess.create(
              ((CraftPlayer) bukkitPlayer).getHandle().level(), BlockPos.ZERO));
      Objects.requireNonNull(title);
      this.checkReachable = false;
      this.bedrock = !geyserAnvilPatch && BedrockUtil.isBedrockPlayer(bukkitPlayer);
      setTitle(title);
    }

    void setParent(AnvilInventoryImpl parent) {
      this.parent = parent;
    }

    /** Maintains a submit result, using a valid rename recipe for Bedrock client prediction. */
    @Override
    public void createResult() {
      Slot output = getSlot(OUTPUT_SLOT);
      Slot input = getSlot(INPUT_SLOT);
      if (bedrock) {
        var result = input.getItem().copy();
        if (!result.isEmpty() && itemName != null) {
          result.set(DataComponents.CUSTOM_NAME, Component.literal(itemName));
        }
        output.set(result);
      } else if (!output.hasItem() && input.hasItem()) {
        output.set(input.getItem().copy());
      }
      // A rename costs one level on Bedrock. The screen supplies fake client XP while open;
      // submission is intercepted and never consumes the player's real experience or items.
      this.cost.set(bedrock ? 1 : 0);
      broadcastChanges();
      // Restore the client's result even when the server-side item has not changed.
      sendAllDataToRemote();
    }

    /** Prevents item drops when the container is closed server-side and notifies parent. */
    @Override
    public void removed(Player player) {
      if (parent != null) {
        parent.onContainerRemoved(this);
      }
    }

    /** No-op: prevents item drops when the container is cleared. */
    @Override
    protected void clearContainer(Player player, Container container) {}

    /** Delegates to {@code super} then notifies the parent of the name change. */
    @Override
    public boolean setItemName(String name) {
      boolean result = super.setItemName(name);
      if (parent != null) {
        parent.onNameChanged(name);
      }
      return result;
    }
  }
}
