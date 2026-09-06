package dev.cyr1en.promptpaper.execution.postaction.template;

import dev.cyr1en.promptcore.logic.transform.ReferenceSegment;
import java.util.Objects;

/** Immutable compiled segment within an action template. */
public sealed interface ActionTemplateSegment
    permits ActionTemplateSegment.Literal,
        ActionTemplateSegment.Reference,
        ActionTemplateSegment.Papi {

  /** Opaque literal text. */
  record Literal(String text) implements ActionTemplateSegment {
    public Literal {
      Objects.requireNonNull(text, "text must not be null");
    }
  }

  /** Core reference placeholder (e.g. {player}, {0:upper}, {input:1}). */
  record Reference(ReferenceSegment reference) implements ActionTemplateSegment {
    public Reference {
      Objects.requireNonNull(reference, "reference must not be null");
    }
  }

  /** Trusted precompiled PlaceholderAPI reference (e.g. %vault_eco_balance%). */
  record Papi(String token) implements ActionTemplateSegment {
    public Papi {
      Objects.requireNonNull(token, "token must not be null");
    }
  }
}
