package dev.cyr1en.promptcore.parser;

import dev.cyr1en.promptcore.BuiltInPromptType;
import dev.cyr1en.promptcore.ParserConfig;
import dev.cyr1en.promptcore.TagFilter;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import dev.cyr1en.promptcore.logic.transform.TransformException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Converts explicit V2 prompt markers without reserializing the containing configuration. */
public final class LegacyPromptMigrator {
  private static final Pattern SELECTOR =
      Pattern.compile("(?<!\\S)-(a|s|p(?::[^\\s]+)?|exac?(?::\\d+)?(?:\\|[pc])?)(?=\\s|$)");
  private static final Pattern FLAG = Pattern.compile("(?<!\\S)-([^\\s]+)");
  private static final Pattern REFERENCE = Pattern.compile("p:(\\d+)");
  private static final Pattern CURRENT = Pattern.compile("^(?:[\\w]+:|[@!]).*", Pattern.DOTALL);

  public record Change(int line, String before, String after) {}

  /** Reason is a message-key suffix, never an English error intended for the player. */
  public record Diagnostic(int line, String reason) {}

  public record Result(String content, List<Change> changes, List<Diagnostic> diagnostics) {}

  private final ParserConfig syntax;
  private final ParserConfig validationSyntax;
  private final TemplateSyntax templates;
  private final TagFilter filter;

  public LegacyPromptMigrator(ParserConfig syntax, TagFilter filter) {
    this(syntax, filter, TemplateSyntax.DEFAULT);
  }

  public LegacyPromptMigrator(ParserConfig syntax, TagFilter filter, TemplateSyntax templates) {
    this(syntax, filter, templates, syntax);
  }

  private LegacyPromptMigrator(
      ParserConfig syntax,
      TagFilter filter,
      TemplateSyntax templates,
      ParserConfig validationSyntax) {
    this.syntax = syntax;
    this.filter = filter;
    this.templates = templates;
    this.validationSyntax = validationSyntax;
  }

  public Result migrate(String source) {
    var syntaxes = new LinkedHashSet<ParserConfig>();
    syntaxes.add(syntax);
    syntaxes.add(new ParserConfig(xml(syntax.opening()), xml(syntax.closing()), syntax.escape()));
    syntaxes.add(
        new ParserConfig(unicode(syntax.opening()), unicode(syntax.closing()), syntax.escape()));
    var changes = new ArrayList<Change>();
    var diagnostics = new ArrayList<Diagnostic>();
    String content = source;
    for (var representation : syntaxes) {
      var result =
          new LegacyPromptMigrator(representation, filter, templates, syntax)
              .migrateLiteral(content);
      content = result.content();
      changes.addAll(result.changes());
      diagnostics.addAll(result.diagnostics());
    }
    changes.sort(Comparator.comparingInt(Change::line));
    diagnostics.sort(Comparator.comparingInt(Diagnostic::line));
    return new Result(content, List.copyOf(changes), List.copyOf(diagnostics));
  }

  private Result migrateLiteral(String source) {
    var changes = new ArrayList<Change>();
    var diagnostics = new ArrayList<Diagnostic>();
    var output = new StringBuilder(source.length());
    int cursor = 0;
    int search = 0;
    int line = 1;
    while (search < source.length()) {
      int start = source.indexOf(syntax.opening(), search);
      if (start < 0) break;
      line += countLines(source, search, start);
      if (escaped(source, start)) {
        search = start + syntax.opening().length();
        continue;
      }
      int bodyStart = start + syntax.opening().length();
      int end = source.indexOf(syntax.closing(), bodyStart);
      if (end < 0) {
        if (FLAG.matcher(source.substring(bodyStart)).find()) {
          diagnostics.add(new Diagnostic(line, "malformed"));
        }
        break;
      }
      String body = source.substring(bodyStart, end);
      String replacement = convert(body, line, diagnostics);
      int after = end + syntax.closing().length();
      if (!body.equals(replacement)) {
        String before = source.substring(start, after);
        String converted = syntax.opening() + replacement + syntax.closing();
        output.append(source, cursor, start).append(converted);
        cursor = after;
        changes.add(new Change(line, before, converted));
      }
      line += countLines(source, start, after);
      search = after;
    }
    output.append(source, cursor, source.length());
    return new Result(output.toString(), List.copyOf(changes), List.copyOf(diagnostics));
  }

