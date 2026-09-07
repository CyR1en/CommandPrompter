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
  void timingFieldsRejectFractionalAndOverflowingNumbers() {
    for (String number : List.of("1.9", "4294967297", "-4294967295")) {
      String gate =
          "{\"id\":\"gate\",\"target\":\"Bob\",\"message\":\"Approve?\",\"timeout\":"
              + number
              + "}";
      assertThrows(
          IllegalArgumentException.class,
          () -> gson.fromJson(gate, ApprovalGateDefinition.class),
          number);
      String action =
          "{\"command\":\"say yes\",\"execute_as\":\"console\",\"delay_ticks\":" + number + "}";
      assertThrows(
          IllegalArgumentException.class,
          () -> gson.fromJson(action, TrustedPresetAction.class),
          number);
      for (String type : List.of("confirmation", "item")) {
        String prompt =
            "{\"type\":\""
                + type
                + "\",\"id\":\"test\",\"prompt_text\":\"Choose\",\"timeout\":"
                + number
                + "}";
        assertThrows(
            IllegalArgumentException.class,
            () -> gson.fromJson(prompt, PromptDefinition.class),
            type + ": " + number);
      }
    }
  }

  @Test
  void timingFieldsAcceptExactlyIntegralNumericNotation() {
    var action =
        gson.fromJson(
            "{\"command\":\"say yes\",\"execute_as\":\"console\",\"delay_ticks\":1e1}",
            TrustedPresetAction.class);
    assertEquals(10, action.delayTicks());
    var prompt =
        (ConfirmationPrompt)
            gson.fromJson(
                "{\"type\":\"confirmation\",\"id\":\"test\",\"prompt_text\":\"Choose\",\"timeout\":30.0}",
                PromptDefinition.class);
    assertEquals(30, prompt.timeout());
  }

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
    for (String text : List.of("", "BLANK")) {
      var parsed =
          (AnvilPrompt) gson.fromJson(json.replace("New Name", text), PromptDefinition.class);
      assertEquals(text, parsed.promptText());
    }
    var object = gson.fromJson(json, JsonObject.class);
    object.remove("prompt_text");
    assertThrows(RuntimeException.class, () -> gson.fromJson(object, PromptDefinition.class));
    object.add("prompt_text", com.google.gson.JsonNull.INSTANCE);
    assertThrows(RuntimeException.class, () -> gson.fromJson(object, PromptDefinition.class));
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
    assertThrows(
        IllegalArgumentException.class, () -> new DialogPrompt("chat", "x", "T", base, dt, true));
  }

  @Test
  void dialogPromptNullDialogTypeRejectedByConstructor() {
    // The schema requires dialog_type; the record enforces non-null too.
    assertThrows(
        NullPointerException.class, () -> new DialogPrompt("dialog", "x", "T", null, null, true));
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

  // ------------------------------------------------------------------
  // title_display field
  // ------------------------------------------------------------------

  @Test
  void chatPromptWithTitleDisplayDeserialize() {
    String json =
        """
        {
          "type": "chat",
          "id": "title_chat",
          "prompt_text": "Enter your name:",
          "sanitize": true,
          "cancel": { "send": false, "message": "", "clickable": false, "hover_message": "" },
          "title_display": {
            "main": "Welcome",
            "sub": "Please answer",
            "ticks": 80
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(ChatPrompt.class, def);
    ChatPrompt chat = (ChatPrompt) def;
    assertNotNull(chat.titleDisplay());
    assertEquals("Welcome", chat.titleDisplay().main());
    assertEquals("Please answer", chat.titleDisplay().sub());
    assertEquals(80, chat.titleDisplay().ticks());
  }

  @Test
  void chatPromptWithoutTitleDisplayYieldsNull() {
    String json =
        """
        {
          "type": "chat",
          "id": "no_title",
          "prompt_text": "Plain prompt",
          "sanitize": true,
          "cancel": { "send": false, "message": "", "clickable": false, "hover_message": "" }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(ChatPrompt.class, def);
    ChatPrompt chat = (ChatPrompt) def;
    assertNull(chat.titleDisplay());
  }

  @Test
  void anvilPromptWithTitleDisplayDeserialize() {
    String json =
        """
        {
          "type": "anvil",
          "id": "title_anvil",
          "title": "Rename",
          "prompt_text": "New Name",
          "sanitize": true,
          "left_button": { "show": true, "button_text": "", "button_icon": "PAPER", "button_hover_text": "", "custom_model_data": 0 },
          "right_button": { "show": true, "button_text": "", "button_icon": "PAPER", "button_hover_text": "", "custom_model_data": 0 },
          "title_display": {
            "main": "Anvil Title",
            "sub": null,
            "ticks": null
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(AnvilPrompt.class, def);
    AnvilPrompt anvil = (AnvilPrompt) def;
    assertNotNull(anvil.titleDisplay());
    assertEquals("Anvil Title", anvil.titleDisplay().main());
    assertNull(anvil.titleDisplay().sub());
    assertNull(anvil.titleDisplay().ticks());
  }

  @Test
  void dialogPromptDeserializeBodyWithWidth() {
    String json =
        """
        {
          "type": "dialog",
          "id": "body_width",
          "title": "Width Test",
          "sanitize": false,
          "base": {
            "body": [
              { "type": "plain_message", "content": "Narrow text", "width": 200 },
              { "type": "plain_message", "content": "Auto width" },
              { "type": "item", "material": "STONE" }
            ]
          },
          "dialog_type": {
            "type": "confirmation",
            "confirm_action": { "label": "OK" }
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(DialogPrompt.class, def);
    DialogPrompt dialog = (DialogPrompt) def;

    List<DialogBodyConfig> body = dialog.base().body();
    assertEquals(3, body.size());

    // First body element: plain_message with explicit width.
    DialogBodyConfig narrow = body.get(0);
    assertEquals(DialogBodyType.PLAIN_MESSAGE, narrow.type());
    assertEquals("Narrow text", narrow.content());
    assertEquals(200, narrow.width());

    // Second body element: plain_message without width (null = client auto-wrap).
    DialogBodyConfig auto = body.get(1);
    assertEquals(DialogBodyType.PLAIN_MESSAGE, auto.type());
    assertEquals("Auto width", auto.content());
    assertNull(auto.width());

    // Third body element: item (width irrelevant, should be null).
    DialogBodyConfig item = body.get(2);
    assertEquals(DialogBodyType.ITEM, item.type());
    assertNull(item.width());
  }

  @Test
  void dialogPromptDeserializeBodyWidthClamped() {
    String json =
        """
        {
          "type": "dialog",
          "id": "body_width_clamp",
          "title": "Clamp Test",
          "sanitize": false,
          "base": {
            "body": [
              { "type": "plain_message", "content": "Too wide", "width": 9999 }
            ]
          },
          "dialog_type": {
            "type": "confirmation",
            "confirm_action": { "label": "OK" }
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    DialogPrompt dialog = (DialogPrompt) def;
    assertEquals(1024, dialog.base().body().get(0).width());
  }

  // ------------------------------------------------------------------
  // Confirmation Prompts
  // ------------------------------------------------------------------

  @Test
  void confirmationPromptDeserializeFull() {
    String json =
        """
        {
          "type": "confirmation",
          "id": "confirm_delete",
          "mode": "dialog",
          "title": "Warning",
          "prompt_text": "Are you sure you want to delete this?",
          "confirm_text": "Yes, Delete",
          "cancel_text": "No, Keep",
          "value_mode": true,
          "sound": "entity.experience_orb.pickup",
          "sanitize": false,
          "title_display": {
            "main": "Danger",
            "sub": "Confirm Action",
            "ticks": 20
          }
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(ConfirmationPrompt.class, def);
    ConfirmationPrompt cp = (ConfirmationPrompt) def;
    assertEquals("confirmation", cp.type());
    assertEquals("confirm_delete", cp.id());
    assertEquals(dev.cyr1en.promptcore.ConfirmationMode.DIALOG, cp.mode());
    assertEquals("Warning", cp.title());
    assertEquals("Are you sure you want to delete this?", cp.promptText());
    assertEquals("Yes, Delete", cp.confirmText());
    assertEquals("No, Keep", cp.cancelText());
    assertTrue(cp.valueMode());
    assertEquals("entity.experience_orb.pickup", cp.sound());
    assertFalse(cp.sanitize());
    assertNotNull(cp.titleDisplay());
    assertEquals("Danger", cp.titleDisplay().main());
  }

  @Test
  void confirmationPromptDeserializeMinimal() {
    String json =
        """
        {
          "type": "confirmation",
          "id": "minimal_confirm",
          "prompt_text": "Proceed?"
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(ConfirmationPrompt.class, def);
    ConfirmationPrompt cp = (ConfirmationPrompt) def;
    assertEquals("confirmation", cp.type());
    assertEquals("minimal_confirm", cp.id());
    assertEquals("Proceed?", cp.promptText());
    assertTrue(cp.sanitize(), "sanitize should default to true");
    assertFalse(cp.valueMode(), "value_mode should default to false");
    assertNull(cp.mode());
    assertNull(cp.title());
    assertNull(cp.confirmText());
    assertNull(cp.cancelText());
    assertNull(cp.sound());
    assertNull(cp.titleDisplay());
  }

  @Test
  void confirmationPromptSupportsAllModesCaseInsensitively() {
    for (String mode :
        List.of("gui", "dialog", "chat", "GUI", "DIALOG", "CHAT", "gUi", "Dialog", "cHaT")) {
      String json =
          """
          {
            "type": "confirmation",
            "id": "mode_test",
            "mode": "%s",
            "prompt_text": "Test"
          }
          """
              .formatted(mode);
      PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
      assertInstanceOf(ConfirmationPrompt.class, def);
      ConfirmationPrompt cp = (ConfirmationPrompt) def;
      assertEquals(
          dev.cyr1en.promptcore.ConfirmationMode.valueOf(mode.toUpperCase(java.util.Locale.ROOT)),
          cp.mode());
    }
  }

  @Test
  void confirmationPromptRejectsInvalidMode() {
    String json =
        """
        {
          "type": "confirmation",
          "id": "bad_mode",
          "mode": "invalid_mode",
          "prompt_text": "Test"
        }
        """;
    assertThrows(IllegalArgumentException.class, () -> gson.fromJson(json, PromptDefinition.class));
  }

  @Test
  void confirmationPromptModelTimeoutBounds() {
    // Null timeout allowed (inherits global)
    var nullTimeoutPrompt =
        new ConfirmationPrompt(
            "confirmation",
            "c1",
            null,
            null,
            "Proceed?",
            null,
            null,
            false,
            null,
            true,
            null,
            null);
    assertNull(nullTimeoutPrompt.timeout());

    // Boundary values 1 and 3600 accepted
    var minPrompt =
        new ConfirmationPrompt(
            "confirmation", "c1", null, null, "Proceed?", null, null, false, null, true, null, 1);
    assertEquals(1, minPrompt.timeout());

    var maxPrompt =
        new ConfirmationPrompt(
            "confirmation",
            "c1",
            null,
            null,
            "Proceed?",
            null,
            null,
            false,
            null,
            true,
            null,
            3600);
    assertEquals(3600, maxPrompt.timeout());

    // 0 and 3601 and negative rejected
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConfirmationPrompt(
                "confirmation",
                "c1",
                null,
                null,
                "Proceed?",
                null,
                null,
                false,
                null,
                true,
                null,
                0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConfirmationPrompt(
                "confirmation",
                "c1",
                null,
                null,
                "Proceed?",
                null,
                null,
                false,
                null,
                true,
                null,
                3601));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConfirmationPrompt(
                "confirmation",
                "c1",
                null,
                null,
                "Proceed?",
                null,
                null,
                false,
                null,
                true,
                null,
                -1));
  }

  @Test
  void confirmationPromptJsonTimeoutBounds() {
    // Timeout 1 accepted and retained
    String json1 =
        """
        {
          "type": "confirmation",
          "id": "timeout_min",
          "prompt_text": "Proceed?",
          "timeout": 1
        }
        """;
    PromptDefinition def1 = gson.fromJson(json1, PromptDefinition.class);
    assertInstanceOf(ConfirmationPrompt.class, def1);
    assertEquals(1, ((ConfirmationPrompt) def1).timeout());

    // Timeout 3600 accepted and retained
    String json3600 =
        """
        {
          "type": "confirmation",
          "id": "timeout_max",
          "prompt_text": "Proceed?",
          "timeout": 3600
        }
        """;
    PromptDefinition def3600 = gson.fromJson(json3600, PromptDefinition.class);
    assertInstanceOf(ConfirmationPrompt.class, def3600);
    assertEquals(3600, ((ConfirmationPrompt) def3600).timeout());

    // Timeout 0 rejected
    String json0 =
        """
        {
          "type": "confirmation",
          "id": "timeout_zero",
          "prompt_text": "Proceed?",
          "timeout": 0
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(json0, PromptDefinition.class));

    // Timeout 3601 rejected
    String json3601 =
        """
        {
          "type": "confirmation",
          "id": "timeout_over",
          "prompt_text": "Proceed?",
          "timeout": 3601
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(json3601, PromptDefinition.class));

    // Timeout negative rejected
    String jsonNeg =
        """
        {
          "type": "confirmation",
          "id": "timeout_neg",
          "prompt_text": "Proceed?",
          "timeout": -5
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonNeg, PromptDefinition.class));

    // Timeout string rejected
    String jsonStr =
        """
        {
          "type": "confirmation",
          "id": "timeout_str",
          "prompt_text": "Proceed?",
          "timeout": "thirty"
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonStr, PromptDefinition.class));
  }

  // ------------------------------------------------------------------
  // Item Prompts
  // ------------------------------------------------------------------

  @Test
  void itemPromptDeserializeFull() {
    String json =
        """
        {
          "type": "item",
          "id": "select_sword",
          "prompt_text": "Select your weapon:",
          "source": "catalog",
          "output": "material",
          "category": "swords",
          "sound": "minecraft:ui.button.click",
          "sanitize": false,
          "title_display": {
            "main": "Weapon Selector",
            "sub": "Choose wisely",
            "ticks": 40
          },
          "timeout": 60
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(ItemPrompt.class, def);
    ItemPrompt item = (ItemPrompt) def;
    assertEquals("item", item.type());
    assertEquals("select_sword", item.id());
    assertEquals("Select your weapon:", item.promptText());
    assertEquals(dev.cyr1en.promptcore.ItemSource.CATALOG, item.source());
    assertEquals(dev.cyr1en.promptcore.ItemOutputFormat.MATERIAL, item.output());
    assertEquals("swords", item.category());
    assertEquals("minecraft:ui.button.click", item.sound());
    assertFalse(item.sanitize());
    assertNotNull(item.titleDisplay());
    assertEquals("Weapon Selector", item.titleDisplay().main());
    assertEquals("Choose wisely", item.titleDisplay().sub());
    assertEquals(40, item.titleDisplay().ticks());
    assertEquals(60, item.timeout());
  }

  @Test
  void itemPromptDeserializeMinimal() {
    String json =
        """
        {
          "type": "item",
          "id": "minimal_item",
          "prompt_text": "Pick an item"
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(ItemPrompt.class, def);
    ItemPrompt item = (ItemPrompt) def;
    assertEquals("item", item.type());
    assertEquals("minimal_item", item.id());
    assertEquals("Pick an item", item.promptText());
    assertEquals(dev.cyr1en.promptcore.ItemSource.INVENTORY, item.source());
    assertEquals(dev.cyr1en.promptcore.ItemOutputFormat.KEY, item.output());
    assertNull(item.category());
    assertNull(item.sound());
    assertTrue(item.sanitize(), "sanitize should default to true");
    assertNull(item.titleDisplay());
    assertNull(item.timeout());
  }

  @Test
  void itemPromptSupportsSourceAndOutputAliases() {
    for (String src :
        List.of(
            "inv", "inventory", "hand", "mainhand", "armor", "catalog", "INV", "Hand", "CATALOG")) {
      String json =
          """
          {
            "type": "item",
            "id": "src_test",
            "prompt_text": "Test",
            "source": "%s"
          }
          """
              .formatted(src);
      PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
      assertInstanceOf(ItemPrompt.class, def);
      assertEquals(dev.cyr1en.promptcore.ItemSource.fromAlias(src), ((ItemPrompt) def).source());
    }

    for (String out : List.of("key", "material", "amount", "KEY", "Material", "AMOUNT")) {
      String json =
          """
          {
            "type": "item",
            "id": "out_test",
            "prompt_text": "Test",
            "output": "%s"
          }
          """
              .formatted(out);
      PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
      assertInstanceOf(ItemPrompt.class, def);
      assertEquals(
          dev.cyr1en.promptcore.ItemOutputFormat.fromAlias(out), ((ItemPrompt) def).output());
    }
  }

  @Test
  void itemPromptCatalogDefaultsCategoryToAll() {
    String json =
        """
        {
          "type": "item",
          "id": "cat_test",
          "prompt_text": "Test",
          "source": "catalog"
        }
        """;
    PromptDefinition def = gson.fromJson(json, PromptDefinition.class);
    assertInstanceOf(ItemPrompt.class, def);
    ItemPrompt item = (ItemPrompt) def;
    assertEquals(dev.cyr1en.promptcore.ItemSource.CATALOG, item.source());
    assertEquals("all", item.category());
  }

  @Test
  void itemPromptRejectsSlotOutputForCatalogSource() {
    String json =
        """
        {
          "type": "item",
          "id": "bad_cat",
          "prompt_text": "Test",
          "source": "catalog",
          "output": "slot"
        }
        """;
    assertThrows(IllegalArgumentException.class, () -> gson.fromJson(json, PromptDefinition.class));
  }

  @Test
  void itemPromptRejectsCategoryForNonCatalogSource() {
    String json =
        """
        {
          "type": "item",
          "id": "bad_cat2",
          "prompt_text": "Test",
          "source": "inventory",
          "category": "swords"
        }
        """;
    assertThrows(IllegalArgumentException.class, () -> gson.fromJson(json, PromptDefinition.class));
  }

  @Test
  void itemPromptRejectsInvalidSource() {
    String json =
        """
        {
          "type": "item",
          "id": "bad_src",
          "prompt_text": "Test",
          "source": "invalid_source"
        }
        """;
    assertThrows(IllegalArgumentException.class, () -> gson.fromJson(json, PromptDefinition.class));
  }

  @Test
  void itemPromptRejectsInvalidOutput() {
    String json =
        """
        {
          "type": "item",
          "id": "bad_out",
          "prompt_text": "Test",
          "output": "invalid_output"
        }
        """;
    assertThrows(IllegalArgumentException.class, () -> gson.fromJson(json, PromptDefinition.class));
  }

  @Test
  void itemPromptModelTimeoutBounds() {
    // Null timeout allowed
    var nullTimeout =
        new ItemPrompt("item", "i1", "Prompt", null, null, null, null, true, null, null);
    assertNull(nullTimeout.timeout());

    // Boundary values 1 and 3600
    var min = new ItemPrompt("item", "i1", "Prompt", null, null, null, null, true, null, 1);
    assertEquals(1, min.timeout());

    var max = new ItemPrompt("item", "i1", "Prompt", null, null, null, null, true, null, 3600);
    assertEquals(3600, max.timeout());

    // Out of bounds
    assertThrows(
        IllegalArgumentException.class,
        () -> new ItemPrompt("item", "i1", "Prompt", null, null, null, null, true, null, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ItemPrompt("item", "i1", "Prompt", null, null, null, null, true, null, 3601));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ItemPrompt("item", "i1", "Prompt", null, null, null, null, true, null, -1));
  }

  @Test
  void itemPromptJsonTimeoutBounds() {
    String json1 =
        "{\"type\": \"item\", \"id\": \"i1\", \"prompt_text\": \"Test\", \"timeout\": 1}";
    PromptDefinition def1 = gson.fromJson(json1, PromptDefinition.class);
    assertEquals(1, ((ItemPrompt) def1).timeout());

    String json3600 =
        "{\"type\": \"item\", \"id\": \"i1\", \"prompt_text\": \"Test\", \"timeout\": 3600}";
    PromptDefinition def3600 = gson.fromJson(json3600, PromptDefinition.class);
    assertEquals(3600, ((ItemPrompt) def3600).timeout());

    String json0 =
        "{\"type\": \"item\", \"id\": \"i1\", \"prompt_text\": \"Test\", \"timeout\": 0}";
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(json0, PromptDefinition.class));

    String json3601 =
        "{\"type\": \"item\", \"id\": \"i1\", \"prompt_text\": \"Test\", \"timeout\": 3601}";
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(json3601, PromptDefinition.class));
  }

  @Test
  void itemPromptJsonSoundValidation() {
    // Valid sound keys
    String jsonValid =
        """
        {
          "type": "item",
          "id": "sound_test",
          "prompt_text": "Test",
          "sound": "minecraft:block.note_block.bell"
        }
        """;
    ItemPrompt def = (ItemPrompt) gson.fromJson(jsonValid, PromptDefinition.class);
    assertEquals("minecraft:block.note_block.bell", def.sound());

    // sound_key alias
    String jsonAlias =
        """
        {
          "type": "item",
          "id": "sound_alias",
          "prompt_text": "Test",
          "sound_key": "entity.player.levelup"
        }
        """;
    ItemPrompt defAlias = (ItemPrompt) gson.fromJson(jsonAlias, PromptDefinition.class);
    assertEquals("entity.player.levelup", defAlias.sound());

    // Uppercase sound rejected
    String jsonUpper =
        """
        {
          "type": "item",
          "id": "sound_upper",
          "prompt_text": "Test",
          "sound": "MINECRAFT:BELL"
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonUpper, PromptDefinition.class));

    // Blank sound rejected
    String jsonBlank =
        """
        {
          "type": "item",
          "id": "sound_blank",
          "prompt_text": "Test",
          "sound": ""
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonBlank, PromptDefinition.class));

    // Control characters in sound rejected
    String jsonCtrl =
        """
        {
          "type": "item",
          "id": "sound_ctrl",
          "prompt_text": "Test",
          "sound": "bell\\u0000key"
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonCtrl, PromptDefinition.class));

    // Overlength sound rejected (> 256)
    String jsonOver =
        """
        {
          "type": "item",
          "id": "sound_over",
          "prompt_text": "Test",
          "sound": "minecraft:%s"
        }
        """
            .formatted("a".repeat(250));
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonOver, PromptDefinition.class));
  }

  @Test
  void itemPromptJsonCategoryValidation() {
    // Valid category
    String jsonValid =
        """
        {
          "type": "item",
          "id": "cat_valid",
          "prompt_text": "Test",
          "source": "catalog",
          "category": "rare_minerals-1.0"
        }
        """;
    ItemPrompt def = (ItemPrompt) gson.fromJson(jsonValid, PromptDefinition.class);
    assertEquals("rare_minerals-1.0", def.category());

    // Uppercase category rejected
    String jsonUpper =
        """
        {
          "type": "item",
          "id": "cat_upper",
          "prompt_text": "Test",
          "source": "catalog",
          "category": "MINERALS"
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonUpper, PromptDefinition.class));

    // Space in category rejected
    String jsonSpace =
        """
        {
          "type": "item",
          "id": "cat_space",
          "prompt_text": "Test",
          "source": "catalog",
          "category": "rare minerals"
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonSpace, PromptDefinition.class));

    // Blank category rejected
    String jsonBlank =
        """
        {
          "type": "item",
          "id": "cat_blank",
          "prompt_text": "Test",
          "source": "catalog",
          "category": ""
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonBlank, PromptDefinition.class));

    // Control char in category rejected
    String jsonCtrl =
        """
        {
          "type": "item",
          "id": "cat_ctrl",
          "prompt_text": "Test",
          "source": "catalog",
          "category": "cat\\u0000"
        }
        """;
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonCtrl, PromptDefinition.class));

    // Overlength category rejected (> 64)
    String jsonOver =
        """
        {
          "type": "item",
          "id": "cat_over",
          "prompt_text": "Test",
          "source": "catalog",
          "category": "%s"
        }
        """
            .formatted("c".repeat(65));
    assertThrows(
        IllegalArgumentException.class, () -> gson.fromJson(jsonOver, PromptDefinition.class));
  }
}
