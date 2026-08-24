package dev.cyr1en.promptcore.logic.transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Compiles template source strings into immutable, single-pass {@link CompiledTemplate} instances.
 *
 * <p>Validates resource limits, rejects nested or chained transformers, enforces escape syntax, and
 * compiles placeholders into literal and reference segments.
 */
public final class TemplateCompiler {

  /** Rewrites a placeholder body before the core transformer grammar is compiled. */
  @FunctionalInterface
  public interface PlaceholderNormalizer {
    String normalize(String body, int sourceOffset);
  }

  private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

  private TemplateCompiler() {}

  /**
   * Compiles a template string into a {@link CompiledTemplate} using legacy default syntax.
   *
   * @param source the raw template source
   * @return the compiled template
   * @throws TransformException if the template is malformed or exceeds bounds
   */
  public static CompiledTemplate compile(String source) {
    return compile(source, TemplateSyntax.DEFAULT);
  }

  /**
   * Compiles a template string into a {@link CompiledTemplate} using the specified syntax.
   *
   * @param source the raw template source
   * @param syntax the template syntax delimiters
   * @return the compiled template
   * @throws TransformException if the template is malformed or exceeds bounds
   */
  public static CompiledTemplate compile(String source, TemplateSyntax syntax) {
    return compile(source, syntax, (body, sourceOffset) -> body, Set.of());
  }

  /**
   * Compiles with a placeholder normalizer and additional single-pass literal escapes. Escaped
   * literals remain typed so downstream compilers cannot reinterpret them as extension syntax.
   */
  public static CompiledTemplate compile(
      String source,
      TemplateSyntax syntax,
      PlaceholderNormalizer placeholderNormalizer,
      Set<String> additionalLiteralEscapes) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(syntax, "syntax must not be null");
    Objects.requireNonNull(placeholderNormalizer, "placeholderNormalizer must not be null");
    additionalLiteralEscapes =
        Set.copyOf(
            Objects.requireNonNull(
                additionalLiteralEscapes, "additionalLiteralEscapes must not be null"));
    if (additionalLiteralEscapes.stream().anyMatch(String::isEmpty)) {
      throw new IllegalArgumentException("additional literal escapes must not be empty");
    }

