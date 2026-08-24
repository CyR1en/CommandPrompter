package dev.cyr1en.promptcore.parser;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomFlagsParserTest {

  private final CommandLineParser parser = new CommandLineParser();

  @Test
  @DisplayName("Parses single bare custom flag as 'true'")
  void parsesBareFlag() {
    var result = parser.parse("/give <ecoitem:Pick weapon -glow>");
    assertEquals(1, result.promptCount());
    var tag = result.promptTags().get(0);
    assertEquals("ecoitem", tag.key());
    assertEquals("Pick weapon", tag.displayText());
    assertEquals(Map.of("glow", "true"), tag.flags());
  }

  @Test
  @DisplayName("Parses unquoted key-value custom flag")
  void parsesKeyValueFlag() {
    var result = parser.parse("/give <ecoitem:Pick weapon -rarity:legendary>");
    var tag = result.promptTags().get(0);
    assertEquals("ecoitem", tag.key());
    assertEquals("Pick weapon", tag.displayText());
    assertEquals(Map.of("rarity", "legendary"), tag.flags());
  }

  @Test
  @DisplayName("Parses quoted key-value custom flag preserving spaces and colons")
  void parsesQuotedValueFlag() {
    var result = parser.parse("/give <ecoitem:Pick weapon -desc:\"Sword of : Awesome Speed\">");
    var tag = result.promptTags().get(0);
    assertEquals("ecoitem", tag.key());
    assertEquals("Pick weapon", tag.displayText());
    assertEquals(Map.of("desc", "Sword of : Awesome Speed"), tag.flags());
  }

  @Test
  @DisplayName("Parses multiple mixed custom flags")
  void parsesMultipleFlags() {
    var result =
        parser.parse(
            "/give <ecoitem:Choose item -glow -tier:mythic -lore:\"Ancient relic from depth\""
                + " -count:3>");
    var tag = result.promptTags().get(0);
    assertEquals("ecoitem", tag.key());
    assertEquals("Choose item", tag.displayText());
    assertEquals(4, tag.flags().size());
    assertEquals("true", tag.flags().get("glow"));
    assertEquals("mythic", tag.flags().get("tier"));
    assertEquals("Ancient relic from depth", tag.flags().get("lore"));
    assertEquals("3", tag.flags().get("count"));
  }

  @Test
  @DisplayName("Does not parse flags when quoted inside display text")
  void preservesQuotedDisplayText() {
    var result = parser.parse("/give <ecoitem:\"-flag:value\">");
    var tag = result.promptTags().get(0);
    assertEquals("ecoitem", tag.key());
    assertEquals("\"-flag:value\"", tag.displayText());
    assertTrue(tag.flags().isEmpty());
  }

  @Test
  @DisplayName("Preserves quotes in display text while parsing trailing flags")
  void preservesDisplayQuotesWithTrailingFlags() {
    var result = parser.parse("/give <ecoitem:Pick \"diamond sword\" -rarity:epic>");
    var tag = result.promptTags().get(0);
    assertEquals("ecoitem", tag.key());
    assertEquals("Pick \"diamond sword\"", tag.displayText());
    assertEquals(Map.of("rarity", "epic"), tag.flags());
  }

  @Test
  @DisplayName("Preserves prose containing hyphens and colons when not trailing")
  void preservesProseCompatibility() {
    var result = parser.parse("/give <ecoitem:Choose -fast: now>");
    var tag = result.promptTags().get(0);
    assertEquals("ecoitem", tag.key());
    assertEquals("Choose -fast: now", tag.displayText());
    assertTrue(tag.flags().isEmpty());
  }

  @Test
  @DisplayName("Preserves negative numbers in display text")
  void preservesNegativeNumbersInDisplay() {
    var result = parser.parse("/spell <custom:Damage -10 -type:fire>");
    var tag = result.promptTags().get(0);
    assertEquals("custom", tag.key());
    assertEquals("Damage -10", tag.displayText());
    assertEquals(Map.of("type", "fire"), tag.flags());
  }

  @Test
  @DisplayName("Fails closed on duplicate custom flags")
  void failsOnDuplicateFlags() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/give <ecoitem:Pick -glow -glow>"));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/give <ecoitem:Pick -rarity:rare -rarity:epic>"));
  }

  @Test
  @DisplayName("Fails closed on malformed flags")
  void failsOnMalformedFlags() {
    // Empty value after colon
    assertThrows(IllegalArgumentException.class, () -> parser.parse("/give <ecoitem:Pick -flag:>"));

    // Invalid flag name syntax (starts with number / invalid character)
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse("/give <ecoitem:Pick -bad@name:val>"));

    // Unclosed quotes in flag value
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/give <ecoitem:Pick -desc:\"unclosed>"));
  }

  @Test
  @DisplayName("Fails closed on exceeding flag count limit (16)")
  void failsOnExcessiveFlagCount() {
    var sb = new StringBuilder("/give <custom:Pick");
    for (int i = 1; i <= 17; i++) {
      sb.append(" -flag").append(i).append(":val");
    }
    sb.append(">");
    assertThrows(IllegalArgumentException.class, () -> parser.parse(sb.toString()));
  }

  @Test
  @DisplayName("Fails closed on exceeding flag name length (32 chars)")
  void failsOnExcessiveFlagNameLength() {
    var longName = "a".repeat(33);
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/give <custom:Pick -" + longName + ":val>"));
  }

  @Test
  @DisplayName("Fails closed on exceeding flag value length (512 chars)")
  void failsOnExcessiveFlagValueLength() {
    var longVal = "v".repeat(513);
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/give <custom:Pick -flag:" + longVal + ">"));
  }

  @Test
  @DisplayName("Fails closed on exceeding aggregate flags length (1024 chars)")
  void failsOnExcessiveAggregateFlagLength() {
    var sb = new StringBuilder("/give <custom:Pick");
    for (int i = 1; i <= 5; i++) {
      sb.append(" -f").append(i).append(":\"").append("a".repeat(300)).append("\"");
    }
    sb.append(">");
    assertThrows(IllegalArgumentException.class, () -> parser.parse(sb.toString()));
  }

  @Test
  @DisplayName("Structural flags are excluded from arbitrary custom flags map")
  void structuralFlagsExcludedFromCustomFlags() {
    var result =
        parser.parse(
            "/give <custom:Select item -source:inventory -ds -timeout:15 -iv:req -int -t:Title>");
    var tag = result.promptTags().get(0);
    assertEquals("custom", tag.key());
    assertEquals("Select item", tag.displayText());
    assertFalse(tag.sanitize());
    assertEquals(15, tag.timeout());
    assertEquals("req", tag.validatorAlias());
    assertEquals(PromptTag.AnswerType.INTEGER, tag.type());
    assertNotNull(tag.title());
    assertEquals("Title", tag.title().main());
    // Only arbitrary custom flags appear in flags()
    assertEquals(Map.of("source", "inventory"), tag.flags());
  }

  @Test
  @DisplayName("Built-in keys do not extract arbitrary custom flags")
  void builtInKeysDoNotExtractCustomFlags() {
    var aResult = parser.parse("/cmd <a:Enter name -timeout:10 -ds>");
    var aTag = aResult.promptTags().get(0);
    assertEquals("a", aTag.key());
    assertEquals("Enter name", aTag.displayText());
    assertTrue(aTag.flags().isEmpty());

    var sResult = parser.parse("/cmd <s:Sign text -ds>");
    var sTag = sResult.promptTags().get(0);
    assertEquals("s", sTag.key());
    assertTrue(sTag.flags().isEmpty());

    var pResult = parser.parse("/cmd <p:online:Select player>");
    var pTag = pResult.promptTags().get(0);
    assertEquals("p", pTag.key());
    assertTrue(pTag.flags().isEmpty());

    var cResult = parser.parse("/cmd <c:Are you sure? -mode:chat>");
    var cTag = cResult.promptTags().get(0);
    assertEquals("c", cTag.key());
    assertTrue(cTag.flags().isEmpty());
  }

  @Test
  @DisplayName("PromptTag backward-compatible constructors default flags to empty map")
  void promptTagConstructorsCompatibility() {
    var tag4 = new PromptTag("<raw>", "key", null, "disp");
    assertTrue(tag4.flags().isEmpty());

    var tag6 = new PromptTag("<raw>", "key", null, "disp", false, "alias");
    assertTrue(tag6.flags().isEmpty());
    assertFalse(tag6.sanitize());

    var tag9 =
        new PromptTag(
            "<raw>",
            "key",
            null,
            "disp",
            true,
            "alias",
            PromptTag.AnswerType.STRING,
            List.of(),
            false);
    assertTrue(tag9.flags().isEmpty());

    var tag10 =
        new PromptTag(
            "<raw>",
            "key",
            null,
            "disp",
            true,
            "alias",
            PromptTag.AnswerType.STRING,
            List.of(),
            false,
            new TitleConfig("T", null, null));
    assertTrue(tag10.flags().isEmpty());
    assertNotNull(tag10.title());

    var tag11 =
        new PromptTag(
            "<raw>",
            "key",
            null,
            "disp",
            true,
            "alias",
            PromptTag.AnswerType.STRING,
            List.of(),
            false,
            new TitleConfig("T", null, null),
            30);
    assertTrue(tag11.flags().isEmpty());
    assertEquals(30, tag11.timeout());
  }
}
