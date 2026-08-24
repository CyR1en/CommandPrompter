package dev.cyr1en.promptpaper.item.snapshot;

import dev.cyr1en.promptpaper.MockBukkitTest;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ItemSnapshot Tests")
class ItemSnapshotTest extends MockBukkitTest {

    private Player player;
    private PlayerInventory inventory;

    @BeforeEach
    void setUpPlayer() {
        player = server.addPlayer();
        inventory = player.getInventory();
    }

    @Test
    @DisplayName("Capture slot produces accurate snapshot and fingerprint")
    void testCaptureSlot() {
        ItemStack item = new ItemStack(Material.GOLDEN_APPLE, 5);
        PlayerInventoryAccessor.setItem(inventory, 0, item);

        ItemSnapshot snapshot = ItemSnapshot.capture(inventory, 0);
        assertEquals(0, snapshot.playerSlot());
        assertEquals(OptionalInt.of(27), snapshot.guiSlot());
        assertEquals(Material.GOLDEN_APPLE, snapshot.item().getType());
        assertEquals(5, snapshot.item().getAmount());
        assertEquals(Material.GOLDEN_APPLE, snapshot.fingerprint().material());
        assertEquals(5, snapshot.fingerprint().amount());
        assertFalse(snapshot.isEmpty());
    }

    @Test
    @DisplayName("Capture GUI slot produces accurate snapshot mapping")
    void testCaptureGuiSlot() {
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET, 1);
        PlayerInventoryAccessor.setItem(inventory, ItemSlotMapping.PLAYER_HELMET, helmet);

        // Helmet GUI slot is 49
        ItemSnapshot snapshot = ItemSnapshot.captureGuiSlot(inventory, 49);
        assertEquals(39, snapshot.playerSlot());
        assertEquals(OptionalInt.of(49), snapshot.guiSlot());
        assertEquals(Material.NETHERITE_HELMET, snapshot.item().getType());
    }

    @Test
    @DisplayName("ItemSnapshot enforces defensive copying on item getter")
    void testDefensiveCopyOnGetter() {
        ItemStack sword = new ItemStack(Material.IRON_SWORD, 1);
        ItemSnapshot snapshot = ItemSnapshot.of(0, sword, ItemFingerprintEncoder.encode(sword));

        ItemStack item1 = snapshot.item();
        item1.setAmount(10);

        ItemStack item2 = snapshot.item();
        assertEquals(1, item2.getAmount(), "Mutating retrieved item should not mutate snapshot");
    }

    @Test
    @DisplayName("captureInventory captures all 41 slots")
    void testCaptureInventory() {
        PlayerInventoryAccessor.setItem(inventory, 0, new ItemStack(Material.DIAMOND, 10));
        PlayerInventoryAccessor.setItem(inventory, 40, new ItemStack(Material.SHIELD, 1));

        Map<Integer, ItemSnapshot> all = ItemSnapshot.captureInventory(inventory);
        assertEquals(41, all.size());
        assertEquals(Material.DIAMOND, all.get(0).item().getType());
        assertEquals(Material.SHIELD, all.get(40).item().getType());
        assertTrue(all.get(5).isEmpty());
    }

    @Test
    @DisplayName("Empty snapshot behaves correctly")
    void testEmptySnapshot() {
        ItemSnapshot empty = ItemSnapshot.empty(0);
        assertTrue(empty.isEmpty());
        assertEquals(ItemFingerprint.EMPTY, empty.fingerprint());
    }
}
