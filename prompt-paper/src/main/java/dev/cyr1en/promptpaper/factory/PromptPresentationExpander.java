package dev.cyr1en.promptpaper.factory;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.TitleConfig;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.hooks.PapiHook;
import dev.cyr1en.promptpaper.preset.ActionButtonConfig;
import dev.cyr1en.promptpaper.preset.AnvilButton;
import dev.cyr1en.promptpaper.preset.AnvilPrompt;
import dev.cyr1en.promptpaper.preset.CancelBehavior;
import dev.cyr1en.promptpaper.preset.ChatPrompt;
import dev.cyr1en.promptpaper.preset.DialogBaseConfig;
import dev.cyr1en.promptpaper.preset.DialogBodyConfig;
import dev.cyr1en.promptpaper.preset.DialogPrompt;
import dev.cyr1en.promptpaper.preset.DialogRow;
import dev.cyr1en.promptpaper.preset.DialogTypeConfig;
import dev.cyr1en.promptpaper.preset.PlayerUiPrompt;
import dev.cyr1en.promptpaper.preset.PromptDefinition;
import dev.cyr1en.promptpaper.preset.SignPrompt;
import dev.cyr1en.promptpaper.preset.UIButton;
import java.util.Objects;
import java.util.function.BiFunction;
import org.bukkit.entity.Player;

/**
 * Immutable presentation-materialization mapper. This is the <b>single</b> boundary at which
 * player-visible prompt text (PlaceholderAPI expansions and the like) is resolved before a screen
 * is constructed, so {@link PromptFactory} never expands the same field twice.
 *
 * <p>The mapper is a pure function of {@code (player, raw model)} → a fresh expanded model: it
 * never mutates its inputs, keeps the registry/session models raw, and leaves every semantic /
 * parser field byte-for-byte untouched.
 *
 * <h2>Expanded exactly once</h2>
 *
 * <ul>
 *   <li>{@code ChatPrompt}: {@code promptText}, {@code cancel.message},
 *       {@code cancel.hoverMessage}.
 *   <li>{@code AnvilPrompt}: {@code title}, {@code promptText}, both {@code AnvilButton}
 *       {@code buttonText} / {@code buttonHoverText}.
 *   <li>{@code SignPrompt}: {@code promptText}, every {@code defaultLines} entry.
 *   <li>{@code PlayerUiPrompt}: {@code promptText}, every {@code UIButton}
 *       {@code buttonText} / {@code buttonHoverText}.
 *   <li>{@code DialogPrompt}: {@code title}, every {@code DialogBodyConfig.content},
 *       every {@code DialogRow.label}, and the {@code label} / {@code tooltip} of every
 *       static / confirm / cancel / exit {@code ActionButtonConfig}.
 *   <li>All variants: {@code titleDisplay.main} / {@code titleDisplay.sub}.
 *   <li>Inline compound {@link PromptTag}: {@code displayText} of every sub-tag (recursively)
 *       and {@code title.main} / {@code title.sub}.
 * </ul>
 *
 * <h2>Never expanded</h2>
 *
 * <p>Preset ids, tag keys, {@code rawTag}, filters, validator aliases, answer types, {@code
 * sanitize}, title ticks, material / icon names, slots, custom-model-data, dialog constraints,
 * action {@code return} values, {@code actions_source}, dimensions, numeric limits, and submitted
 * answers all pass through byte-for-byte unchanged.
 *
 * <p>Null and empty strings are never sent to the expansion delegate: null stays null and an empty
 * {@code titleDisplay.main} keeps its "use the prompt text" fallback meaning for
 * {@link PromptFactory}.
 */
final class PromptPresentationExpander {

  private final BiFunction<Player, String, String> expander;

  /**
   * @param expander the per-field expansion function (e.g. a PAPI hook). Injected so tests can use
   *     a deterministic, non-idempotent function.
   */
  PromptPresentationExpander(BiFunction<Player, String, String> expander) {
    this.expander = Objects.requireNonNull(expander, "expander");
  }

