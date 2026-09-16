package dev.cyr1en.promptpaper.factory;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.cyr1en.promptcore.ConfirmationGrammar;
import dev.cyr1en.promptcore.ItemGrammar;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.config.sub.DialogConfig;
import dev.cyr1en.promptpaper.preset.ActionButtonConfig;
import dev.cyr1en.promptpaper.preset.ActionsSource;
import dev.cyr1en.promptpaper.preset.AnvilButton;
import dev.cyr1en.promptpaper.preset.AnvilPrompt;
import dev.cyr1en.promptpaper.preset.CancelBehavior;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.ConfirmationPrompt;
import dev.cyr1en.promptpaper.preset.DialogBaseConfig;
import dev.cyr1en.promptpaper.preset.DialogBodyConfig;
import dev.cyr1en.promptpaper.preset.DialogBodyType;
import dev.cyr1en.promptpaper.preset.DialogPrompt;
import dev.cyr1en.promptpaper.preset.DialogRow;
import dev.cyr1en.promptpaper.preset.DialogType;
import dev.cyr1en.promptpaper.preset.DialogTypeConfig;
import dev.cyr1en.promptpaper.preset.InputType;
import dev.cyr1en.promptpaper.preset.ItemPrompt;
import dev.cyr1en.promptpaper.preset.PlayerUiPrompt;
import dev.cyr1en.promptpaper.preset.PromptBehavior;
import dev.cyr1en.promptpaper.preset.PromptDefinition;
import dev.cyr1en.promptpaper.preset.SignPrompt;
import dev.cyr1en.promptpaper.screen.dialog.DialogConstraints;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps inline tags to transient prompt definitions for {@link PromptFactory}. Required presentation
 * fields absent from inline syntax receive valid defaults; screens resolve cosmetic settings from
 * YAML configuration.
 *
 * <p>Dialog rows preserve their input constraints and layout in the JSON definition.
 */
public final class InlineTagMapper {

  /** Prefix used for transient ids assigned to inline prompts. */
  public static final String INLINE_ID_PREFIX = "inline-";

  private InlineTagMapper() {}

  /**
   * Generates a fresh transient id of the form {@code inline-<uuid>}. Public so the factory and
   * tests can use the same id shape.
   */
  public static String nextInlineId() {
    return INLINE_ID_PREFIX + UUID.randomUUID();
  }

  /**
   * Maps a {@link PromptTag} to the appropriate {@link PromptDefinition} subtype based on the tag's
   * {@code key}.
   *
   * <table>
   *   <caption>Key → type mapping</caption>
   *   <tr><th>key</th><th>PromptDefinition</th></tr>
   *   <tr><td>{@code ""}</td><td>{@link ChatPrompt}</td></tr>
   *   <tr><td>{@code "a"}</td><td>{@link AnvilPrompt}</td></tr>
   *   <tr><td>{@code "s"}</td><td>{@link SignPrompt}</td></tr>
   *   <tr><td>{@code "p"}</td><td>{@link PlayerUiPrompt}</td></tr>
   *   <tr><td>{@code "d"}</td><td>{@link DialogPrompt}</td></tr>
   *   <tr><td>{@code "c"}</td><td>{@link ConfirmationPrompt}</td></tr>
   *   <tr><td>{@code "i"}</td><td>{@link ItemPrompt}</td></tr>
   * </table>
   *
   * @param tag the parsed inline tag
   * @return a non-null {@link PromptDefinition} with a fresh {@code inline-*} id
   */
  public static PromptDefinition toPromptDefinition(PromptTag tag) {
    return toPromptDefinition(tag, Map.of());
  }

