package dev.cyr1en.promptcore.logic.transform;

/** Platform-neutral pure string/numeric transformer applied to bound placeholder references. */
public sealed interface Transformer
    permits UpperTransformer,
        LowerTransformer,
        CapitalizeTransformer,
        TrimTransformer,
        StripColorTransformer,
        DefaultTransformer,
        MathTransformer,
        RoundTransformer,
        NoOpTransformer {

  /**
   * Applies the transformation to the given raw input value.
   *
   * @param input the raw bound input value (may be null for unbound reference)
   * @param mathMode math execution mode (strict or legacy)
   * @return the result of the transformation
   */
  SingleTransformResult transform(String input, MathMode mathMode);

  /**
   * Returns the canonical name of this transformer.
   *
   * @return the transformer name
   */
  String name();
}
