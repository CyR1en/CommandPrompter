package dev.cyr1en.promptpaper.factory;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.preset.AnvilButton;
import dev.cyr1en.promptpaper.preset.AnvilPrompt;
import dev.cyr1en.promptpaper.preset.CancelBehavior;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.DialogBaseConfig;
import dev.cyr1en.promptpaper.preset.DialogPrompt;
import dev.cyr1en.promptpaper.preset.DialogRow;
import dev.cyr1en.promptpaper.preset.DialogType;
import dev.cyr1en.promptpaper.preset.DialogTypeConfig;
import dev.cyr1en.promptpaper.preset.InputType;
import dev.cyr1en.promptpaper.preset.PlayerUiPrompt;
import dev.cyr1en.promptpaper.preset.PromptDefinition;
import dev.cyr1en.promptpaper.preset.SignPrompt;
import dev.cyr1en.promptpaper.preset.UIButton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converts a legacy inline {@link PromptTag} (extracted from a command string by the regex
 * parser) into an ephemeral {@link PromptDefinition} record, so the same
 * {@link PromptFactory#create(org.bukkit.entity.Player, PromptDefinition,
 * dev.cyr1en.promptpaper.screen.dialog.DialogCompletionContext) factory entry point} can serve
 * both preset and inline prompts.
 *
 * <h2>Why</h2>
 *
 * <p>Before Scope 3, the inline syntax ({@code <a:Why?>}) and the preset syntax
 * ({@code <@my_anvil>}) took completely different code paths: inline tags flowed through
 * {@code ScreenRouter.create(PromptTag)} and preset ids through
 * {@code PresetRegistry.getPrompt(id)} + a hand-rolled screen. The factory pipeline collapses
 * both into a single {@code PromptDefinition} → screen path so the runtime behavior is
 * uniform.
 *
 * <h2>Defaults</h2>
 *
 * <p>The JSON schema has many required fields that the inline syntax does not carry
 * ({@code title}, {@code left_button}, etc.). The mapper injects <b>blank-but-valid</b>
 * defaults so the resulting record satisfies its canonical constructor. The screens
 * themselves still pull the cosmetic configuration (button icons, sign material, etc.) from
 * the legacy YAML config — that wiring is unchanged.
 *
 * <h2>Dialog</h2>
 *
 * <p>Dialogs are mapped too, but with a known limitation: per-row {@code constraints} from
 * the JSON schema are <b>not</b> expressible in a single- or compound-{@link PromptTag}, so
 * the mapper produces a {@link DialogPrompt} whose {@code base.inputs} carry the
 * {@link InputType} but no constraints. For inline dialogs this is irrelevant (the
 * constraints live in the YAML dialog config); for preset dialogs the factory will reject
 * them with a clear {@link UnsupportedOperationException} until the full dialog refactor
 * lands.
 */
public final class InlineTagMapper {

  /** Prefix used for transient ids assigned to inline prompts. */
  public static final String INLINE_ID_PREFIX = "inline-";

  private InlineTagMapper() {}

  /**
   * Generates a fresh transient id of the form {@code inline-<uuid>}. Public so the factory
   * and tests can use the same id shape.
   */
  public static String nextInlineId() {
    return INLINE_ID_PREFIX + UUID.randomUUID();
  }

