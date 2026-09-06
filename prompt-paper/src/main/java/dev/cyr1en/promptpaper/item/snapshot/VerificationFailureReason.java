package dev.cyr1en.promptpaper.item.snapshot;

/** Enumerates reasons why an item selection verification check may fail. */
public enum VerificationFailureReason {
  /** The inventory reference provided was null. */
  NULL_INVENTORY,

  /** The expected snapshot reference provided was null. */
  NULL_SNAPSHOT,

  /** The GUI slot index does not map to any valid player inventory slot. */
  SLOT_UNMAPPED,

  /** The player inventory slot index is out of the valid 0..40 range. */
  SLOT_OUT_OF_BOUNDS,

  /** The target slot is now empty/air, but an item was expected. */
  EMPTY_SLOT_EXPECTED_ITEM,

  /** The target slot now contains an item, but an empty slot was expected. */
  ITEM_EXPECTED_EMPTY_SLOT,

  /** The item material in the slot does not match the expected snapshot material. */
  MATERIAL_MISMATCH,

  /** The item quantity in the slot does not match the expected snapshot quantity. */
  AMOUNT_MISMATCH,

  /**
   * The item metadata, lore, display name, enchants, or components do not match the expected
   * snapshot.
   */
  METADATA_MISMATCH,

  /** The item serialized payload exceeds the 64 KiB per-item bound. */
  ITEM_OVERSIZED,

  /** The cumulative serialized payload exceeds the 2 MiB screen aggregate bound. */
  AGGREGATE_OVERSIZED,

  /** A serialization error occurred while attempting to inspect or fingerprint the item. */
  SERIALIZATION_ERROR
}
