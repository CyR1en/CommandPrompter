package dev.cyr1en.promptcore.parser;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.ParserConfig;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LegacyPromptMigratorTest {
  private final LegacyPromptMigrator migrator =
      new LegacyPromptMigrator(ParserConfig.ANGLE_BRACKETS, null);

  static Stream<Arguments> conversions() {
    return Stream.of(
        Arguments.of("<-a Enter a name>", "<a:Enter a name>"),
        Arguments.of("<-s Enter a name>", "<s:Enter a name>"),
        Arguments.of("<-p Select a player>", "<p:Select a player>"),
        Arguments.of("<-p:w Select>", "<p:w:Select>"),
        Arguments.of("<-p:wr100s Select>", "<p:wr100s:Select>"),
        Arguments.of("<-a Why -ds -iv:required -str>", "<a:Why -ds -iv:required -str>"),
        Arguments.of("<-ds -a How many -int>", "<a:-ds  How many -int>"),
        Arguments.of("<Enter a name -a >", "<a:Enter a name>"),
        Arguments.of("<-s Name:{br}Age:>", "<s::Name:{br}Age:>"),
        Arguments.of("<-exa say p:0 p:12 p:0>", "<! say {0} {12} {0}>"),
        Arguments.of("<-exac say Cancelled>", "<!! say Cancelled>"),
        Arguments.of("<-exa:20 say p:0>", "<!:20 say {0}>"),
        Arguments.of("<-exac:20|c say p:0>", "<!!:20 say {0} @console>"),
        Arguments.of("<-exa|p say p:0>", "<! say {0} @player>"),
        Arguments.of("<-exa othercommand -a value>", "<! othercommand -a value>"),
        Arguments.of("<-a Enter \"name\">", "<a:Enter \"name\">"),
        Arguments.of("<-a Enter \\\"name\\\">", "<a:Enter \\\"name\\\">"));
  }

  @ParameterizedTest
  @MethodSource("conversions")
  void convertsAndIsIdempotent(String before, String after) {
    var result = migrator.migrate(before);
    assertEquals(after, result.content());
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertEquals(1, result.changes().size());
    var repeated = migrator.migrate(after);
    assertEquals(after, repeated.content());
    assertTrue(repeated.changes().isEmpty());
    assertTrue(repeated.diagnostics().isEmpty());
  }

  @Test
  void preservesContainingFileAndOnlyConvertsPostCommandReferences() {
    String original =
        "\uFEFF# Español 日本語\r\ncommands:\r\n  - 'msg <-p Who?> <-a p:0> <-exa say p:0>' # keep\r\nother: p:0\r\n";
    var result = migrator.migrate(original);
    assertEquals(
        original
            .replace("<-p Who?>", "<p:Who?>")
            .replace("<-a p:0>", "<a::p:0>")
            .replace("<-exa say p:0>", "<! say {0}>"),
        result.content());
    assertTrue(result.diagnostics().isEmpty());
    assertTrue(result.changes().stream().allMatch(c -> c.line() == 3));
  }

  @Test
  void leavesCurrentPromptsFlagsChatAndFormattingUntouched() {
    String source =
        "<a:Name -ds> <s:Sign> <p:w:Who> <! say {0}> <@preset> <Enter name> <-iv:required> <red>hello</red>";
    var result = migrator.migrate(source);
    assertEquals(source, result.content());
    assertTrue(result.changes().isEmpty());
    assertTrue(result.diagnostics().isEmpty());
  }

  @Test
  void reportsUnsupportedAndAmbiguousSyntax() {
    for (String source :
        new String[] {
          "<-custom Name>",
          "<-p:custom Who>",
          "<-a -s Name>",
          "<-a Name -unknown>",
          "<-a Name <red>bad</red>>",
          "<-exa:999999 say hi>",
          "<-exa say {0}>",
          "<-a Unclosed",
          "<-a \"bad > quote\">",
          "<-a hi\\>there>"
        }) {
      var result = migrator.migrate(source);
      assertEquals(source, result.content(), source);
      assertFalse(result.diagnostics().isEmpty(), source);
    }
  }

  @Test
  void customDelimitersAndEscapedTags() {
    var custom = new LegacyPromptMigrator(new ParserConfig("[[", "]]", "\\"), null);
    var result = custom.migrate("\\[[-a Literal]]\n[[-a Name]]\n[[-exa say p:0]]");
    assertEquals("\\[[-a Literal]]\n[[a:Name]]\n[[! say {0}]]", result.content());
    assertEquals(2, result.changes().getFirst().line());
    assertEquals(3, result.changes().getLast().line());
  }

  @Test
  void signLabelsRemainDisplayTextRatherThanBecomingFilters() {
    var result = migrator.migrate("<-s Name:{br}Age:>");
    var prompt = new CommandLineParser().parse(result.content()).promptTags().getFirst();
    assertEquals("", prompt.filter());
    assertEquals("Name:{br}Age:", prompt.displayText());
  }

  @Test
  void serializedDelimitersStayEncoded() {
    String source =
        "<command>say &lt;-a Name&gt;</command>\n{\"command\":\"say \\u003c-a Name\\u003e\"}";
    var result = migrator.migrate(source);
    assertEquals(source.replace("-a Name", "a:Name"), result.content());
    assertEquals(2, result.changes().size());
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(migrator.migrate(result.content()).changes().isEmpty());
  }

  @Test
  void usesConfiguredTemplateDelimitersForAnswerReferences() {
    var converter =
        new LegacyPromptMigrator(
            new ParserConfig("[[", "]]", "\\"), null, new TemplateSyntax("${", "}", ":", "\\"));
    var result = converter.migrate("[[-exa say p:0]]");
    assertEquals("[[! say ${0}]]", result.content());
    assertTrue(result.diagnostics().isEmpty());
  }

  @Test
  void preventsNewV3SemanticsFromChangingLegacyDisplayOrCommands() {
    for (String source :
        new String[] {
          "<-a One && two>",
          "<-exa @somecommand>",
          "<-exa say {other}>",
          "<-a \"Age -int \" >",
          "<-exa say p:999999999999>"
        }) {
      var result = migrator.migrate(source);
      assertEquals(source, result.content());
      assertFalse(result.diagnostics().isEmpty(), source);
    }
  }

  @Test
  void unknownColonTagsNeedManualReview() {
    for (String source : new String[] {"<Name:>", "<custom:Text>"}) {
      var result = migrator.migrate(source);
      assertEquals(source, result.content());
      assertEquals("ambiguous", result.diagnostics().getFirst().reason());
    }
  }
}
