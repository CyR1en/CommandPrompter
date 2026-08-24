package dev.cyr1en.promptpaper.execution.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable unique identifier for a command execution plan instance.
 *
 * <p>Combines a monotonic sequence number with a unique {@link UUID} to ensure uniqueness across
 * time and threads while preserving sequence ordering.
 *
 * @param sequence monotonic sequence identifier
 * @param uuid unique identifier
 */
public record ExecutionId(long sequence, UUID uuid) implements Comparable<ExecutionId> {

  private static final AtomicLong SEQUENCE_GENERATOR = new AtomicLong(0);

  public ExecutionId {
    Objects.requireNonNull(uuid, "uuid must not be null");
  }

  /** Creates a new uniquely generated ExecutionId with an incremented sequence and random UUID. */
  public static ExecutionId create() {
    return new ExecutionId(SEQUENCE_GENERATOR.incrementAndGet(), UUID.randomUUID());
  }

  /**
   * Creates an ExecutionId with the given UUID and a newly incremented sequence.
   *
   * @param uuid the unique identifier
   * @return a new ExecutionId
   */
  public static ExecutionId of(UUID uuid) {
    return new ExecutionId(SEQUENCE_GENERATOR.incrementAndGet(), uuid);
  }

  /**
   * Creates an ExecutionId with an explicit sequence and UUID.
   *
   * @param sequence the monotonic sequence
   * @param uuid the unique identifier
   * @return an ExecutionId
   */
  public static ExecutionId of(long sequence, UUID uuid) {
    return new ExecutionId(sequence, uuid);
  }

  /**
   * Parses an ExecutionId from its string representation (formatted as {@code sequence:uuid}).
   *
   * @param value the string representation
   * @return the parsed ExecutionId
   * @throws IllegalArgumentException if the format is invalid
   */
  public static ExecutionId fromString(String value) {
    Objects.requireNonNull(value, "value must not be null");
    int colonIdx = value.indexOf(':');
    if (colonIdx <= 0 || colonIdx >= value.length() - 1) {
      throw new IllegalArgumentException("Invalid ExecutionId format: " + value);
    }
    long seq = Long.parseLong(value.substring(0, colonIdx));
    UUID u = UUID.fromString(value.substring(colonIdx + 1));
    return new ExecutionId(seq, u);
  }

  @Override
  public int compareTo(ExecutionId other) {
    Objects.requireNonNull(other, "other must not be null");
    int cmp = Long.compare(this.sequence, other.sequence);
    if (cmp != 0) {
      return cmp;
    }
    return this.uuid.compareTo(other.uuid);
  }

  @Override
  public String toString() {
    return sequence + ":" + uuid;
  }
}
