package dev.cyr1en.promptpaper.item.snapshot;

/**
 * Thrown when an individual item's serialized payload exceeds the maximum allowed per-item byte limit (64 KiB).
 */
public class OversizedItemException extends ItemSerializationException {

    private final long actualBytes;
    private final long maxBytes;

    public OversizedItemException(String message, long actualBytes, long maxBytes) {
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
