package dev.cyr1en.promptpaper.item.snapshot;

/**
 * Thrown when the cumulative serialized payload of all items on a screen exceeds the aggregate
 * limit (2 MiB).
 */
public class OversizedAggregateException extends ItemSerializationException {

  private final long actualBytes;
  private final long maxBytes;

  public OversizedAggregateException(String message, long actualBytes, long maxBytes) {
    super(message);
    this.actualBytes = actualBytes;
    this.maxBytes = maxBytes;
  }

  public long getActualBytes() {
    return actualBytes;
  }

  public long getMaxBytes() {
    return maxBytes;
  }
}