  /**
   * Maps a {@link PromptTag} to the appropriate {@link PromptDefinition} subtype using the provided
   * screen mappings.
   *
   * @param tag the parsed inline tag
   * @param mappings configured screen mappings (e.g. from prompt config)
   * @return a non-null {@link PromptDefinition} with a fresh {@code inline-*} id
   */
  public static PromptDefinition toPromptDefinition(
      PromptTag tag, Map<String, ScreenType> mappings) {
    return toPromptDefinition(
        tag, mappings, nextInlineId(), DialogConfig.legacy("Dialog", "Confirm", "", "Cancel", ""));
  }

  /** Converts an inline prompt into a persistable definition with the requested ID. */
  public static PromptDefinition toPromptDefinition(
      PromptTag tag, Map<String, ScreenType> mappings, String id, DialogConfig dialogDefaults) {
    if (tag == null) throw new IllegalArgumentException("tag must not be null");
    if (tag.isPreset())
      throw new IllegalArgumentException("Expected an inline prompt, not a preset reference");
    var behavior = PromptBehavior.fromTag(tag);
    var text = tag.displayText() == null ? "" : tag.displayText();
    var sanitize = tag.sanitize();
    var title = resolveTitle(tag);
    var screenType = resolveScreenType(tag, mappings);
    if (tag.isCompound()) {
      for (var row : tag.subTags()) resolveScreenType(row, mappings);
    }
    return switch (screenType) {
      case CHAT -> new ChatPrompt("chat", id, text, defaultCancel(), sanitize, title, behavior);
      case ANVIL ->
          new AnvilPrompt(
              "anvil",
              id,
              "Anvil",
              text,
              defaultAnvilButton(),
              defaultAnvilButton(),
              sanitize,
              title,
              behavior);
      case SIGN -> new SignPrompt("sign", id, text, List.of(), sanitize, title, behavior);
      case PLAYER ->
          new PlayerUiPrompt(
              "player_ui", id, text, tag.filter(), null, null, null, sanitize, title, behavior);
      case DIALOG -> toDialogPrompt(tag, id, sanitize, title, dialogDefaults);
      case CONFIRMATION -> toConfirmationPrompt(tag, id, sanitize, title);
      case ITEM -> toItemPrompt(tag, id, sanitize, title);
    };
  }

  private static ScreenType resolveScreenType(PromptTag tag, Map<String, ScreenType> mappings) {
    var rawKey = tag.key();
    var key = rawKey == null ? "" : rawKey.trim().toLowerCase(java.util.Locale.ROOT);
    if (mappings != null && mappings.containsKey(key)) {
      var mapped = mappings.get(key);
      if (mapped != null) return mapped;
    }
    var builtIn = ScreenType.fromBuiltInKey(key);
    if (builtIn != null) return builtIn;
    throw new IllegalArgumentException("Unknown or unsupported prompt tag key: " + rawKey);
  }

  /**
   * Resolves the title-wrapper config for an inline tag.
   *
   * <p>If the tag has no {@code -t} flag, returns {@code null}. If the flag is the standalone
   * {@code -t} (no parameters), the prompt's display text supplies the main title.
   *
   * @param tag the parsed inline tag
   * @return a resolved {@link TitleConfig} with a non-empty {@code main}, or {@code null}
   */
  private static TitleConfig resolveTitle(PromptTag tag) {
    var raw = tag.title();
    if (raw == null) return null;
    if (raw.main().isEmpty()) {
      // Standalone -t flag: inject the prompt's display text as the main title.
      var displayText = tag.displayText() == null ? "" : tag.displayText();
      return new TitleConfig(displayText, raw.sub(), raw.ticks());
    }
    return raw;
  }

  // Defaults used to keep records well-formed for canonical constructors.

  private static CancelBehavior defaultCancel() {
    return new CancelBehavior(false, "", false, "");
  }

  private static AnvilButton defaultAnvilButton() {
    return new AnvilButton(true, "", "PAPER", "", 0);
  }

