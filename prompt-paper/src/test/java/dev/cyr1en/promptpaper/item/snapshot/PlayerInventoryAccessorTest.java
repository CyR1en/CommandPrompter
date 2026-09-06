package dev.cyr1en.promptpaper.item.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PlayerInventoryAccessor Tests")
class PlayerInventoryAccessorTest extends MockBukkitTest {

  private Player player;
  private PlayerInventory inventory;

  @BeforeEach
  void setupPlayer() {
    player = server.addPlayer();
    inventory = player.getInventory();
  }

  @Test
  @DisplayName("Explicit armor accessors are used for slots 36..39")
  void testArmorAccessors() {
    ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS, 1);
    ItemStack leggings = new ItemStack(Material.DIAMOND_LEGGINGS, 1);
    ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE, 1);
    ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET, 1);

    PlayerInventoryAccessor.setItem(inventory, ItemSlotMapping.PLAYER_BOOTS, boots);
    PlayerInventoryAccessor.setItem(inventory, ItemSlotMapping.PLAYER_LEGGINGS, leggings);
    PlayerInventoryAccessor.setItem(inventory, ItemSlotMapping.PLAYER_CHESTPLATE, chestplate);
    PlayerInventoryAccessor.setItem(inventory, ItemSlotMapping.PLAYER_HELMET, helmet);

    assertEquals(Material.DIAMOND_BOOTS, inventory.getBoots().getType());
    assertEquals(Material.DIAMOND_LEGGINGS, inventory.getLeggings().getType());
    assertEquals(Material.DIAMOND_CHESTPLATE, inventory.getChestplate().getType());
    assertEquals(Material.DIAMOND_HELMET, inventory.getHelmet().getType());

    assertEquals(Material.DIAMOND_BOOTS, PlayerInventoryAccessor.getItem(inventory, 36).getType());
    assertEquals(
        Material.DIAMOND_LEGGINGS, PlayerInventoryAccessor.getItem(inventory, 37).getType());
    assertEquals(
        Material.DIAMOND_CHESTPLATE, PlayerInventoryAccessor.getItem(inventory, 38).getType());
    assertEquals(Material.DIAMOND_HELMET, PlayerInventoryAccessor.getItem(inventory, 39).getType());
  }

  @Test
  @DisplayName("Explicit offhand accessor is used for slot 40")
  void testOffhandAccessor() {
    ItemStack shield = new ItemStack(Material.SHIELD, 1);
    PlayerInventoryAccessor.setItem(inventory, ItemSlotMapping.PLAYER_OFFHAND, shield);

    assertEquals(Material.SHIELD, inventory.getItemInOffHand().getType());
    assertEquals(Material.SHIELD, PlayerInventoryAccessor.getItem(inventory, 40).getType());
  }

  @Test
  @DisplayName("Main storage and hotbar slots accessors work correctly")
  void testMainStorageAndHotbarAccessors() {
    ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
    ItemStack bread = new ItemStack(Material.BREAD, 64);

    PlayerInventoryAccessor.setItem(inventory, 0, sword);
    PlayerInventoryAccessor.setItem(inventory, 15, bread);

    assertEquals(Material.DIAMOND_SWORD, PlayerInventoryAccessor.getItem(inventory, 0).getType());
    assertEquals(Material.BREAD, PlayerInventoryAccessor.getItem(inventory, 15).getType());
    assertEquals(64, PlayerInventoryAccessor.getItem(inventory, 15).getAmount());
  }

  @Test
  @DisplayName("GUI slot accessors route to correct player slots")
  void testGuiSlotRouting() {
    ItemStack torch = new ItemStack(Material.TORCH, 32);
    // GUI 27 maps to player slot 0 (first hotbar slot)
    PlayerInventoryAccessor.setItemByGuiSlot(inventory, 27, torch);
    assertEquals(Material.TORCH, PlayerInventoryAccessor.getItem(inventory, 0).getType());
    assertEquals(Material.TORCH, PlayerInventoryAccessor.getItemByGuiSlot(inventory, 27).getType());

    // GUI 0 maps to player slot 9
    ItemStack iron = new ItemStack(Material.IRON_INGOT, 16);
    PlayerInventoryAccessor.setItemByGuiSlot(inventory, 0, iron);
    assertEquals(Material.IRON_INGOT, PlayerInventoryAccessor.getItem(inventory, 9).getType());
    assertEquals(
        Material.IRON_INGOT, PlayerInventoryAccessor.getItemByGuiSlot(inventory, 0).getType());

    // GUI 45 maps to offhand (40)
    ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING, 1);
    PlayerInventoryAccessor.setItemByGuiSlot(inventory, 45, totem);
    assertEquals(Material.TOTEM_OF_UNDYING, inventory.getItemInOffHand().getType());

    // GUI 49 maps to helmet (39)
    ItemStack cap = new ItemStack(Material.LEATHER_HELMET, 1);
    PlayerInventoryAccessor.setItemByGuiSlot(inventory, 49, cap);
    assertEquals(Material.LEATHER_HELMET, inventory.getHelmet().getType());
  }

  @Test
  @DisplayName("Defensive copying prevents external mutation of inventory")
  void testDefensiveCopies() {
    ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
    PlayerInventoryAccessor.setItem(inventory, 0, sword);

    // Mutating sword outside after set should not change inventory
    sword.setAmount(10);
    assertEquals(1, PlayerInventoryAccessor.getItem(inventory, 0).getAmount());

    // Mutating returned ItemStack should not change inventory
    ItemStack readSword = PlayerInventoryAccessor.getItem(inventory, 0);
    readSword.setAmount(5);
    assertEquals(1, PlayerInventoryAccessor.getItem(inventory, 0).getAmount());
  }

  @Test
  @DisplayName("captureAll captures defensive copies of all 41 slots")
  void testCaptureAll() {
    PlayerInventoryAccessor.setItem(inventory, 0, new ItemStack(Material.APPLE, 5));
    PlayerInventoryAccessor.setItem(inventory, 40, new ItemStack(Material.SHIELD, 1));

    Map<Integer, ItemStack> all = PlayerInventoryAccessor.captureAll(inventory);
    assertEquals(41, all.size());
    assertEquals(Material.APPLE, all.get(0).getType());
    assertEquals(5, all.get(0).getAmount());
    assertEquals(Material.SHIELD, all.get(40).getType());

    // Mutating captured map item should not mutate inventory
    all.get(0).setAmount(99);
    assertEquals(5, PlayerInventoryAccessor.getItem(inventory, 0).getAmount());
  }

  @Test
  @DisplayName("Empty slots return ItemStack.empty() without error")
  void testEmptySlots() {
    ItemStack item = PlayerInventoryAccessor.getItem(inventory, 5);
    assertNotNull(item);
    assertTrue(item.getType().isAir() || item.isEmpty());
  }

  @Test
  @DisplayName("Invalid slots throw IllegalArgumentException")
  void testInvalidSlots() {
    assertThrows(
        IllegalArgumentException.class, () -> PlayerInventoryAccessor.getItem(inventory, -1));
    assertThrows(
        IllegalArgumentException.class, () -> PlayerInventoryAccessor.getItem(inventory, 41));
    assertThrows(
        IllegalArgumentException.class,
        () -> PlayerInventoryAccessor.getItemByGuiSlot(inventory, 36));
    assertThrows(IllegalArgumentException.class, () -> PlayerInventoryAccessor.getItem(null, 0));
  }
}
