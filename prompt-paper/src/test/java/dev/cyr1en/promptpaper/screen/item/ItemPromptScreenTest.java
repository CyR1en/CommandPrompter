package dev.cyr1en.promptpaper.screen.item;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptcore.ItemOutputFormat;
import dev.cyr1en.promptcore.ItemSource;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.item.catalog.CatalogEntry;
import dev.cyr1en.promptpaper.item.catalog.CatalogSnapshot;
import dev.cyr1en.promptpaper.preset.ItemPrompt;
import dev.cyr1en.promptui.ScreenResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

@DisplayName("ItemPromptScreen Runtime & Lifecycle Tests")
class ItemPromptScreenTest extends MockBukkitTest {

  private PlayerMock player;
  private AtomicReference<ScreenResult> resultRef;
  private AtomicInteger resultCount;

  @BeforeEach
  void setUpItemPromptScreen() {
    player = createPlayer("ItemUser");
    resultRef = new AtomicReference<>();
    resultCount = new AtomicInteger();
  }

  private void attachCallback(ItemPromptScreen screen) {
    screen.onResult(
        result -> {
          resultRef.set(result);
          resultCount.incrementAndGet();
        });
  }

  private InventoryClickEvent createTopClickEvent(
      ItemPromptScreen screen, int slot, ClickType clickType) {
    InventoryView view = player.getOpenInventory();
    return new InventoryClickEvent(
        view, InventoryType.SlotType.CONTAINER, slot, clickType, InventoryAction.PICKUP_ALL);
  }

  private InventoryClickEvent createBottomClickEvent(
      ItemPromptScreen screen, int playerSlot, ClickType clickType) {
    InventoryView view = player.getOpenInventory();
    int rawSlot = view.getTopInventory().getSize() + playerSlot;
    return new InventoryClickEvent(
        view, InventoryType.SlotType.CONTAINER, rawSlot, clickType, InventoryAction.PICKUP_ALL);
  }

  // =========================================================================
  // ITEM-02: INVENTORY & ARMOR Mode Layout & Selectable Slots
  // =========================================================================

