package dev.cyr1en.promptcore.logic.transform;

import java.util.Objects;

/** A literal produced by an explicitly allowed escape outside a placeholder. */
public record EscapedLiteralSegment(String text) implements TemplateSegment {

  public EscapedLiteralSegment {
    Objects.requireNonNull(text, "text must not be null");
  }
}
