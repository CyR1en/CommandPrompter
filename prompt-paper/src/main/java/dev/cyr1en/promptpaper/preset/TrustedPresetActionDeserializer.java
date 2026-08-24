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
 * Gson serializer and deserializer for {@link TrustedPresetAction}.
 */
public class TrustedPresetActionDeserializer
    implements JsonDeserializer<TrustedPresetAction>, JsonSerializer<TrustedPresetAction> {

  private final TemplateSyntax syntax;

  public TrustedPresetActionDeserializer() {
    this(TemplateSyntax.DEFAULT);
  }

  public TrustedPresetActionDeserializer(TemplateSyntax syntax) {
    this.syntax = syntax != null ? syntax : TemplateSyntax.DEFAULT;
  }

  @Override
  public TrustedPresetAction deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (json == null || json.isJsonNull()) {
      return null;
    }

    if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
      String raw = json.getAsString().trim();
      if (raw.isEmpty()) {
        throw new IllegalArgumentException("Action command string cannot be empty");
      }
      ExecuteAs executeAs;
      String commandStr;
      String lower = raw.toLowerCase(Locale.ROOT);
      if (lower.startsWith("console:")) {
        executeAs = ExecuteAs.CONSOLE;
        commandStr = raw.substring("console:".length()).trim();
      } else if (lower.startsWith("player:")) {
        executeAs = ExecuteAs.PLAYER;
        commandStr = raw.substring("player:".length()).trim();
      } else {
        executeAs = ExecuteAs.CONSOLE;
        commandStr = raw;
      }
      if (commandStr.isEmpty()) {
        throw new IllegalArgumentException("Action command cannot be empty after prefix");
      }
      return TrustedPresetAction.of(commandStr, executeAs, 0, syntax);
    }

    if (!json.isJsonObject()) {
      throw new JsonParseException(
          "Expected JSON object or string for TrustedPresetAction, got: " + json);
    }

    JsonObject obj = json.getAsJsonObject();
    if (!obj.has("command") || obj.get("command").isJsonNull()) {
      throw new IllegalArgumentException("Action definition missing required 'command' field");
    }
    String commandSource = obj.get("command").getAsString();

    if (!obj.has("execute_as") || obj.get("execute_as").isJsonNull()) {
      throw new IllegalArgumentException("Action definition missing required 'execute_as' field");
    }
    String executeAsStr = obj.get("execute_as").getAsString();
    ExecuteAs executeAs;
    try {
      executeAs = ExecuteAs.valueOf(executeAsStr.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown execute_as value: " + executeAsStr, e);
    }

    int delayTicks = 0;
    if (obj.has("delay_ticks") && !obj.get("delay_ticks").isJsonNull()) {
      delayTicks = obj.get("delay_ticks").getAsInt();
    }

    return TrustedPresetAction.of(commandSource, executeAs, delayTicks, syntax);
  }

  @Override
  public JsonElement serialize(
      TrustedPresetAction src, Type typeOfSrc, JsonSerializationContext context) {
    if (src == null) return null;
    JsonObject obj = new JsonObject();
    obj.addProperty("command", src.command().source());
    obj.addProperty("execute_as", src.executeAs().name().toLowerCase(Locale.ROOT));
    obj.addProperty("delay_ticks", src.delayTicks());
    return obj;
  }
}