  @Test
  @DisplayName("INVENTORY mode mirrors storage, hotbar, equipment, dividers, and controls")
  void testInventoryModeLayoutAndItemMirroring() {
    // Hotbar 0..8
    player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD, 1));
    player.getInventory().setItem(8, new ItemStack(Material.TORCH, 32));

    // Main storage 9..35
    player.getInventory().setItem(9, new ItemStack(Material.APPLE, 16));
    player.getInventory().setItem(35, new ItemStack(Material.GOLD_INGOT, 5));

    // Armor 36..39
    player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
    player.getInventory().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
    player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
    player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));

    // Offhand 40
    player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));

    var prompt =
        new ItemPrompt(
            "item",
            "test",
            "Pick item",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);

    screen.open();
    assertTrue(screen.isOpen());
    assertTrue(screen.isListenerRegistered());

    var inv = screen.getInventory();
    assertNotNull(inv);
    assertEquals(54, inv.getSize());

    // Storage slots 0..26 mirror player slots 9..35
    assertEquals(Material.APPLE, inv.getItem(0).getType());
    assertEquals(16, inv.getItem(0).getAmount());
    assertEquals(Material.GOLD_INGOT, inv.getItem(26).getType());
    assertEquals(5, inv.getItem(26).getAmount());

    // Hotbar slots 27..35 mirror player slots 0..8
    assertEquals(Material.DIAMOND_SWORD, inv.getItem(27).getType());
    assertEquals(Material.TORCH, inv.getItem(35).getType());
    assertEquals(32, inv.getItem(35).getAmount());

    // Divider row 36..44
    for (int i = 36; i <= 44; i++) {
      assertEquals(
          Material.GRAY_STAINED_GLASS_PANE,
          inv.getItem(i).getType(),
          "Slot " + i + " should be divider");
    }

    // Equipment row: 45 offhand, 46 boots, 47 leggings, 48 chest, 49 helmet
    assertEquals(Material.SHIELD, inv.getItem(45).getType());
    assertEquals(Material.DIAMOND_BOOTS, inv.getItem(46).getType());
    assertEquals(Material.DIAMOND_LEGGINGS, inv.getItem(47).getType());
    assertEquals(Material.DIAMOND_CHESTPLATE, inv.getItem(48).getType());
    assertEquals(Material.DIAMOND_HELMET, inv.getItem(49).getType());

    // Controls: 50 info, 51..52 filler, 53 cancel
    assertEquals(Material.PAPER, inv.getItem(50).getType());
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, inv.getItem(51).getType());
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, inv.getItem(52).getType());
    assertEquals(Material.BARRIER, inv.getItem(53).getType());
  }

  @Test
  @DisplayName("INVENTORY mode outputs KEY, MATERIAL, SLOT, and AMOUNT tokens correctly")
  void testInventoryModeSelectionAllOutputFormats() {
    player
        .getInventory()
        .setItem(0, new ItemStack(Material.DIAMOND, 12)); // hotbar 0 -> GUI slot 27

    // KEY
    var promptKey =
        new ItemPrompt(
            "item", "k", "Choose", ItemSource.INVENTORY, ItemOutputFormat.KEY, null, null, false);
    var screenKey = new ItemPromptScreen(plugin, player, promptKey);
    screenKey.onResult(resultRef::set);
    screenKey.open();
    var eventKey = createTopClickEvent(screenKey, 27, ClickType.LEFT);
    screenKey.onInventoryClick(eventKey);
    assertEquals("minecraft:diamond", resultRef.get().answer());

    // MATERIAL
    var promptMat =
        new ItemPrompt(
            "item",
            "m",
            "Choose",
            ItemSource.INVENTORY,
            ItemOutputFormat.MATERIAL,
            null,
            null,
            false);
    var screenMat = new ItemPromptScreen(plugin, player, promptMat);
    screenMat.onResult(resultRef::set);
    screenMat.open();
    var eventMat = createTopClickEvent(screenMat, 27, ClickType.LEFT);
    screenMat.onInventoryClick(eventMat);
    assertEquals("DIAMOND", resultRef.get().answer());

    // SLOT (returns physical player slot index "0")
    var promptSlot =
        new ItemPrompt(
            "item", "s", "Choose", ItemSource.INVENTORY, ItemOutputFormat.SLOT, null, null, false);
    var screenSlot = new ItemPromptScreen(plugin, player, promptSlot);
    screenSlot.onResult(resultRef::set);
    screenSlot.open();
    var eventSlot = createTopClickEvent(screenSlot, 27, ClickType.LEFT);
    screenSlot.onInventoryClick(eventSlot);
    assertEquals("0", resultRef.get().answer());

    // AMOUNT (returns string representation of amount "12")
    var promptAmt =
        new ItemPrompt(
            "item",
            "a",
            "Choose",
            ItemSource.INVENTORY,
            ItemOutputFormat.AMOUNT,
            null,
            null,
            false);
    var screenAmt = new ItemPromptScreen(plugin, player, promptAmt);
    screenAmt.onResult(resultRef::set);
    screenAmt.open();
    var eventAmt = createTopClickEvent(screenAmt, 27, ClickType.LEFT);
    screenAmt.onInventoryClick(eventAmt);
    assertEquals("12", resultRef.get().answer());
  }

  @Test
  @DisplayName("ARMOR mode allows only equipment slots 45..49 to be selected")
  void testArmorModeLayoutAndSelectableSlots() {
    player
        .getInventory()
        .setItem(0, new ItemStack(Material.GOLDEN_SWORD)); // hotbar 0 -> GUI slot 27
    player.getInventory().setItem(9, new ItemStack(Material.BREAD, 5)); // storage 9 -> GUI slot 0
    player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS)); // boots -> GUI slot 46
    player
        .getInventory()
        .setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING)); // offhand -> GUI slot 45

    var prompt =
        new ItemPrompt(
            "item",
            "armor",
            "Pick armor",
            ItemSource.ARMOR,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    // Clicking storage slot 0 (Bread) should do nothing in ARMOR mode
    var clickStorage = createTopClickEvent(screen, 0, ClickType.LEFT);
    screen.onInventoryClick(clickStorage);
    assertNull(resultRef.get());
    assertTrue(screen.isOpen());

    // Clicking hotbar slot 27 (Golden Sword) should do nothing in ARMOR mode
    var clickHotbar = createTopClickEvent(screen, 27, ClickType.LEFT);
    screen.onInventoryClick(clickHotbar);
    assertNull(resultRef.get());
    assertTrue(screen.isOpen());

    // Clicking equipment slot 46 (Iron Boots) should succeed
    var clickBoots = createTopClickEvent(screen, 46, ClickType.LEFT);
    screen.onInventoryClick(clickBoots);
    assertNotNull(resultRef.get());
    assertEquals("minecraft:iron_boots", resultRef.get().answer());
    assertFalse(screen.isOpen());
  }

  // =========================================================================
  // ITEM-02: HAND Mode Layout & Mirroring
  // =========================================================================

  @Test
  @DisplayName("HAND mode captures held item at slot 4, cancel at slot 8, fillers elsewhere")
  void testHandModeLayoutAndMirroring() {
    player.getInventory().setHeldItemSlot(3);
    player.getInventory().setItem(3, new ItemStack(Material.BOW, 1));

    var prompt =
        new ItemPrompt(
            "item", "hand", "Pick hand", ItemSource.HAND, ItemOutputFormat.KEY, null, null, false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    var inv = screen.getInventory();
    assertEquals(9, inv.getSize());

    // Fillers at 0..3, 5..7
    for (int i = 0; i < 9; i++) {
      if (i == 4) {
        assertEquals(Material.BOW, inv.getItem(4).getType());
      } else if (i == 8) {
        assertEquals(Material.BARRIER, inv.getItem(8).getType());
      } else {
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, inv.getItem(i).getType());
      }
    }

    // Clicking filler slot 0 does nothing
    var clickFiller = createTopClickEvent(screen, 0, ClickType.LEFT);
    screen.onInventoryClick(clickFiller);
    assertNull(resultRef.get());
    assertTrue(screen.isOpen());

    // Clicking slot 4 delivers answer
    var clickHand = createTopClickEvent(screen, 4, ClickType.LEFT);
    screen.onInventoryClick(clickHand);
    assertNotNull(resultRef.get());
    assertEquals("minecraft:bow", resultRef.get().answer());
    assertFalse(screen.isOpen());
  }

  // =========================================================================
  // ITEM-02 & ITEM-04: CATALOG Mode Pagination (0, 1, 45, 46 entries) & Stable Choices
  // =========================================================================

  @Test
  @DisplayName("CATALOG mode with 0 entries displays empty placeholder and handles clicks safely")
  void testCatalogModeZeroEntries() {
    var catalog = new CatalogSnapshot(Map.of("all", List.of()));
    var prompt =
        new ItemPrompt(
            "item",
            "cat0",
            "Catalog",
            ItemSource.CATALOG,
            ItemOutputFormat.KEY,
            "all",
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt, catalog);
    attachCallback(screen);
    screen.open();

    var inv = screen.getInventory();
    assertEquals(54, inv.getSize());
    assertEquals(Material.GRAY_DYE, inv.getItem(22).getType()); // empty placeholder
    assertEquals(Material.PAPER, inv.getItem(49).getType()); // page info
    assertEquals(Material.ARROW, inv.getItem(45).getType()); // prev
    assertEquals(Material.ARROW, inv.getItem(52).getType()); // next
    assertEquals(Material.BARRIER, inv.getItem(53).getType()); // cancel

    // Clicking empty slot 22 does nothing
    var clickEmpty = createTopClickEvent(screen, 22, ClickType.LEFT);
    screen.onInventoryClick(clickEmpty);
    assertNull(resultRef.get());

    // Clicking prev/next does nothing
    screen.onInventoryClick(createTopClickEvent(screen, 45, ClickType.LEFT));
    screen.onInventoryClick(createTopClickEvent(screen, 52, ClickType.LEFT));
    assertNull(resultRef.get());
    assertTrue(screen.isOpen());

    // Cancel works
    screen.onInventoryClick(createTopClickEvent(screen, 53, ClickType.LEFT));
    assertNotNull(resultRef.get());
    assertTrue(resultRef.get().cancelled());
    assertEquals(CancelReason.MANUAL, resultRef.get().cancelReason());
  }

  @Test
  @DisplayName("CATALOG mode with 1 entry renders at slot 0 and allows selection")
  void testCatalogModeOneEntry() {
    var entry = CatalogEntry.of(Material.EMERALD);
    var catalog = new CatalogSnapshot(Map.of("all", List.of(entry)));
    var prompt =
        new ItemPrompt(
            "item",
            "cat1",
            "Catalog",
            ItemSource.CATALOG,
            ItemOutputFormat.KEY,
            "all",
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt, catalog);
    attachCallback(screen);
    screen.open();

    var inv = screen.getInventory();
    assertEquals(Material.EMERALD, inv.getItem(0).getType());
    assertNull(inv.getItem(1));

    // Click slot 0
    var click = createTopClickEvent(screen, 0, ClickType.LEFT);
    screen.onInventoryClick(click);
    assertNotNull(resultRef.get());
    assertEquals("minecraft:emerald", resultRef.get().answer());
    assertFalse(screen.isOpen());
  }

  @Test
  @DisplayName("CATALOG mode with exactly 45 entries occupies 1 full page without overflowing")
  void testCatalogModeFortyFiveEntries() {
    List<CatalogEntry> entries = new ArrayList<>();
    Material[] materials = Material.values();
    int count = 0;
    for (Material m : materials) {
      if (m.isItem() && !m.isAir()) {
        entries.add(CatalogEntry.of(m));
        count++;
        if (count == 45) break;
      }
    }
    assertEquals(45, entries.size());

    var catalog = new CatalogSnapshot(Map.of("all", entries));
    var prompt =
        new ItemPrompt(
            "item",
            "cat45",
            "Catalog",
            ItemSource.CATALOG,
            ItemOutputFormat.KEY,
            "all",
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt, catalog);
    attachCallback(screen);
    screen.open();

    var inv = screen.getInventory();
    // Slots 0..44 should all be populated
    for (int i = 0; i <= 44; i++) {
      assertNotNull(inv.getItem(i), "Slot " + i + " should be populated");
    }

    // Next page does nothing since pageCount is 1
    screen.onInventoryClick(createTopClickEvent(screen, 52, ClickType.LEFT));
    assertEquals(0, screen.getCatalogPage());

    // Selecting slot 44 emits the 45th entry's key
    screen.onInventoryClick(createTopClickEvent(screen, 44, ClickType.LEFT));
    assertNotNull(resultRef.get());
    assertEquals(entries.get(44).canonicalKey(), resultRef.get().answer());
  }

  @Test
  @DisplayName(
      "CATALOG mode with 46 entries paginates correctly and allows stable selection across pages")
  void testCatalogModeFortySixEntriesPaginationAndNavigation() {
    List<CatalogEntry> entries = new ArrayList<>();
    Material[] materials = Material.values();
    int count = 0;
    for (Material m : materials) {
      if (m.isItem() && !m.isAir()) {
        entries.add(CatalogEntry.of(m));
        count++;
        if (count == 46) break;
      }
    }
    assertEquals(46, entries.size());

    var catalog = new CatalogSnapshot(Map.of("all", entries));
    var prompt =
        new ItemPrompt(
            "item",
            "cat46",
            "Catalog",
            ItemSource.CATALOG,
            ItemOutputFormat.KEY,
            "all",
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt, catalog);
    attachCallback(screen);
    screen.open();

    assertEquals(0, screen.getCatalogPage());
    assertEquals(entries.get(0).material(), screen.getInventory().getItem(0).getType());

    // Click NEXT page (slot 52)
    screen.onInventoryClick(createTopClickEvent(screen, 52, ClickType.LEFT));
    assertEquals(1, screen.getCatalogPage());
    // Page 1 should contain entry 45 at slot 0
    assertEquals(entries.get(45).material(), screen.getInventory().getItem(0).getType());
    assertNull(screen.getInventory().getItem(1));

    // Click NEXT page again; should remain on page 1
    screen.onInventoryClick(createTopClickEvent(screen, 52, ClickType.LEFT));
    assertEquals(1, screen.getCatalogPage());

    // Click PREV page (slot 45); should return to page 0
    screen.onInventoryClick(createTopClickEvent(screen, 45, ClickType.LEFT));
    assertEquals(0, screen.getCatalogPage());
    assertEquals(entries.get(0).material(), screen.getInventory().getItem(0).getType());

    // Click PREV page again; should stay on page 0
    screen.onInventoryClick(createTopClickEvent(screen, 45, ClickType.LEFT));
    assertEquals(0, screen.getCatalogPage());

    // Advance to page 1 and select the 46th item
    screen.onInventoryClick(createTopClickEvent(screen, 52, ClickType.LEFT));
    screen.onInventoryClick(createTopClickEvent(screen, 0, ClickType.LEFT));
    assertNotNull(resultRef.get());
    assertEquals(entries.get(45).canonicalKey(), resultRef.get().answer());
  }

  @Test
  @DisplayName("CATALOG mode supports KEY, MATERIAL, and AMOUNT output formats")
  void testCatalogModeOutputFormats() {
    var entry = CatalogEntry.of(Material.GOLDEN_APPLE);
    var catalog = new CatalogSnapshot(Map.of("all", List.of(entry)));

    // KEY
    var pKey =
        new ItemPrompt(
            "item", "ck", "Cat", ItemSource.CATALOG, ItemOutputFormat.KEY, "all", null, false);
    var sKey = new ItemPromptScreen(plugin, player, pKey, catalog);
    sKey.onResult(resultRef::set);
    sKey.open();
    sKey.onInventoryClick(createTopClickEvent(sKey, 0, ClickType.LEFT));
    assertEquals("minecraft:golden_apple", resultRef.get().answer());

    // MATERIAL
    var pMat =
        new ItemPrompt(
            "item", "cm", "Cat", ItemSource.CATALOG, ItemOutputFormat.MATERIAL, "all", null, false);
    var sMat = new ItemPromptScreen(plugin, player, pMat, catalog);
    sMat.onResult(resultRef::set);
    sMat.open();
    sMat.onInventoryClick(createTopClickEvent(sMat, 0, ClickType.LEFT));
    assertEquals("GOLDEN_APPLE", resultRef.get().answer());

    // AMOUNT (always 1 for catalog)
    var pAmt =
        new ItemPrompt(
            "item", "ca", "Cat", ItemSource.CATALOG, ItemOutputFormat.AMOUNT, "all", null, false);
    var sAmt = new ItemPromptScreen(plugin, player, pAmt, catalog);
    sAmt.onResult(resultRef::set);
    sAmt.open();
    sAmt.onInventoryClick(createTopClickEvent(sAmt, 0, ClickType.LEFT));
    assertEquals("1", resultRef.get().answer());
  }

  // =========================================================================
  // ITEM-05 & ITEM-06: Event Hardening, Cancellation & Player Inventory Invariance
  // =========================================================================

  @ParameterizedTest
  @EnumSource(value = ClickType.class)
  @DisplayName("All ClickTypes in top inventory are unconditionally cancelled and denied")
  void testAllClickTypesCancelledInTopInventory(ClickType clickType) {
    player.getInventory().setItem(0, new ItemStack(Material.DIRT, 64));
    var prompt =
        new ItemPrompt(
            "item",
            "hard",
            "Hardened",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    screen.open();

    var event = createTopClickEvent(screen, 27, clickType);
    screen.onInventoryClick(event);

    assertTrue(event.isCancelled(), "ClickType " + clickType + " must be cancelled");
    assertEquals(
        Event.Result.DENY, event.getResult(), "ClickType " + clickType + " must be DENIED");
  }

  @Test
  @DisplayName(
      "Bottom inventory clicks are unconditionally cancelled, denied, and do not trigger selection")
  void testBottomInventoryClicksCancelledAndIgnored() {
    player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 1));
    var prompt =
        new ItemPrompt(
            "item",
            "bot",
            "Bottom click",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    var bottomClick = createBottomClickEvent(screen, 0, ClickType.LEFT);
    screen.onInventoryClick(bottomClick);

    assertTrue(bottomClick.isCancelled());
    assertEquals(Event.Result.DENY, bottomClick.getResult());
    assertNull(resultRef.get(), "Bottom click must not trigger result delivery");
    assertTrue(screen.isOpen());
  }

  @Test
  @DisplayName("InventoryDragEvents are unconditionally cancelled and denied")
  void testInventoryDragEventsCancelled() {
    var prompt =
        new ItemPrompt(
            "item",
            "drag",
            "Drag test",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    screen.open();

    InventoryView view = player.getOpenInventory();
    var dragEvent =
        new InventoryDragEvent(
            view,
            new ItemStack(Material.DIRT),
            new ItemStack(Material.AIR),
            false,
            Map.of(0, new ItemStack(Material.DIRT)));

    screen.onInventoryDrag(dragEvent);
    assertTrue(dragEvent.isCancelled());
    assertEquals(Event.Result.DENY, dragEvent.getResult());
  }

  @Test
  @DisplayName("Player inventory remains 100% unchanged across clicks, drags, and cancellations")
  void testPlayerInventoryRemainsUnchangedAfterInteractions() {
    ItemStack hotbarItem = new ItemStack(Material.DIAMOND_SWORD, 1);
    ItemStack storageItem = new ItemStack(Material.IRON_INGOT, 42);
    ItemStack armorItem = new ItemStack(Material.NETHERITE_CHESTPLATE, 1);
    ItemStack offhandItem = new ItemStack(Material.SHIELD, 1);

    player.getInventory().setItem(0, hotbarItem);
    player.getInventory().setItem(9, storageItem);
    player.getInventory().setChestplate(armorItem);
    player.getInventory().setItemInOffHand(offhandItem);

    var prompt =
        new ItemPrompt(
            "item",
            "inv-intact",
            "Test intact",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    screen.open();

    // Perform various click actions
    screen.onInventoryClick(createTopClickEvent(screen, 27, ClickType.SHIFT_LEFT));
    screen.onInventoryClick(createTopClickEvent(screen, 0, ClickType.NUMBER_KEY));
    screen.onInventoryClick(createTopClickEvent(screen, 48, ClickType.DOUBLE_CLICK));
    screen.onInventoryClick(createBottomClickEvent(screen, 0, ClickType.LEFT));

    // Drag action
    InventoryView view = player.getOpenInventory();
    screen.onInventoryDrag(
        new InventoryDragEvent(view, hotbarItem, ItemStack.empty(), false, Map.of(0, hotbarItem)));

    // Verify player inventory remains completely identical
    assertEquals(hotbarItem, player.getInventory().getItem(0));
    assertEquals(storageItem, player.getInventory().getItem(9));
    assertEquals(armorItem, player.getInventory().getChestplate());
    assertEquals(offhandItem, player.getInventory().getItemInOffHand());
  }

  // =========================================================================
  // ITEM-03 & ITEM-04: Physical Revalidation, Mutation/Drop Strikes & 3-Strike MANUAL Cancel
  // =========================================================================

  @Test
  @DisplayName(
      "Physical item mismatch tolerates up to 2 strikes and cancels with MANUAL on 3rd strike")
  void testPhysicalItemMismatchStrikesAndCancelOnThirdMismatch() {
    player
        .getInventory()
        .setItem(0, new ItemStack(Material.GOLD_INGOT, 10)); // hotbar 0 -> GUI slot 27

    var prompt =
        new ItemPrompt(
            "item",
            "mismatch",
            "Pick gold",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    // Mutate physical item in player's inventory before clicking
    player.getInventory().setItem(0, new ItemStack(Material.IRON_INGOT, 10));

    // Strike 1
    screen.onInventoryClick(createTopClickEvent(screen, 27, ClickType.LEFT));
    assertEquals(1, screen.getMismatchStrikes());
    assertTrue(screen.isOpen());
    assertNull(resultRef.get());

    // Strike 2
    screen.onInventoryClick(createTopClickEvent(screen, 27, ClickType.LEFT));
    assertEquals(2, screen.getMismatchStrikes());
    assertTrue(screen.isOpen());
    assertNull(resultRef.get());

    // Strike 3: should close and deliver MANUAL cancel
    screen.onInventoryClick(createTopClickEvent(screen, 27, ClickType.LEFT));
    assertEquals(3, screen.getMismatchStrikes());
    assertFalse(screen.isOpen());
    assertNotNull(resultRef.get());
    assertTrue(resultRef.get().cancelled());
    assertEquals(CancelReason.MANUAL, resultRef.get().cancelReason());
  }

  @Test
  @DisplayName("HAND mode item mismatch also increments strikes and cancels on 3rd strike")
  void testHandModeMismatchStrikes() {
    player.getInventory().setHeldItemSlot(0);
    player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));

    var prompt =
        new ItemPrompt(
            "item",
            "hand-mismatch",
            "Pick hand",
            ItemSource.HAND,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    // Player drops or swaps item in hand
    player.getInventory().setItem(0, ItemStack.empty());

    // Strike 1 & 2
    screen.onInventoryClick(createTopClickEvent(screen, 4, ClickType.LEFT));
    assertEquals(1, screen.getMismatchStrikes());
    screen.onInventoryClick(createTopClickEvent(screen, 4, ClickType.LEFT));
    assertEquals(2, screen.getMismatchStrikes());
    assertTrue(screen.isOpen());

    // Strike 3
    screen.onInventoryClick(createTopClickEvent(screen, 4, ClickType.LEFT));
    assertEquals(3, screen.getMismatchStrikes());
    assertFalse(screen.isOpen());
    assertEquals(CancelReason.MANUAL, resultRef.get().cancelReason());
  }

  // =========================================================================
  // ITEM-05, ITEM-06, ITEM-09: Cancellation, Close Paths & Exactly-Once Delivery
  // =========================================================================

  @Test
  @DisplayName("Clicking CANCEL slot delivers ScreenResult.cancel(MANUAL) and closes screen")
  void testCancelButtonClickDeliversManualCancel() {
    var prompt =
        new ItemPrompt(
            "item",
            "canc",
            "Cancel test",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    screen.onInventoryClick(createTopClickEvent(screen, 53, ClickType.LEFT));

    assertFalse(screen.isOpen());
    assertNotNull(resultRef.get());
    assertTrue(resultRef.get().cancelled());
    assertEquals(CancelReason.MANUAL, resultRef.get().cancelReason());
  }

  @Test
  @DisplayName("Manual ESC (InventoryCloseEvent) delivers ScreenResult.cancel(MANUAL)")
  void testEscManualCloseDeliversManualCancel() {
    var prompt =
        new ItemPrompt(
            "item",
            "esc",
            "Esc test",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    var closeEvent = new InventoryCloseEvent(player.getOpenInventory());
    screen.onInventoryClose(closeEvent);

    assertFalse(screen.isOpen());
    assertNotNull(resultRef.get());
    assertTrue(resultRef.get().cancelled());
    assertEquals(CancelReason.MANUAL, resultRef.get().cancelReason());
  }

  @Test
  @DisplayName("Programmatic close() is silent and invalidates callback without delivering results")
  void testProgrammaticCloseIsSilentAndDeliversNothing() {
    var prompt =
        new ItemPrompt(
            "item",
            "prog",
            "Programmatic close",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    screen.close();

    assertFalse(screen.isOpen());
    assertEquals(0, resultCount.get());
    assertNull(resultRef.get());

    // Subsequent close event fired by Bukkit should do nothing
    screen.onInventoryClose(new InventoryCloseEvent(player.getOpenInventory()));
    assertEquals(0, resultCount.get());
  }

  @Test
  @DisplayName("PlayerQuitEvent delivers ScreenResult.cancel(MANUAL) and unregisters listeners")
  @SuppressWarnings("deprecation")
  void testPlayerQuitDeliversManualCancel() {
    var prompt =
        new ItemPrompt(
            "item",
            "quit",
            "Quit test",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    var quitEvent = new PlayerQuitEvent(player, net.kyori.adventure.text.Component.text("quit"));
    screen.onPlayerQuit(quitEvent);

    assertFalse(screen.isOpen());
    assertNotNull(resultRef.get());
    assertEquals(CancelReason.MANUAL, resultRef.get().cancelReason());
    assertFalse(screen.isListenerRegistered());
  }

  @Test
  @DisplayName("Callback is delivered exactly once across duplicate or trailing events")
  @SuppressWarnings("deprecation")
  void testCallbackDeliveredExactlyOnce() {
    player.getInventory().setItem(0, new ItemStack(Material.APPLE));
    var prompt =
        new ItemPrompt(
            "item",
            "once",
            "Once test",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    // 1st click delivers answer
    screen.onInventoryClick(createTopClickEvent(screen, 27, ClickType.LEFT));
    assertEquals(1, resultCount.get());

    // Trailing events do not deliver additional results
    screen.onInventoryClick(createTopClickEvent(screen, 27, ClickType.LEFT));
    screen.onInventoryClose(new InventoryCloseEvent(player.getOpenInventory()));
    screen.onPlayerQuit(
        new PlayerQuitEvent(player, net.kyori.adventure.text.Component.text("quit")));

    assertEquals(1, resultCount.get());
  }

  // =========================================================================
  // ITEM-09: Listener Cleanup on Terminal Paths
  // =========================================================================

  @Test
  @DisplayName(
      "Listener is cleaned up on success, cancel button, manual close, programmatic close, and quit")
  @SuppressWarnings("deprecation")
  void testListenerCleanupOnAllTerminalPaths() {
    player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
    var prompt =
        new ItemPrompt(
            "item",
            "cleanup",
            "Cleanup",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);

    // Success
    var s1 = new ItemPromptScreen(plugin, player, prompt);
    s1.open();
    assertTrue(s1.isListenerRegistered());
    s1.onInventoryClick(createTopClickEvent(s1, 27, ClickType.LEFT));
    assertFalse(s1.isListenerRegistered());

    // Cancel button
    var s2 = new ItemPromptScreen(plugin, player, prompt);
    s2.open();
    assertTrue(s2.isListenerRegistered());
    s2.onInventoryClick(createTopClickEvent(s2, 53, ClickType.LEFT));
    assertFalse(s2.isListenerRegistered());

    // Manual close
    var s3 = new ItemPromptScreen(plugin, player, prompt);
    s3.open();
    assertTrue(s3.isListenerRegistered());
    s3.onInventoryClose(new InventoryCloseEvent(player.getOpenInventory()));
    assertFalse(s3.isListenerRegistered());

    // Programmatic close
    var s4 = new ItemPromptScreen(plugin, player, prompt);
    s4.open();
    assertTrue(s4.isListenerRegistered());
    s4.close();
    assertFalse(s4.isListenerRegistered());

    // Player quit
    var s5 = new ItemPromptScreen(plugin, player, prompt);
    s5.open();
    assertTrue(s5.isListenerRegistered());
    s5.onPlayerQuit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.text("quit")));
    assertFalse(s5.isListenerRegistered());
  }

  @Test
  @DisplayName("Open failure triggers onOpenFailure callback and unregisters listener")
  void testOpenFailureCallbackAndCleanup() {
    AtomicReference<Throwable> failureRef = new AtomicReference<>();
    var prompt =
        new ItemPrompt(
            "item",
            "fail",
            "Fail test",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    screen.onOpenFailure(failureRef::set);

    assertNotNull(screen);
    assertFalse(screen.isOpen());
  }

  @Test
  @DisplayName(
      "Clicks on non-selectable slots (divider, info, filler) do not deliver answer or close")
  void testNonSelectableSlotClicksDoNothing() {
    var prompt =
        new ItemPrompt(
            "item",
            "non-sel",
            "Non selectable",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    // Divider (36)
    screen.onInventoryClick(createTopClickEvent(screen, 36, ClickType.LEFT));
    // Info (50)
    screen.onInventoryClick(createTopClickEvent(screen, 50, ClickType.LEFT));
    // Filler (51)
    screen.onInventoryClick(createTopClickEvent(screen, 51, ClickType.LEFT));

    assertNull(resultRef.get());
    assertTrue(screen.isOpen());
  }

  @Test
  @DisplayName("Screen plays sound on open when configured")
  void testSoundPlayedOnOpen() {
    var prompt =
        new ItemPrompt(
            "item",
            "sound",
            "Sound test",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            "minecraft:ui.button.click",
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    screen.open();
    assertTrue(screen.isOpen());
  }

  // =========================================================================
  // Empty Physical Slot Rejection & HAND Open Cancellation
  // =========================================================================

  @ParameterizedTest
  @EnumSource(ItemOutputFormat.class)
  @DisplayName(
      "Empty physical slots in INVENTORY mode are rejected with strikes and never produce AIR or slot tokens")
  void testEmptyPhysicalSlotRejectedAcrossAllOutputFormats(ItemOutputFormat format) {
    // Player inventory is completely empty
    var prompt =
        new ItemPrompt(
            "item",
            "empty-" + format,
            "Pick item",
            ItemSource.INVENTORY,
            format,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    // GUI slot 0 maps to player slot 9 (which is empty)
    var clickEmpty = createTopClickEvent(screen, 0, ClickType.LEFT);

    // Strike 1
    screen.onInventoryClick(clickEmpty);
    assertEquals(1, screen.getMismatchStrikes());
    assertTrue(screen.isOpen());
    assertNull(resultRef.get());

    // Strike 2
    screen.onInventoryClick(clickEmpty);
    assertEquals(2, screen.getMismatchStrikes());
    assertTrue(screen.isOpen());
    assertNull(resultRef.get());

    // Strike 3: cancels with MANUAL
    screen.onInventoryClick(clickEmpty);
    assertEquals(3, screen.getMismatchStrikes());
    assertFalse(screen.isOpen());
    assertNotNull(resultRef.get());
    assertTrue(resultRef.get().cancelled());
    assertEquals(CancelReason.MANUAL, resultRef.get().cancelReason());
  }

  @Test
  @DisplayName("Empty equipment slot in ARMOR mode is rejected with mismatch strikes")
  void testArmorModeEmptySlotRejected() {
    // Boots are equipped, but helmet (slot 49) is empty
    player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));

    var prompt =
        new ItemPrompt(
            "item",
            "armor-empty",
            "Pick armor",
            ItemSource.ARMOR,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);
    screen.open();

    // GUI slot 49 (helmet) is empty
    var clickHelmet = createTopClickEvent(screen, 49, ClickType.LEFT);

    // Strike 1
    screen.onInventoryClick(clickHelmet);
    assertEquals(1, screen.getMismatchStrikes());
    assertTrue(screen.isOpen());
    assertNull(resultRef.get());

    // Strike 2
    screen.onInventoryClick(clickHelmet);
    assertEquals(2, screen.getMismatchStrikes());
    assertTrue(screen.isOpen());
    assertNull(resultRef.get());

    // Strike 3
    screen.onInventoryClick(clickHelmet);
    assertEquals(3, screen.getMismatchStrikes());
    assertFalse(screen.isOpen());
    assertNotNull(resultRef.get());
    assertEquals(CancelReason.MANUAL, resultRef.get().cancelReason());
  }

  @ParameterizedTest
  @EnumSource(ItemOutputFormat.class)
  @DisplayName("Empty hand at open visibly cancels with MANUAL across all output formats")
  void testEmptyHandAtOpenVisiblyCancelsManualAcrossAllFormats(ItemOutputFormat format) {
    // Player hand is empty (AIR)
    player.getInventory().setItemInMainHand(ItemStack.empty());

    var prompt =
        new ItemPrompt(
            "item",
            "hand-empty-" + format,
            "Pick hand",
            ItemSource.HAND,
            format,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    attachCallback(screen);

    screen.open();

    assertFalse(screen.isOpen());
    assertFalse(screen.isListenerRegistered());
    assertNotNull(resultRef.get());
    assertTrue(resultRef.get().cancelled());
    assertEquals(CancelReason.MANUAL, resultRef.get().cancelReason());
    assertEquals(1, resultCount.get());
  }

  // =========================================================================
  // Stale Replacement Screen Protection & Open Failure Reporting
  // =========================================================================

  @Test
  @DisplayName("Stale screen close does not close a replacement screen opened on the player")
  void testStaleReplacementInventoryNotClosed() {
    player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
    var prompt1 =
        new ItemPrompt(
            "item",
            "screen1",
            "Screen 1",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen1 = new ItemPromptScreen(plugin, player, prompt1);
    screen1.open();
    assertTrue(screen1.isOpen());

    // A new replacement screen (e.g. screen 2) is opened on the player
    var prompt2 =
        new ItemPrompt(
            "item",
            "screen2",
            "Screen 2",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen2 = new ItemPromptScreen(plugin, player, prompt2);
    screen2.open();
    assertTrue(screen2.isOpen());

    // Current top inventory belongs to screen2
    var currentTop = player.getOpenInventory().getTopInventory();
    assertNotNull(currentTop);
    assertEquals(screen2.getInventory(), currentTop);

    // Closing screen1 programmatically must NOT close screen2's inventory
    screen1.close();
    assertFalse(screen1.isOpen());
    assertEquals(screen2.getInventory(), player.getOpenInventory().getTopInventory());

    // Delivering result on screen1 must also NOT close screen2's inventory
    screen2.close();
    assertFalse(screen2.isOpen());
  }

  @Test
  @DisplayName("Synchronous open failure cleans up listener and reports once via onOpenFailure")
  void testOpenFailureSyncReportsOnceAndCleansUp() {
    AtomicReference<Throwable> failureRef = new AtomicReference<>();
    AtomicInteger failCount = new AtomicInteger();

    var throwingPlayer =
        new dev.cyr1en.promptpaper.testutil.TestPlayerMock(server, "ThrowingUser") {
          @Override
          public org.bukkit.inventory.InventoryView openInventory(
              org.bukkit.inventory.Inventory inventory) {
            throw new RuntimeException("Simulated open failure");
          }
        };
    server.addPlayer(throwingPlayer);

    var prompt =
        new ItemPrompt(
            "item",
            "fail-test",
            "Fail",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, throwingPlayer, prompt);
    screen.onOpenFailure(
        t -> {
          failureRef.set(t);
          failCount.incrementAndGet();
        });

    screen.open();

    assertFalse(screen.isOpen());
    assertFalse(screen.isListenerRegistered());
    assertEquals(1, failCount.get());
    assertNotNull(failureRef.get());
    assertEquals("Simulated open failure", failureRef.get().getMessage());
  }

  @Test
  @DisplayName("Clicks on matching inventory remain cancelled even after screen is closed")
  void testClickWindowHardenedAfterClose() {
    player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
    var prompt =
        new ItemPrompt(
            "item",
            "window",
            "Click window",
            ItemSource.INVENTORY,
            ItemOutputFormat.KEY,
            null,
            null,
            false);
    var screen = new ItemPromptScreen(plugin, player, prompt);
    screen.open();
    assertTrue(screen.isOpen());

    // Close screen
    screen.close();
    assertFalse(screen.isOpen());

    // A click event arriving with the screen's inventory view must still be cancelled and denied
    var event = createTopClickEvent(screen, 27, ClickType.LEFT);
    screen.onInventoryClick(event);

    assertTrue(event.isCancelled());
    assertEquals(Event.Result.DENY, event.getResult());
  }
}
