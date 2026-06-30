package dev.cyr1en.promptui.v26_2;

import dev.cyr1en.promptui.gui.AnvilInventory;
import dev.cyr1en.promptui.gui.TextHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * MC 26.2 NMS implementation of {@link AnvilInventory}.
 *
 * <p>Manages the lifecycle of an NMS {@link AnvilMenu}, including packet-based
 * opening and closing, rename text interception, and inventory creation.</p>
 */
public final class AnvilInventoryImpl extends AnvilInventory {

    private static final int INPUT_SLOT = 0;
    private static final int SECOND_SLOT = 1;
    private static final int OUTPUT_SLOT = 2;

    private final CraftPlayer craftPlayer;
    private final org.bukkit.entity.Player player;
    private NMSAnvilContainer container;
    private Consumer<String> nameChangeCallback;
    private boolean opened;

    public AnvilInventoryImpl(@NotNull org.bukkit.entity.Player player) {
        this.player = Objects.requireNonNull(player, "player");
        this.craftPlayer = (CraftPlayer) player;
    }

    /**
     * Creates the NMS {@link AnvilMenu} container and returns its Bukkit inventory view.
     */
    @NotNull
    @Override
    public org.bukkit.inventory.Inventory createInventory(@NotNull TextHolder title) {
        Component nmsTitle = Component.literal(getPlainText(title));
        container = new NMSAnvilContainer(player, nmsTitle);
        container.setParent(this);
        return container.getBukkitView().getTopInventory();
    }

    /**
     * Opens the anvil screen using NMS packets.
     */
    public void open() {
        if (container == null) {
            throw new IllegalStateException("createInventory() must be called before open()");
        }
        var nmsPlayer = craftPlayer.getHandle();
        nmsPlayer.connection.send(new ClientboundContainerClosePacket(0));
        Component title = container.getTitle();
        // containerId was already assigned in the constructor via nextContainerCounter
        int id = container.containerId;
        nmsPlayer.connection.send(new ClientboundOpenScreenPacket(id, MenuType.ANVIL, title));
        nmsPlayer.containerMenu = container;
        nmsPlayer.initMenu(container);
        opened = true;
    }

    /**
     * Closes the anvil screen using NMS packets.
     */
    public void close() {
        if (!opened || container == null) return;
        opened = false;
        var nmsPlayer = craftPlayer.getHandle();
        nmsPlayer.connection.send(new ClientboundContainerClosePacket(container.containerId));
        nmsPlayer.doCloseContainer();
    }

    /**
     * Places an item in a specific anvil slot.
     */
    public void setSlotItem(int slot, @Nullable org.bukkit.inventory.ItemStack bukkitItem) {
        if (container == null) return;
        var slotObj = container.getSlot(slot);
        if (bukkitItem != null) {
            slotObj.set(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(bukkitItem));
        } else {
            slotObj.set(net.minecraft.world.item.ItemStack.EMPTY);
        }
        container.createResult();
        container.broadcastChanges();
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

    // Called by NMSAnvilContainer.setItemName
    /** Relays rename-text changes from the NMS container to the subscribed callback. */
    void onNameChanged(String text) {
        this.renameText = text;
        if (nameChangeCallback != null) {
            nameChangeCallback.accept(text);
        }
    }

    /** Strips a {@link TextHolder} to its plain-text representation, or returns {@code ""} if null. */
    static String getPlainText(@Nullable TextHolder holder) {
        if (holder != null && holder.getComponent() != null) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(holder.getComponent());
        }
        return "";
    }

    /**
     * The NMS container. Mirrors the old {@code AnvilContainer} but with
     * a callback to {@link AnvilInventoryImpl} for name changes.
     */
    static final class NMSAnvilContainer extends AnvilMenu {

        private AnvilInventoryImpl parent;

        NMSAnvilContainer(org.bukkit.entity.Player bukkitPlayer, Component title) {
            super(
                ((CraftPlayer) bukkitPlayer).getHandle().nextContainerCounter(),
                ((CraftPlayer) bukkitPlayer).getHandle().getInventory(),
                ContainerLevelAccess.create(
                    ((CraftPlayer) bukkitPlayer).getHandle().level(),
                    BlockPos.ZERO));
            Objects.requireNonNull(title);
            this.checkReachable = false;
            setTitle(title);
        }

        void setParent(AnvilInventoryImpl parent) {
            this.parent = parent;
        }

        /** Copies the input item to the output slot with zero cost, bypassing normal anvil logic. */
        @Override
        public void createResult() {
            Slot output = getSlot(OUTPUT_SLOT);
            Slot input = getSlot(INPUT_SLOT);
            if (!output.hasItem() && input.hasItem()) {
                output.set(input.getItem().copy());
            }
            this.cost.set(0);
            broadcastChanges();
        }

        /** No-op: prevents item drops when the container is closed server-side. */
        @Override
        public void removed(Player player) {
            // Prevent item drops when container is removed
        }

        /** No-op: prevents item drops when the container is cleared. */
        @Override
        protected void clearContainer(Player player, Container container) {
            // Prevent item drops
        }

        /** Delegates to {@code super} then notifies the parent of the name change. */
        @Override
        public boolean setItemName(String name) {
            boolean result = super.setItemName(name);
            if (parent != null) {
                parent.onNameChanged(name);
            }
            return result;
        }

        /** Returns the Bukkit inventory view of this container's top section. */
        org.bukkit.inventory.Inventory getBukkitInventory() {
            return getBukkitView().getTopInventory();
        }
    }
}
