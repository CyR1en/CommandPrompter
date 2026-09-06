package dev.cyr1en.promptpaper.preset;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.ItemOutputFormat;
import dev.cyr1en.promptcore.ItemSource;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.factory.InlineTagMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ItemPromptTest {

  @Nested
  @DisplayName("Model and Constructor Tests")
  class ModelTests {

    @Test
    void defaultsMatchingCore() {
      var prompt = new ItemPrompt("item", "i1", "Select item", null, null, null, null, true);
      assertEquals("item", prompt.type());
      assertEquals("i1", prompt.id());
      assertEquals("Select item", prompt.promptText());
      assertEquals(ItemSource.INVENTORY, prompt.source());
      assertEquals(ItemOutputFormat.KEY, prompt.output());
      assertEquals(ItemOutputFormat.KEY, prompt.outputFormat());
      assertEquals(ItemOutputFormat.KEY, prompt.format());
      assertNull(prompt.category());
      assertNull(prompt.sound());
      assertNull(prompt.soundKey());
      assertTrue(prompt.sanitize());
      assertNull(prompt.titleDisplay());
      assertNull(prompt.timeout());
    }

    @Test
    void catalogDefaultsCategoryToAll() {
      var prompt =
          new ItemPrompt(
              "item",
              "i1",
              "Select item",
              ItemSource.CATALOG,
              ItemOutputFormat.MATERIAL,
              null,
              null,
              false);
      assertEquals(ItemSource.CATALOG, prompt.source());
      assertEquals("all", prompt.category());
      assertEquals(ItemOutputFormat.MATERIAL, prompt.output());
      assertFalse(prompt.sanitize());
    }

    @Test
    void catalogWithCustomCategory() {
      var prompt =
          new ItemPrompt(
              "item",
              "i1",
              "Select item",
              ItemSource.CATALOG,
              ItemOutputFormat.KEY,
              "weapons",
              "sound.click",
              true);
      assertEquals("weapons", prompt.category());
      assertEquals("sound.click", prompt.sound());
      assertEquals("sound.click", prompt.soundKey());
    }

    @Test
    void itemPromptSoundValidation() {
      // Valid sounds
      assertDoesNotThrow(
          () ->
              new ItemPrompt(
                  "item",
                  "i1",
                  "Select item",
                  null,
                  null,
                  null,
                  "minecraft:ui.button.click",
                  true));
      assertDoesNotThrow(
          () ->
              new ItemPrompt(
                  "item", "i1", "Select item", null, null, null, "entity.player.levelup", true));
      assertDoesNotThrow(
          () ->
              new ItemPrompt(
                  "item", "i1", "Select item", null, null, null, "custom.click_1", true));

      // Uppercase rejected
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ItemPrompt(
                  "item", "i1", "Select item", null, null, null, "MINECRAFT:CLICK", true));
      assertThrows(
          IllegalArgumentException.class,
          () -> new ItemPrompt("item", "i1", "Select item", null, null, null, "Sound.Click", true));

      // Blank rejected
      assertThrows(
          IllegalArgumentException.class,
          () -> new ItemPrompt("item", "i1", "Select item", null, null, null, "", true));
      assertThrows(
          IllegalArgumentException.class,
          () -> new ItemPrompt("item", "i1", "Select item", null, null, null, "   ", true));

      // Control characters rejected
      assertThrows(
          IllegalArgumentException.class,
          () -> new ItemPrompt("item", "i1", "Select item", null, null, null, "click\u0000", true));
      assertThrows(
          IllegalArgumentException.class,
          () -> new ItemPrompt("item", "i1", "Select item", null, null, null, "click\n", true));
      assertThrows(
          IllegalArgumentException.class,
          () -> new ItemPrompt("item", "i1", "Select item", null, null, null, "click\u001F", true));

      // Overlength rejected (> 256 chars)
      String longSound = "minecraft:" + "a".repeat(250);
      assertThrows(
          IllegalArgumentException.class,
          () -> new ItemPrompt("item", "i1", "Select item", null, null, null, longSound, true));
    }

    @Test
    void itemPromptCategoryValidation() {
      // Valid categories
      assertDoesNotThrow(
          () ->
              new ItemPrompt(
                  "item", "i1", "Select item", ItemSource.CATALOG, null, "weapons", null, true));
      assertDoesNotThrow(
          () ->
              new ItemPrompt(
                  "item",
                  "i1",
                  "Select item",
                  ItemSource.CATALOG,
                  null,
                  "rare_ores.tier-1",
                  null,
                  true));

      // Uppercase rejected
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ItemPrompt(
                  "item", "i1", "Select item", ItemSource.CATALOG, null, "WEAPONS", null, true));

      // Spaces rejected
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ItemPrompt(
                  "item",
                  "i1",
                  "Select item",
                  ItemSource.CATALOG,
                  null,
                  "rare minerals",
                  null,
                  true));

      // Control characters rejected
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ItemPrompt(
                  "item", "i1", "Select item", ItemSource.CATALOG, null, "ores\u0000", null, true));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ItemPrompt(
                  "item", "i1", "Select item", ItemSource.CATALOG, null, "ores\n", null, true));

      // Overlength rejected (> 64 chars)
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ItemPrompt(
                  "item",
                  "i1",
                  "Select item",
                  ItemSource.CATALOG,
                  null,
                  "c".repeat(65),
                  null,
                  true));

      // Invalid symbols rejected
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ItemPrompt(
                  "item", "i1", "Select item", ItemSource.CATALOG, null, "weapons!", null, true));
    }

    @Test
    void nonCatalogWithCategoryThrows() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ItemPrompt(
                  "item",
                  "i1",
                  "Select item",
                  ItemSource.INVENTORY,
                  ItemOutputFormat.KEY,
                  "weapons",
                  null,
                  true));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ItemPrompt(
                  "item",
                  "i1",
                  "Select item",
                  ItemSource.HAND,
                  ItemOutputFormat.KEY,
                  "weapons",
                  null,
                  true));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ItemPrompt(
                  "item",
                  "i1",
                  "Select item",
                  ItemSource.ARMOR,
                  ItemOutputFormat.KEY,
                  "weapons",
                  null,
                  true));
    }

    @Test
    void catalogWithSlotOutputThrows() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ItemPrompt(
                  "item",
                  "i1",
                  "Select item",
                  ItemSource.CATALOG,
                  ItemOutputFormat.SLOT,
                  null,
                  null,
                  true));
    }

    @Test
    void invalidTypeThrows() {
      assertThrows(
          IllegalArgumentException.class,
          () -> new ItemPrompt("chat", "i1", "Select item", null, null, null, null, true));
    }

    @Test
    void nullRequiredFieldsThrow() {
      assertThrows(
          NullPointerException.class,
          () -> new ItemPrompt(null, "i1", "Text", null, null, null, null, true));
      assertThrows(
          NullPointerException.class,
          () -> new ItemPrompt("item", null, "Text", null, null, null, null, true));
      assertThrows(
          NullPointerException.class,
          () -> new ItemPrompt("item", "i1", null, null, null, null, null, true));
    }

    @Test
    void timeoutValidation() {
      var valid = new ItemPrompt("item", "i1", "Text", null, null, null, null, true, null, 100);
      assertEquals(100, valid.timeout());

      assertThrows(
          IllegalArgumentException.class,
          () -> new ItemPrompt("item", "i1", "Text", null, null, null, null, true, null, 0));
      assertThrows(
          IllegalArgumentException.class,
          () -> new ItemPrompt("item", "i1", "Text", null, null, null, null, true, null, 3601));
    }
  }

  @Nested
  @DisplayName("InlineTagMapper Tests")
  class InlineTagMapperTests {

    @Test
    void basicItemTagMapping() {
      var tag = new PromptTag("<i:Select an item>", "i", null, "Select an item");
      var def = InlineTagMapper.toPromptDefinition(tag);
      assertInstanceOf(ItemPrompt.class, def);
      ItemPrompt item = (ItemPrompt) def;
      assertTrue(item.id().startsWith(InlineTagMapper.INLINE_ID_PREFIX));
      assertEquals("Select an item", item.promptText());
      assertEquals(ItemSource.INVENTORY, item.source());
      assertEquals(ItemOutputFormat.KEY, item.output());
      assertNull(item.category());
      assertNull(item.sound());
      assertTrue(item.sanitize());
    }

    @Test
    void itemAliasTagMapping() {
      var tag = new PromptTag("<item:Select an item>", "item", null, "Select an item");
      var def = InlineTagMapper.toPromptDefinition(tag);
      assertInstanceOf(ItemPrompt.class, def);
      assertEquals("Select an item", ((ItemPrompt) def).promptText());
    }

    @Test
    void caseInsensitiveItemKeys() {
      assertInstanceOf(
          ItemPrompt.class,
          InlineTagMapper.toPromptDefinition(new PromptTag("<I:val>", "I", null, "val")));
      assertInstanceOf(
          ItemPrompt.class,
          InlineTagMapper.toPromptDefinition(new PromptTag("<ITEM:val>", "ITEM", null, "val")));
    }

    @Test
    void itemTagWithSourceAndOutputFlags() {
      var tag =
          new PromptTag(
              "<i:Pick -source:hand -out:material>", "i", null, "Pick -source:hand -out:material");
      var def = (ItemPrompt) InlineTagMapper.toPromptDefinition(tag);
      assertEquals("Pick", def.promptText());
      assertEquals(ItemSource.HAND, def.source());
      assertEquals(ItemOutputFormat.MATERIAL, def.output());
    }

    @Test
    void itemTagCatalogWithCategoryAndSound() {
      var tag =
          new PromptTag(
              "<i:Pick weapon -source:catalog -cat:swords -sound:minecraft:ui.button.click>",
              "i",
              null,
              "Pick weapon -source:catalog -cat:swords -sound:minecraft:ui.button.click");
      var def = (ItemPrompt) InlineTagMapper.toPromptDefinition(tag);
      assertEquals("Pick weapon", def.promptText());
      assertEquals(ItemSource.CATALOG, def.source());
      assertEquals("swords", def.category());
      assertEquals("minecraft:ui.button.click", def.sound());
    }

    @Test
    void itemTagWithTimeoutAndTitleFlags() {
      var tag =
          new PromptTag(
              "<i:Select -timeout:45 -t:Title:Sub:20>",
              "i",
              null,
              "Select -timeout:45",
              true,
              null,
              PromptTag.AnswerType.NONE,
              java.util.List.of(),
              false,
              new TitleConfig("Title", "Sub", 20),
              45);
      var def = (ItemPrompt) InlineTagMapper.toPromptDefinition(tag);
      assertEquals("Select", def.promptText());
      assertEquals(45, def.timeout());
      assertNotNull(def.titleDisplay());
      assertEquals("Title", def.titleDisplay().main());
      assertEquals("Sub", def.titleDisplay().sub());
      assertEquals(20, def.titleDisplay().ticks());
    }

    @Test
    void itemTagWithConfiguredScreenMapping() {
      var tag = new PromptTag("<pick_item:Choose>", "pick_item", null, "Choose");
      var def = InlineTagMapper.toPromptDefinition(tag, Map.of("pick_item", ScreenType.ITEM));
      assertInstanceOf(ItemPrompt.class, def);
      assertEquals("Choose", ((ItemPrompt) def).promptText());
    }

    @Test
    void itemTagIncompatibleFlagsThrow() {
      var invalidTag =
          new PromptTag(
              "<i:Choose -source:inv -cat:swords>", "i", null, "Choose -source:inv -cat:swords");
      assertThrows(
          IllegalArgumentException.class, () -> InlineTagMapper.toPromptDefinition(invalidTag));

      var invalidSlotTag =
          new PromptTag(
              "<i:Choose -source:catalog -out:slot>",
              "i",
              null,
              "Choose -source:catalog -out:slot");
      assertThrows(
          IllegalArgumentException.class, () -> InlineTagMapper.toPromptDefinition(invalidSlotTag));
    }
  }
}
