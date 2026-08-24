package dev.cyr1en.promptpaper.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import dev.cyr1en.promptcore.ConfirmationMode;
import dev.cyr1en.promptcore.ItemOutputFormat;
import dev.cyr1en.promptcore.ItemSource;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import java.util.Locale;

/**
 * Factory methods for the Gson instances used to parse {@code presets.json}.
 *
 * <p>The central piece is {@link #presetGson()}, which knows how to deserialize the {@code
 * type}-discriminated {@link PromptDefinition} hierarchy as well as approval gates, conditional
 * post-commands, and trusted preset actions.
 */
public final class PresetGson {

  private PresetGson() {}

  /**
   * Build a {@link Gson} instance configured with deserializers for all preset definitions
   * using default template syntax.
   */
  public static Gson presetGson() {
    return presetGson(TemplateSyntax.DEFAULT);
  }

  /**
   * Build a {@link Gson} instance configured with deserializers for all preset definitions
   * using the specified template syntax.
   */
  public static Gson presetGson(TemplateSyntax syntax) {
    TemplateSyntax effectiveSyntax = syntax != null ? syntax : TemplateSyntax.DEFAULT;
    return new GsonBuilder()
        .registerTypeAdapter(PromptDefinition.class, new PromptDefinitionDeserializer())
        .registerTypeAdapter(
            ConfirmationMode.class,
            (JsonDeserializer<ConfirmationMode>)
                (json, typeOfT, context) -> {
                  if (json == null || json.isJsonNull()) return ConfirmationMode.GUI;
                  if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Confirmation mode must be a string");
                  }
                  try {
                    return ConfirmationMode.valueOf(
                        json.getAsString().toUpperCase(Locale.ROOT));
                  } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Unknown confirmation mode: " + json.getAsString(), e);
                  }
                })
        .registerTypeAdapter(
            ConfirmationMode.class,
            (JsonSerializer<ConfirmationMode>)
                (src, typeOfSrc, context) ->
                    new JsonPrimitive(src.name().toLowerCase(Locale.ROOT)))
        .registerTypeAdapter(
            ItemSource.class,
            (JsonDeserializer<ItemSource>)
                (json, typeOfT, context) -> {
                  if (json == null || json.isJsonNull()) return ItemSource.INVENTORY;
                  if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Item source must be a string");
                  }
                  return ItemSource.fromAlias(json.getAsString());
                })
        .registerTypeAdapter(
            ItemSource.class,
            (JsonSerializer<ItemSource>)
                (src, typeOfSrc, context) ->
                    new JsonPrimitive(src.name().toLowerCase(Locale.ROOT)))
        .registerTypeAdapter(
            ItemOutputFormat.class,
            (JsonDeserializer<ItemOutputFormat>)
                (json, typeOfT, context) -> {
                  if (json == null || json.isJsonNull()) return ItemOutputFormat.KEY;
                  if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Item output format must be a string");
                  }
                  return ItemOutputFormat.fromAlias(json.getAsString());
                })
        .registerTypeAdapter(
            ItemOutputFormat.class,
            (JsonSerializer<ItemOutputFormat>)
                (src, typeOfSrc, context) ->
                    new JsonPrimitive(src.name().toLowerCase(Locale.ROOT)))
        .registerTypeAdapter(SelfApprovalPolicy.class, new SelfApprovalPolicyDeserializer())
        .registerTypeAdapter(TrustedPresetAction.class, new TrustedPresetActionDeserializer(effectiveSyntax))
        .registerTypeAdapter(
            ApprovalGateDefinition.class, new ApprovalGateDefinitionDeserializer(effectiveSyntax))
        .registerTypeAdapter(
            ConditionalPostCommandDefinition.class,
            new ConditionalPostCommandDefinitionDeserializer())
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
  }
}
