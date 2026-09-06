package dev.cyr1en.promptpaper.item.snapshot;

/** Thrown when an error occurs during item serialization or fingerprinting. */
public class ItemSerializationException extends RuntimeException {

  public ItemSerializationException(String message) {
    super(message);
  }

  public ItemSerializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
