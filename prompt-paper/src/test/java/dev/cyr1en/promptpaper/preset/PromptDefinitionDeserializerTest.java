package dev.cyr1en.promptpaper.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Direct unit coverage for {@link PromptDefinitionDeserializer} and the default Gson mapping of the
 * {@code PostCommand} record.
 *
 * <p>Dialog tests cover the post-refactor {@code base} + {@code dialog_type} schema defined in
 * {@code docs/superpowers/specs/2026-06-16-dialog-ui-refactor-spec.html}.
 */
class PromptDefinitionDeserializerTest {

  private final Gson gson = PresetGson.presetGson();

  @Test
  void chatPromptDeserialize() {
    String json =
        """
        {
          "type": "chat",
          "id": "reason_prompt",
          "prompt_text": "Please enter a reason:",
          "sanitize": true,
          "cancel": {
            "send": true,
            "message": "Cancelled reason input.",
            "clickable": false,
            "hover_message": "Action aborted"
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(ChatPrompt.class, def);
    ChatPrompt chat = (ChatPrompt) def;
    assertEquals("chat", chat.type());
    assertEquals("reason_prompt", chat.id());
    assertEquals("Please enter a reason:", chat.promptText());
    assertTrue(chat.sanitize());
    assertEquals("Cancelled reason input.", chat.cancel().message());
    assertFalse(chat.cancel().clickable());
  }

  @Test
  void anvilPromptDeserialize() {
    String json =
        """
        {
          "type": "anvil",
          "id": "rename_item_prompt",
          "title": "Rename your item",
          "prompt_text": "New Name",
          "sanitize": false,
          "left_button": {
            "show": true,
            "button_text": "Cancel",
            "button_icon": "BARRIER",
            "button_hover_text": "Click to cancel",
            "custom_model_data": 0
          },
          "right_button": {
            "show": true,
            "button_text": "Confirm",
            "button_icon": "PAPER",
            "button_hover_text": "Click to confirm",
            "custom_model_data": 0
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(AnvilPrompt.class, def);
    AnvilPrompt anvil = (AnvilPrompt) def;
    assertEquals("anvil", anvil.type());
    assertEquals("Rename your item", anvil.title());
    assertFalse(anvil.sanitize());
    assertEquals("BARRIER", anvil.leftButton().buttonIcon());
    assertEquals("PAPER", anvil.rightButton().buttonIcon());
  }

  @Test
  void playerUiPromptDeserialize() {
    String json =
        """
        {
          "type": "player_ui",
          "id": "pick_player",
          "prompt_text": "Choose a target",
          "sanitize": true,
          "filter": "online",
          "cancel_button": {
            "show": true,
            "slot": 0,
            "button_text": "Cancel",
            "button_icon": "BARRIER",
            "button_hover_text": "Cancel",
            "custom_model_data": 0
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(PlayerUiPrompt.class, def);
    PlayerUiPrompt pui = (PlayerUiPrompt) def;
    assertEquals("player_ui", pui.type());
    assertEquals("Choose a target", pui.promptText());
    assertEquals("online", pui.filter());
    assertNotNull(pui.cancelButton());
    assertEquals(0, pui.cancelButton().slot());
  }

  @Test
  void playerUiPromptOmitsOptionalButtons() {
    String json =
        """
        {
          "type": "player_ui",
          "id": "pick_player_min",
          "prompt_text": "Pick one"
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(PlayerUiPrompt.class, def);
    PlayerUiPrompt pui = (PlayerUiPrompt) def;
    // sanitize defaults to true
    assertTrue(pui.sanitize());
    assertEquals(null, pui.filter());
    assertEquals(null, pui.cancelButton());
    assertEquals(null, pui.previousButton());
    assertEquals(null, pui.nextButton());
  }

  @Test
  void signPromptDeserialize() {
    String json =
        """
        {
          "type": "sign",
          "id": "pin_prompt",
          "prompt_text": "Enter your 4 digit PIN:",
          "sanitize": true,
          "default_lines": ["^^^^^^^", "Enter PIN", "=======", ""]
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(SignPrompt.class, def);
    SignPrompt sign = (SignPrompt) def;
    assertEquals("pin_prompt", sign.id());
    assertEquals(4, sign.defaultLines().size());
    assertEquals("Enter PIN", sign.defaultLines().get(1));
  }

  @Test
  void signPromptDefaultLinesCoercedToEmptyWhenAbsent() {
    String json =
        """
        {
          "type": "sign",
          "id": "bare_sign",
          "prompt_text": "Sign it"
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(SignPrompt.class, def);
    SignPrompt sign = (SignPrompt) def;
    assertTrue(sign.defaultLines().isEmpty());
  }

  // ------------------------------------------------------------------
  // Dialog (post-refactor schema: base + dialog_type)
  // ------------------------------------------------------------------

  @Test
  void dialogPromptDeserializeMixedConstraints() {
    String json =
        """
        {
          "type": "dialog",
          "id": "ban_form",
          "title": "Ban Player",
          "sanitize": true,
          "base": {
            "inputs": [
              {
                "label": "Reason",
                "input_type": "choice",
                "constraints": ["Hacking", "Spam", "Toxicity"]
              },
              {
                "label": "Duration (Days)",
                "input_type": "number",
                "constraints": [1, 365]
              }
            ]
          },
          "dialog_type": {
            "type": "confirmation",
            "confirm_action": { "label": "Confirm" },
            "cancel_action": { "label": "Cancel" }
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(DialogPrompt.class, def);
    DialogPrompt dialog = (DialogPrompt) def;
    assertEquals("Ban Player", dialog.title());
    assertTrue(dialog.sanitize());

    // Sanitize default injection still applies.
    assertNotNull(dialog.base());
    List<DialogRow> inputs = dialog.base().inputs();
    assertEquals(2, inputs.size());

    DialogRow reason = inputs.get(0);
    assertEquals("Reason", reason.label());
    assertEquals(InputType.CHOICE, reason.inputType());
    assertEquals(3, reason.constraints().size());
    assertEquals("Hacking", reason.constraintsAsStrings().get(0));

    DialogRow duration = inputs.get(1);
    assertEquals(InputType.NUMBER, duration.inputType());
    assertEquals(2, duration.constraints().size());
    assertEquals("1", duration.constraintsAsStrings().get(0));
    assertEquals("365", duration.constraintsAsStrings().get(1));

    // Dialog type block.
    DialogTypeConfig dt = dialog.dialogType();
    assertEquals(DialogType.CONFIRMATION, dt.type());
    assertNotNull(dt.confirmAction());
    assertEquals("Confirm", dt.confirmAction().label());
    assertNotNull(dt.cancelAction());
    assertEquals("Cancel", dt.cancelAction().label());
  }

  @Test
  void dialogPromptDeserializeBodyAndTooltipAndReturn() {
    String json =
        """
        {
          "type": "dialog",
          "id": "with_body",
          "title": "With body",
          "sanitize": true,
          "base": {
            "body": [
              { "type": "plain_message", "content": "Welcome!" },
              { "type": "item", "material": "DIAMOND", "amount": 3 }
            ]
          },
          "dialog_type": {
            "type": "multi_action",
            "columns": 2,
            "actions": [
              { "label": "A", "tooltip": "A tip", "return": "alpha" },
              { "label": "B" }
            ],
            "exit_action": { "label": "Done" }
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(DialogPrompt.class, def);
    DialogPrompt dialog = (DialogPrompt) def;

    assertNotNull(dialog.base());
    List<DialogBodyConfig> body = dialog.base().body();
    assertEquals(2, body.size());

    DialogBodyConfig msg = body.get(0);
    assertEquals(DialogBodyType.PLAIN_MESSAGE, msg.type());
    assertEquals("Welcome!", msg.content());

    DialogBodyConfig item = body.get(1);
    assertEquals(DialogBodyType.ITEM, item.type());
    assertEquals("DIAMOND", item.material());
    assertEquals(3, item.amount());

    DialogTypeConfig dt = dialog.dialogType();
    assertEquals(DialogType.MULTI_ACTION, dt.type());
    assertEquals(2, dt.columns());

    List<ActionButtonConfig> actions = dt.actions();
    assertEquals(2, actions.size());
    assertEquals("A", actions.get(0).label());
    assertEquals("A tip", actions.get(0).tooltip());
    assertEquals("alpha", actions.get(0).returnValue());
    assertEquals("B", actions.get(1).label());
    assertNull(actions.get(1).tooltip());
    assertNull(actions.get(1).returnValue());

    assertNotNull(dt.exitAction());
    assertEquals("Done", dt.exitAction().label());
  }

  @Test
  void dialogPromptDeserializeMultiActionWithActionsSource() {
    String json =
        """
        {
          "type": "dialog",
          "id": "tab_dialog",
          "title": "Players",
          "sanitize": true,
          "dialog_type": {
            "type": "multi_action",
            "columns": 2,
            "actions_source": "tab_completion",
            "exit_action": { "label": "Cancel" }
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(DialogPrompt.class, def);
    DialogPrompt dialog = (DialogPrompt) def;

    DialogTypeConfig dt = dialog.dialogType();
    assertEquals(DialogType.MULTI_ACTION, dt.type());
    assertEquals(ActionsSource.TAB_COMPLETION, dt.actionsSource());
    // actions is absent in the JSON; the record's canonical constructor coerces null to [].
    assertTrue(dt.actions().isEmpty());
    assertNotNull(dt.exitAction());
  }

  @Test
  void dialogPromptBaseAndInputsCoercedToEmptyWhenAbsent() {
    String json =
        """
        {
          "type": "dialog",
          "id": "minimal",
          "title": "Minimal",
          "sanitize": true,
          "dialog_type": {
            "type": "confirmation",
            "confirm_action": { "label": "OK" }
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(DialogPrompt.class, def);
    DialogPrompt dialog = (DialogPrompt) def;
    // base is optional in the schema; Gson leaves it null when absent.
    assertNull(dialog.base());
    assertNotNull(dialog.dialogType());
  }

  // ------------------------------------------------------------------
  // Validation: multi_action requires exactly one of actions / actions_source
  // ------------------------------------------------------------------

  @Test
  void dialogPromptMultiActionWithBothActionsAndActionsSourceThrows() {
    String json =
        """
        {
          "type": "dialog",
          "id": "bad",
          "title": "Bad",
          "sanitize": true,
          "dialog_type": {
            "type": "multi_action",
            "columns": 2,
            "actions": [{ "label": "A" }],
            "actions_source": "tab_completion"
          }
        }
        """;
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> gson.fromJson(json, PromptDefinition.class));
    assertTrue(ex.getMessage().contains("multi_action"));
    assertTrue(ex.getMessage().contains("both"));
  }

  @Test
  void dialogPromptMultiActionWithNeitherActionsNorActionsSourceThrows() {
    String json =
        """
        {
          "type": "dialog",
          "id": "bad",
          "title": "Bad",
          "sanitize": true,
          "dialog_type": {
            "type": "multi_action",
            "columns": 2
          }
        }
        """;
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> gson.fromJson(json, PromptDefinition.class));
    assertTrue(ex.getMessage().contains("multi_action"));
    assertTrue(ex.getMessage().contains("neither"));
  }

  @Test
  void dialogPromptMultiActionWithExplicitNullBothThrows() {
    // Explicit JSON null for both keys still violates the rule.
    String json =
        """
        {
          "type": "dialog",
          "id": "bad",
          "title": "Bad",
          "sanitize": true,
          "dialog_type": {
            "type": "multi_action",
            "columns": 2,
            "actions": null,
            "actions_source": null
          }
        }
        """;
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> gson.fromJson(json, PromptDefinition.class));
    assertTrue(ex.getMessage().contains("neither"));
  }

  @Test
  void dialogPromptConfirmationDoesNotTriggerMultiActionValidation() {
    // confirmation dialogs don't require the multi_action one-of check.
    String json =
        """
        {
          "type": "dialog",
          "id": "confirm",
          "title": "Are you sure?",
          "sanitize": true,
          "dialog_type": {
            "type": "confirmation",
            "confirm_action": { "label": "Yes" }
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(DialogPrompt.class, def);
    assertEquals(DialogType.CONFIRMATION, ((DialogPrompt) def).dialogType().type());
  }

  // ------------------------------------------------------------------
  // Default sanitize injection
  // ------------------------------------------------------------------

  @Test
  void sanitizeDefaultsToTrueWhenMissing() {
    // Schema says default is true. The deserializer should inject it.
    String json =
        """
        {
          "type": "chat",
          "id": "no_sanitize",
          "prompt_text": "Type something",
          "cancel": {
            "send": false,
            "message": "x",
            "clickable": false,
            "hover_message": "y"
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertTrue(def.sanitize(), "sanitize should default to true when absent");
  }

  // ------------------------------------------------------------------
  // Type dispatch
  // ------------------------------------------------------------------

  @Test
  void unknownTypeFailsFast() {
    String json = "{\"type\": \"nope\", \"id\": \"x\"}";
    assertThrows(JsonParseException.class, () -> gson.fromJson(json, PromptDefinition.class));
  }

  @Test
  void missingTypeFailsFast() {
    String json = "{\"id\": \"x\"}";
    assertThrows(JsonParseException.class, () -> gson.fromJson(json, PromptDefinition.class));
  }

  // ------------------------------------------------------------------
  // PostCommand
  // ------------------------------------------------------------------

  @Test
  void postCommandDeserializeDefaultGson() {
    // PostCommand does not need a custom deserializer; enum @SerializedName covers the wire
    // format. Use the plain Gson (no special registration needed).
    String json =
        """
        {
          "id": "log_reason",
          "command": "discord broadcast {player} punished {input:1} for: {input:2}",
          "execution_policy": "on_complete",
          "execute_as": "console"
        }
        """;
    PostCommand pc = gson.fromJson(json, PostCommand.class);
    assertEquals("log_reason", pc.id());
    assertEquals("discord broadcast {player} punished {input:1} for: {input:2}", pc.command());
    assertEquals(ExecutionPolicy.ON_COMPLETE, pc.executionPolicy());
    assertEquals(ExecuteAs.CONSOLE, pc.executeAs());
    assertEquals(0, pc.delayTicks());
  }

  @Test
  void postCommandDeserializeWithDelay() {
    String json =
        """
        {
          "id": "refund_fee",
          "command": "eco give {player} 100",
          "execution_policy": "on_cancel",
          "execute_as": "console",
          "delay_ticks": 20
        }
        """;
    PostCommand pc = gson.fromJson(json, PostCommand.class);
    assertEquals(ExecutionPolicy.ON_CANCEL, pc.executionPolicy());
    assertEquals(ExecuteAs.CONSOLE, pc.executeAs());
    assertEquals(20, pc.delayTicks());
  }

  @Test
  void postCommandBadEnumThrows() {
    String json =
        """
        {
          "id": "bad",
          "command": "x",
          "execution_policy": "on_maybe",
          "execute_as": "console"
        }
        """;
    // Gson wraps the underlying JsonSyntaxException (a JsonParseException subclass) in a
    // RuntimeException via RecordAdapter when the record canonical constructor rejects nulls.
    // The contract is just "parsing fails on bad input" — the wrapping is acceptable.
    assertThrows(RuntimeException.class, () -> gson.fromJson(json, PostCommand.class));
  }

  @Test
  void postCommandNegativeDelayRejectedByConstructor() {
    // The record's canonical constructor enforces delay_ticks >= 0; bypassing Gson to
    // construct directly must throw.
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostCommand("x", "cmd", ExecutionPolicy.ON_COMPLETE, ExecuteAs.CONSOLE, -1));
  }

  // ------------------------------------------------------------------
  // DialogPrompt constructor contract
  // ------------------------------------------------------------------

  @Test
  void dialogPromptWrongTypeRejectedByConstructor() {
    // The record's canonical constructor enforces type=="dialog".
    var base = new DialogBaseConfig(List.of(), List.of());
    var dt = new DialogTypeConfig(DialogType.CONFIRMATION, null, null, null, null, null, null);
    assertThrows(IllegalArgumentException.class,
        () -> new DialogPrompt("chat", "x", "T", base, dt, true));
  }

  @Test
  void dialogPromptNullDialogTypeRejectedByConstructor() {
    // The schema requires dialog_type; the record enforces non-null too.
    assertThrows(
        NullPointerException.class,
        () -> new DialogPrompt("dialog", "x", "T", null, null, true));
  }

  @Test
  void actionButtonConfigEmptyLabelRejectedByConstructor() {
    // Schema says minLength=1.
    assertThrows(IllegalArgumentException.class, () -> new ActionButtonConfig("", null, null));
  }

  // ------------------------------------------------------------------
  // Round-trip via JsonObject
  // ------------------------------------------------------------------

  @Test
  void deserializerRoundTripViaRawJsonObject() {
    // Build a JsonObject programmatically and feed it through the deserializer directly to make
    // sure the dispatch path also works for in-memory JsonElements.
    JsonObject obj = new JsonObject();
    obj.addProperty("type", "sign");
    obj.addProperty("id", "rt");
    obj.addProperty("prompt_text", "Sign please");
    PromptDefinition def = gson.fromJson(obj, PromptDefinition.class);
    assertInstanceOf(SignPrompt.class, def);
  }
}