  /** Maps layout rows, input constraints, and tab completion to their JSON fields. */
  private static DialogPrompt toDialogPrompt(
      PromptTag tag, String id, boolean sanitize, TitleConfig titleConfig, DialogConfig defaults) {
    var sourceRows = tag.isCompound() ? tag.subTags() : List.of(tag);
    var rows = new ArrayList<DialogRow>();
    var body = new ArrayList<DialogBodyConfig>();
    String dialogTitle = defaults.title();
    boolean foundTitle = false;
    Integer tabMaxButtons = null;
    boolean tab = false;
    for (var sub : sourceRows) {
      var c = DialogConstraints.from(sub.filter(), defaults);
      var label = sub.displayText();
      switch (c.kind()) {
        case TITLE -> {
          if (!foundTitle) dialogTitle = label;
          foundTitle = true;
        }
        case BODY ->
            body.add(
                c.rawFilter().equalsIgnoreCase("item")
                    ? new DialogBodyConfig(DialogBodyType.ITEM, null, label, 1)
                    : new DialogBodyConfig(
                        DialogBodyType.PLAIN_MESSAGE,
                        label,
                        null,
                        1,
                        c.width() == 0 ? null : c.width()));
        case TEXT ->
            rows.add(
                new DialogRow(
                    label,
                    InputType.TEXT,
                    List.of(),
                    c.maxLength(),
                    c.multilineMaxLines(),
                    c.width()));
        case NUMBER ->
            rows.add(
                new DialogRow(
                    label,
                    InputType.NUMBER,
                    List.of(
                        new JsonPrimitive(c.min()),
                        new JsonPrimitive(c.max()),
                        new JsonPrimitive(c.step()),
                        new JsonPrimitive(c.initial()))));
        case CHOICE ->
            rows.add(
                c.options().isEmpty()
                    ? new DialogRow(label, InputType.TEXT, List.of())
                    : new DialogRow(
                        label,
                        InputType.CHOICE,
                        c.options().stream().<JsonElement>map(JsonPrimitive::new).toList()));
        case TAB -> {
          tab = true;
          tabMaxButtons = c.maxButtons();
          if (!label.isBlank()) dialogTitle = label;
        }
      }
    }
    var confirm =
        new ActionButtonConfig(defaults.confirm().label(), defaults.confirm().tooltip(), null);
    var cancel =
        new ActionButtonConfig(defaults.cancel().label(), defaults.cancel().tooltip(), null);
    var dialogType =
        tab
            ? new DialogTypeConfig(
                DialogType.MULTI_ACTION,
                2,
                null,
                ActionsSource.TAB_COMPLETION,
                cancel,
                null,
                null,
                tabMaxButtons)
            : new DialogTypeConfig(
                DialogType.CONFIRMATION, null, null, null, null, confirm, cancel);
    return new DialogPrompt(
        "dialog",
        id,
        dialogTitle,
        new DialogBaseConfig(body, rows),
        dialogType,
        sanitize,
        titleConfig,
        PromptBehavior.fromTag(tag));
  }

  private static ConfirmationPrompt toConfirmationPrompt(
      PromptTag tag, String id, boolean sanitize, TitleConfig titleConfig) {
    var syntax = ConfirmationGrammar.parse(tag.displayText());
    return new ConfirmationPrompt(
        "confirmation",
        id,
        syntax.mode(),
        null,
        syntax.promptText(),
        syntax.confirmLabel(),
        syntax.cancelLabel(),
        syntax.valueMode(),
        syntax.soundKey(),
        sanitize,
        titleConfig,
        tag.timeout(),
        PromptBehavior.fromTag(tag));
  }

  private static ItemPrompt toItemPrompt(
      PromptTag tag, String id, boolean sanitize, TitleConfig titleConfig) {
    var syntax = ItemGrammar.parse(tag.displayText());
    return new ItemPrompt(
        "item",
        id,
        syntax.promptText(),
        syntax.source(),
        syntax.outputFormat(),
        syntax.category(),
        syntax.soundKey(),
        sanitize,
        titleConfig,
        tag.timeout(),
        PromptBehavior.fromTag(tag));
  }
}