  /**
   * Maps a {@link PromptTag} to the appropriate {@link PromptDefinition} subtype based on
   * the tag's {@code key}.
   *
   * <table>
   *   <caption>Key → type mapping</caption>
   *   <tr><th>key</th><th>PromptDefinition</th></tr>
   *   <tr><td>{@code ""}</td><td>{@link ChatPrompt}</td></tr>
   *   <tr><td>{@code "a"}</td><td>{@link AnvilPrompt}</td></tr>
   *   <tr><td>{@code "s"}</td><td>{@link SignPrompt}</td></tr>
   *   <tr><td>{@code "p"}</td><td>{@link PlayerUiPrompt}</td></tr>
   *   <tr><td>{@code "d"}</td><td>{@link DialogPrompt}</td></tr>
   *   <tr><td>other</td><td>{@link ChatPrompt} (fallback)</td></tr>
   * </table>
   *
   * @param tag the parsed inline tag
   * @return a non-null {@link PromptDefinition} with a fresh {@code inline-*} id
   */
  public static PromptDefinition toPromptDefinition(PromptTag tag) {
    if (tag == null) throw new IllegalArgumentException("tag must not be null");
    var id = nextInlineId();
    var text = tag.displayText() == null ? "" : tag.displayText();
    var sanitize = tag.sanitize();
    var title = resolveTitle(tag);
    return switch (tag.key()) {
      case "" -> new ChatPrompt("chat", id, text, defaultCancel(), sanitize, title);
      case "a" -> new AnvilPrompt("anvil", id, defaultAnvilTitle(), text,
          defaultAnvilButton(), defaultAnvilButton(), sanitize, title);
      case "s" -> new SignPrompt("sign", id, text, defaultSignLines(), sanitize, title);
      case "p" -> new PlayerUiPrompt("player_ui", id, text, tag.filter(),
          null, null, null, sanitize, title);
      case "d" -> toDialogPrompt(tag, id, sanitize, title);
      default -> new ChatPrompt("chat", id, text, defaultCancel(), sanitize, title);
    };
  }

  /**
   * Resolves the title-wrapper config for an inline tag.
   *
   * <p>If the tag has no {@code -t} flag, returns {@code null}. If the flag is the standalone
   * {@code -t} (no parameters), the {@code main} field is empty — the caller (factory) will
   * inject the prompt's display text as the main title text when building the wrapper screen.
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

  private static String defaultAnvilTitle() {
    return "Anvil";
  }

  private static AnvilButton defaultAnvilButton() {
    return new AnvilButton(true, "", "PAPER", "", 0);
  }

  private static List<String> defaultSignLines() {
    return List.of();
  }

  private static UIButton defaultUIButton() {
    return new UIButton(true, 0, "", "PAPER", "", 0);
  }

  /**
   * Builds a {@link DialogPrompt} from a (possibly compound) dialog {@link PromptTag}.
   *
   * <p>For a single-row tag ({@code <d:text:Label>}) the dialog has one row whose
   * {@code inputType} is parsed from the tag's {@code filter} segment. For a compound tag
   * ({@code <d:choice[…] && d:num[…] …>}) each sub-tag becomes one row. The tag's
   * {@code filter} starting with {@code "tab"} switches the resulting dialog into
   * {@link DialogType#MULTI_ACTION} mode; everything else falls back to a
   * {@link DialogType#CONFIRMATION} layout per the spec's Rule 1 / Rule 2 mapping.
   *
   * <p>{@code constraints} are not preserved: the JSON schema stores them as a separate
   * field that has no analog in a {@link PromptTag}.
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
    var dialogTitle = tag.displayText() == null || tag.displayText().isBlank()
        ? "Dialog"
        : tag.displayText();

    var base = new DialogBaseConfig(List.of(), rows);
    var dialogType = isTabFilter(tag)
        ? new DialogTypeConfig(DialogType.MULTI_ACTION, 2, null, null, null, null, null)
        : new DialogTypeConfig(DialogType.CONFIRMATION, null, null, null, null, null, null);
    return new DialogPrompt("dialog", id, dialogTitle, base, dialogType, sanitize, titleConfig);
  }

  private static boolean isTabFilter(PromptTag tag) {
    if (tag == null || tag.filter() == null) return false;
    return tag.filter().toLowerCase().startsWith("tab");
  }

  private static InputType parseInputType(String filter) {
    if (filter == null) return InputType.TEXT;
    var f = filter.toLowerCase();
    if (f.startsWith("num")) return InputType.NUMBER;
    if (f.startsWith("choice")) return InputType.CHOICE;
    return InputType.TEXT;
  }
}