  /**
   * Production delegate: resolves PlaceholderAPI placeholders through the registered
   * {@link PapiHook}, passing text through unchanged when the hook is absent. The hook lookup is
   * deliberately lazy (inside the lambda) because {@code initHooks()} runs after the factory is
   * constructed during plugin enable.
   */
  static PromptPresentationExpander forPlugin(CommandPrompter plugin) {
    return new PromptPresentationExpander((player, text) ->
        plugin.getHookContainer().getHook(PapiHook.class)
            .map(h -> h.setPlaceholder(player, text))
            .orElse(text));
  }

  /** Expands every player-visible presentation field of the definition; semantic fields are raw. */
  PromptDefinition expand(Player player, PromptDefinition raw) {
    Objects.requireNonNull(raw, "raw");
    return switch (raw) {
      case ChatPrompt chat -> expandChat(player, chat);
      case AnvilPrompt anvil -> expandAnvil(player, anvil);
      case SignPrompt sign -> expandSign(player, sign);
      case PlayerUiPrompt pui -> expandPlayerUi(player, pui);
      case DialogPrompt dialog -> expandDialog(player, dialog);
    };
  }

  /**
   * Expands the presentation-only copy of an inline (compound) dialog tag: every sub-tag's
   * {@code displayText} (recursively) plus the {@code -t} title config. All parser/semantic
   * components ({@code rawTag}, {@code key}, {@code filter}, {@code validatorAlias}, {@code type},
   * {@code sanitize}, {@code preset}) are copied through unchanged.
   */
  PromptTag expandInlineDialog(Player player, PromptTag raw) {
    Objects.requireNonNull(raw, "raw");
    var subTags = raw.subTags().stream()
        .map(sub -> expandInlineDialog(player, sub))
        .toList();
    return new PromptTag(
        raw.rawTag(),
        raw.key(),
        raw.filter(),
        expand(player, raw.displayText()),
        raw.sanitize(),
        raw.validatorAlias(),
        raw.type(),
        subTags,
        raw.preset(),
        expandTitle(player, raw.title()));
  }

  // ------------------------------------------------------------------
  // Per-variant expansion
  // ------------------------------------------------------------------

  private ChatPrompt expandChat(Player player, ChatPrompt chat) {
    return new ChatPrompt(
        chat.type(),
        chat.id(),
        expand(player, chat.promptText()),
        expandCancel(player, chat.cancel()),
        chat.sanitize(),
        expandTitle(player, chat.titleDisplay()));
  }

  private CancelBehavior expandCancel(Player player, CancelBehavior cancel) {
    return new CancelBehavior(
        cancel.send(),
        expand(player, cancel.message()),
        cancel.clickable(),
        expand(player, cancel.hoverMessage()));
  }

  private AnvilPrompt expandAnvil(Player player, AnvilPrompt anvil) {
    return new AnvilPrompt(
        anvil.type(),
        anvil.id(),
        expand(player, anvil.title()),
        expand(player, anvil.promptText()),
        expandAnvilButton(player, anvil.leftButton()),
        expandAnvilButton(player, anvil.rightButton()),
        anvil.sanitize(),
        expandTitle(player, anvil.titleDisplay()));
  }

  private AnvilButton expandAnvilButton(Player player, AnvilButton button) {
    if (button == null) return null;
    return new AnvilButton(
        button.show(),
        expand(player, button.buttonText()),
        button.buttonIcon(),
        expand(player, button.buttonHoverText()),
        button.customModelData());
  }

  private SignPrompt expandSign(Player player, SignPrompt sign) {
    return new SignPrompt(
        sign.type(),
        sign.id(),
        expand(player, sign.promptText()),
        sign.defaultLines().stream().map(line -> expand(player, line)).toList(),
        sign.sanitize(),
        expandTitle(player, sign.titleDisplay()));
  }

