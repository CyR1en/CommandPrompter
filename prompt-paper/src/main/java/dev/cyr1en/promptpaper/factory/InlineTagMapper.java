package dev.cyr1en.promptpaper.factory;

import dev.cyr1en.promptcore.ConfirmationGrammar;
import dev.cyr1en.promptcore.ItemGrammar;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.preset.AnvilButton;
import dev.cyr1en.promptpaper.preset.AnvilPrompt;
import dev.cyr1en.promptpaper.preset.CancelBehavior;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.ConfirmationPrompt;
import dev.cyr1en.promptpaper.preset.DialogBaseConfig;
import dev.cyr1en.promptpaper.preset.DialogPrompt;
import dev.cyr1en.promptpaper.preset.DialogRow;
import dev.cyr1en.promptpaper.preset.DialogType;
import dev.cyr1en.promptpaper.preset.DialogTypeConfig;
import dev.cyr1en.promptpaper.preset.InputType;
import dev.cyr1en.promptpaper.preset.ItemPrompt;
import dev.cyr1en.promptpaper.preset.PlayerUiPrompt;
import dev.cyr1en.promptpaper.preset.PromptDefinition;
import dev.cyr1en.promptpaper.preset.SignPrompt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps inline tags to transient prompt definitions for {@link PromptFactory}. Required presentation
 * fields absent from inline syntax receive valid defaults; screens resolve cosmetic settings from
 * YAML configuration.
 *
 * <p>Inline dialog rows retain input kinds. Their constraints are resolved from the original tag
 * and YAML configuration by the factory's inline-dialog path.
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
    if (tag == null) throw new IllegalArgumentException("tag must not be null");
    var id = nextInlineId();
    var text = tag.displayText() == null ? "" : tag.displayText();
    var sanitize = tag.sanitize();
    var title = resolveTitle(tag);
    var screenType = resolveScreenType(tag, mappings);
    return switch (screenType) {
      case CHAT -> new ChatPrompt("chat", id, text, defaultCancel(), sanitize, title);
      case ANVIL ->
          new AnvilPrompt(
              "anvil",
              id,
              "Anvil",
              text,
              defaultAnvilButton(),
              defaultAnvilButton(),
              sanitize,
              title);
      case SIGN -> new SignPrompt("sign", id, text, List.of(), sanitize, title);
      case PLAYER ->
          new PlayerUiPrompt(
              "player_ui", id, text, tag.filter(), null, null, null, sanitize, title);
      case DIALOG -> toDialogPrompt(tag, id, sanitize, title);
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

  /**
   * Builds a {@link DialogPrompt} from a (possibly compound) dialog {@link PromptTag}.
   *
   * <p>For a single-row tag ({@code <d:text:Label>}) the dialog has one row whose {@code inputType}
   * is parsed from the tag's {@code filter} segment. For a compound tag ({@code <d:choice[…] &&
   * d:num[…] …>}) each sub-tag becomes one row. The tag's {@code filter} starting with {@code
   * "tab"} switches the resulting dialog into {@link DialogType#MULTI_ACTION} mode; everything else
   * falls back to a {@link DialogType#CONFIRMATION} layout per the spec's Rule 1 / Rule 2 mapping.
   *
   * <p>{@code constraints} are not preserved: the JSON schema stores them as a separate field that
   * has no analog in a {@link PromptTag}.
   */
  private static DialogPrompt toDialogPrompt(
      PromptTag tag, String id, boolean sanitize, TitleConfig titleConfig) {
    var sourceRows = tag.isCompound() ? tag.subTags() : List.of(tag);
    var rows = new ArrayList<DialogRow>(sourceRows.size());
    for (var sub : sourceRows) {
      var inputType = parseInputType(sub.filter());
      var label = sub.displayText() == null ? "" : sub.displayText();
      rows.add(new DialogRow(label, inputType, null));
    }
    var dialogTitle =
        tag.displayText() == null || tag.displayText().isBlank() ? "Dialog" : tag.displayText();

    var base = new DialogBaseConfig(List.of(), rows);
    var dialogType =
        isTabFilter(tag)
            ? new DialogTypeConfig(DialogType.MULTI_ACTION, 2, null, null, null, null, null)
            : new DialogTypeConfig(DialogType.CONFIRMATION, null, null, null, null, null, null);
    return new DialogPrompt("dialog", id, dialogTitle, base, dialogType, sanitize, titleConfig);
  }

  private static boolean isTabFilter(PromptTag tag) {
    if (tag == null || tag.filter() == null) return false;
    return tag.filter().toLowerCase().startsWith("tab");
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
        tag.timeout());
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
        tag.timeout());
  }

  private static InputType parseInputType(String filter) {
    if (filter == null) return InputType.TEXT;
    var f = filter.toLowerCase();
    if (f.startsWith("num")) return InputType.NUMBER;
    if (f.startsWith("choice")) return InputType.CHOICE;
    return InputType.TEXT;
  }
}
