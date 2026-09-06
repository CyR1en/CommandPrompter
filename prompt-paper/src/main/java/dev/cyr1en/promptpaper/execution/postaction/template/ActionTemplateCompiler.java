package dev.cyr1en.promptpaper.execution.postaction.template;

import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import dev.cyr1en.promptcore.logic.transform.EscapedLiteralSegment;
import dev.cyr1en.promptcore.logic.transform.LiteralSegment;
import dev.cyr1en.promptcore.logic.transform.ReferenceSegment;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.logic.transform.TemplateSegment;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import dev.cyr1en.promptcore.logic.transform.TransformException;
import dev.cyr1en.promptpaper.execution.dispatch.ActionTrustLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles raw template strings into immutable {@link CompiledActionTemplate} instances.
 *
 * <p>Validates template bounds, rejects raw C0 control characters, parses syntax placeholders into
 * typed references delegating transformers to prompt-core, and parses trusted PAPI placeholder
 * references into typed segments without runtime ambiguity.
 */
public final class ActionTemplateCompiler {

  /**
   * Pattern for validating PAPI reference tokens: Alphanumeric, underscores, hyphens, periods,
   * colons; length between 1 and 64; starts and ends with alphanumeric/underscore.
   */
  private static final Pattern PAPI_SAFE_TOKEN_PATTERN =
      Pattern.compile("^[a-zA-Z0-9_](?:[a-zA-Z0-9_.:-]{0,62}[a-zA-Z0-9_])?$");

  private ActionTemplateCompiler() {}

  public static CompiledActionTemplate compile(String source) {
    return compile(source, TemplateSyntax.DEFAULT, ActionTrustLevel.UNTRUSTED_INLINE);
  }

  public static CompiledActionTemplate compile(String source, boolean trusted) {
    return compile(
        source,
        TemplateSyntax.DEFAULT,
        trusted ? ActionTrustLevel.TRUSTED_PRESET : ActionTrustLevel.UNTRUSTED_INLINE);
  }

  public static CompiledActionTemplate compile(String source, ActionTrustLevel trustLevel) {
    return compile(source, TemplateSyntax.DEFAULT, trustLevel);
  }

  public static CompiledActionTemplate compile(String source, TemplateSyntax syntax) {
    return compile(source, syntax, ActionTrustLevel.UNTRUSTED_INLINE);
  }

  public static CompiledActionTemplate compile(
      String source, TemplateSyntax syntax, boolean trusted) {
    return compile(
        source,
        syntax,
        trusted ? ActionTrustLevel.TRUSTED_PRESET : ActionTrustLevel.UNTRUSTED_INLINE);
  }

  public static CompiledActionTemplate compile(
      String source, TemplateSyntax syntax, ActionTrustLevel trustLevel) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(syntax, "syntax must not be null");
    Objects.requireNonNull(trustLevel, "trustLevel must not be null");
    CompiledTemplate coreTemplate;
    try {
      coreTemplate =
          TemplateCompiler.compile(
              source,
              syntax,
              (body, sourceOffset) -> normalizePlaceholderBody(body, sourceOffset, syntax),
              Set.of("%"));
    } catch (TransformException error) {
      throw new ActionTemplateException(
          ActionTemplateError.fromCoreCode(error.code()), error.getMessage(), error);
    }

    boolean trusted =
        trustLevel == ActionTrustLevel.TRUSTED_PRESET
            || trustLevel == ActionTrustLevel.CONSOLE_DELEGATED;
    List<ActionTemplateSegment> segments = new ArrayList<>();
    for (TemplateSegment segment : stripLegacyAnswerQuotes(coreTemplate.segments())) {
      switch (segment) {
        case ReferenceSegment reference ->
            segments.add(new ActionTemplateSegment.Reference(reference));
        case EscapedLiteralSegment literal -> appendLiteral(segments, literal.text());
        case LiteralSegment literal -> appendLiteralWithPapi(segments, literal.text(), trusted);
      }
    }

    long refCount =
        segments.stream()
            .filter(
                s ->
                    s instanceof ActionTemplateSegment.Reference
                        || s instanceof ActionTemplateSegment.Papi)
            .count();
    if (refCount > ActionTemplateLimits.MAX_REFERENCES) {
      throw new ActionTemplateException(
          ActionTemplateErrorCode.TOO_MANY_REFERENCES,
          "Template reference count exceeds limit of "
              + ActionTemplateLimits.MAX_REFERENCES
              + ": "
              + refCount);
    }

