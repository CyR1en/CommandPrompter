package dev.cyr1en.promptpaper.item.snapshot;

import java.util.Objects;
import org.bukkit.Material;

/**
 * Immutable fingerprint representing an item's real material, real amount, normalized SHA-256 metadata hash,
 * and serialized byte length.
 *
 * @param material            The item material (never null)
 * @param amount              The real item amount (>= 0)
 * @param sha256              The SHA-256 hex string of the canonical serialization of the item normalized to amount 1 (never null)
 * @param serializedByteCount The serialized payload size in bytes (>= 0)
 */
public record ItemFingerprint(Material material, int amount, String sha256, int serializedByteCount) {

    public static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    public static final ItemFingerprint EMPTY = new ItemFingerprint(Material.AIR, 0, EMPTY_SHA256, 0);

    public ItemFingerprint {
        Objects.requireNonNull(material, "material cannot be null");
        Objects.requireNonNull(sha256, "sha256 cannot be null");
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative: " + amount);
        }
        if (serializedByteCount < 0) {
            throw new IllegalArgumentException("serializedByteCount cannot be negative: " + serializedByteCount);
        }
    }

    /**
     * Checks if this fingerprint represents an empty or air slot.
     */
    public boolean isEmpty() {
        return material.isAir() || amount == 0 || serializedByteCount == 0;
    }

    /**
     * Checks if this fingerprint exactly matches another fingerprint in material, amount, and normalized SHA-256 hash.
     * Empty fingerprints are considered matching any other empty fingerprint.
     */
    public boolean matches(ItemFingerprint other) {
        if (other == null) {
            return false;
        }
        if (this.isEmpty() && other.isEmpty()) {
            return true;
        }
        return this.material == other.material
                && this.amount == other.amount
                && this.sha256.equalsIgnoreCase(other.sha256);
    }

    /**
     * Checks if this fingerprint matches another fingerprint in material and normalized SHA-256 hash, ignoring amount.
     */
    public boolean matchesIgnoringAmount(ItemFingerprint other) {
        if (other == null) {
            return false;
        }
        if (this.isEmpty() && other.isEmpty()) {
            return true;
        }
        return this.material == other.material
                && this.sha256.equalsIgnoreCase(other.sha256);
    }
}
