package dev.cyr1en.promptpaper.preset;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.Locale;

/** Gson serializer and deserializer for {@link SelfApprovalPolicy}. */
public class SelfApprovalPolicyDeserializer
    implements JsonDeserializer<SelfApprovalPolicy>, JsonSerializer<SelfApprovalPolicy> {

  @Override
  public SelfApprovalPolicy deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (json == null || json.isJsonNull()) {
      return SelfApprovalPolicy.AUTO_APPROVE;
    }
    if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("SelfApprovalPolicy must be a string");
    }
    return SelfApprovalPolicy.fromString(json.getAsString());
  }

  @Override
  public JsonElement serialize(
      SelfApprovalPolicy src, Type typeOfSrc, JsonSerializationContext context) {
    if (src == null) {
      return new JsonPrimitive(SelfApprovalPolicy.AUTO_APPROVE.name().toLowerCase(Locale.ROOT));
    }
    return new JsonPrimitive(src.name().toLowerCase(Locale.ROOT));
  }
}
