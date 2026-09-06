package dev.cyr1en.promptpaper.item.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ItemSelectionVerifier Tests")
class ItemSelectionVerifierTest extends MockBukkitTest {

  private Player player;
  private PlayerInventory inventory;

  @BeforeEach
  void setUpPlayer() {
    player = server.addPlayer();
    inventory = player.getInventory();
  }

  @Test
  @DisplayName("Successful verification on GUI slot returns verified item and success result")
  void testSuccessfulGuiSlotVerification() {
    ItemStack bow = new ItemStack(Material.BOW, 1);
    // GUI 27 -> player slot 0
    PlayerInventoryAccessor.setItem(inventory, 0, bow);
    ItemSnapshot snapshot = ItemSnapshot.captureGuiSlot(inventory, 27);

    VerificationResult result = ItemSelectionVerifier.verifyGuiSlot(inventory, 27, snapshot);
    assertTrue(result.isSuccess());
    assertFalse(result.isFailure());
    assertTrue(result.getItem().isPresent());
    assertEquals(Material.BOW, result.getItem().get().getType());
    assertEquals(Material.BOW, result.getItemOrThrow().getType());
    assertTrue(result.getFailureReason().isEmpty());
    assertTrue(result.getErrorMessage().isEmpty());
  }

  @Test
  @DisplayName("Successful verification on player slot returns success result")
  void testSuccessfulPlayerSlotVerification() {
    ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET, 1);
    PlayerInventoryAccessor.setItem(inventory, ItemSlotMapping.PLAYER_HELMET, helmet);
    ItemSnapshot snapshot = ItemSnapshot.capture(inventory, ItemSlotMapping.PLAYER_HELMET);

