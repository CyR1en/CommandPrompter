package dev.cyr1en.promptpaper.factory;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.parser.CommandLineParser;
import dev.cyr1en.promptpaper.config.sub.DialogConfig;
import dev.cyr1en.promptpaper.preset.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InlinePresetConversionTest {
  private PromptDefinition convert(String source) {
    var tag = new CommandLineParser().parse(source).promptTags().getFirst();
    var definition =
        InlineTagMapper.toPromptDefinition(
            tag,
            Map.of("ask", dev.cyr1en.promptpaper.config.ScreenType.CHAT),
            "saved",
            DialogConfig.legacy("Configured title", "Confirm", "Yes", "Cancel", "No"));
    var gson = PresetGson.presetGson();
    var json = gson.toJsonTree(definition);
    assertFalse(
        json.getAsJsonObject().has("prompt"), "Must persist structured fields, not inline source");
    var loaded = gson.fromJson(json, PromptDefinition.class);
    assertEquals(definition, loaded);
    return loaded;
  }

  @Test
  void everyBuiltInTypeRoundTripsThroughJson() {
    for (var entry :
        Map.of(
                "<Question>",
                "chat",
                "<a:Name>",
                "anvil",
                "<s:Question>",
                "sign",
                "<p:Pick>",
                "player_ui",
                "<d:text:Name>",
                "dialog",
                "<c:Proceed?>",
                "confirmation",
                "<i:Pick item>",
                "item")
            .entrySet()) {
      assertEquals(entry.getValue(), convert(entry.getKey()).type());
    }
  }

  @Test
  void dialogPreservesLayoutAndAllInputConstraints() {
    var dialog =
        assertInstanceOf(
            DialogPrompt.class,
            convert(
                "<d:title:Form && d:body[plain,300]:Instructions && d:choice[set,add]:Mode && d:num[0,24,2,6]:Amount && d:text[80,3,250]:Reason>"));
    assertEquals("Form", dialog.title());
    assertEquals("Instructions", dialog.base().body().getFirst().content());
    assertEquals(300, dialog.base().body().getFirst().width());
    assertEquals(3, dialog.base().inputs().size());
    assertEquals(
        java.util.List.of("set", "add"), dialog.base().inputs().get(0).constraintsAsStrings());
    var range = dialog.base().inputs().get(1).constraints();
    assertEquals(
        java.util.List.of(0f, 24f, 2f, 6f), range.stream().map(v -> v.getAsFloat()).toList());
    var text = dialog.base().inputs().get(2);
    assertEquals(80, text.maxLength());
    assertEquals(3, text.maxLines());
    assertEquals(250, text.width());
  }

  @Test
  void tabCompletionPersistsSourceAndThresholdWithoutConflictingActions() {
    var tab = assertInstanceOf(DialogPrompt.class, convert("<d:tab[12]:Choose>"));
    assertEquals(ActionsSource.TAB_COMPLETION, tab.dialogType().actionsSource());
    assertEquals(12, tab.dialogType().maxButtons());
    assertTrue(tab.base().inputs().isEmpty());
    assertFalse(
        PresetGson.presetGson()
            .toJsonTree(tab)
            .getAsJsonObject()
            .getAsJsonObject("dialog_type")
            .has("actions"));
  }

  @Test
  void executionOptionsAndPlaceholdersSurviveJson() {
    var definition =
        assertInstanceOf(
            ChatPrompt.class,
            convert("<Hello %player_name% -ds -int -iv:whole -timeout:30 -breakIf:{0} == 5>"));
    assertEquals("Hello %player_name%", definition.promptText());
    assertFalse(definition.sanitize());
    var behavior = definition.behavior();
    assertEquals("whole", behavior.validatorAlias());
    assertEquals(PromptTag.AnswerType.INTEGER, behavior.answerType());
    assertEquals(30, behavior.timeout());
    var mapped = convert("<ask:Hello -theme:dark>");
    assertEquals("dark", mapped.behavior().flags().get("theme"));
    assertEquals("{0} == 5", behavior.breakIf());
  }
}