  private String convert(String body, int line, List<Diagnostic> diagnostics) {
    String trimmed = body.strip();
    if (filter != null && filter.test(trimmed)) return body;
    if (CURRENT.matcher(trimmed).matches()) {
      int colon = trimmed.indexOf(':');
      boolean unknownKey =
          !trimmed.startsWith("@")
              && !trimmed.startsWith("!")
              && colon >= 0
              && BuiltInPromptType.resolve(trimmed.substring(0, colon)).isEmpty();
      if (unknownKey) diagnostics.add(new Diagnostic(line, "ambiguous"));
      return body;
    }
    var selector = SELECTOR.matcher(body);
    if (!selector.find()) {
      var flags = FLAG.matcher(body);
      while (flags.find()) {
        if (!flags.group(1).matches("(?:ds|int|str|iv:[\\w]+)")) {
          diagnostics.add(new Diagnostic(line, "unsupported"));
          break;
        }
      }
      return body;
    }
    String key = selector.group(1);
    int start = selector.start();
    int end = selector.end();
    boolean post = key.startsWith("exa");
    // Leave host-format escapes in place; only escaped quotes have identical conversion rules.
    String validationBody = body.replace("\\\"", "\"");
    if (body.substring(0, start).contains("\"")
        || body.contains(syntax.opening())
        || validationBody.contains(syntax.escape())
        || (!post && selector.find())) {
      diagnostics.add(new Diagnostic(line, "ambiguous"));
      return body;
    }
    String text = (body.substring(0, start) + body.substring(end)).strip();
    String converted;
    if (post) {
      if (!body.substring(0, start).isBlank()) {
        diagnostics.add(new Diagnostic(line, "ambiguous"));
        return body;
      }
      String target = key.endsWith("|c") ? " @console" : key.endsWith("|p") ? " @player" : "";
      String marker = key.split("\\|", 2)[0];
      marker = marker.replace("exac", "!!").replace("exa", "!");
      if (text.contains("@console")
          || text.contains("@player")
          || text.startsWith("@")
          || text.contains(templates.open())
          || text.isBlank()) {
        diagnostics.add(new Diagnostic(line, "ambiguous"));
        return body;
      }
      String referenceOpen = isXml() ? xml(templates.open()) : templates.open();
      String referenceClose = isXml() ? xml(templates.close()) : templates.close();
      text =
          REFERENCE
              .matcher(text)
              .replaceAll(
                  match ->
                      java.util.regex.Matcher.quoteReplacement(
                          referenceOpen + match.group(1) + referenceClose));
      converted = marker + " " + text + target;
    } else {
      if (key.startsWith("p:") && !key.substring(2).matches("(?:w|s|r[0-9]+)+")) {
        diagnostics.add(new Diagnostic(line, "unsupported"));
        return body;
      }
      var flags = FLAG.matcher(text);
      while (flags.find()) {
        if (!flags.group(1).matches("(?:ds|int|str|iv:[\\w]+)")) {
          diagnostics.add(new Diagnostic(line, "unsupported"));
          return body;
        }
        if (text.contains("\"")) {
          diagnostics.add(new Diagnostic(line, "ambiguous"));
          return body;
        }
      }
      // An empty filter protects labels such as Name: without inserting ': ' into plain YAML.
      int colon = text.indexOf(':');
      boolean needsEmptyFilter =
          key.indexOf(':') < 0
              && colon >= 0
              && !text.substring(0, colon).matches("(?s).*[\\s\"-].*");
      converted = key + (needsEmptyFilter ? "::" : ":") + text;
    }
    try {
      String validationText = converted.replace("\\\"", "\"");
      if (isXml())
        validationText =
            validationText
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
      var parsed =
          new CommandLineParser(validationSyntax)
              .parse(validationSyntax.opening() + validationText + validationSyntax.closing());
      if (post ? parsed.postCmds().size() != 1 : parsed.promptTags().size() != 1) {
        throw new IllegalArgumentException("Unexpected tag count");
      }
      if (!post && !parsed.promptTags().getFirst().subTags().isEmpty()) {
        throw new IllegalArgumentException("Legacy display became a compound prompt");
      }
      if (post) {
        var references = REFERENCE.matcher(body);
        while (references.find()) Integer.parseInt(references.group(1));
        TemplateCompiler.compile(parsed.postCmds().getFirst().command(), templates);
      }
    } catch (IllegalArgumentException | TransformException e) {
      diagnostics.add(new Diagnostic(line, "invalid"));
      return body;
    }
    return converted;
  }

  private boolean escaped(String source, int index) {
    int count = 0;
    for (int i = index - syntax.escape().length();
        i >= 0 && source.startsWith(syntax.escape(), i);
        i -= syntax.escape().length()) count++;
    return count % 2 != 0;
  }

  private static int countLines(String text, int from, int to) {
    int count = 0;
    for (int i = from; i < to; i++) if (text.charAt(i) == '\n') count++;
    return count;
  }

  private static String xml(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private boolean isXml() {
    return !syntax.equals(validationSyntax)
        && syntax.opening().equals(xml(validationSyntax.opening()));
  }

  private static String unicode(String text) {
    var result = new StringBuilder();
    for (int i = 0; i < text.length(); i++)
      result.append(String.format("\\u%04x", (int) text.charAt(i)));
    return result.toString();
  }
}
