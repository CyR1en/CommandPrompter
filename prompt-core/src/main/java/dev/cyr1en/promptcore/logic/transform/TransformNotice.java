package dev.cyr1en.promptcore.logic.transform;

/** Typed notices produced during non-fatal transformation fallbacks. */
public enum TransformNotice {
  /** Indicates legacy division-by-zero substituted 0 instead of throwing an error. */
  DIVISION_BY_ZERO_SUBSTITUTED
}
