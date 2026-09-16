package dev.cyr1en.promptui.v26_1;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class AnvilOutputSyncTest {

  @BeforeAll
  static void initializeMinecraft() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Items.PAPER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
  }

  @Test
  void createResultResendsUnchangedOutputAfterClientClearsItsPrediction() throws Exception {
    var menu = createMenu();
    var input = new ItemStack(Items.PAPER);
    input.set(DataComponents.CUSTOM_NAME, Component.empty());
    var output = new ItemStack(Items.PAPER);
    output.set(DataComponents.CUSTOM_NAME, Component.literal("Submit"));
    menu.getSlot(0).set(input);
    menu.getSlot(2).set(output.copy());

    var client = new RecordingClient();
    menu.setSynchronizer(client);
    assertTrue(ItemStack.matches(output, client.slots.get(2)));
    assertTrue(menu.remoteSlots.get(2).matches(output));

    // A client can clear its locally predicted result without changing the server's snapshot.
    client.slots.set(2, ItemStack.EMPTY);
    menu.createResult();

    assertTrue(ItemStack.matches(output, menu.getSlot(2).getItem()));
    assertTrue(
        ItemStack.matches(output, client.slots.get(2)),
        "Recalculating the result must restore an unchanged output on the client");
    assertEquals(0, menu.getCost());
  }

  private static AnvilInventoryImpl.NMSAnvilContainer createMenu() throws Exception {
    Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    var unsafe = (Unsafe) unsafeField.get(null);
    var menu =
        (AnvilInventoryImpl.NMSAnvilContainer)
            unsafe.allocateInstance(AnvilInventoryImpl.NMSAnvilContainer.class);
    var initializedMenu = new ThreeSlotMenu();
    // Keep the real menu synchronization state while avoiding a running server/world fixture.
    for (Field field : AbstractContainerMenu.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) continue;
      field.setAccessible(true);
      field.set(menu, field.get(initializedMenu));
    }
    Field costField = AnvilMenu.class.getDeclaredField("cost");
    costField.setAccessible(true);
    costField.set(menu, initializedMenu.cost);
    return menu;
  }

  private static final class ThreeSlotMenu extends AbstractContainerMenu {
    private final DataSlot cost = DataSlot.standalone();

    ThreeSlotMenu() {
      super(MenuType.ANVIL, 1);
      var inventory = new SimpleContainer(3);
      for (int i = 0; i < 3; i++) addSlot(new Slot(inventory, i, 0, 0));
      addDataSlot(cost);
    }

    @Override
    public InventoryView getBukkitView() {
      return null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
      return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
      return true;
    }
  }

  private static final class RecordingClient implements ContainerSynchronizer {
    private final List<ItemStack> slots = new ArrayList<>();

    @Override
    public void sendInitialData(
        AbstractContainerMenu menu, List<ItemStack> items, ItemStack carried, int[] data) {
      slots.clear();
      items.forEach(item -> slots.add(item.copy()));
    }

    @Override
    public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack item) {
      slots.set(slot, item.copy());
    }

    @Override
    public void sendCarriedChange(AbstractContainerMenu menu, ItemStack carried) {}

    @Override
    public void sendDataChange(AbstractContainerMenu menu, int slot, int value) {}

    @Override
    public RemoteSlot createSlot() {
      // No incoming hashed stacks are used; forced snapshots exercise real ItemStack matching.
      return new RemoteSlot.Synchronized(null);
    }
  }
}
