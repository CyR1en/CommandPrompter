package dev.cyr1en.promptcore.logic.transform;

/** Math execution mode controlling division-by-zero behavior. */
public enum MathMode {
  /** Substitutes exact 0 and produces {@link TransformNotice#DIVISION_BY_ZERO_SUBSTITUTED}. */
  LEGACY,
  /** Emits a typed {@link TransformError} and fails closed. */
  STRICT
}
