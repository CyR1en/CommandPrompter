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
 *   <li>{@code "confirmation"} → {@link ConfirmationPrompt}
 *   <li>{@code "item"} → {@link ItemPrompt}
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
      case "confirmation" -> deserializeConfirmation(obj, context);
      case "item" -> deserializeItem(obj, context);
      default -> throw new JsonParseException("Unknown PromptDefinition type: " + type);
    };
  }

  /**
   * Deserialize a {@code "confirmation"} prompt after strictly validating the {@code mode}
   * and {@code timeout} fields if present.
   */
  private PromptDefinition deserializeConfirmation(
      JsonObject obj, JsonDeserializationContext context) {
    if (obj.has("mode") && !obj.get("mode").isJsonNull()) {
      JsonElement modeEl = obj.get("mode");
      if (!modeEl.isJsonPrimitive() || !modeEl.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Confirmation mode must be a string");
      }
      String modeStr = modeEl.getAsString();
      try {
        dev.cyr1en.promptcore.ConfirmationMode.valueOf(
            modeStr.toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unknown confirmation mode: " + modeStr, e);
      }
    }
    if (obj.has("timeout") && !obj.get("timeout").isJsonNull()) {
      JsonElement timeoutEl = obj.get("timeout");
      if (!timeoutEl.isJsonPrimitive() || !timeoutEl.getAsJsonPrimitive().isNumber()) {
        throw new IllegalArgumentException("Confirmation timeout must be an integer");
      }
      try {
        int timeoutVal = timeoutEl.getAsInt();
        if (timeoutVal < 1 || timeoutVal > 3600) {
          throw new IllegalArgumentException(
              "Confirmation timeout out of range [1, 3600]: " + timeoutVal);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Confirmation timeout must be an integer", e);
      }
    }
    return context.deserialize(obj, ConfirmationPrompt.class);
  }

  /**
   * Deserialize an {@code "item"} prompt after strictly validating the {@code source},
   * {@code output}/{@code output_format}, {@code timeout}, and source/output/category compatibility.
   */
  private PromptDefinition deserializeItem(JsonObject obj, JsonDeserializationContext context) {
    dev.cyr1en.promptcore.ItemSource source = dev.cyr1en.promptcore.ItemSource.INVENTORY;
    if (obj.has("source") && !obj.get("source").isJsonNull()) {
      JsonElement sourceEl = obj.get("source");
      if (!sourceEl.isJsonPrimitive() || !sourceEl.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Item source must be a string");
      }
      String sourceStr = sourceEl.getAsString();
      try {
        source = dev.cyr1en.promptcore.ItemSource.fromAlias(sourceStr);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unknown item source: " + sourceStr, e);
      }
    }

    dev.cyr1en.promptcore.ItemOutputFormat output = dev.cyr1en.promptcore.ItemOutputFormat.KEY;
    if (obj.has("output") && !obj.get("output").isJsonNull()) {
      JsonElement outEl = obj.get("output");
      if (!outEl.isJsonPrimitive() || !outEl.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Item output format must be a string");
      }
      String outStr = outEl.getAsString();
      try {
        output = dev.cyr1en.promptcore.ItemOutputFormat.fromAlias(outStr);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unknown item output format: " + outStr, e);
      }
    } else if (obj.has("output_format") && !obj.get("output_format").isJsonNull()) {
      JsonElement outEl = obj.get("output_format");
      if (!outEl.isJsonPrimitive() || !outEl.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Item output format must be a string");
      }
      String outStr = outEl.getAsString();
      try {
        output = dev.cyr1en.promptcore.ItemOutputFormat.fromAlias(outStr);
        obj.addProperty("output", outStr);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unknown item output format: " + outStr, e);
      }
    }

    if (obj.has("sound_key") && !obj.has("sound") && !obj.get("sound_key").isJsonNull()) {
      obj.add("sound", obj.get("sound_key"));
    }

    if (obj.has("sound") && !obj.get("sound").isJsonNull()) {
      JsonElement soundEl = obj.get("sound");
      if (!soundEl.isJsonPrimitive() || !soundEl.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Item sound must be a string");
      }
      String soundStr = soundEl.getAsString();
      dev.cyr1en.promptcore.ItemGrammar.validateSoundKey(soundStr);
    }

    if (obj.has("timeout") && !obj.get("timeout").isJsonNull()) {
      JsonElement timeoutEl = obj.get("timeout");
      if (!timeoutEl.isJsonPrimitive() || !timeoutEl.getAsJsonPrimitive().isNumber()) {
        throw new IllegalArgumentException("Item prompt timeout must be an integer");
      }
      try {
        int timeoutVal = timeoutEl.getAsInt();
        if (timeoutVal < 1 || timeoutVal > 3600) {
          throw new IllegalArgumentException(
              "Item prompt timeout out of range [1, 3600]: " + timeoutVal);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Item prompt timeout must be an integer", e);
      }
    }

    String category = null;
    if (obj.has("category") && !obj.get("category").isJsonNull()) {
      JsonElement catEl = obj.get("category");
      if (!catEl.isJsonPrimitive() || !catEl.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Item category must be a string");
      }
      category = catEl.getAsString();
      dev.cyr1en.promptcore.ItemGrammar.validateCategory(category);
    }

    if (source == dev.cyr1en.promptcore.ItemSource.CATALOG) {
      if (output == dev.cyr1en.promptcore.ItemOutputFormat.SLOT) {
        throw new IllegalArgumentException(
            "Output format 'slot' is not supported for catalog source");
      }
    } else {
      if (category != null) {
        throw new IllegalArgumentException(
            "Category filter is only valid for catalog source, but source was: " + source);
      }
    }

    return context.deserialize(obj, ItemPrompt.class);
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
