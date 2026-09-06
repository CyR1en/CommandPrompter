package dev.cyr1en.promptpaper.preset;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.cyr1en.promptcore.logic.condition.Condition;
import dev.cyr1en.promptcore.logic.condition.ConditionCompileOptions;
import dev.cyr1en.promptcore.logic.condition.ConditionCompiler;
import java.lang.reflect.Type;
import java.util.Locale;

/** Gson serializer and deserializer for {@link ConditionalPostCommandDefinition}. */
public class ConditionalPostCommandDefinitionDeserializer
    implements JsonDeserializer<ConditionalPostCommandDefinition>,
        JsonSerializer<ConditionalPostCommandDefinition> {

  @Override
  public ConditionalPostCommandDefinition deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (json == null || json.isJsonNull()) {
      return null;
    }
    if (!json.isJsonObject()) {
      throw new JsonParseException(
          "Expected JSON object for ConditionalPostCommandDefinition, got: " + json);
    }

    JsonObject obj = json.getAsJsonObject();

    if (!obj.has("id") || obj.get("id").isJsonNull()) {
      throw new IllegalArgumentException("Conditional post-command missing required 'id' field");
    }
    String id = obj.get("id").getAsString();
    ConditionalPostCommandDefinition.validateId(id);

    if (!obj.has("condition") || obj.get("condition").isJsonNull()) {
      throw new IllegalArgumentException(
          "Conditional post-command '" + id + "' missing required 'condition' field");
    }
    String conditionStr = obj.get("condition").getAsString();
    Condition compiledCondition =
        ConditionCompiler.compile(conditionStr, ConditionCompileOptions.forPreset());

    if (!obj.has("execution_policy") || obj.get("execution_policy").isJsonNull()) {
      throw new IllegalArgumentException(
          "Conditional post-command '" + id + "' missing required 'execution_policy' field");
    }
    String policyStr = obj.get("execution_policy").getAsString();
    ExecutionPolicy executionPolicy;
    try {
      executionPolicy = ExecutionPolicy.valueOf(policyStr.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown execution_policy value: " + policyStr, e);
    }

    TrustedPresetAction ifTrueAction = null;
    if (obj.has("if_true") && !obj.get("if_true").isJsonNull()) {
      ifTrueAction = context.deserialize(obj.get("if_true"), TrustedPresetAction.class);
    } else if (obj.has("then") && !obj.get("then").isJsonNull()) {
      ifTrueAction = context.deserialize(obj.get("then"), TrustedPresetAction.class);
    }

    TrustedPresetAction ifFalseAction = null;
    if (obj.has("if_false") && !obj.get("if_false").isJsonNull()) {
      ifFalseAction = context.deserialize(obj.get("if_false"), TrustedPresetAction.class);
    } else if (obj.has("else") && !obj.get("else").isJsonNull()) {
      ifFalseAction = context.deserialize(obj.get("else"), TrustedPresetAction.class);
    }

    return new ConditionalPostCommandDefinition(
        id, compiledCondition, executionPolicy, ifTrueAction, ifFalseAction);
  }

  @Override
  public JsonElement serialize(
      ConditionalPostCommandDefinition src, Type typeOfSrc, JsonSerializationContext context) {
    if (src == null) return null;
    JsonObject obj = new JsonObject();
    obj.addProperty("id", src.id());
    obj.addProperty("condition", src.condition().source());
    obj.addProperty("execution_policy", src.executionPolicy().name().toLowerCase(Locale.ROOT));
    if (src.ifTrueAction() != null) {
      obj.add("if_true", context.serialize(src.ifTrueAction(), TrustedPresetAction.class));
    }
    if (src.ifFalseAction() != null) {
      obj.add("if_false", context.serialize(src.ifFalseAction(), TrustedPresetAction.class));
    }
    return obj;
  }
}
