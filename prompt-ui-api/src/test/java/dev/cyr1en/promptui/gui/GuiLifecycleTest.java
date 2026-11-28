package dev.cyr1en.promptui.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GuiLifecycleTest {

  private final JavaPlugin plugin = mock(JavaPlugin.class);
  private final Player player = mock(Player.class);
  private final PlayerInventory storage = mock(PlayerInventory.class);
  private final InventoryView view = mock(InventoryView.class);
  private GuiListener listener;

  @BeforeEach
  void setUp() {
    var server = mock(Server.class);
    when(plugin.getServer()).thenReturn(server);
    when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
    when(player.getInventory()).thenReturn(storage);
    when(player.getOpenInventory()).thenReturn(view);
    when(view.getPlayer()).thenReturn(player);
    when(view.getBottomInventory()).thenReturn(storage);
    listener = new GuiListener(plugin);
  }

  @AfterEach
  void clearRegistry() {
    Gui.clearRegistry();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deathConsumesTheSnapshotAndHonorsKeepInventory(boolean keepInventory) {
    var gui = openAnvil();
    var original = mock(ItemStack.class);
    when(original.clone()).thenReturn(original);
    when(storage.getItem(0)).thenReturn(original);
    gui.getHumanEntityCache().storeAndClear(player);

    var world = mock(World.class);
    var location = new Location(world, 0, 0, 0);
    when(player.getWorld()).thenReturn(world);
    when(player.getLocation()).thenReturn(location);
    var death = mock(PlayerDeathEvent.class);
    when(death.getEntity()).thenReturn(player);

    listener.onPlayerDeath(death);
    assertTrue(gui.getHumanEntityCache().contains(player));
    verify(death, never()).getDrops();
    verify(world, never()).dropItemNaturally(location, original);
    when(death.getKeepInventory()).thenReturn(keepInventory);
    listener.onInventoryClose(new InventoryCloseEvent(view, InventoryCloseEvent.Reason.DEATH));

    assertFalse(gui.getHumanEntityCache().contains(player));
    verify(world, keepInventory ? never() : times(1)).dropItemNaturally(location, original);
    verify(storage, keepInventory ? times(1) : never()).setItem(0, original);
  }

  @Test
  void deathCancelledAfterOurListenerLeavesTheSnapshotAvailableOnClose() {
    var gui = openAnvil();
    var original = mock(ItemStack.class);
    when(original.clone()).thenReturn(original);
    when(storage.getItem(0)).thenReturn(original);
    gui.getHumanEntityCache().storeAndClear(player);
    var death = mock(PlayerDeathEvent.class);
    when(death.getEntity()).thenReturn(player);

    listener.onPlayerDeath(death);
    when(death.isCancelled()).thenReturn(true);
    listener.onInventoryClose(new InventoryCloseEvent(view, InventoryCloseEvent.Reason.PLAYER));

    verify(storage).setItem(0, original);
    assertFalse(gui.getHumanEntityCache().contains(player));
  }

  @Test
  void preparingATopOnlyAnvilLeavesStorageAvailableForNativeDeathDrops() {
    var gui = openAnvil();
    when(storage.getItem(0)).thenReturn(mock(ItemStack.class));

    assertFalse(gui.prepareOpen(player));

    assertFalse(gui.getHumanEntityCache().contains(player));
    verify(storage, never()).setItem(anyInt(), any());
  }

  @ParameterizedTest
  @CsvSource({
    "SWAP_OFFHAND,HOTBAR_SWAP,3",
    "NUMBER_KEY,HOTBAR_SWAP,3",
    "SWAP_OFFHAND,HOTBAR_SWAP,0",
    "NUMBER_KEY,HOTBAR_SWAP,0",
    "DOUBLE_CLICK,COLLECT_TO_CURSOR,0",
    "SHIFT_LEFT,MOVE_TO_OTHER_INVENTORY,0"
  })
  void anvilBlocksTransfersFromTheLivePlayerInventory(
      ClickType clickType, InventoryAction action, int rawSlot) {
    var gui = openAnvil();
    gui.prepareOpen(player);
    when(view.getInventory(rawSlot)).thenReturn(rawSlot < 3 ? gui.getInventory() : storage);
    var event =
        new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, clickType, action);

    listener.onInventoryClick(event);

    assertTrue(event.isCancelled());
  }

  @Test
  void anvilDisablesItsUnrenderedBottomComponent() {
    var gui = openAnvil();
    gui.setPlayerInventoryUsed(true);
    var event = mock(InventoryDragEvent.class);
    when(event.getInventory()).thenReturn(gui.getInventory());
    when(event.getView()).thenReturn(view);
    when(event.getRawSlots()).thenReturn(Set.of(3));

    listener.onInventoryDrag(event);

    assertFalse(gui.isPlayerInventoryUsed());
    verify(event).setCancelled(true);
  }

  @Test
  void switchingGuisDoesNotHoldTheTargetMonitorDuringPlatformCalls() {
    var previous = openAnvil();
    var next = anvil();
    var otherPlayer = mock(Player.class);
    var otherView = mock(InventoryView.class);
    when(otherView.getPlayer()).thenReturn(otherPlayer);
    when(otherView.getTopInventory()).thenReturn(next.getInventory());
    next.markViewer(otherPlayer);
    previous.setOnClose(event -> assertFalse(Thread.holdsLock(next)));

    doAnswer(
            invocation -> {
              assertFalse(Thread.holdsLock(next));
              listener.onInventoryClose(new InventoryCloseEvent(view));
              when(view.getTopInventory()).thenReturn(null);
              return null;
            })
        .when(player)
        .closeInventory();
    when(player.openInventory(next.getInventory()))
        .thenAnswer(
            invocation -> {
              assertFalse(Thread.holdsLock(next));
              listener.onInventoryClose(new InventoryCloseEvent(otherView));
              assertSame(next, Gui.getGui(next.getInventory()));
              return view;
            });

    next.show(player);

    assertFalse(previous.isViewer(player));
    assertTrue(next.isViewer(player));
  }

  @Test
  void pickupHonorsEarlierCancellation() throws Exception {
    var gui = openAnvil();
    gui.getHumanEntityCache().storeAndClear(player);
    var item = mock(Item.class);
    var stack = mock(ItemStack.class);
    when(stack.clone()).thenReturn(stack);
    when(item.getItemStack()).thenReturn(stack);
    var event = new EntityPickupItemEvent(player, item, 0);
    event.setCancelled(true);
    var handler =
        GuiListener.class
            .getMethod("onEntityPickupItem", EntityPickupItemEvent.class)
            .getAnnotation(EventHandler.class);
    var registration =
        new RegisteredListener(
            listener,
            (target, pickup) -> listener.onEntityPickupItem((EntityPickupItemEvent) pickup),
            handler.priority(),
            plugin,
            handler.ignoreCancelled());

    registration.callEvent(event);

    verify(item, never()).remove();
    assertEquals(EventPriority.HIGHEST, registration.getPriority());

    event.setCancelled(false);
    registration.callEvent(event);

    verify(item).remove();
    assertTrue(event.isCancelled());
  }

  private AnvilGui openAnvil() {
    var gui = anvil();
    when(view.getTopInventory()).thenReturn(gui.getInventory());
    gui.markViewer(player);
    return gui;
  }

  private AnvilGui anvil() {
    var adapter = mock(AnvilInventory.class);
    var inventory = mock(Inventory.class);
    when(adapter.createInventory(any())).thenReturn(inventory);
    when(inventory.getSize()).thenReturn(3);
    var gui = new AnvilGui(plugin, adapter);
    gui.createInventory();
    return gui;
  }
}
