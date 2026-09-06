package dev.cyr1en.promptpaper.item.snapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Immutable snapshot of an item at a specific inventory slot, captured at a point in time. Enforces
 * defensive copying of the contained {@link ItemStack}.
 */
public final class ItemSnapshot {

  private final int playerSlot;
  private final OptionalInt guiSlot;
  private final ItemStack item;
  private final ItemFingerprint fingerprint;
  private final long timestampMillis;

  public ItemSnapshot(
      int playerSlot,
      OptionalInt guiSlot,
      ItemStack item,
      ItemFingerprint fingerprint,
      long timestampMillis) {
    if (!ItemSlotMapping.isValidPlayerSlot(playerSlot)) {
      throw new IllegalArgumentException("Invalid player slot: " + playerSlot);
    }
    this.playerSlot = playerSlot;
    this.guiSlot = Objects.requireNonNull(guiSlot, "guiSlot cannot be null");
    this.item = PlayerInventoryAccessor.cloneOrEmpty(item);
    this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
    this.timestampMillis = timestampMillis;
  }

  public static ItemSnapshot of(int playerSlot, ItemStack item, ItemFingerprint fingerprint) {
    OptionalInt guiSlot = ItemSlotMapping.toGuiSlot(playerSlot);
    return new ItemSnapshot(playerSlot, guiSlot, item, fingerprint, System.currentTimeMillis());
  }

  public static ItemSnapshot of(
      int playerSlot, OptionalInt guiSlot, ItemStack item, ItemFingerprint fingerprint) {
    return new ItemSnapshot(playerSlot, guiSlot, item, fingerprint, System.currentTimeMillis());
  }

  public static ItemSnapshot empty(int playerSlot) {
    OptionalInt guiSlot = ItemSlotMapping.toGuiSlot(playerSlot);
    return new ItemSnapshot(
        playerSlot, guiSlot, ItemStack.empty(), ItemFingerprint.EMPTY, System.currentTimeMillis());
  }

  /**
   * Captures an {@link ItemSnapshot} from the specified player slot in a {@link PlayerInventory}.
   */
  public static ItemSnapshot capture(PlayerInventory inventory, int playerSlot) {
    Objects.requireNonNull(inventory, "inventory cannot be null");
    ItemStack item = PlayerInventoryAccessor.getItem(inventory, playerSlot);
    ItemFingerprint fingerprint = ItemFingerprintEncoder.encode(item);
    OptionalInt guiSlot = ItemSlotMapping.toGuiSlot(playerSlot);
    return new ItemSnapshot(playerSlot, guiSlot, item, fingerprint, System.currentTimeMillis());
  }

  /** Captures an {@link ItemSnapshot} from the specified GUI slot in a {@link PlayerInventory}. */
  public static ItemSnapshot captureGuiSlot(PlayerInventory inventory, int guiSlot) {
    Objects.requireNonNull(inventory, "inventory cannot be null");
    OptionalInt playerSlot = ItemSlotMapping.toPlayerSlot(guiSlot);
    if (playerSlot.isEmpty()) {
      throw new IllegalArgumentException("Unmapped GUI slot: " + guiSlot);
    }
    ItemStack item = PlayerInventoryAccessor.getItem(inventory, playerSlot.getAsInt());
    ItemFingerprint fingerprint = ItemFingerprintEncoder.encode(item);
    return new ItemSnapshot(
        playerSlot.getAsInt(),
        OptionalInt.of(guiSlot),
        item,
        fingerprint,
        System.currentTimeMillis());
  }

  /**
   * Captures snapshots of all 41 slots in the player inventory and validates the 2 MiB screen
   * aggregate limit.
   */
  public static Map<Integer, ItemSnapshot> captureInventory(PlayerInventory inventory) {
    Objects.requireNonNull(inventory, "inventory cannot be null");
    Map<Integer, ItemSnapshot> snapshots = new LinkedHashMap<>(ItemSlotMapping.TOTAL_PLAYER_SLOTS);
    ScreenAggregateAccounting accounting = new ScreenAggregateAccounting();

    for (int slot = 0; slot < ItemSlotMapping.TOTAL_PLAYER_SLOTS; slot++) {
      ItemSnapshot snapshot = capture(inventory, slot);
      accounting.add(snapshot.fingerprint());
      snapshots.put(slot, snapshot);
    }

    return Collections.unmodifiableMap(snapshots);
  }

  public int playerSlot() {
    return playerSlot;
  }

  public OptionalInt guiSlot() {
    return guiSlot;
  }

  /** Returns a defensive copy of the snapshot's item. */
  public ItemStack item() {
    return PlayerInventoryAccessor.cloneOrEmpty(item);
  }

  public ItemFingerprint fingerprint() {
    return fingerprint;
  }

  public long timestampMillis() {
    return timestampMillis;
  }

  public boolean isEmpty() {
    return fingerprint.isEmpty() || item.getType().isAir();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ItemSnapshot that)) return false;
    return playerSlot == that.playerSlot
        && guiSlot.equals(that.guiSlot)
        && fingerprint.equals(that.fingerprint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(playerSlot, guiSlot, fingerprint);
  }

  @Override
  public String toString() {
    return "ItemSnapshot{"
        + "playerSlot="
        + playerSlot
        + ", guiSlot="
        + guiSlot
        + ", item="
        + item
        + ", fingerprint="
        + fingerprint
        + ", timestampMillis="
        + timestampMillis
        + '}';
  }
}
