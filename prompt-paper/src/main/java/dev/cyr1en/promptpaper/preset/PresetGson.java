package dev.cyr1en.promptpaper.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Factory methods for the Gson instances used to parse {@code presets.json}.
 *
 * <p>The central piece is {@link #presetGson()}, which knows how to deserialize the {@code
 * type}-discriminated {@link PromptDefinition} hierarchy.
 */
public final class PresetGson {

  private PresetGson() {}

  /**
   * Build a {@link Gson} instance configured with the {@link PromptDefinitionDeserializer} for
   * deserializing preset prompt objects.
   */
  public static Gson presetGson() {
    return new GsonBuilder()
        .registerTypeAdapter(PromptDefinition.class, new PromptDefinitionDeserializer())
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
  }
}
