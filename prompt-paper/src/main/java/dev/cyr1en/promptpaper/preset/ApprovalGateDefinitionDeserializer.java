package dev.cyr1en.promptpaper.preset;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import java.lang.reflect.Type;
import java.util.Locale;

/**
 * Gson serializer and deserializer for {@link ApprovalGateDefinition}.
 */
public class ApprovalGateDefinitionDeserializer
    implements JsonDeserializer<ApprovalGateDefinition>, JsonSerializer<ApprovalGateDefinition> {

  private final TemplateSyntax syntax;

  public ApprovalGateDefinitionDeserializer() {
    this(TemplateSyntax.DEFAULT);
  }

  public ApprovalGateDefinitionDeserializer(TemplateSyntax syntax) {
    this.syntax = syntax != null ? syntax : TemplateSyntax.DEFAULT;
  }

  @Override
  public ApprovalGateDefinition deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (json == null || json.isJsonNull()) {
      return null;
    }
    if (!json.isJsonObject()) {
      throw new JsonParseException("Expected JSON object for ApprovalGateDefinition, got: " + json);
    }

    JsonObject obj = json.getAsJsonObject();

    if (!obj.has("id") || obj.get("id").isJsonNull()) {
      throw new IllegalArgumentException("Approval gate missing required 'id' field");
    }
    String id = obj.get("id").getAsString();
    ApprovalGateDefinition.validateId(id);

    if (!obj.has("target") || obj.get("target").isJsonNull()) {
      throw new IllegalArgumentException(
          "Approval gate '" + id + "' missing required 'target' field");
    }
    String targetStr = obj.get("target").getAsString();

    if (!obj.has("message") || obj.get("message").isJsonNull()) {
      throw new IllegalArgumentException(
          "Approval gate '" + id + "' missing required 'message' field");
    }
    String messageStr = obj.get("message").getAsString();

    int timeout = ApprovalGateDefinition.DEFAULT_TIMEOUT;
    if (obj.has("timeout") && !obj.get("timeout").isJsonNull()) {
      timeout = obj.get("timeout").getAsInt();
    }

    SelfApprovalPolicy policy = SelfApprovalPolicy.AUTO_APPROVE;
    if (obj.has("self_approval_policy") && !obj.get("self_approval_policy").isJsonNull()) {
      policy = context.deserialize(obj.get("self_approval_policy"), SelfApprovalPolicy.class);
    }

    TrustedPresetAction onDenyAction = null;
    if (obj.has("on_deny") && !obj.get("on_deny").isJsonNull()) {
      onDenyAction = context.deserialize(obj.get("on_deny"), TrustedPresetAction.class);
    } else if (obj.has("on_deny_action") && !obj.get("on_deny_action").isJsonNull()) {
      onDenyAction = context.deserialize(obj.get("on_deny_action"), TrustedPresetAction.class);
    }

    return ApprovalGateDefinition.of(
        id, targetStr, messageStr, timeout, policy, onDenyAction, syntax);
  }

  @Override
  public JsonElement serialize(
      ApprovalGateDefinition src, Type typeOfSrc, JsonSerializationContext context) {
    if (src == null) return null;
    JsonObject obj = new JsonObject();
    obj.addProperty("id", src.id());
    obj.addProperty("target", src.target().source());
    obj.addProperty("message", src.message().source());
    obj.addProperty("timeout", src.timeout());
    obj.addProperty(
        "self_approval_policy", src.selfApprovalPolicy().name().toLowerCase(Locale.ROOT));
    if (src.onDenyAction() != null) {
      obj.add("on_deny", context.serialize(src.onDenyAction(), TrustedPresetAction.class));
    }
    return obj;
  }
}
