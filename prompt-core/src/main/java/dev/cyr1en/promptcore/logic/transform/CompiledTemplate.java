package dev.cyr1en.promptcore.logic.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable compiled representation of a template string.
 *
 * <p>Compiled templates consist of an ordered sequence of literal and reference segments. During
 * rendering, bound values are inserted as opaque data and are never re-lexed or reparsed.
 */
public record CompiledTemplate(String source, List<TemplateSegment> segments) {

  public CompiledTemplate {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(segments, "segments must not be null");
    segments = List.copyOf(segments);
  }

  /**
   * Returns an unmodifiable set of all reference keys required by this template.
   *
   * @return set of unique reference keys
   */
  public Set<String> referencedKeys() {
    Set<String> keys = new LinkedHashSet<>();
    for (TemplateSegment segment : segments) {
      if (segment instanceof ReferenceSegment ref) {
        keys.add(ref.key());
      }
    }
    return Collections.unmodifiableSet(keys);
  }

  /**
   * Renders this compiled template using the provided bindings and math mode.
   *
   * @param bindings source of bound values for reference keys
   * @param mathMode math execution mode (strict or legacy)
   * @return the result of the render operation
   */
  public RenderResult render(TemplateBindings bindings, MathMode mathMode) {
    Objects.requireNonNull(bindings, "bindings must not be null");
    Objects.requireNonNull(mathMode, "mathMode must not be null");

    StringBuilder sb = new StringBuilder();
    List<TransformNotice> notices = new ArrayList<>();

    for (TemplateSegment segment : segments) {
      String literalText =
          switch (segment) {
            case LiteralSegment literal -> literal.text();
            case EscapedLiteralSegment literal -> literal.text();
            default -> null;
          };
      if (literalText != null) {
        sb.append(literalText);
        if (sb.length() > TransformLimits.MAX_OUTPUT_LENGTH) {
          return RenderResult.failure(
              TransformErrorCode.OUTPUT_TOO_LONG,
              "Rendered output length exceeds limit of "
                  + TransformLimits.MAX_OUTPUT_LENGTH
                  + " characters");
        }
      } else if (segment instanceof ReferenceSegment ref) {
        String boundValue = bindings.get(ref.key());

        if (boundValue != null) {
          if (boundValue.length() > TransformLimits.MAX_INPUT_LENGTH) {
            return RenderResult.failure(
                TransformErrorCode.INPUT_TOO_LONG,
                "Bound value for key '"
                    + ref.key()
                    + "' exceeds length limit of "
                    + TransformLimits.MAX_INPUT_LENGTH);
          }
          for (int i = 0; i < boundValue.length(); i++) {
            char c = boundValue.charAt(i);
            if (c < 0x20 || c == 0x7F) {
              return RenderResult.failure(
                  TransformErrorCode.CONTROL_CHARACTER_DETECTED,
                  "Control character detected in bound value for key '" + ref.key() + "'");
            }
          }
        } else if (!(ref.transformer() instanceof DefaultTransformer)) {
          return RenderResult.failure(
              TransformErrorCode.MISSING_BINDING,
              "Missing binding for reference key: " + ref.key());
        }

        SingleTransformResult transformResult = ref.transformer().transform(boundValue, mathMode);
        if (transformResult.isFailure()) {
          return RenderResult.failure(transformResult.error());
        }

        if (!transformResult.notices().isEmpty()) {
          notices.addAll(transformResult.notices());
        }

        String transformedValue = transformResult.value();
        if (transformedValue != null) {
          sb.append(transformedValue);
          if (sb.length() > TransformLimits.MAX_OUTPUT_LENGTH) {
            return RenderResult.failure(
                TransformErrorCode.OUTPUT_TOO_LONG,
                "Rendered output length exceeds limit of "
                    + TransformLimits.MAX_OUTPUT_LENGTH
                    + " characters");
          }
        }
      }
    }

    return RenderResult.success(sb.toString(), notices);
  }

  public RenderResult render(TemplateBindings bindings) {
    return render(bindings, MathMode.LEGACY);
  }

  public RenderResult render(Map<String, String> bindings, MathMode mathMode) {
    return render(TemplateBindings.of(bindings), mathMode);
  }

  public RenderResult render(Map<String, String> bindings) {
    return render(TemplateBindings.of(bindings), MathMode.LEGACY);
  }

  public RenderResult render(List<String> indexedAnswers, MathMode mathMode) {
    return render(TemplateBindings.fromList(indexedAnswers), mathMode);
  }

  public RenderResult render(List<String> indexedAnswers) {
    return render(TemplateBindings.fromList(indexedAnswers), MathMode.LEGACY);
  }

  public RenderResult render(String[] indexedAnswers, MathMode mathMode) {
    return render(TemplateBindings.fromIndexed(indexedAnswers), mathMode);
  }

  public RenderResult render(String[] indexedAnswers) {
    return render(TemplateBindings.fromIndexed(indexedAnswers), MathMode.LEGACY);
  }
}
