package dev.cyr1en.promptcore.logic.transform;

import java.util.Objects;

/**
 * An immutable literal string segment within a compiled template.
 *
 * @param text the literal text
 */
public record LiteralSegment(String text) implements TemplateSegment {

  public LiteralSegment {
    Objects.requireNonNull(text, "text must not be null");
  }
}
