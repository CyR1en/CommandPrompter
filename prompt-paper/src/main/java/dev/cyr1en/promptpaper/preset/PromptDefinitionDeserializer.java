package dev.cyr1en.promptpaper.preset;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;

/**
 * Gson deserializer that dispatches a {@link PromptDefinition} JSON object to the correct concrete
 * record based on its {@code type} field.
 *
 * <p>Supported {@code type} values:
 *
 * <ul>
 *   <li>{@code "chat"} → {@link ChatPrompt}
 *   <li>{@code "anvil"} → {@link AnvilPrompt}
 *   <li>{@code "player_ui"} → {@link PlayerUiPrompt}
 *   <li>{@code "sign"} → {@link SignPrompt}
 *   <li>{@code "dialog"} → {@link DialogPrompt}
 * </ul>
 *
 * <p>The deserializer also injects the schema-declared default of {@code true} for the
 * <b>sanitize</b> field when it is missing from the JSON.
 *
 * <p>For {@code "dialog"} prompts the deserializer additionally enforces the schema rule
 * that a {@code multi_action} dialog must have exactly one of {@code actions} or
 * {@code actions_source}; violations surface as an {@link IllegalArgumentException} (not a
 * {@link JsonParseException}) per the dialog refactor spec.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * Gson gson = new GsonBuilder()
 *     .registerTypeAdapter(PromptDefinition.class, new PromptDefinitionDeserializer())
 *     .create();
 * }</pre>
 */
public class PromptDefinitionDeserializer implements JsonDeserializer<PromptDefinition> {

  @Override
  public PromptDefinition deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (!json.isJsonObject()) {
      throw new JsonParseException("PromptDefinition must be a JSON object, got: " + json);
    }
    JsonObject obj = json.getAsJsonObject();
    if (!obj.has("type") || obj.get("type").isJsonNull()) {
      throw new JsonParseException("PromptDefinition is missing required 'type' field: " + obj);
    }
    String type = obj.get("type").getAsString();

    // Inject the schema default of true for sanitize if absent.
    if (!obj.has("sanitize")) {
      obj.addProperty("sanitize", true);
    }

    return switch (type) {
      case "chat" -> context.deserialize(obj, ChatPrompt.class);
      case "anvil" -> context.deserialize(obj, AnvilPrompt.class);
      case "player_ui" -> context.deserialize(obj, PlayerUiPrompt.class);
      case "sign" -> context.deserialize(obj, SignPrompt.class);
      case "dialog" -> deserializeDialog(obj, context);
      default -> throw new JsonParseException("Unknown PromptDefinition type: " + type);
    };
  }

  /**
   * Deserialize a {@code "dialog"} prompt after enforcing the {@code multi_action}
   * schema rule.
   *
   * <p>Per the dialog refactor spec, a {@code multi_action} dialog requires exactly one of
   * {@code actions} or {@code actions_source}; both or neither must throw an
   * {@link IllegalArgumentException}. The check is performed against the raw JSON
   * <em>before</em> delegating to Gson so the exception is not wrapped in a
   * {@link JsonParseException}.
   */
  private PromptDefinition deserializeDialog(JsonObject obj, JsonDeserializationContext context) {
    if (obj.has("dialog_type") && obj.get("dialog_type").isJsonObject()) {
      JsonObject dialogTypeObj = obj.getAsJsonObject("dialog_type");
      JsonElement typeEl = dialogTypeObj.get("type");
      if (typeEl != null
          && !typeEl.isJsonNull()
          && "multi_action".equals(typeEl.getAsString())) {
        boolean hasActions = hasNonNullMember(dialogTypeObj, "actions");
        boolean hasActionsSource = hasNonNullMember(dialogTypeObj, "actions_source");
        if (hasActions == hasActionsSource) {
          throw new IllegalArgumentException(
              "DialogPrompt with dialog_type 'multi_action' must have exactly one of "
                  + "'actions' or 'actions_source', got: "
                  + (hasActions ? "both" : "neither"));
        }
      }
    }
    return context.deserialize(obj, DialogPrompt.class);
  }

  /** {@code true} when {@code obj} has a member named {@code name} that is not JSON null. */
  private static boolean hasNonNullMember(JsonObject obj, String name) {
    if (!obj.has(name)) {
      return false;
    }
    JsonElement el = obj.get(name);
    return el != null && !el.isJsonNull();
  }
}
