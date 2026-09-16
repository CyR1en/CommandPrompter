package dev.cyr1en.promptpaper.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptui.gui.ChestGui;
import dev.cyr1en.promptui.gui.Gui;
import dev.cyr1en.promptui.gui.GuiComponent;
import dev.cyr1en.promptui.gui.GuiItem;
import dev.cyr1en.promptui.gui.GuiListener;
import dev.cyr1en.promptui.gui.Slot;
import dev.cyr1en.promptui.pane.Mask;
import dev.cyr1en.promptui.pane.OutlinePane;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GuiLifecycleTest extends MockBukkitTest {

  private Gui firstGui;

  @BeforeEach
  void registerGuiListener() {
    firstGui = testGui();
    HandlerList.unregisterAll((Plugin) plugin);
    new GuiListener(plugin);
  }

  @AfterEach
  void clearGuiRegistry() {
    HandlerList.unregisterAll((Plugin) plugin);
    Gui.clearRegistry();
  }

  @Test
  void switchingGuisPreservesPlayerInventory() {
    var player = createPlayer("Viewer");
    var diamonds = new ItemStack(Material.DIAMOND, 5);
    player.getInventory().setItem(0, diamonds);
    var secondGui = testGui();

    firstGui.show(player);
    secondGui.show(player);

    assertNull(player.getInventory().getItem(0));
    assertFalse(firstGui.getHumanEntityCache().contains(player));
    player.closeInventory();
    assertEquals(diamonds, player.getInventory().getItem(0));
    assertFalse(secondGui.getHumanEntityCache().contains(player));
  }

  @Test
  void cancelledReplacementRestoresPlayerInventory() {
    var player = spy(createPlayer("Viewer"));
    var diamonds = new ItemStack(Material.DIAMOND, 5);
    player.getInventory().setItem(0, diamonds);
    var secondGui = testGui();
    doReturn(null).when(player).openInventory(secondGui.getInventory());

    firstGui.show(player);
    assertThrows(IllegalStateException.class, () -> secondGui.show(player));

    assertEquals(diamonds, player.getInventory().getItem(0));
    assertFalse(firstGui.getHumanEntityCache().contains(player));
    assertFalse(secondGui.getHumanEntityCache().contains(player));
    assertFalse(secondGui.hasViewers());
  }

  @Test
  void showingTheSameGuiTwiceKeepsItsInventorySnapshot() {
    var player = createPlayer("Viewer");
    var diamonds = new ItemStack(Material.DIAMOND, 5);
    player.getInventory().setItem(0, diamonds);

    firstGui.show(player);
    firstGui.show(player);

    assertNull(player.getInventory().getItem(0));
    assertTrue(firstGui.getHumanEntityCache().contains(player));
    player.closeInventory();
    assertEquals(diamonds, player.getInventory().getItem(0));
  }

  @Test
  void replacementRestoresAGuiOpenedByThePreviousCloseCallback() {
    var player = createPlayer("Viewer");
    var diamonds = new ItemStack(Material.DIAMOND, 5);
    player.getInventory().setItem(0, diamonds);
    var secondGui = testGui();
    var intermediateGui = testGui();
    firstGui.setOnClose(event -> intermediateGui.show(player));

    firstGui.show(player);
    secondGui.show(player);

    assertFalse(intermediateGui.getHumanEntityCache().contains(player));
    player.closeInventory();
    assertEquals(diamonds, player.getInventory().getItem(0));
  }

  @Test
  void previousCloseCallbackCanOpenTheRequestedGui() {
    var player = createPlayer("Viewer");
    var diamonds = new ItemStack(Material.DIAMOND, 5);
    player.getInventory().setItem(0, diamonds);
    var secondGui = testGui();
    firstGui.setOnClose(event -> secondGui.show(player));

    firstGui.show(player);
    secondGui.show(player);

    assertNull(player.getInventory().getItem(0));
    assertTrue(secondGui.getHumanEntityCache().contains(player));
    player.closeInventory();
    assertEquals(diamonds, player.getInventory().getItem(0));
  }

  private Gui testGui() {
    return new Gui(plugin, server.createInventory(null, 9)) {
      @Override
      public void update() {}

      @Override
      public boolean click(InventoryClickEvent event) {
        return false;
      }
    };
  }

  @Test
  void activeGuiRemainsTrackedAfterItsInventoryMappingIsDetached() {
    var gui = spy(new ChestGui(plugin, 1));
    var player = createPlayer("Viewer");
    var inventory = mock(Inventory.class);
    doReturn(inventory).when(gui).getInventory();

    Gui.addInventory(inventory, gui);
    gui.markViewer(player);
    Gui.removeInventories(gui);

    assertFalse(Gui.getGuis().contains(gui));
    assertTrue(Gui.getActiveGuis().contains(gui));

    new GuiListener(plugin).closeAll();

    assertFalse(gui.hasViewers());
    assertFalse(Gui.getActiveGuis().contains(gui));
  }

  @Test
  void outlineClickUsesTheMaskedHorizontalLayout() {
    var clicked = new AtomicReference<String>();
    var first = item("first", clicked);
    var second = item("second", clicked);
    var pane = new OutlinePane(3, 2, List.of(first, second));
    var mask = new Mask(3, 2);
    mask.setEnabled(0, 0, false);
    pane.setMask(mask);

    var event = clickEventFor(second);

    assertTrue(pane.click(mock(Gui.class), mock(GuiComponent.class), event, Slot.of(2, 0)));
    assertEquals("second", clicked.get());
  }

  @Test
  void outlineClickUsesTheMaskedVerticalLayout() {
    var clicked = new AtomicReference<String>();
    var first = item("first", clicked);
    var second = item("second", clicked);
    var pane = new OutlinePane(3, 2, List.of(first, second));
    var mask = new Mask(3, 2);
    mask.setEnabled(0, 0, false);
    pane.setMask(mask);
    pane.setOrientation(OutlinePane.Orientation.VERTICAL);

    var event = clickEventFor(second);

    assertTrue(pane.click(mock(Gui.class), mock(GuiComponent.class), event, Slot.of(1, 0)));
    assertEquals("second", clicked.get());
  }

  private static GuiItem item(String name, AtomicReference<String> clicked) {
    var item = new GuiItem(new ItemStack(Material.PAPER), event -> clicked.set(name));
    item.applyUUID();
    return item;
  }

  private static InventoryClickEvent clickEventFor(GuiItem item) {
    var event = mock(InventoryClickEvent.class);
    when(event.getCurrentItem()).thenReturn(item.getItem());
    return event;
  }
}