    VerificationResult result =
        ItemSelectionVerifier.verifyPlayerSlot(inventory, ItemSlotMapping.PLAYER_HELMET, snapshot);
    assertTrue(result.isSuccess());
    assertEquals(Material.DIAMOND_HELMET, result.getItem().get().getType());
  }

  @Test
  @DisplayName("Material mismatch fails verification with MATERIAL_MISMATCH reason")
  void testMaterialMismatch() {
    ItemStack diamond = new ItemStack(Material.DIAMOND, 1);
    PlayerInventoryAccessor.setItem(inventory, 0, diamond);
    ItemSnapshot snapshot = ItemSnapshot.capture(inventory, 0);

    // Player switches diamond for emerald
    PlayerInventoryAccessor.setItem(inventory, 0, new ItemStack(Material.EMERALD, 1));

    VerificationResult result = ItemSelectionVerifier.verifyPlayerSlot(inventory, 0, snapshot);
    assertFalse(result.isSuccess());
    assertTrue(result.isFailure());
    assertEquals(VerificationFailureReason.MATERIAL_MISMATCH, result.getFailureReason().get());
    assertTrue(result.getErrorMessage().get().contains("Item material changed"));
    assertThrows(IllegalStateException.class, result::getItemOrThrow);
  }

  @Test
  @DisplayName("Amount mismatch fails verification with AMOUNT_MISMATCH reason")
  void testAmountMismatch() {
    ItemStack stack = new ItemStack(Material.GOLD_INGOT, 10);
    PlayerInventoryAccessor.setItem(inventory, 0, stack);
    ItemSnapshot snapshot = ItemSnapshot.capture(inventory, 0);

    // Amount drops to 9
    PlayerInventoryAccessor.setItem(inventory, 0, new ItemStack(Material.GOLD_INGOT, 9));

    VerificationResult result = ItemSelectionVerifier.verifyPlayerSlot(inventory, 0, snapshot);
    assertFalse(result.isSuccess());
    assertEquals(VerificationFailureReason.AMOUNT_MISMATCH, result.getFailureReason().get());
  }

  @Test
  @DisplayName("Metadata / enchantment / name mismatch fails verification with METADATA_MISMATCH")
  void testMetadataMismatch() {
    ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
    PlayerInventoryAccessor.setItem(inventory, 0, sword);
    ItemSnapshot snapshot = ItemSnapshot.capture(inventory, 0);

    // Item gets renamed or enchanted after screen opened
    ItemStack enchantedSword = sword.clone();
    ItemMeta meta = enchantedSword.getItemMeta();
    meta.addEnchant(Enchantment.SHARPNESS, 5, true);
    enchantedSword.setItemMeta(meta);
    PlayerInventoryAccessor.setItem(inventory, 0, enchantedSword);

    VerificationResult result = ItemSelectionVerifier.verifyPlayerSlot(inventory, 0, snapshot);
    assertFalse(result.isSuccess());
    assertEquals(VerificationFailureReason.METADATA_MISMATCH, result.getFailureReason().get());
  }

  @Test
  @DisplayName("Empty slot when item was expected fails with EMPTY_SLOT_EXPECTED_ITEM")
  void testEmptySlotExpectedItem() {
    ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
    PlayerInventoryAccessor.setItem(inventory, 0, sword);
    ItemSnapshot snapshot = ItemSnapshot.capture(inventory, 0);

    // Player drops or moves item, slot is now empty
    PlayerInventoryAccessor.setItem(inventory, 0, ItemStack.empty());

    VerificationResult result = ItemSelectionVerifier.verifyPlayerSlot(inventory, 0, snapshot);
    assertFalse(result.isSuccess());
    assertEquals(
        VerificationFailureReason.EMPTY_SLOT_EXPECTED_ITEM, result.getFailureReason().get());
  }

  @Test
  @DisplayName("Item present when empty slot was expected fails with ITEM_EXPECTED_EMPTY_SLOT")
  void testItemExpectedEmptySlot() {
    ItemSnapshot emptySnapshot = ItemSnapshot.empty(0);

    // Player picks up item into empty slot
    PlayerInventoryAccessor.setItem(inventory, 0, new ItemStack(Material.DIRT, 1));

    VerificationResult result = ItemSelectionVerifier.verifyPlayerSlot(inventory, 0, emptySnapshot);
    assertFalse(result.isSuccess());
    assertEquals(
        VerificationFailureReason.ITEM_EXPECTED_EMPTY_SLOT, result.getFailureReason().get());
  }

  @Test
  @DisplayName("Unmapped GUI slot returns SLOT_UNMAPPED failure")
  void testUnmappedGuiSlot() {
    ItemSnapshot snapshot = ItemSnapshot.empty(0);
    VerificationResult result = ItemSelectionVerifier.verifyGuiSlot(inventory, 36, snapshot);
    assertFalse(result.isSuccess());
    assertEquals(VerificationFailureReason.SLOT_UNMAPPED, result.getFailureReason().get());
  }

  @Test
  @DisplayName("Out of bounds player slot returns SLOT_OUT_OF_BOUNDS failure")
  void testOutOfBoundsPlayerSlot() {
    ItemSnapshot snapshot = ItemSnapshot.empty(0);
    VerificationResult result = ItemSelectionVerifier.verifyPlayerSlot(inventory, 99, snapshot);
    assertFalse(result.isSuccess());
    assertEquals(VerificationFailureReason.SLOT_OUT_OF_BOUNDS, result.getFailureReason().get());
  }

  @Test
  @DisplayName("Null inventory or null snapshot returns explicit fail-closed failure")
  void testNullInputs() {
    ItemSnapshot snapshot = ItemSnapshot.empty(0);
    VerificationResult r1 = ItemSelectionVerifier.verifyPlayerSlot(null, 0, snapshot);
    assertEquals(VerificationFailureReason.NULL_INVENTORY, r1.getFailureReason().get());

    VerificationResult r2 = ItemSelectionVerifier.verifyPlayerSlot(inventory, 0, null);
    assertEquals(VerificationFailureReason.NULL_SNAPSHOT, r2.getFailureReason().get());

    VerificationResult r3 = ItemSelectionVerifier.verifyItem(new ItemStack(Material.STONE), null);
    assertEquals(VerificationFailureReason.NULL_SNAPSHOT, r3.getFailureReason().get());
  }

  @Test
  @DisplayName("verifyInventory verifies entire inventory snapshots")
  void testVerifyInventory() {
    PlayerInventoryAccessor.setItem(inventory, 0, new ItemStack(Material.DIAMOND, 5));
    PlayerInventoryAccessor.setItem(inventory, 1, new ItemStack(Material.EMERALD, 10));

    Map<Integer, ItemSnapshot> snapshots = new HashMap<>();
    snapshots.put(0, ItemSnapshot.capture(inventory, 0));
    snapshots.put(1, ItemSnapshot.capture(inventory, 1));

    VerificationResult result = ItemSelectionVerifier.verifyInventory(inventory, snapshots);
    assertTrue(result.isSuccess());

    // Mutate one item
    PlayerInventoryAccessor.setItem(inventory, 1, new ItemStack(Material.EMERALD, 9));
    VerificationResult failedResult = ItemSelectionVerifier.verifyInventory(inventory, snapshots);
    assertFalse(failedResult.isSuccess());
    assertEquals(VerificationFailureReason.AMOUNT_MISMATCH, failedResult.getFailureReason().get());
  }

  @Test
  @DisplayName("VerificationResult failure factory with cause populates fields correctly")
  void testVerificationResultFailureWithCause() {
    RuntimeException cause = new RuntimeException("Underlying issue");
    VerificationResult result =
        VerificationResult.failure(
            VerificationFailureReason.SERIALIZATION_ERROR, "Failed to serialize", cause);
    assertFalse(result.isSuccess());
    assertTrue(result.isFailure());
    assertEquals(VerificationFailureReason.SERIALIZATION_ERROR, result.getFailureReason().get());
    assertEquals("Failed to serialize", result.getErrorMessage().get());
    assertEquals(cause, result.getCause().get());
    assertTrue(result.getItem().isEmpty());
  }

  @Test
  @DisplayName("VerificationResult toString formatting check")
  void testVerificationResultToString() {
    VerificationResult success =
        VerificationResult.success(new ItemStack(Material.DIAMOND), ItemFingerprint.EMPTY);
    assertTrue(success.toString().contains("SUCCESS"));

    VerificationResult failure =
        VerificationResult.failure(VerificationFailureReason.ITEM_OVERSIZED, "Too big");
    assertTrue(failure.toString().contains("FAILURE"));
    assertTrue(failure.toString().contains("ITEM_OVERSIZED"));
  }
}
