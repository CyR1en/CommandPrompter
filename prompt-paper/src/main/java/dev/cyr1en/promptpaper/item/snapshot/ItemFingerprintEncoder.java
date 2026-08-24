package dev.cyr1en.promptpaper.item.snapshot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Encodes Bukkit/Paper {@link ItemStack} objects into canonical {@link ItemFingerprint} instances.
 * <p>
 * Enforces:
 * <ul>
 *   <li>Normalization of a defensive clone to amount 1 prior to canonical Paper serialization</li>
 *   <li>Per-item size bound of 64 KiB (65,536 bytes)</li>
 *   <li>Aggregate screen size bound of 2 MiB (2,097,152 bytes)</li>
 *   <li>Fail-closed error handling on serialization failures or size limit violations</li>
 * </ul>
 */
public final class ItemFingerprintEncoder {

    public static final int MAX_ITEM_BYTES = 64 * 1024; // 64 KiB (65,536 bytes)
    public static final long MAX_AGGREGATE_BYTES = 2L * 1024 * 1024; // 2 MiB (2,097,152 bytes)

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private ItemFingerprintEncoder() {
    }

    /**
     * Encodes an {@link ItemStack} into an {@link ItemFingerprint}.
     *
     * @param item The item to fingerprint (defensive copy is made)
     * @return The canonical {@link ItemFingerprint}
     * @throws OversizedItemException     if normalized serialization exceeds 64 KiB
     * @throws ItemSerializationException if serialization fails unexpectedly
     */
    public static ItemFingerprint encode(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return ItemFingerprint.EMPTY;
        }

        Material material = item.getType();
        int amount = item.getAmount();

        ItemStack normalized = item.clone();
        normalized.setAmount(1);

        byte[] serializedBytes;
        try {
            serializedBytes = normalized.serializeAsBytes();
        } catch (Exception ex) {
            throw new ItemSerializationException("Failed to canonically serialize item " + material + ": " + ex.getMessage(), ex);
        }

        if (serializedBytes == null) {
            throw new ItemSerializationException("Canonical item serialization returned null for " + material);
        }

        if (serializedBytes.length > MAX_ITEM_BYTES) {
            throw new OversizedItemException(
                    "Serialized item size exceeds 64 KiB limit (" + serializedBytes.length + " bytes > " + MAX_ITEM_BYTES + " bytes) for " + material,
                    serializedBytes.length,
                    MAX_ITEM_BYTES
            );
        }

        String sha256 = computeSha256Hex(serializedBytes);
        return new ItemFingerprint(material, amount, sha256, serializedBytes.length);
    }

    /**
     * Encodes a map of slot-to-item entries into slot-to-fingerprint entries and validates the 2 MiB screen aggregate.
     *
     * @param slotItemMap Map of slots to items
     * @return Unmodifiable map of slots to item fingerprints
     * @throws OversizedItemException      if any item exceeds 64 KiB
     * @throws OversizedAggregateException if total serialized bytes exceeds 2 MiB
     * @throws ItemSerializationException  if serialization fails
     */
    public static Map<Integer, ItemFingerprint> encodeSlots(Map<Integer, ItemStack> slotItemMap) {
        Objects.requireNonNull(slotItemMap, "slotItemMap cannot be null");
        Map<Integer, ItemFingerprint> result = new LinkedHashMap<>(slotItemMap.size());
        ScreenAggregateAccounting accounting = new ScreenAggregateAccounting();

        for (Map.Entry<Integer, ItemStack> entry : slotItemMap.entrySet()) {
            ItemFingerprint fp = encode(entry.getValue());
            accounting.add(fp);
            result.put(entry.getKey(), fp);
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Captures and encodes all 41 player inventory slots (0..40) and validates the 2 MiB screen aggregate.
     *
     * @param inventory Player inventory (must not be null)
     * @return Unmodifiable map from player slot index to ItemFingerprint
     * @throws OversizedItemException      if any item exceeds 64 KiB
     * @throws OversizedAggregateException if total serialized bytes exceeds 2 MiB
     * @throws ItemSerializationException  if serialization fails
     */
    public static Map<Integer, ItemFingerprint> encodeInventory(PlayerInventory inventory) {
        Objects.requireNonNull(inventory, "inventory cannot be null");
        Map<Integer, ItemStack> items = PlayerInventoryAccessor.captureAll(inventory);
        return encodeSlots(items);
    }

    /**
     * Computes the lowercase SHA-256 hex string for the given byte array.
     */
    public static String computeSha256Hex(byte[] data) {
        if (data == null || data.length == 0) {
            return ItemFingerprint.EMPTY_SHA256;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HEX_FORMAT.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
