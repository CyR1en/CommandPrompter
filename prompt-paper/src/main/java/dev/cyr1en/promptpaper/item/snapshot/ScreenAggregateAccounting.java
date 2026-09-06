package dev.cyr1en.promptpaper.item.snapshot;

import java.util.Objects;

/**
 * Tracks and enforces cumulative byte accounting for items displayed or snapshotted on a screen.
 * The aggregate limit is 2 MiB (2,097,152 bytes).
 */
public final class ScreenAggregateAccounting {

  public static final long MAX_AGGREGATE_BYTES = 2L * 1024 * 1024; // 2 MiB (2,097,152 bytes)

  private long totalBytes = 0;
  private int itemCount = 0;

  public ScreenAggregateAccounting() {}

  public ScreenAggregateAccounting(long initialBytes, int initialCount) {
    this.totalBytes = initialBytes;
    this.itemCount = initialCount;
    validateLimit();
  }

  /**
   * Adds an item fingerprint's serialized byte count to the cumulative screen total.
   *
   * @param fingerprint The item fingerprint (must not be null)
   * @throws OversizedAggregateException if adding this item causes total bytes to exceed 2 MiB
   */
  public synchronized void add(ItemFingerprint fingerprint) {
    Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
    addBytes(fingerprint.serializedByteCount());
    itemCount++;
  }

  /**
   * Adds raw byte count to the cumulative screen total.
   *
   * @param bytes Number of bytes to add
   * @throws OversizedAggregateException if total bytes exceeds 2 MiB
   */
  public synchronized void addBytes(long bytes) {
    if (bytes < 0) {
      throw new IllegalArgumentException("Byte count cannot be negative: " + bytes);
    }
    totalBytes += bytes;
    validateLimit();
  }

  /**
   * Validates that current total bytes does not exceed {@link #MAX_AGGREGATE_BYTES}.
   *
   * @throws OversizedAggregateException if total bytes exceeds 2 MiB
   */
  public synchronized void validateLimit() {
    if (totalBytes > MAX_AGGREGATE_BYTES) {
      throw new OversizedAggregateException(
          "Screen aggregate serialized item size exceeds 2 MiB limit ("
              + totalBytes
              + " bytes > "
              + MAX_AGGREGATE_BYTES
              + " bytes)",
          totalBytes,
          MAX_AGGREGATE_BYTES);
    }
  }

  public synchronized long getTotalBytes() {
    return totalBytes;
  }

  public synchronized int getItemCount() {
    return itemCount;
  }

  public synchronized long getRemainingBytes() {
    return Math.max(0, MAX_AGGREGATE_BYTES - totalBytes);
  }

  public synchronized boolean isWithinLimit() {
    return totalBytes <= MAX_AGGREGATE_BYTES;
  }

  public synchronized void reset() {
    totalBytes = 0;
    itemCount = 0;
  }

  /**
   * Creates an accounting tracker from an iterable of fingerprints and validates the aggregate
   * limit.
   *
   * @param fingerprints Iterable of fingerprints
   * @return ScreenAggregateAccounting with calculated totals
   * @throws OversizedAggregateException if total exceeds 2 MiB
   */
  public static ScreenAggregateAccounting from(Iterable<ItemFingerprint> fingerprints) {
    ScreenAggregateAccounting accounting = new ScreenAggregateAccounting();
    if (fingerprints != null) {
      for (ItemFingerprint fp : fingerprints) {
        if (fp != null) {
          accounting.add(fp);
        }
      }
    }
    return accounting;
  }

  /**
   * Validates that the sum of bytes for the given fingerprints does not exceed 2 MiB.
   *
   * @param fingerprints Iterable of fingerprints
   * @throws OversizedAggregateException if total exceeds 2 MiB
   */
  public static void validate(Iterable<ItemFingerprint> fingerprints) {
    from(fingerprints);
  }
}
