package dev.cyr1en.promptpaper.item.snapshot;

import java.util.Map;
import java.util.OptionalInt;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Pure and fail-closed verifier for player item selections against captured {@link ItemSnapshot} or
 * {@link ItemFingerprint} data.
 *
 * <p>Designed to be safely invoked from an entity-scheduler-owned screen.
 */
public final class ItemSelectionVerifier {

  private ItemSelectionVerifier() {}

  /**
   * Verifies the item currently in a player's inventory at the logical slot mapped from the
   * provided GUI slot.
   *
   * @param inventory The player inventory (must not be null)
   * @param guiSlot The GUI container slot index clicked
   * @param expectedSnapshot The expected snapshot captured when the screen opened
   * @return {@link VerificationResult} describing success or explicit failure reason
   */
  public static VerificationResult verifyGuiSlot(
      PlayerInventory inventory, int guiSlot, ItemSnapshot expectedSnapshot) {
    if (inventory == null) {
      return VerificationResult.failure(
          VerificationFailureReason.NULL_INVENTORY, "Inventory cannot be null");
    }
    if (expectedSnapshot == null) {
      return VerificationResult.failure(
          VerificationFailureReason.NULL_SNAPSHOT, "Expected snapshot cannot be null");
    }

    OptionalInt playerSlotOpt = ItemSlotMapping.toPlayerSlot(guiSlot);
    if (playerSlotOpt.isEmpty()) {
      return VerificationResult.failure(
          VerificationFailureReason.SLOT_UNMAPPED,
          "GUI slot " + guiSlot + " does not map to any valid player inventory slot");
    }

    int playerSlot = playerSlotOpt.getAsInt();
    if (expectedSnapshot.playerSlot() != playerSlot) {
      return VerificationResult.failure(
          VerificationFailureReason.SLOT_OUT_OF_BOUNDS,
          "GUI slot "
              + guiSlot
              + " maps to player slot "
              + playerSlot
              + " but expected snapshot is for slot "
              + expectedSnapshot.playerSlot());
    }

    return verifyPlayerSlot(inventory, playerSlot, expectedSnapshot);
  }

  /**
   * Verifies the item currently in a player's inventory at the specified logical player slot.
   *
   * @param inventory The player inventory (must not be null)
   * @param playerSlot The logical player slot index (0..40)
   * @param expectedSnapshot The expected snapshot captured when the screen opened
   * @return {@link VerificationResult} describing success or explicit failure reason
   */
  public static VerificationResult verifyPlayerSlot(
      PlayerInventory inventory, int playerSlot, ItemSnapshot expectedSnapshot) {
    if (inventory == null) {
      return VerificationResult.failure(
          VerificationFailureReason.NULL_INVENTORY, "Inventory cannot be null");
    }
    if (expectedSnapshot == null) {
      return VerificationResult.failure(
          VerificationFailureReason.NULL_SNAPSHOT, "Expected snapshot cannot be null");
    }
    if (!ItemSlotMapping.isValidPlayerSlot(playerSlot)) {
      return VerificationResult.failure(
          VerificationFailureReason.SLOT_OUT_OF_BOUNDS,
          "Player slot index "
              + playerSlot
              + " is out of bounds (0.."
              + (ItemSlotMapping.TOTAL_PLAYER_SLOTS - 1)
              + ")");
    }
    if (expectedSnapshot.playerSlot() != playerSlot) {
      return VerificationResult.failure(
          VerificationFailureReason.SLOT_OUT_OF_BOUNDS,
          "Target player slot "
              + playerSlot
              + " does not match expected snapshot slot "
              + expectedSnapshot.playerSlot());
    }

    ItemStack currentItem;
    try {
      currentItem = PlayerInventoryAccessor.getItem(inventory, playerSlot);
    } catch (Exception ex) {
      return VerificationResult.failure(
          VerificationFailureReason.SERIALIZATION_ERROR,
          "Failed to retrieve item from player inventory slot "
              + playerSlot
              + ": "
              + ex.getMessage(),
          ex);
    }

    return verifyItem(currentItem, expectedSnapshot.fingerprint());
  }

