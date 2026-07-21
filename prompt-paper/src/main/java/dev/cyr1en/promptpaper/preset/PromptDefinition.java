package dev.cyr1en.promptpaper.preset;

import dev.cyr1en.promptcore.TitleConfig;

/**
 * Common interface for every prompt definition loaded from {@code presets.json}.
 *
 * <p>All prompt variants share three properties: a discriminator {@code type}, a unique {@code id}
 * (used by {@code <@id>} tag resolution), and a {@code sanitize} flag (whether the player's input
 * should be stripped of color codes and formatting symbols).
 *
 * <p>The hierarchy is {@code sealed} so the deserializer and any future pattern-matching code can
 * enumerate every variant exhaustively. To add a new prompt kind, add a new {@code permits} target
 * and a corresponding branch in {@link PromptDefinitionDeserializer}.
 */
public sealed interface PromptDefinition
    permits ChatPrompt, AnvilPrompt, PlayerUiPrompt, SignPrompt, DialogPrompt {

  /** The discriminator value as it appears in the JSON {@code type} field. */
  String type();

  /** The unique identifier referenced by {@code <@id>} tags in command strings. */
  String id();

  /**
   * Whether color codes and formatting symbols should be stripped from the player's input before it
   * is substituted into the final command.
   */
  boolean sanitize();

  /**
   * Optional title-wrapper configuration. When present (non-null), the prompt screen is wrapped in
   * a {@code TitleWrapperScreen} that shows an Adventure API title for {@code stay} ticks before
   * opening the underlying prompt. {@code null} when no title wrapper is requested.
   */
  TitleConfig titleDisplay();
}

