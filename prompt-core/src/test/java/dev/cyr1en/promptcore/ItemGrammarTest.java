package dev.cyr1en.promptcore;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.parser.CommandLineParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemGrammarTest {

  private final CommandLineParser parser = new CommandLineParser();

  // ====================================================================
  // Key Aliases & Case Normalization
  // ====================================================================

  @ParameterizedTest
  @ValueSource(strings = {"i", "item", "I", "ITEM", "Item", "ItEm"})
  @DisplayName("isItemKey matches case-insensitively")
  void isItemKey_matchesCaseInsensitively(String key) {
    assertTrue(ItemGrammar.isItemKey(key));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "", "a", "anvil", "d", "dialog", "p", "player", "s", "sign", "c", "confirm", "custom"
      })
  @DisplayName("isItemKey rejects non-item keys")
  void isItemKey_rejectsNonItemKeys(String key) {
    assertFalse(ItemGrammar.isItemKey(key));
  }

  @Test
  @DisplayName("isItemKey handles null")
  void isItemKey_handlesNull() {
    assertFalse(ItemGrammar.isItemKey(null));
  }

  @Test
  @DisplayName("parse PromptTag with valid aliases")
  void parsePromptTag_withValidAliases() {
    var tagI = new PromptTag("<i:Prompt>", "i", null, "Prompt");
    var syntaxI = ItemGrammar.parse(tagI);
    assertEquals("Prompt", syntaxI.promptText());
    assertEquals(ItemSource.INVENTORY, syntaxI.source());
    assertEquals(ItemOutputFormat.KEY, syntaxI.outputFormat());

    var tagItem = new PromptTag("<item:Prompt>", "item", null, "Prompt");
    var syntaxItem = ItemGrammar.parse(tagItem);
    assertEquals("Prompt", syntaxItem.promptText());

    var tagUpper = new PromptTag("<ITEM:Prompt>", "ITEM", null, "Prompt");
    var syntaxUpper = ItemGrammar.parse(tagUpper);
    assertEquals("Prompt", syntaxUpper.promptText());
  }

  @Test
  @DisplayName("parse PromptTag with non-item key throws")
  void parsePromptTag_withNonItemKey_throws() {
    var tag = new PromptTag("<a:Prompt>", "a", null, "Prompt");
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse(tag));
  }

  @Test
  @DisplayName("parse PromptTag with null throws")
  void parsePromptTag_withNull_throws() {
    assertThrows(NullPointerException.class, () -> ItemGrammar.parse((PromptTag) null));
  }

  // ====================================================================
  // Source Aliases & Parsing
  // ====================================================================

  @ParameterizedTest
  @ValueSource(strings = {"inv", "INV", "inventory", "INVENTORY", "Inventory"})
  @DisplayName("Parses inventory source aliases")
  void parsesInventorySourceAliases(String alias) {
    var syntax = ItemGrammar.parse("Select item -source:" + alias);
    assertEquals(ItemSource.INVENTORY, syntax.source());
    assertEquals("Select item", syntax.promptText());
    assertNull(syntax.category());
  }

  @ParameterizedTest
  @ValueSource(strings = {"hand", "HAND", "mainhand", "MAINHAND", "MainHand"})
  @DisplayName("Parses hand source aliases")
  void parsesHandSourceAliases(String alias) {
    var syntax = ItemGrammar.parse("Confirm held item -source:" + alias);
    assertEquals(ItemSource.HAND, syntax.source());
    assertEquals("Confirm held item", syntax.promptText());
    assertNull(syntax.category());
  }

  @ParameterizedTest
  @ValueSource(strings = {"armor", "ARMOR", "Armor"})
  @DisplayName("Parses armor source aliases")
  void parsesArmorSourceAliases(String alias) {
    var syntax = ItemGrammar.parse("Select armor piece -source:" + alias);
    assertEquals(ItemSource.ARMOR, syntax.source());
    assertEquals("Select armor piece", syntax.promptText());
    assertNull(syntax.category());
  }

  @ParameterizedTest
  @ValueSource(strings = {"catalog", "CATALOG", "Catalog"})
  @DisplayName("Parses catalog source alias and defaults category to 'all'")
  void parsesCatalogSourceAlias(String alias) {
    var syntax = ItemGrammar.parse("Select reward -source:" + alias);
    assertEquals(ItemSource.CATALOG, syntax.source());
    assertEquals("Select reward", syntax.promptText());
    assertEquals("all", syntax.category());
    assertEquals("all", syntax.optionalCategory().orElseThrow());
  }

  @Test
  @DisplayName("Default source is INVENTORY when unspecified")
  void defaultSourceIsInventory() {
    var syntax = ItemGrammar.parse("Select item");
    assertEquals(ItemSource.INVENTORY, syntax.source());
  }

  // ====================================================================
  // Output Format Aliases & Parsing
  // ====================================================================

  @ParameterizedTest
  @ValueSource(strings = {"key", "KEY", "Key"})
  @DisplayName("Parses key output format")
  void parsesKeyOutputFormat(String alias) {
    var syntax = ItemGrammar.parse("Select item -out:" + alias);
    assertEquals(ItemOutputFormat.KEY, syntax.outputFormat());
    assertEquals(ItemOutputFormat.KEY, syntax.output());
    assertEquals(ItemOutputFormat.KEY, syntax.format());
  }

  @ParameterizedTest
  @ValueSource(strings = {"material", "MATERIAL", "Material"})
  @DisplayName("Parses material output format")
  void parsesMaterialOutputFormat(String alias) {
    var syntax = ItemGrammar.parse("Select item -out:" + alias);
    assertEquals(ItemOutputFormat.MATERIAL, syntax.outputFormat());
  }

  @ParameterizedTest
  @ValueSource(strings = {"slot", "SLOT", "Slot"})
  @DisplayName("Parses slot output format")
  void parsesSlotOutputFormat(String alias) {
    var syntax = ItemGrammar.parse("Select item -out:" + alias);
    assertEquals(ItemOutputFormat.SLOT, syntax.outputFormat());
  }

  @ParameterizedTest
  @ValueSource(strings = {"amount", "AMOUNT", "Amount"})
  @DisplayName("Parses amount output format")
  void parsesAmountOutputFormat(String alias) {
    var syntax = ItemGrammar.parse("Select item -out:" + alias);
    assertEquals(ItemOutputFormat.AMOUNT, syntax.outputFormat());
  }

  @Test
  @DisplayName("Default output format is KEY when unspecified")
  void defaultOutputFormatIsKey() {
    var syntax = ItemGrammar.parse("Select item");
    assertEquals(ItemOutputFormat.KEY, syntax.outputFormat());
  }

  // ====================================================================
  // Category & Combination Invariants
  // ====================================================================

  @Test
  @DisplayName("Catalog mode with explicit category")
  void catalogModeWithCategory() {
    var syntax = ItemGrammar.parse("Select mineral -source:catalog -cat:minerals");
    assertEquals(ItemSource.CATALOG, syntax.source());
    assertEquals("minerals", syntax.category());
    assertEquals("minerals", syntax.optionalCategory().orElseThrow());
    assertEquals("Select mineral", syntax.promptText());
  }

  @Test
  @DisplayName("Catalog mode with quoted category")
  void catalogModeWithQuotedCategory() {
    var syntax = ItemGrammar.parse("Select item -source:catalog -cat:\"rare_minerals\"");
    assertEquals(ItemSource.CATALOG, syntax.source());
    assertEquals("rare_minerals", syntax.category());
  }

  @Test
  @DisplayName("Rejects category filter on non-catalog source")
  void rejectsCategoryOnNonCatalog() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Select item -source:inv -cat:minerals"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Select item -source:hand -cat:minerals"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Select item -source:armor -cat:minerals"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Select item -cat:minerals")); // default is inv
  }

  @Test
  @DisplayName("Rejects slot output format in catalog mode")
  void rejectsSlotOutputInCatalogMode() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Select item -source:catalog -out:slot"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Select item -source:catalog -cat:minerals -out:slot"));
  }

  @Test
  @DisplayName("Allows slot output format in inventory, hand, and armor modes")
  void allowsSlotOutputInNonCatalogModes() {
    var inv = ItemGrammar.parse("Select item -source:inv -out:slot");
    assertEquals(ItemOutputFormat.SLOT, inv.outputFormat());

    var hand = ItemGrammar.parse("Select item -source:hand -out:slot");
    assertEquals(ItemOutputFormat.SLOT, hand.outputFormat());

    var armor = ItemGrammar.parse("Select item -source:armor -out:slot");
    assertEquals(ItemOutputFormat.SLOT, armor.outputFormat());
  }

  // ====================================================================
  // Sound & Timeout Flags
  // ====================================================================

  @Test
  @DisplayName("Parses valid sound key flag")
  void parsesSoundKeyFlag() {
    var syntax = ItemGrammar.parse("Select item -sound:minecraft:block.note_block.bell");
    assertEquals("minecraft:block.note_block.bell", syntax.soundKey());
    assertEquals("minecraft:block.note_block.bell", syntax.optionalSoundKey().orElseThrow());
    assertEquals("Select item", syntax.promptText());
  }

  @Test
  @DisplayName("Parses bare sound key")
  void parsesBareSoundKey() {
    var syntax = ItemGrammar.parse("Select item -sound:entity.player.levelup");
    assertEquals("entity.player.levelup", syntax.soundKey());
  }

  @Test
  @DisplayName("Strips and validates timeout flag")
  void stripsAndValidatesTimeoutFlag() {
    var syntax = ItemGrammar.parse("Select item -timeout:45");
    assertEquals("Select item", syntax.promptText());
  }

  @Test
  @DisplayName("All flags combined")
  void allFlagsCombined() {
    var syntax =
        ItemGrammar.parse(
            "\"Choose reward item\" -source:catalog -cat:weapons -out:material -sound:minecraft:block.note_block.bell -timeout:20 -ds");
    assertEquals("Choose reward item", syntax.promptText());
    assertEquals(ItemSource.CATALOG, syntax.source());
    assertEquals("weapons", syntax.category());
    assertEquals(ItemOutputFormat.MATERIAL, syntax.outputFormat());
    assertEquals("minecraft:block.note_block.bell", syntax.soundKey());
  }

  // ====================================================================
  // Error Handling & Fail-Closed Validation
  // ====================================================================

  @Test
  @DisplayName("Fails closed on unknown source")
  void failsOnUnknownSource() {
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -source:enderchest"));
  }

  @Test
  @DisplayName("Fails closed on empty source value")
  void failsOnEmptySourceValue() {
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -source:"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -source"));
  }

  @Test
  @DisplayName("Fails closed on malformed source flag")
  void failsOnMalformedSourceFlag() {
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -source=inv"));
  }

  @Test
  @DisplayName("Fails closed on duplicate source flag")
  void failsOnDuplicateSourceFlag() {
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -source:inv -source:hand"));
  }

  @Test
  @DisplayName("Fails closed on unknown output format")
  void failsOnUnknownOutputFormat() {
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -out:nbt"));
  }

  @Test
  @DisplayName("Fails closed on empty output value")
  void failsOnEmptyOutputValue() {
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -out:"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -out"));
  }

  @Test
  @DisplayName("Fails closed on duplicate output flag")
  void failsOnDuplicateOutputFlag() {
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -out:key -out:material"));
  }

  @Test
  @DisplayName("Fails closed on blank category")
  void failsOnBlankCategory() {
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -source:catalog -cat:"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -source:catalog -cat:\"\""));
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -source:catalog -cat"));
  }

  @Test
  @DisplayName("Fails closed on duplicate category flag")
  void failsOnDuplicateCategoryFlag() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -source:catalog -cat:minerals -cat:tools"));
  }

  @Test
  @DisplayName("Fails closed on invalid sound key")
  void failsOnInvalidSoundKey() {
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -sound:invalid$key"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -sound:"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -sound"));
    // Uppercase sound keys rejected
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -sound:minecraft:BLOCK.NOTE_BLOCK.BELL"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -sound:ENTITY.PLAYER.LEVELUP"));
    // Control characters in sound keys rejected
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -sound:minecraft:bell\u0000"));
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -sound:bell\nname"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -sound:bell\r"));
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -sound:bell\u001F"));
    // Overlength sound key (> 256 chars) rejected
    String longSound = "minecraft:" + "a".repeat(250);
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -sound:" + longSound));
    // Direct validator assertions
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.validateSoundKey(null));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.validateSoundKey(""));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.validateSoundKey("   "));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.validateSoundKey("UPPERCASE"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.validateSoundKey("bad:key$"));
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.validateSoundKey("a".repeat(257)));
  }

  @Test
  @DisplayName("Validates namespaced and bare sound keys")
  void validatesSoundKeys() {
    assertDoesNotThrow(() -> ItemGrammar.validateSoundKey("minecraft:block.note_block.bell"));
    assertDoesNotThrow(() -> ItemGrammar.validateSoundKey("entity.player.levelup"));
    assertDoesNotThrow(() -> ItemGrammar.validateSoundKey("custom-pack:ui/click.1"));
    assertDoesNotThrow(() -> ItemGrammar.validateSoundKey("custom.sound_1"));

    var s1 = ItemGrammar.parse("Prompt -sound:minecraft:block.note_block.bell");
    assertEquals("minecraft:block.note_block.bell", s1.soundKey());
    var s2 = ItemGrammar.parse("Prompt -sound:entity.player.levelup");
    assertEquals("entity.player.levelup", s2.soundKey());
  }

  @Test
  @DisplayName("Fails closed on invalid catalog categories")
  void failsOnInvalidCatalogCategories() {
    // Uppercase
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -source:catalog -cat:MINERALS"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -source:catalog -cat:Weapons"));
    // Spaces
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -source:catalog -cat:\"rare minerals\""));
    // Control characters
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -source:catalog -cat:minerals\u0000"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -source:catalog -cat:minerals\n"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -source:catalog -cat:minerals\u001F"));
    // Overlength category (> 64 chars)
    String longCat = "c".repeat(65);
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -source:catalog -cat:" + longCat));
    // Invalid characters
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -source:catalog -cat:weapons!"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -source:catalog -cat:weapons$1"));

    // Direct validator assertions
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.validateCategory(null));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.validateCategory(""));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.validateCategory("   "));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.validateCategory("UPPER"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.validateCategory("has space"));
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.validateCategory("c".repeat(65)));
  }

  @Test
  @DisplayName("Validates allowed catalog category syntax")
  void validatesAllowedCategorySyntax() {
    assertDoesNotThrow(() -> ItemGrammar.validateCategory("all"));
    assertDoesNotThrow(() -> ItemGrammar.validateCategory("minerals"));
    assertDoesNotThrow(() -> ItemGrammar.validateCategory("rare_minerals"));
    assertDoesNotThrow(() -> ItemGrammar.validateCategory("weapons-tier.1"));
    assertDoesNotThrow(() -> ItemGrammar.validateCategory("c".repeat(64)));

    var syntax = ItemGrammar.parse("Prompt -source:catalog -cat:rare_minerals.1");
    assertEquals("rare_minerals.1", syntax.category());
  }

  @Test
  @DisplayName("ItemSyntax constructor validates soundKey and category invariants")
  void itemSyntaxConstructorValidation() {
    assertDoesNotThrow(
        () ->
            new ItemSyntax(
                "Prompt",
                ItemSource.CATALOG,
                ItemOutputFormat.KEY,
                "valid_cat.1",
                "minecraft:block.note_block.bell"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ItemSyntax(
                "Prompt",
                ItemSource.CATALOG,
                ItemOutputFormat.KEY,
                "INVALID_CAT",
                "minecraft:bell"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ItemSyntax(
                "Prompt", ItemSource.CATALOG, ItemOutputFormat.KEY, "valid_cat", "INVALID:SOUND"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ItemSyntax(
                "Prompt", ItemSource.INVENTORY, ItemOutputFormat.KEY, null, "invalid$sound"));
  }

  @Test
  @DisplayName("Fails closed on duplicate sound flag")
  void failsOnDuplicateSoundFlag() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemGrammar.parse("Prompt -sound:minecraft:bell -sound:minecraft:chime"));
  }

  @Test
  @DisplayName("Fails closed on invalid timeout value")
  void failsOnInvalidTimeoutValue() {
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -timeout:0"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -timeout:99999"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -timeout:abc"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -timeout:"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -timeout"));
    assertThrows(
        IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -timeout:10 -timeout:20"));
  }

  @Test
  @DisplayName("Fails closed on unknown or boolean flag in item prompt")
  void failsOnUnknownOrBooleanFlags() {
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -mode:gui"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -value"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -glow"));
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt -unknown:val"));
  }

  @Test
  @DisplayName("Fails closed on unbalanced quotes")
  void failsOnUnbalancedQuotes() {
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("\"Unclosed prompt"));
  }

  @Test
  @DisplayName("Fails closed on trailing escape")
  void failsOnTrailingEscape() {
    assertThrows(IllegalArgumentException.class, () -> ItemGrammar.parse("Prompt \\"));
  }

  // ====================================================================
  // CommandLineParser Integration & Spec Tests (ITEM-01, ITEM-08)
  // ====================================================================

  @Test
  @DisplayName("ITEM-01: Parse <i:Pick item -source:inv -out:key>")
  void item01_parseItemPromptTag() {
    var result = parser.parse("/trade Steve <i:Pick item -source:inv -out:key>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("i", tag.key());

    var syntax = ItemGrammar.parse(tag);
    assertEquals("Pick item", syntax.promptText());
    assertEquals(ItemSource.INVENTORY, syntax.source());
    assertEquals(ItemOutputFormat.KEY, syntax.outputFormat());
    assertNull(syntax.category());
    assertTrue(tag.flags().isEmpty(), "Built-in key must not populate arbitrary custom flags");
  }

  @Test
  @DisplayName("ITEM-01 variant: Parse <item:Select reward -source:catalog -cat:minerals -out:key>")
  void item01_parseItemTagCatalog() {
    var result =
        parser.parse(
            "/admin giveblock Steve <item:Select reward -source:catalog -cat:minerals -out:key>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("item", tag.key());

    var syntax = ItemGrammar.parse(tag);
    assertEquals("Select reward", syntax.promptText());
    assertEquals(ItemSource.CATALOG, syntax.source());
    assertEquals("minerals", syntax.category());
    assertEquals(ItemOutputFormat.KEY, syntax.outputFormat());
  }

  @Test
  @DisplayName("ITEM-08: -cat on non-catalog source fails closed at parse time")
  void item08_catOnNonCatalogFailsClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/trade Steve <i:Pick item -source:inv -cat:minerals>"));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("/trade Steve <i:Pick item -cat:minerals>"));
  }

  @Test
  @DisplayName("Preserves colons in prompt text without interpreting as filter")
  void preservesColonsInPromptText() {
    var result = parser.parse("/test <i:Warning: select diamond -source:hand -out:material>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("i", tag.key());
    assertNull(tag.filter(), "Item tag must not parse second colon as filter");

    var syntax = ItemGrammar.parse(tag);
    assertEquals("Warning: select diamond", syntax.promptText());
    assertEquals(ItemSource.HAND, syntax.source());
    assertEquals(ItemOutputFormat.MATERIAL, syntax.outputFormat());
  }

  @Test
  @DisplayName("Preserves negative numbers in prompt display text")
  void preservesNegativeNumbersInDisplayText() {
    var syntax = ItemGrammar.parse("Select item -10 -source:hand");
    assertEquals("Select item -10", syntax.promptText());
    assertEquals(ItemSource.HAND, syntax.source());
  }

  @Test
  @DisplayName("Preserves authoritatively extracted timeout and title")
  void preservesTimeoutAndTitle() {
    var result =
        parser.parse(
            "/test <i:Choose item -source:inv -timeout:30 -t:\"Inventory\"|\"Pick one\"|60>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals(30, tag.timeout());
    assertNotNull(tag.title());
    assertEquals("Inventory", tag.title().main());
    assertEquals("Pick one", tag.title().sub());
    assertEquals(60, tag.title().ticks());

    var syntax = ItemGrammar.parse(tag);
    assertEquals("Choose item", syntax.promptText());
    assertEquals(ItemSource.INVENTORY, syntax.source());
  }

  @Test
  @DisplayName("Custom screen keys still parse arbitrary custom flags without interference")
  void customScreenKeysRegression() {
    var result = parser.parse("/give <custom:Select item -source:inv -glow -rarity:rare>");
    assertEquals(1, result.promptTags().size());
    var tag = result.promptTags().get(0);
    assertEquals("custom", tag.key());
    assertEquals("Select item", tag.displayText());
    assertEquals("inv", tag.flags().get("source"));
    assertEquals("true", tag.flags().get("glow"));
    assertEquals("rare", tag.flags().get("rarity"));
  }
}