  /**
   * Verifies an {@link ItemStack} against an expected {@link ItemFingerprint}.
   *
   * @param currentItem The item currently present (defensive copy is made)
   * @param expectedFingerprint The expected fingerprint
   * @return {@link VerificationResult} describing success or explicit failure reason
   */
  public static VerificationResult verifyItem(
      ItemStack currentItem, ItemFingerprint expectedFingerprint) {
    if (expectedFingerprint == null) {
      return VerificationResult.failure(
          VerificationFailureReason.NULL_SNAPSHOT, "Expected fingerprint cannot be null");
    }

    ItemStack safeCurrent = PlayerInventoryAccessor.cloneOrEmpty(currentItem);

    boolean currentEmpty = safeCurrent.getType().isAir() || safeCurrent.getAmount() <= 0;
    boolean expectedEmpty = expectedFingerprint.isEmpty();

    if (currentEmpty && expectedEmpty) {
      return VerificationResult.success(safeCurrent, ItemFingerprint.EMPTY);
    }

    if (currentEmpty) {
      return VerificationResult.failure(
          VerificationFailureReason.EMPTY_SLOT_EXPECTED_ITEM,
          "Slot is empty but expected item "
              + expectedFingerprint.material()
              + " x"
              + expectedFingerprint.amount(),
          ItemFingerprint.EMPTY);
    }

    if (expectedEmpty) {
      ItemFingerprint actualFp = tryEncode(safeCurrent);
      return VerificationResult.failure(
          VerificationFailureReason.ITEM_EXPECTED_EMPTY_SLOT,
          "Slot contains item "
              + safeCurrent.getType()
              + " x"
              + safeCurrent.getAmount()
              + " but expected empty slot",
          actualFp);
    }

    if (safeCurrent.getType() != expectedFingerprint.material()) {
      ItemFingerprint actualFp = tryEncode(safeCurrent);
      return VerificationResult.failure(
          VerificationFailureReason.MATERIAL_MISMATCH,
          "Item material changed: expected "
              + expectedFingerprint.material()
              + " but found "
              + safeCurrent.getType(),
          actualFp);
    }

    if (safeCurrent.getAmount() != expectedFingerprint.amount()) {
      ItemFingerprint actualFp = tryEncode(safeCurrent);
      return VerificationResult.failure(
          VerificationFailureReason.AMOUNT_MISMATCH,
          "Item amount changed: expected "
              + expectedFingerprint.amount()
              + " but found "
              + safeCurrent.getAmount(),
          actualFp);
    }

    ItemFingerprint actualFp;
    try {
      actualFp = ItemFingerprintEncoder.encode(safeCurrent);
    } catch (OversizedItemException ex) {
      return VerificationResult.failure(
          VerificationFailureReason.ITEM_OVERSIZED, ex.getMessage(), ex);
    } catch (ItemSerializationException ex) {
      return VerificationResult.failure(
          VerificationFailureReason.SERIALIZATION_ERROR, ex.getMessage(), ex);
    } catch (Throwable t) {
      return VerificationResult.failure(
          VerificationFailureReason.SERIALIZATION_ERROR,
          "Unexpected error during item fingerprinting: " + t.getMessage(),
          t);
    }

    if (!actualFp.sha256().equalsIgnoreCase(expectedFingerprint.sha256())) {
      return VerificationResult.failure(
          VerificationFailureReason.METADATA_MISMATCH,
          "Item metadata hash mismatch: expected "
              + expectedFingerprint.sha256()
              + " but found "
              + actualFp.sha256(),
          actualFp);
    }

    return VerificationResult.success(safeCurrent, actualFp);
  }

  /**
   * Verifies the entire inventory against a set of expected snapshots and ensures the 2 MiB
   * aggregate limit is respected.
   *
   * @param inventory The player inventory (must not be null)
   * @param expectedSnapshots Map of player slot index to expected ItemSnapshot
   * @return {@link VerificationResult} describing success or first failure encountered
   */
  public static VerificationResult verifyInventory(
      PlayerInventory inventory, Map<Integer, ItemSnapshot> expectedSnapshots) {
    if (inventory == null) {
      return VerificationResult.failure(
          VerificationFailureReason.NULL_INVENTORY, "Inventory cannot be null");
    }
    if (expectedSnapshots == null) {
      return VerificationResult.failure(
          VerificationFailureReason.NULL_SNAPSHOT, "Expected snapshots map cannot be null");
    }

    ScreenAggregateAccounting accounting = new ScreenAggregateAccounting();

    for (Map.Entry<Integer, ItemSnapshot> entry : expectedSnapshots.entrySet()) {
      int slot = entry.getKey();
      ItemSnapshot expected = entry.getValue();

      VerificationResult result = verifyPlayerSlot(inventory, slot, expected);
      if (result.isFailure()) {
        return result;
      }

      if (result.getFingerprint().isPresent()) {
        try {
          accounting.add(result.getFingerprint().get());
        } catch (OversizedAggregateException ex) {
          return VerificationResult.failure(
              VerificationFailureReason.AGGREGATE_OVERSIZED, ex.getMessage(), ex);
        }
      }
    }

    return VerificationResult.success(ItemStack.empty(), ItemFingerprint.EMPTY);
  }

  private static ItemFingerprint tryEncode(ItemStack item) {
    try {
      return ItemFingerprintEncoder.encode(item);
    } catch (Throwable ignored) {
      return ItemFingerprint.EMPTY;
    }
  }
}