    if (segments.size() > ActionTemplateLimits.MAX_SEGMENTS) {
      throw new ActionTemplateException(
          ActionTemplateErrorCode.TOO_MANY_SEGMENTS,
          "Template segment count exceeds limit of "
              + ActionTemplateLimits.MAX_SEGMENTS
              + ": "
              + segments.size());
    }

    return new CompiledActionTemplate(source, trustLevel, segments);
  }

  private static List<TemplateSegment> stripLegacyAnswerQuotes(List<TemplateSegment> source) {
    var segments = new ArrayList<>(source);
    for (int i = 1; i + 1 < segments.size(); i++) {
      if (!(segments.get(i) instanceof ReferenceSegment reference)
          || !CompiledActionTemplate.isAnswerKey(reference.key())
          || !(segments.get(i - 1) instanceof LiteralSegment before)
          || !(segments.get(i + 1) instanceof LiteralSegment after)
          || !before.text().endsWith("\"")
          || !after.text().startsWith("\"")) {
        continue;
      }
      segments.set(
          i - 1, new LiteralSegment(before.text().substring(0, before.text().length() - 1)));
      segments.set(i + 1, new LiteralSegment(after.text().substring(1)));
    }
    return segments;
  }

  private static void appendLiteralWithPapi(
      List<ActionTemplateSegment> segments, String text, boolean trusted) {
    if (!trusted) {
      appendLiteral(segments, text);
      return;
    }
    int cursor = 0;
    while (cursor < text.length()) {
      int open = text.indexOf('%', cursor);
      if (open < 0) {
        appendLiteral(segments, text.substring(cursor));
        return;
      }
      int close = text.indexOf('%', open + 1);
      if (close > open + 1) {
        var token = text.substring(open + 1, close);
        if (isValidPapiToken(token)) {
          appendLiteral(segments, text.substring(cursor, open));
          segments.add(new ActionTemplateSegment.Papi(token));
          cursor = close + 1;
          continue;
        }
      }
      appendLiteral(segments, text.substring(cursor, open + 1));
      cursor = open + 1;
    }
  }

  private static void appendLiteral(List<ActionTemplateSegment> segments, String text) {
    if (text == null || text.isEmpty()) return;
    if (!segments.isEmpty()
        && segments.get(segments.size() - 1) instanceof ActionTemplateSegment.Literal previous) {
      segments.set(segments.size() - 1, new ActionTemplateSegment.Literal(previous.text() + text));
    } else {
      segments.add(new ActionTemplateSegment.Literal(text));
    }
  }

  private static boolean isValidPapiToken(String token) {
    if (token == null
        || token.isEmpty()
        || token.length() > ActionTemplateLimits.MAX_PAPI_TOKEN_LENGTH) {
      return false;
    }
    return PAPI_SAFE_TOKEN_PATTERN.matcher(token).matches();
  }

  private static String normalizePlaceholderBody(
      String body, int sourceOffset, TemplateSyntax syntax) {
    String trimmed = body.strip();
    if (trimmed.isEmpty()) {
      throw new ActionTemplateException(
          ActionTemplateErrorCode.MALFORMED_PLACEHOLDER,
          "Reference key must not be empty at index " + sourceOffset);
    }

    String sep = syntax.transformSeparator();
    String patternStr = "^input(?::(\\d+))?(?:" + Pattern.quote(sep) + "(.*))?$";
    Pattern inputPattern = Pattern.compile(patternStr, Pattern.DOTALL);
    Matcher matcher = inputPattern.matcher(trimmed);

    if (matcher.matches()) {
      String indexDigits = matcher.group(1);
      String transformerSpec = matcher.group(2);

      String key;
      if (indexDigits == null) {
        key = "0";
      } else {
        try {
          int index1Based = Integer.parseInt(indexDigits);
          if (index1Based < 1) {
            throw new ActionTemplateException(
                ActionTemplateErrorCode.MALFORMED_PLACEHOLDER,
                "Input placeholder index must be positive (>= 1), got: "
                    + index1Based
                    + " at index "
                    + sourceOffset);
          }
          key = String.valueOf(index1Based - 1);
        } catch (NumberFormatException e) {
          throw new ActionTemplateException(
              ActionTemplateErrorCode.MALFORMED_PLACEHOLDER,
              "Input placeholder index is invalid: " + indexDigits + " at index " + sourceOffset);
        }
      }

      if (transformerSpec != null && !transformerSpec.isBlank()) {
        return key + sep + transformerSpec;
      }
      return key;
    }

    return trimmed;
  }
}