  private PlayerUiPrompt expandPlayerUi(Player player, PlayerUiPrompt pui) {
    return new PlayerUiPrompt(
        pui.type(),
        pui.id(),
        expand(player, pui.promptText()),
        pui.filter(),
        expandUiButton(player, pui.cancelButton()),
        expandUiButton(player, pui.previousButton()),
        expandUiButton(player, pui.nextButton()),
        pui.sanitize(),
        expandTitle(player, pui.titleDisplay()));
  }

  private UIButton expandUiButton(Player player, UIButton button) {
    if (button == null) return null;
    return new UIButton(
        button.show(),
        button.slot(),
        expand(player, button.buttonText()),
        button.buttonIcon(),
        expand(player, button.buttonHoverText()),
        button.customModelData());
  }

  private DialogPrompt expandDialog(Player player, DialogPrompt dialog) {
    return new DialogPrompt(
        dialog.type(),
        dialog.id(),
        expand(player, dialog.title()),
        expandBase(player, dialog.base()),
        expandDialogType(player, dialog.dialogType()),
        dialog.sanitize(),
        expandTitle(player, dialog.titleDisplay()));
  }

  private DialogBaseConfig expandBase(Player player, DialogBaseConfig base) {
    if (base == null) return null;
    return new DialogBaseConfig(
        base.body().stream().map(entry -> expandBody(player, entry)).toList(),
        base.inputs().stream().map(row -> expandRow(player, row)).toList());
  }

  private DialogBodyConfig expandBody(Player player, DialogBodyConfig body) {
    return new DialogBodyConfig(
        body.type(),
        body.content() == null ? null : expand(player, body.content()),
        body.material(),
        body.amount(),
        body.width());
  }

  private DialogRow expandRow(Player player, DialogRow row) {
    return new DialogRow(
        expand(player, row.label()),
        row.inputType(),
        row.constraints(),
        row.maxLength(),
        row.maxLines(),
        row.width());
  }

  private DialogTypeConfig expandDialogType(Player player, DialogTypeConfig dt) {
    return new DialogTypeConfig(
        dt.type(),
        dt.columns(),
        dt.actions().stream().map(action -> expandActionButton(player, action)).toList(),
        dt.actionsSource(),
        expandActionButton(player, dt.exitAction()),
        expandActionButton(player, dt.confirmAction()),
        expandActionButton(player, dt.cancelAction()));
  }

  private ActionButtonConfig expandActionButton(Player player, ActionButtonConfig button) {
    if (button == null) return null;
    // ActionButtonConfig forbids an empty label; if the expansion resolves to an empty string,
    // keep the original text rather than failing record construction.
    var expandedLabel = expand(player, button.label());
    var label = expandedLabel.isEmpty() ? button.label() : expandedLabel;
    return new ActionButtonConfig(
        label,
        button.tooltip() == null ? null : expand(player, button.tooltip()),
        button.returnValue());
  }

  // ------------------------------------------------------------------
  // Shared helpers
  // ------------------------------------------------------------------

  /**
   * Expands {@code titleDisplay.main}/{@code sub}. An empty {@code main} is preserved verbatim so
   * {@link PromptFactory} can inject the already-expanded prompt text as the fallback without
   * re-expanding it. {@code null} {@code sub} stays {@code null}.
   */
  private TitleConfig expandTitle(Player player, TitleConfig title) {
    if (title == null) return null;
    return new TitleConfig(
        expand(player, title.main()),
        title.sub() == null ? null : expand(player, title.sub()),
        title.ticks());
  }

  /**
   * Resolves one string through the expansion delegate. Null and empty strings are returned
   * unchanged and never reach the delegate (a PAPI expansion of an empty string is a no-op anyway,
   * and the empty-main marker must survive for the title fallback).
   */
  private String expand(Player player, String text) {
    if (text == null || text.isEmpty()) return text;
    return expander.apply(player, text);
  }
}
