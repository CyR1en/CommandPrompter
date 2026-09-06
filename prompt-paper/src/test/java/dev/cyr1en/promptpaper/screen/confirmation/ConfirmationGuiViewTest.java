package dev.cyr1en.promptpaper.screen.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmationGuiViewTest extends MockBukkitTest {

  private Player player;
  private ConfirmationGuiView view;
  private AtomicReference<ConfirmationOutcome> outcomeRef;
  private AtomicInteger outcomeCount;

  @BeforeEach
  void setUpConfirmationGui() {
    player = createPlayer("ConfirmUser");
    outcomeRef = new AtomicReference<>();
    outcomeCount = new AtomicInteger();

    var config =
        ConfirmationGuiConfig.createDefault(
            Component.text("Confirm Action"), Component.text("Are you sure?"));
    view = new ConfirmationGuiView(plugin, player, config);
  }

  @Test
  void layoutSlotsAndMaterials() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    var inventory = view.getInventory();
    assertNotNull(inventory);
    assertEquals(27, inventory.getSize());

    // Slot 11: Confirm
    var confirmItem = inventory.getItem(11);
    assertNotNull(confirmItem);
    assertEquals(Material.LIME_STAINED_GLASS_PANE, confirmItem.getType());

    // Slot 13: Info
    var infoItem = inventory.getItem(13);
    assertNotNull(infoItem);
    assertEquals(Material.PAPER, infoItem.getType());

    // Slot 15: Decline
    var declineItem = inventory.getItem(15);
    assertNotNull(declineItem);
    assertEquals(Material.RED_STAINED_GLASS_PANE, declineItem.getType());

    // Filler slots
    for (int i = 0; i < 27; i++) {
      if (i == 11 || i == 13 || i == 15) continue;
      var filler = inventory.getItem(i);
      assertNotNull(filler, "Slot " + i + " should have a filler item");
      assertEquals(Material.GRAY_STAINED_GLASS_PANE, filler.getType());
    }

    assertTrue(view.isOpen());
    assertTrue(view.isListenerRegistered());
  }

  @Test
  void confirmClickEmitsConfirmedAndCleansUp() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    var invView = player.getOpenInventory();
    var clickEvent =
        new InventoryClickEvent(
            invView, InventoryType.SlotType.CONTAINER, 11, ClickType.LEFT, InventoryAction.NOTHING);

    server.getPluginManager().callEvent(clickEvent);

    assertTrue(clickEvent.isCancelled());
    assertEquals(Event.Result.DENY, clickEvent.getResult());
    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Confirmed.class, outcomeRef.get());
    assertFalse(view.isOpen());
    assertFalse(view.isListenerRegistered());
  }

  @Test
  void declineClickEmitsDeclinedAndCleansUp() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    var invView = player.getOpenInventory();
    var clickEvent =
        new InventoryClickEvent(
            invView, InventoryType.SlotType.CONTAINER, 15, ClickType.LEFT, InventoryAction.NOTHING);

    server.getPluginManager().callEvent(clickEvent);

    assertTrue(clickEvent.isCancelled());
    assertEquals(Event.Result.DENY, clickEvent.getResult());
    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Declined.class, outcomeRef.get());
    assertFalse(view.isOpen());
    assertFalse(view.isListenerRegistered());
  }

  @Test
  void infoSlotClickIsCancelledWithoutEmittingOutcome() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    var invView = player.getOpenInventory();
    var clickEvent =
        new InventoryClickEvent(
            invView, InventoryType.SlotType.CONTAINER, 13, ClickType.LEFT, InventoryAction.NOTHING);

    server.getPluginManager().callEvent(clickEvent);

    assertTrue(clickEvent.isCancelled());
    assertEquals(0, outcomeCount.get());
    assertTrue(view.isOpen());
    assertTrue(view.isListenerRegistered());
  }

  @Test
  void playerEscCloseEmitsDeclinedExactlyOnce() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    var invView = player.getOpenInventory();
    var closeEvent = new InventoryCloseEvent(invView);

    server.getPluginManager().callEvent(closeEvent);

    assertEquals(1, outcomeCount.get());
    assertInstanceOf(ConfirmationOutcome.Declined.class, outcomeRef.get());
    assertFalse(view.isOpen());
    assertFalse(view.isListenerRegistered());

    // Repeated close event should be ignored
    server.getPluginManager().callEvent(closeEvent);
    assertEquals(1, outcomeCount.get());
  }

  @Test
  void programmaticCloseEmitsNothing() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    view.close();

    assertFalse(view.isOpen());
    assertFalse(view.isListenerRegistered());
    assertEquals(0, outcomeCount.get());

    // Closing the inventory on Bukkit side emits nothing
    var invView = player.getOpenInventory();
    var closeEvent = new InventoryCloseEvent(invView);
    server.getPluginManager().callEvent(closeEvent);
    assertEquals(0, outcomeCount.get());
  }

  @Test
  void duplicateClicksEmitOnlyOnce() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    var invView = player.getOpenInventory();
    var click1 =
        new InventoryClickEvent(
            invView, InventoryType.SlotType.CONTAINER, 11, ClickType.LEFT, InventoryAction.NOTHING);
    var click2 =
        new InventoryClickEvent(
            invView, InventoryType.SlotType.CONTAINER, 11, ClickType.LEFT, InventoryAction.NOTHING);

    server.getPluginManager().callEvent(click1);
    server.getPluginManager().callEvent(click2);

    assertEquals(1, outcomeCount.get());
  }

  @Test
  void allInventoryInteractionsCancelled() {
    view.open(outcome -> {});
    server.getScheduler().performOneTick();

    var invView = player.getOpenInventory();

    // 1. Shift click in top inventory
    var shiftClickTop =
        new InventoryClickEvent(
            invView,
            InventoryType.SlotType.CONTAINER,
            0,
            ClickType.SHIFT_LEFT,
            InventoryAction.MOVE_TO_OTHER_INVENTORY);
    server.getPluginManager().callEvent(shiftClickTop);
    assertTrue(shiftClickTop.isCancelled());
    assertEquals(Event.Result.DENY, shiftClickTop.getResult());

    // 2. Click in bottom inventory (player inventory slot index 27+)
    var clickBottom =
        new InventoryClickEvent(
            invView,
            InventoryType.SlotType.QUICKBAR,
            27,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL);
    server.getPluginManager().callEvent(clickBottom);
    assertTrue(clickBottom.isCancelled());
    assertEquals(Event.Result.DENY, clickBottom.getResult());

    // 3. Number key swap
    var numberKey =
        new InventoryClickEvent(
            invView,
            InventoryType.SlotType.CONTAINER,
            11,
            ClickType.NUMBER_KEY,
            InventoryAction.HOTBAR_SWAP);
    server.getPluginManager().callEvent(numberKey);
    assertTrue(numberKey.isCancelled());
    assertEquals(Event.Result.DENY, numberKey.getResult());

    // 4. Offhand swap (F key)
    var offhandSwap =
        new InventoryClickEvent(
            invView,
            InventoryType.SlotType.CONTAINER,
            11,
            ClickType.SWAP_OFFHAND,
            InventoryAction.HOTBAR_SWAP);
    server.getPluginManager().callEvent(offhandSwap);
    assertTrue(offhandSwap.isCancelled());
    assertEquals(Event.Result.DENY, offhandSwap.getResult());

    // 5. Drag event
    var dragEvent =
        new InventoryDragEvent(
            invView,
            new ItemStack(Material.DIRT),
            new ItemStack(Material.DIRT),
            false,
            Map.of(0, new ItemStack(Material.DIRT)));
    server.getPluginManager().callEvent(dragEvent);
    assertTrue(dragEvent.isCancelled());
    assertEquals(Event.Result.DENY, dragEvent.getResult());
  }

  @Test
  void playerQuitCancelsAndCleansUp() {
    view.open(
        outcome -> {
          outcomeRef.set(outcome);
          outcomeCount.incrementAndGet();
        });
    server.getScheduler().performOneTick();

    var quitEvent = new PlayerQuitEvent(player, (Component) null);
    server.getPluginManager().callEvent(quitEvent);

    assertEquals(1, outcomeCount.get());
    assertTrue(outcomeRef.get().isCancelled());
    var cancelled = (ConfirmationOutcome.Cancelled) outcomeRef.get();
    assertEquals(CancelReason.MANUAL, cancelled.reason());
    assertFalse(view.isOpen());
    assertFalse(view.isListenerRegistered());
  }

  @Test
  void repeatedCloseIsIdempotent() {
    assertFalse(view.isOpen());
    view.close();
    assertFalse(view.isOpen());

    view.open(outcome -> {});
    server.getScheduler().performOneTick();
    assertTrue(view.isOpen());

    view.close();
    assertFalse(view.isOpen());
    view.close();
    assertFalse(view.isOpen());
  }
}