    if (source.length() > TransformLimits.MAX_TEMPLATE_LENGTH) {
      throw new TransformException(
          TransformErrorCode.TEMPLATE_TOO_LONG,
          "Template source length exceeds limit of "
              + TransformLimits.MAX_TEMPLATE_LENGTH
              + ": "
              + source.length());
    }

    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c < 0x20 || c == 0x7F) {
        throw new TransformException(
            TransformErrorCode.CONTROL_CHARACTER_DETECTED,
            "Control character detected in template source at index " + i);
      }
    }

    String open = syntax.open();
    String close = syntax.close();
    String escape = syntax.escape();

    List<TemplateSegment> segments = new ArrayList<>();
    StringBuilder literalBuffer = new StringBuilder();
    int i = 0;
    int n = source.length();

    while (i < n) {
      if (source.startsWith(escape, i)) {
        int nextIdx = i + escape.length();
        if (nextIdx >= n) {
          throw new TransformException(
              TransformErrorCode.MALFORMED_ESCAPE, "Dangling escape character at end of template");
        }
        if (source.startsWith(open, nextIdx)) {
          literalBuffer.append(open);
          i = nextIdx + open.length();
        } else if (source.startsWith(close, nextIdx)) {
          literalBuffer.append(close);
          i = nextIdx + close.length();
        } else if (source.startsWith(escape, nextIdx)) {
          literalBuffer.append(escape);
          i = nextIdx + escape.length();
        } else {
          String extraLiteral = null;
          for (var candidate : additionalLiteralEscapes) {
            if (source.startsWith(candidate, nextIdx)) {
              extraLiteral = candidate;
              break;
            }
          }
          if (extraLiteral == null) {
            throw new TransformException(
                TransformErrorCode.MALFORMED_ESCAPE,
                "Invalid escape sequence starting at index " + i);
          }
          flushLiteral(segments, literalBuffer);
          segments.add(new EscapedLiteralSegment(extraLiteral));
          i = nextIdx + extraLiteral.length();
        }
      } else if (source.startsWith(close, i)) {
        throw new TransformException(
            TransformErrorCode.MALFORMED_TEMPLATE,
            "Unescaped closing delimiter '"
                + close
                + "' at index "
                + i
                + "; use '"
                + escape
                + close
                + "' for literal delimiter");
      } else if (source.startsWith(open, i)) {
        int placeholderStart = i;
        i += open.length(); // skip open delimiter

        boolean inQuotes = false;
        boolean closed = false;
        StringBuilder body = new StringBuilder();

        while (i < n) {
          if (source.startsWith(escape, i)) {
            int nextIdx = i + escape.length();
            if (nextIdx >= n) {
              throw new TransformException(
                  TransformErrorCode.MALFORMED_ESCAPE,
                  "Dangling escape inside placeholder starting at index " + placeholderStart);
            }
            if (inQuotes) {
              if (source.startsWith("\"", nextIdx)) {
                body.append('\\').append('"');
                i = nextIdx + 1;
                continue;
              } else if (source.startsWith(escape, nextIdx)) {
                body.append('\\').append('\\');
                i = nextIdx + escape.length();
                continue;
              } else {
                throw new TransformException(
                    TransformErrorCode.MALFORMED_ESCAPE,
                    "Invalid escape sequence inside quoted string at index " + i);
              }
            } else {
              throw new TransformException(
                  TransformErrorCode.MALFORMED_ESCAPE,
                  "Unexpected escape sequence outside quotes inside placeholder at index " + i);
            }
          }

          char inner = source.charAt(i);
          if (inner == '"') {
            inQuotes = !inQuotes;
            body.append(inner);
            i++;
            continue;
          }

          if (!inQuotes) {
            if (source.startsWith(open, i)) {
              throw new TransformException(
                  TransformErrorCode.MALFORMED_PLACEHOLDER,
                  "Nested '" + open + "' inside placeholder at index " + i);
            }
            if (source.startsWith(close, i)) {
              closed = true;
              i += close.length(); // consume close delimiter
              break;
            }
          }

          body.append(inner);
          i++;
        }

        if (!closed) {
          throw new TransformException(
              TransformErrorCode.MALFORMED_TEMPLATE,
              "Unclosed placeholder starting at index " + placeholderStart);
        }

        flushLiteral(segments, literalBuffer);

        String normalizedBody = placeholderNormalizer.normalize(body.toString(), placeholderStart);
        if (normalizedBody == null) {
          throw new TransformException(
              TransformErrorCode.MALFORMED_PLACEHOLDER,
              "Placeholder normalizer returned null at index " + placeholderStart);
        }
        ReferenceSegment refSegment = parsePlaceholder(normalizedBody, placeholderStart, syntax);
        segments.add(refSegment);
      } else {
        literalBuffer.append(source.charAt(i));
        i++;
      }
    }

    flushLiteral(segments, literalBuffer);

    return new CompiledTemplate(source, segments);
  }

  private static void flushLiteral(List<TemplateSegment> segments, StringBuilder literalBuffer) {
    if (literalBuffer.length() == 0) return;
    segments.add(new LiteralSegment(literalBuffer.toString()));
    literalBuffer.setLength(0);
  }

  private static ReferenceSegment parsePlaceholder(
      String body, int sourceOffset, TemplateSyntax syntax) {
    int sepIdx = -1;
    boolean inQuotes = false;
    String sep = syntax.transformSeparator();

    int i = 0;
    while (i < body.length()) {
      if (body.charAt(i) == '\\' && inQuotes && i + 1 < body.length()) {
        i += 2;
        continue;
      }
      char c = body.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
        i++;
      } else if (!inQuotes && body.startsWith(sep, i)) {
        sepIdx = i;
        break;
      } else {
        i++;
      }
    }

    String key;
    Transformer transformer;

    if (sepIdx == -1) {
      key = body.strip();
      transformer = NoOpTransformer.INSTANCE;
    } else {
      key = body.substring(0, sepIdx).strip();
      String transformSpec = body.substring(sepIdx + sep.length()).strip();

      if (transformSpec.isEmpty()) {
        throw new TransformException(
            TransformErrorCode.MALFORMED_PLACEHOLDER,
            "Empty transformer specification after '" + sep + "' at index " + sourceOffset);
      }

      boolean specInQuotes = false;
      int j = 0;
      while (j < transformSpec.length()) {
        if (transformSpec.charAt(j) == '\\' && specInQuotes && j + 1 < transformSpec.length()) {
          j += 2;
          continue;
        }
        char c = transformSpec.charAt(j);
        if (c == '"') {
          specInQuotes = !specInQuotes;
          j++;
        } else if (!specInQuotes && transformSpec.startsWith(sep, j)) {
          throw new TransformException(
              TransformErrorCode.CHAINED_TRANSFORMER,
              "Chained transformers are not allowed: " + transformSpec);
        } else {
          j++;
        }
      }

      transformer = parseTransformer(transformSpec, sourceOffset);
    }

    if (key.isEmpty()) {
      throw new TransformException(
          TransformErrorCode.MALFORMED_PLACEHOLDER,
          "Reference key must not be empty at index " + sourceOffset);
    }

    if (!KEY_PATTERN.matcher(key).matches()) {
      throw new TransformException(
          TransformErrorCode.MALFORMED_PLACEHOLDER,
          "Reference key contains invalid characters: '" + key + "' at index " + sourceOffset);
    }

    return new ReferenceSegment(key, transformer);
  }

  private static Transformer parseTransformer(String spec, int sourceOffset) {
    if (spec.equals("upper")) {
      return UpperTransformer.INSTANCE;
    }
    if (spec.equals("lower")) {
      return LowerTransformer.INSTANCE;
    }
    if (spec.equals("capitalize")) {
      return CapitalizeTransformer.INSTANCE;
    }
    if (spec.equals("trim")) {
      return TrimTransformer.INSTANCE;
    }
    if (spec.equals("stripcolor")) {
      return StripColorTransformer.INSTANCE;
    }
    if (spec.equals("round")) {
      return RoundTransformer.INSTANCE;
    }
    if (spec.startsWith("round")) {
      throw new TransformException(
          TransformErrorCode.MALFORMED_TRANSFORMER_ARGUMENT,
          "The 'round' transformer takes no arguments, got: " + spec);
    }

    if (spec.startsWith("default")) {
      return parseDefaultTransformer(spec, sourceOffset);
    }

    if (spec.startsWith("math")) {
      return parseMathTransformer(spec, sourceOffset);
    }

    throw new TransformException(
        TransformErrorCode.UNKNOWN_TRANSFORMER, "Unknown transformer: '" + spec + "'");
  }

  private static DefaultTransformer parseDefaultTransformer(String spec, int sourceOffset) {
    String afterDefault = spec.substring("default".length()).strip();
    if (!afterDefault.startsWith("=")) {
      throw new TransformException(
          TransformErrorCode.MALFORMED_TRANSFORMER_ARGUMENT,
          "Expected '=' after 'default' in transformer spec: " + spec);
    }
    String afterEquals = afterDefault.substring(1).strip();
    if (!afterEquals.startsWith("\"")) {
      throw new TransformException(
          TransformErrorCode.MALFORMED_TRANSFORMER_ARGUMENT,
          "Default value must be quoted with double quotes in: " + spec);
    }

    int i = 1; // skip leading '"'
    int n = afterEquals.length();
    StringBuilder defaultVal = new StringBuilder();
    boolean closed = false;

    while (i < n) {
      char c = afterEquals.charAt(i);
      if (c == '\\') {
        if (i + 1 >= n) {
          throw new TransformException(
              TransformErrorCode.MALFORMED_ESCAPE, "Dangling escape in default value: " + spec);
        }
        char next = afterEquals.charAt(i + 1);
        if (next == '"' || next == '\\') {
          defaultVal.append(next);
          i += 2;
          continue;
        } else {
          throw new TransformException(
              TransformErrorCode.MALFORMED_ESCAPE,
              "Invalid escape sequence \\" + next + " in default value: " + spec);
        }
      }
      if (c == '"') {
        closed = true;
        i++;
        break;
      }
      defaultVal.append(c);
      i++;
    }

    if (!closed) {
      throw new TransformException(
          TransformErrorCode.MALFORMED_TRANSFORMER_ARGUMENT,
          "Unclosed quote in default value: " + spec);
    }

    String trailing = afterEquals.substring(i).strip();
    if (!trailing.isEmpty()) {
      throw new TransformException(
          TransformErrorCode.MALFORMED_TRANSFORMER_ARGUMENT,
          "Unexpected trailing characters after default value: " + trailing);
    }

    return new DefaultTransformer(defaultVal.toString());
  }

  private static MathTransformer parseMathTransformer(String spec, int sourceOffset) {
    String afterMath = spec.substring("math".length()).strip();
    if (!afterMath.startsWith("(") || !afterMath.endsWith(")")) {
      throw new TransformException(
          TransformErrorCode.MALFORMED_TRANSFORMER_ARGUMENT,
          "Math expression must be enclosed in parentheses 'math(...)', got: " + spec);
    }

    String inner = afterMath.substring(1, afterMath.length() - 1);
    return MathTransformer.parse(inner);
  }
}
