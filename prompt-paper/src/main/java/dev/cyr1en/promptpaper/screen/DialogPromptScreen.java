package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.sub.DialogConfig;
import dev.cyr1en.promptpaper.factory.MaterialMapper;
import dev.cyr1en.promptpaper.preset.ActionButtonConfig;
import dev.cyr1en.promptpaper.preset.ActionsSource;
import dev.cyr1en.promptpaper.preset.DialogBaseConfig;
import dev.cyr1en.promptpaper.preset.DialogBodyConfig;
import dev.cyr1en.promptpaper.preset.DialogBodyType;
import dev.cyr1en.promptpaper.preset.DialogPrompt;
import dev.cyr1en.promptpaper.preset.DialogRow;
import dev.cyr1en.promptpaper.preset.DialogType;
import dev.cyr1en.promptpaper.preset.DialogTypeConfig;
import dev.cyr1en.promptpaper.preset.InputType;
import dev.cyr1en.promptpaper.screen.dialog.DialogCompletionContext;
import dev.cyr1en.promptpaper.screen.dialog.DialogConstraints;
import dev.cyr1en.promptpaper.screen.dialog.DialogInputBuilder;
import dev.cyr1en.promptpaper.screen.dialog.DialogInputKind;
import dev.cyr1en.promptpaper.screen.dialog.TabCompletionService;
import dev.cyr1en.promptui.ComponentUtil;
import dev.cyr1en.promptui.DialogScreen;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import dev.cyr1en.promptcore.i18n.Placeholder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Dialog-based prompt screen using Paper's native dialog system. Supports
 * standard text/number/choice inputs, compound dialogs (multiple rows via
 * {@code &&}-delimited sub-tags), and the {@code d:tab} completion picker
 * that branches between a multi-action button list and a text-input fallback
 * based on the configured {@code MaxButtons} threshold.
 *
 * <h2>Two construction paths</h2>
 *
 * <ul>
 *   <li><b>Legacy (inline)</b>: takes a {@link PromptTag} and consumes its
 *       {@code filter} / sub-tag structure directly. This is the
 *       pre-refactor code path and is still used for inline dialog tags
 *       like {@code <d:text:Label>}.
 *   <li><b>JSON (preset)</b>: takes a {@link DialogPrompt} record loaded
 *       from {@code presets.json}. The base body, base inputs, and
 *       dialog-type actions are read directly from the JSON model. This is
 *       the post-refactor code path used by preset dialogs.
 * </ul>
 */
public class DialogPromptScreen implements InputScreen, DialogScreen {

    private static final int DEFAULT_BUTTON_WIDTH = 200;

    private final CommandPrompter plugin;
    private final Player player;

    // Legacy (PromptTag) path fields.
    private final PromptTag tag;
    private final List<PromptTag> rows; // 1 row for single, N rows for compound

    // JSON (DialogPrompt) path fields.
    private final DialogPrompt dialogPrompt;
    private final List<DialogRow> inputRows;
    private final List<ActionButtonConfig> staticActions;
    private final MaterialMapper materialMapper;

    // Shared.
    private final PromptConfig promptConfig;
    private final DialogConfig dialogConfig;
    private final DialogCompletionContext context;
    private final TabCompletionService tabCompletionService;
    private final DialogInputKind kind;
    private final String customTitle;
    private final boolean useNewModel;
    private Consumer<ScreenResult> callback;
    private boolean open;

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    /**
     * Legacy constructor — builds a dialog screen from a parsed inline
     * {@link PromptTag}. Preserves the pre-refactor behavior of the
     * {@code <d:…>} inline syntax.
     */
    public DialogPromptScreen(
            CommandPrompter plugin,
            Player player,
            PromptTag tag,
            PromptConfig promptConfig,
            DialogCompletionContext context) {
        this.plugin = plugin;
        this.player = player;
        this.tag = tag;
        this.dialogPrompt = null;
        this.inputRows = List.of();
        this.staticActions = List.of();
        this.materialMapper = null;
        // Keep all rows (including TITLE) so that indices stay aligned with
        // subTags() — the answer list decoded by ScreenManager must match
        // the subTags index. TITLE rows contribute "" to the answer list and
        // are skipped in buildInputs().
        this.rows = tag.isCompound() ? tag.subTags() : List.of(tag);
        String foundTitle = null;
        for (var row : this.rows) {
            if (DialogInputKind.parse(row.filter()) == DialogInputKind.TITLE) {
                foundTitle = row.displayText();
                break;
            }
        }
        this.customTitle = foundTitle;
        this.promptConfig = promptConfig;
        this.dialogConfig = promptConfig.dialogConfig();
        this.context = context;
        this.tabCompletionService = new TabCompletionService();
        this.kind = computeKind();
        this.useNewModel = false;
    }

    /**
     * Backward-compatible constructor for non-TAB dialogs. Delegates with a null context.
     */
    public DialogPromptScreen(
            CommandPrompter plugin,
            Player player,
            PromptTag tag,
            PromptConfig promptConfig) {
        this(plugin, player, tag, promptConfig, null);
    }

    /**
     * New constructor — builds a dialog screen from a JSON-sourced
     * {@link DialogPrompt} record. The prompt's {@code base} block provides
     * the body elements and input rows, and the {@code dialog_type} block
     * describes the action layout (confirmation or multi-action).
     *
     * <p>When the dialog type is {@code multi_action} with an
     * {@code actions_source} of {@link ActionsSource#TAB_COMPLETION}, the
     * screen looks up completions via {@link TabCompletionService} and
     * appends them as buttons after any static {@code actions} defined in
     * the config. If the completion count is zero or exceeds the
     * configured {@code MaxButtons} threshold, the screen falls back to a
     * confirmation layout with a single text input.
     */
    public DialogPromptScreen(
            CommandPrompter plugin,
            Player player,
            DialogPrompt dialogPrompt,
            PromptConfig promptConfig,
            DialogCompletionContext context) {
        this.plugin = plugin;
        this.player = player;
        this.tag = null;
        this.rows = List.of();
        this.dialogPrompt = dialogPrompt;
        this.promptConfig = promptConfig;
        this.dialogConfig = promptConfig.dialogConfig();
        this.context = context;
        this.tabCompletionService = new TabCompletionService();
        this.materialMapper = new MaterialMapper(plugin.getPluginLogger());
        this.customTitle = dialogPrompt.title();

        var base = dialogPrompt.base();
        this.inputRows = base != null ? base.inputs() : List.of();
        var dt = dialogPrompt.dialogType();
        this.staticActions = dt.actions() != null ? dt.actions() : List.of();

        this.useNewModel = true;
        this.kind = computeKindFromDialogType();
    }

    /**
     * Convenience overload that defaults the completion context to {@code null}.
     */
    public DialogPromptScreen(
            CommandPrompter plugin,
            Player player,
            DialogPrompt dialogPrompt,
            PromptConfig promptConfig) {
        this(plugin, player, dialogPrompt, promptConfig, null);
    }

    // ------------------------------------------------------------------
    // Kind computation
    // ------------------------------------------------------------------

    private DialogInputKind computeKind() {
        if (rows.size() == 1) {
            return DialogInputKind.parse(rows.get(0).filter());
        }
        return DialogInputKind.TEXT;
    }

    private DialogInputKind computeKindFromDialogType() {
        if (dialogPrompt.dialogType().type() == DialogType.MULTI_ACTION
                && dialogPrompt.dialogType().actionsSource() == ActionsSource.TAB_COMPLETION) {
            return DialogInputKind.TAB;
        }
        if (!inputRows.isEmpty()) {
            return switch (inputRows.get(0).inputType()) {
                case NUMBER -> DialogInputKind.NUMBER;
                case CHOICE -> DialogInputKind.CHOICE;
                case TEXT -> DialogInputKind.TEXT;
            };
        }
        return DialogInputKind.TEXT;
    }

    // ------------------------------------------------------------------
    // open() — entry point
    // ------------------------------------------------------------------

    @Override
    public void open() {
        if (useNewModel) {
            openFromDialogPrompt();
        } else if (kind == DialogInputKind.TAB && context != null && context.hasCompletions()) {
            openTab();
        } else {
            openStandard();
        }
    }

    // ------------------------------------------------------------------
    // Legacy path (PromptTag)
    // ------------------------------------------------------------------

    private void openStandard() {
        plugin.getPluginLogger().debug("Opening dialog prompt for " + player.getName()
                + " key=" + tag.key() + " rows=" + rows.size()
                + (tag.isCompound() ? " (compound)" : "")
                + " kind=" + kind);

        var title = ComponentUtil.mini(effectiveTitle());
        List<DialogInput> inputs = buildInputs();
        var dialog = buildDialogWithButtons(title, inputs);
        player.showDialog(dialog);
        open = true;
    }

    private void openTab() {
        plugin.getPluginLogger().debug("Opening d:tab prompt for " + player.getName()
                + " partial=\"" + context.partialCommand() + "\"");

        var constraints = DialogConstraints.from(rows.get(0).filter(), dialogConfig);
        int maxButtons = constraints.maxButtons();
        var completions = tabCompletionService.complete(
                context.player(), context.partialCommand());

        plugin.getPluginLogger().debug("d:tab resolved " + completions.size()
                + " completions (max=" + maxButtons + ")");

        // Zero completions always fall through to the text-input fallback:
        // DialogType.multiAction rejects an empty action list, and an empty
        // button grid has nothing useful to render anyway.
        Dialog dialog = !completions.isEmpty() && completions.size() <= maxButtons
                ? buildTabMultiActionDialog(completions)
                : buildTabFallbackDialog(completions.size());

        player.showDialog(dialog);
        open = true;
    }

    private Dialog buildTabMultiActionDialog(List<String> completions) {
        String titleStr = customTitle != null ? customTitle : 
                (tag != null && tag.displayText() != null && !tag.displayText().isEmpty() ? tag.displayText() : dialogConfig.title());
        var title = ComponentUtil.mini(titleStr);

        var buttons = new ArrayList<ActionButton>(completions.size());
        for (var completion : completions) {
            var label = Component.text(completion);
            var completionCaptured = completion;
            var button = ActionButton.builder(label)
                    .tooltip(label)
                    .width(DEFAULT_BUTTON_WIDTH)
                    .action(DialogAction.customClick(
                            (view, audience) -> onTabClick(completionCaptured),
                            clickOptions()))
                    .build();
            buttons.add(button);
        }

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .build())
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.multiAction(List.copyOf(buttons), buildExitButton(), 1)));
    }

    private void onTabClick(String completion) {
        player.getScheduler().run(plugin, scheduledTask -> {
            if (!open) return;
            open = false;
            plugin.getPluginLogger().debug("d:tab button clicked for " + player.getName()
                    + " completion=\"" + completion + "\"");
            if (callback != null) callback.accept(ScreenResult.answer(completion));
        }, null);
    }

    private ActionButton buildExitButton() {
        return ActionButton.builder(ComponentUtil.mini(dialogConfig.cancel().label()))
                .tooltip(ComponentUtil.mini(dialogConfig.cancel().tooltip()))
                .action(DialogAction.customClick(
                        (view, audience) -> onCancel(), clickOptions()))
                .build();
    }

    private Dialog buildTabFallbackDialog(int completionCount) {
        var title = ComponentUtil.mini(effectiveTitle());
        var i18n = plugin.getConfigLoader().getI18n();
        Component notice = completionCount == 0
                ? i18n.get("dialog.no_options", player)
                : i18n.get("dialog.too_many_options",
                        player,
                        Placeholder.of("count", String.valueOf(completionCount)));
        var label = Component.text(tag.displayText());
        var textConstraints = DialogConstraints.from(null, dialogConfig);
        var input = DialogInputBuilder.buildText(textConstraints, label, "answer");

        var options = clickOptions();
        var confirmBtn = ActionButton.builder(
                        ComponentUtil.mini(dialogConfig.confirm().label()))
                .tooltip(ComponentUtil.mini(dialogConfig.confirm().tooltip()))
                .action(DialogAction.customClick(
                        (view, audience) -> onTabFallbackConfirm(view), options))
                .build();
        var cancelBtn = ActionButton.builder(
                        ComponentUtil.mini(dialogConfig.cancel().label()))
                .tooltip(ComponentUtil.mini(dialogConfig.cancel().tooltip()))
                .action(DialogAction.customClick(
                        (view, audience) -> onCancel(), options))
                .build();

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(notice)))
                        .inputs(List.of(input))
                        .build())
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(confirmBtn, cancelBtn)));
    }

    private void onTabFallbackConfirm(DialogResponseView view) {
        player.getScheduler().run(plugin, scheduledTask -> {
            if (!open) return;
            open = false;
            var v = view.getText("answer");
            if (v == null) v = "";
            // Apply the same text-input sanitization as the standard path.
            String answer = tag.sanitize() ? v : ComponentUtil.miniToLegacy(v);
            plugin.getPluginLogger().debug("d:tab fallback confirmed for " + player.getName()
                    + " answer=\"" + answer + "\"");
            if (callback != null) callback.accept(ScreenResult.answer(answer));
        }, null);
    }

    /**
     * Returns the effective dialog title: the custom title from a {@code d:title:...}
     * row if one was supplied (legacy path), or the title from the JSON
     * definition (new path), otherwise the configured default title.
     */
    private String effectiveTitle() {
        return customTitle != null ? customTitle : dialogConfig.title();
    }

    private static ClickCallback.Options clickOptions() {
        return ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofMinutes(5))
                .build();
    }

    private Dialog buildDialogWithButtons(
            Component title, List<DialogInput> inputs) {
        var options = clickOptions();

        var confirmBtn = ActionButton.builder(ComponentUtil.mini(dialogConfig.confirm().label()))
                .tooltip(ComponentUtil.mini(dialogConfig.confirm().tooltip()))
                .action(DialogAction.customClick((view, audience) -> onConfirm(view), options))
                .build();

        var cancelBtn = ActionButton.builder(ComponentUtil.mini(dialogConfig.cancel().label()))
                .tooltip(ComponentUtil.mini(dialogConfig.cancel().tooltip()))
                .action(DialogAction.customClick((view, audience) -> onCancel(), options))
                .build();

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .inputs(inputs)
                        .build())
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(confirmBtn, cancelBtn))
        );
    }

    private List<DialogInput> buildInputs() {
        var inputs = new ArrayList<DialogInput>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var constraints = DialogConstraints.from(row.filter(), dialogConfig);
            // TITLE rows carry no input widget — they only override the dialog
            // title. Skip them here; readAnswers() emits "" at the same index
            // so the answer list stays aligned with subTags().
            if (constraints.kind() == DialogInputKind.TITLE) continue;
            var label = ComponentUtil.mini(row.displayText());
            var key = keyFor(i);
            inputs.add(switch (constraints.kind()) {
                case NUMBER -> DialogInputBuilder.buildNumber(constraints, label, key);
                case CHOICE -> DialogInputBuilder.buildChoice(constraints, label, key);
                case TEXT -> DialogInputBuilder.buildText(constraints, label, key);
                // TAB kind is rendered as either a multiAction or fallback
                // dialog by openTab() and never reaches buildInputs().
                case TAB -> throw new UnsupportedOperationException(
                        "TAB prompts must use the multiAction dialog flow, not buildInputs()");
                // Unreachable — TITLE is handled by the continue above.
                case TITLE -> throw new UnsupportedOperationException("unreachable");
            });
        }
        return inputs;
    }

    /** {@code "answer"} for the first row, {@code "answer_1"}, {@code "answer_2"}, ... for the rest. */
    private static String keyFor(int rowIndex) {
        return rowIndex == 0 ? DialogInputBuilder.DEFAULT_INPUT_KEY
                : DialogInputBuilder.DEFAULT_INPUT_KEY + "_" + rowIndex;
    }

    private void onConfirm(DialogResponseView view) {
        // Dialog callbacks fire on the network thread. Hop to the player scheduler
        // before touching ScreenManager / engine / Player.sendMessage.
        player.getScheduler().run(plugin, scheduledTask -> {
            if (!open) return;
            open = false;
            var answers = readAnswers(view);
            plugin.getPluginLogger().debug("Dialog confirmed for " + player.getName()
                    + " key=" + tag.key() + " rows=" + rows.size()
                    + " answers=" + answers);
            if (callback != null) callback.accept(ScreenResult.answer(encodeAnswers(answers)));
        }, null);
    }

    private void onCancel() {
        player.getScheduler().run(plugin, scheduledTask -> {
            if (!open) return;
            open = false;
            plugin.getPluginLogger().debug("Dialog cancelled for " + player.getName());
            if (callback != null) callback.accept(ScreenResult.cancel());
        }, null);
    }

    /**
     * Reads one answer per row from the response view, coercing
     * each to its expected type (number, choice, text).
     */
    private List<String> readAnswers(DialogResponseView view) {
        var answers = new ArrayList<String>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var constraints = DialogConstraints.from(row.filter(), dialogConfig);
            var key = keyFor(i);
            answers.add(readOneAnswer(view, constraints, key));
        }
        return answers;
    }

    private String readOneAnswer(DialogResponseView view, DialogConstraints c, String key) {
        return switch (c.kind()) {
            // TAB kind captures its answer via per-button lambdas and never
            // reaches this method.
            case TAB -> "";
            // TITLE rows are stripped out by the constructor; this case is
            // unreachable when readAnswers() is called on this.rows.
            case TITLE -> "";
            case TEXT -> {
                var v = view.getText(key);
                if (v == null) yield "";
                // -ds (don't sanitize) lets the user type MiniMessage tags like
                // <red>red</red>. Downstream command dispatch (e.g. /say) only
                // understands legacy §X codes, so convert before handing the
                // answer to the placeholder substitution pipeline.
                yield tag.sanitize() ? v : ComponentUtil.miniToLegacy(v);
            }
            case CHOICE -> {
                // The id of the selected option. With our builder the id
                // equals the display label, so the answer is the label the
                // user saw in the dropdown.
                var v = view.getText(key);
                yield v == null ? "" : v;
            }
            case NUMBER -> {
                var v = view.getFloat(key);
                if (v == null) yield "0";
                if (c.step() == 1.0f && v.floatValue() == Math.floor(v.floatValue())) {
                    yield Long.toString(v.longValue());
                }
                yield Float.toString(v);
            }
        };
    }

    // ------------------------------------------------------------------
    // New path (DialogPrompt JSON)
    // ------------------------------------------------------------------

    private void openFromDialogPrompt() {
        plugin.getPluginLogger().debug("Opening JSON dialog prompt for " + player.getName()
                + " id=" + dialogPrompt.id()
                + " type=" + dialogPrompt.dialogType().type()
                + " inputs=" + inputRows.size());

        var title = ComponentUtil.mini(effectiveTitle());
        var body = buildBodyFromDialogPrompt();
        var inputs = buildInputsFromDialogPrompt();
        var dialogType = buildDialogTypeFromDialogPrompt();

        player.showDialog(Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(dialogType)));
        open = true;
    }

    /**
     * Maps {@link DialogBodyConfig} entries from
     * {@link DialogBaseConfig#body()} into Paper's {@link DialogBody}
     * elements. A {@code plain_message} entry maps to
     * {@link DialogBody#plainMessage(Component)}; an {@code item} entry
     * resolves its material via {@link MaterialMapper} and maps to
     * {@link DialogBody#item(ItemStack)}.
     *
     * <p>When the new path is in a tab-completion fallback state, a body
     * notice is prepended explaining the fallback.
     */
    private List<DialogBody> buildBodyFromDialogPrompt() {
        var bodies = new ArrayList<DialogBody>();
        if (needsTabFallbackNotice()) {
            bodies.add(DialogBody.plainMessage(tabFallbackNotice()));
        }
        var base = dialogPrompt.base();
        if (base != null) {
            for (var entry : base.body()) {
                var mapped = mapBodyEntry(entry);
                if (mapped != null) bodies.add(mapped);
            }
        }
        return List.copyOf(bodies);
    }

    /** Maps a single {@link DialogBodyConfig} to a {@link DialogBody} or null if it can't be mapped. */
    private DialogBody mapBodyEntry(DialogBodyConfig config) {
        return switch (config.type()) {
            case PLAIN_MESSAGE -> {
                if (config.content() == null) yield null;
                yield DialogBody.plainMessage(ComponentUtil.mini(config.content()));
            }
            case ITEM -> {
                var mat = materialMapper.resolveOrDefault(config.material(),
                        "dialog prompt '" + dialogPrompt.id() + "' body item");
                yield DialogBody.item(new ItemStack(mat, config.amount())).build();
            }
        };
    }

    /**
     * Builds the {@link DialogInput} list for the new path. Each
     * {@link DialogRow} is converted to a {@link DialogConstraints} record
     * and handed to the existing {@link DialogInputBuilder}. For
     * {@code tab_completion} dialogs that fall back to a confirmation
     * layout, a single text input (under the reserved key {@code answer})
     * is appended so the user can type the value manually.
     */
    private List<DialogInput> buildInputsFromDialogPrompt() {
        var inputs = new ArrayList<DialogInput>(inputRows.size() + 1);
        for (int i = 0; i < inputRows.size(); i++) {
            var row = inputRows.get(i);
            var label = ComponentUtil.mini(row.label());
            var key = keyFor(i);
            inputs.add(buildInputForRow(row, label, key));
        }
        if (needsTabFallbackNotice()) {
            var fallbackLabel = ComponentUtil.mini(effectiveTitle());
            var textConstraints = DialogConstraints.from(null, dialogConfig);
            inputs.add(DialogInputBuilder.buildText(textConstraints, fallbackLabel, "answer"));
        }
        return List.copyOf(inputs);
    }

    private DialogInput buildInputForRow(DialogRow row, Component label, String key) {
        return switch (row.inputType()) {
            case TEXT -> {
                var c = constraintsForTextRow(row);
                yield DialogInputBuilder.buildText(c, label, key);
            }
            case NUMBER -> {
                var c = constraintsForNumberRow(row);
                yield DialogInputBuilder.buildNumber(c, label, key);
            }
            case CHOICE -> {
                var c = constraintsForChoiceRow(row);
                yield DialogInputBuilder.buildChoice(c, label, key);
            }
        };
    }

    private DialogConstraints constraintsForTextRow(DialogRow row) {
        var dText = dialogConfig.text();
        int maxLength = row.maxLength() != null ? row.maxLength() : dText.maxLength();
        int maxLines = row.maxLines() != null ? row.maxLines() : (dText.multiline() ? dText.multilineMaxLines() : 1);
        int width = row.width() != null ? row.width() : dText.width();
        
        // Clamp bounds to prevent client crash
        maxLength = Math.max(1, Math.min(8192, maxLength));
        maxLines = Math.max(1, Math.min(8192, maxLines));
        width = Math.max(1, Math.min(8192, width));

        return new DialogConstraints(
                DialogInputKind.TEXT, "", 
                maxLength, maxLines > 1, maxLines, width,
                List.of(),
                0f, 0f, 0f, 0f,
                null);
    }

    /**
     * Translates the {@link DialogRow#constraints()} array for a
     * {@code number} row into a {@link DialogConstraints} record. Missing
     * bounds fall back to the configured defaults; the resulting
     * {@code initial} is re-clamped against the effective range so
     * Paper's {@code NumberRangeDialogInput} builder doesn't reject it.
     */
    private DialogConstraints constraintsForNumberRow(DialogRow row) {
        var cs = row.constraintsAsStrings();
        var dNum = dialogConfig.number();
        float min = dNum.min();
        float max = dNum.max();
        float step = dNum.step();
        float initial = dNum.effectiveInitial();
        var rangeOverridden = false;
        var perTagInitialSupplied = false;
        try {
            if (cs.size() >= 1 && !cs.get(0).isBlank()) {
                min = Float.parseFloat(cs.get(0).trim());
                rangeOverridden = true;
            }
            if (cs.size() >= 2 && !cs.get(1).isBlank()) {
                max = Float.parseFloat(cs.get(1).trim());
                rangeOverridden = true;
            }
            if (cs.size() >= 3 && !cs.get(2).isBlank()) {
                step = Float.parseFloat(cs.get(2).trim());
            }
            if (cs.size() >= 4 && !cs.get(3).isBlank()) {
                initial = Float.parseFloat(cs.get(3).trim());
                perTagInitialSupplied = true;
            }
        } catch (NumberFormatException ignored) {
            // fall back to defaults
        }
        if (min >= max) max = min + 1f;
        if (step <= 0f) step = 1f;
        if (rangeOverridden && !perTagInitialSupplied) {
            initial = (min + max) / 2.0f;
        }
        initial = Math.max(min, Math.min(max, initial));
        return new DialogConstraints(
                DialogInputKind.NUMBER, "",
                0, false, 0, 200, List.of(),
                min, max, step, initial, null);
    }

    /**
     * Translates the {@link DialogRow#constraints()} array for a
     * {@code choice} row into a {@link DialogConstraints} record. The
     * option ids default to the JSON constraint values verbatim.
     */
    private DialogConstraints constraintsForChoiceRow(DialogRow row) {
        return new DialogConstraints(
                DialogInputKind.CHOICE, "",
                0, false, 0, 200,
                row.constraintsAsStrings(),
                0f, 0f, 0f, 0f, null);
    }

    /**
     * Dispatches on the dialog's type to build a Paper
     * {@link io.papermc.paper.registry.data.dialog.type.DialogType}.
     *
     * <p>For {@code confirmation}, the {@code confirm_action} and
     * {@code cancel_action} buttons are mapped to {@link ActionButton}s
     * (falling back to the configured default labels when a button is
     * absent or label-only).
     *
     * <p>For {@code multi_action}:
     * <ul>
     *   <li>If {@code actions_source} is {@code tab_completion}, completions
     *       are looked up and appended after any static {@code actions}.
     *       If completions are empty or exceed the {@code MaxButtons}
     *       threshold, a confirmation fallback layout is returned.
     *   <li>Otherwise, the static {@code actions} list is used as-is.
     * </ul>
     */
    private io.papermc.paper.registry.data.dialog.type.DialogType buildDialogTypeFromDialogPrompt() {
        var dt = dialogPrompt.dialogType();
        return switch (dt.type()) {
            case CONFIRMATION -> buildConfirmationType(dt);
            case MULTI_ACTION -> buildMultiActionType(dt);
        };
    }

    private io.papermc.paper.registry.data.dialog.type.DialogType buildConfirmationType(DialogTypeConfig dt) {
        return io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(buildConfirmButton(dt), buildCancelButton(dt));
    }

    /**
     * Resolves the confirm button for the confirmation layout. Prefers
     * the {@code confirm_action} from the config; falls back to the
     * configured default label/tooltip when the config button is null
     * or doesn't carry a label.
     */
    private ActionButton buildConfirmButton(DialogTypeConfig dt) {
        var cfg = dt.confirmAction();
        var defaultLabel = dialogConfig.confirm().label();
        var defaultTooltip = dialogConfig.confirm().tooltip();
        if (cfg == null) {
            return buildConfirmActionButton(
                    new ActionButtonConfig(defaultLabel, defaultTooltip, null));
        }
        var label = cfg.label() != null && !cfg.label().isEmpty() ? cfg.label() : defaultLabel;
        var tooltip = cfg.tooltip() != null ? cfg.tooltip() : defaultTooltip;
        return buildConfirmActionButton(
                new ActionButtonConfig(label, tooltip, cfg.returnValue()));
    }

    /**
     * Resolves the cancel button for the confirmation layout. Prefers
     * the {@code cancel_action} from the config; falls back to the
     * configured default label/tooltip.
     */
    private ActionButton buildCancelButton(DialogTypeConfig dt) {
        var cfg = dt.cancelAction();
        var defaultLabel = dialogConfig.cancel().label();
        var defaultTooltip = dialogConfig.cancel().tooltip();
        var resolved = cfg == null
                ? new ActionButtonConfig(defaultLabel, defaultTooltip, null)
                : new ActionButtonConfig(
                        cfg.label() != null && !cfg.label().isEmpty() ? cfg.label() : defaultLabel,
                        cfg.tooltip() != null ? cfg.tooltip() : defaultTooltip,
                        cfg.returnValue());
        return buildCancelActionButton(resolved);
    }

    /**
     * Resolves the exit button for the multi-action layout. The exit
     * button is functionally a cancel — clicking it produces a
     * {@link ScreenResult#cancel()}. If no {@code exit_action} is
     * configured, falls back to the cancel button defaults.
     */
    private ActionButton buildExitButton(DialogTypeConfig dt) {
        var cfg = dt.exitAction();
        if (cfg == null) {
            return buildCancelActionButton(
                    new ActionButtonConfig(
                            dialogConfig.cancel().label(), dialogConfig.cancel().tooltip(), null));
        }
        return buildCancelActionButton(cfg);
    }

    /**
     * Multi-action dialog assembly. Static actions come first, then (when
     * the {@code actions_source} is {@code tab_completion}) the completion
     * buttons are appended. The exit button is the third argument to
     * Paper's {@code DialogType.multiAction}.
     */
    private io.papermc.paper.registry.data.dialog.type.DialogType buildMultiActionType(DialogTypeConfig dt) {
        int columns = dt.columns() != null && dt.columns() > 0 ? dt.columns() : 1;
        var exitButton = buildExitButton(dt);

        if (dt.actionsSource() == ActionsSource.TAB_COMPLETION) {
            int maxButtons = dialogConfig.tab().maxButtons();
            var completions = tabCompletions();
            if (completions.isEmpty() || completions.size() > maxButtons) {
                return buildTabFallbackConfirmation(dt);
            }
            var buttons = new ArrayList<ActionButton>(staticActions.size() + completions.size());
            for (var staticAction : staticActions) {
                buttons.add(buildStaticActionAnswerButton(staticAction));
            }
            for (var completion : completions) {
                buttons.add(buildCompletionActionButton(completion));
            }
            return io.papermc.paper.registry.data.dialog.type.DialogType.multiAction(List.copyOf(buttons), exitButton, columns);
        }

        var buttons = new ArrayList<ActionButton>(staticActions.size());
        for (var staticAction : staticActions) {
            buttons.add(buildStaticActionAnswerButton(staticAction));
        }
        return io.papermc.paper.registry.data.dialog.type.DialogType.multiAction(List.copyOf(buttons), exitButton, columns);
    }

    /**
     * Tab-completion fallback. Renders the dialog as a confirmation layout
     * with a single injected text input (under the reserved key
     * {@code answer}) and an explanatory body notice. The injected input
     * is added in {@link #buildInputsFromDialogPrompt()} via
     * {@link #needsTabFallbackNotice()}.
     */
    private io.papermc.paper.registry.data.dialog.type.DialogType buildTabFallbackConfirmation(DialogTypeConfig dt) {
        return buildConfirmationType(dt);
    }

    /**
     * Builds a static action button for the multi-action layout. The
     * button's {@code return} value is the answer the dialog submits
     * when the button is clicked (falling back to the label when no
     * return value is configured).
     */
    private ActionButton buildStaticActionAnswerButton(ActionButtonConfig config) {
        var returnValue = config.returnValue() != null ? config.returnValue() : config.label();
        return buildAnswerButton(config, returnValue);
    }

    /**
     * Builds the confirm button for the confirmation layout. When
     * clicked, the button reads the dialog's input values from the
     * response view and submits the encoded answers.
     */
    private ActionButton buildConfirmActionButton(ActionButtonConfig config) {
        return buildViewConsumingButton(config, this::onMultiActionConfirm);
    }

    /**
     * Builds a cancel-style button (cancel / exit) for any layout. When
     * clicked, the button submits {@link ScreenResult#cancel()}.
     */
    private ActionButton buildCancelActionButton(ActionButtonConfig config) {
        return buildViewConsumingButton(config, this::onCancelFromView);
    }

    /**
     * Generic action-button builder for buttons whose answer is a
     * pre-resolved string. The click handler dispatches
     * {@link ScreenResult#answer(String)} on the player scheduler so it
     * fires on the main thread.
     */
    private ActionButton buildAnswerButton(ActionButtonConfig config, String answerValue) {
        var label = ComponentUtil.mini(config.label());
        var builder = ActionButton.builder(label);
        if (config.tooltip() != null) {
            builder = builder.tooltip(ComponentUtil.mini(config.tooltip()));
        }
        return builder
                .width(DEFAULT_BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (view, audience) -> player.getScheduler().run(plugin,
                                scheduledTask -> {
                                    if (!open) return;
                                    open = false;
                                    if (callback != null) callback.accept(ScreenResult.answer(answerValue));
                                }, null),
                        clickOptions()))
                .build();
    }

    /**
     * Generic action-button builder for buttons that need to read the
     * response view. Used by the confirm and cancel buttons of the
     * confirmation layout. The {@code onClick} callback is dispatched
     * on the player scheduler.
     */
    private ActionButton buildViewConsumingButton(
            ActionButtonConfig config,
            java.util.function.Consumer<DialogResponseView> onClick) {
        var label = ComponentUtil.mini(config.label());
        var builder = ActionButton.builder(label);
        if (config.tooltip() != null) {
            builder = builder.tooltip(ComponentUtil.mini(config.tooltip()));
        }
        return builder
                .width(DEFAULT_BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (view, audience) -> player.getScheduler().run(plugin,
                                scheduledTask -> onClick.accept(view), null),
                        clickOptions()))
                .build();
    }

    /**
     * Builds an {@link ActionButton} for a tab-completion entry. The label
     * and tooltip are both the completion string, and the answer on click
     * is the completion verbatim.
     */
    private ActionButton buildCompletionActionButton(String completion) {
        var label = Component.text(completion);
        return ActionButton.builder(label)
                .tooltip(label)
                .width(DEFAULT_BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (view, audience) -> onTabClick(completion),
                        clickOptions()))
                .build();
    }

    /**
     * Looks up tab completions for the {@code actions_source} source. Returns
     * an empty list when the dialog has no completion context, when the
     * partial command is empty, or when the {@link TabCompletionService}
     * itself reports zero matches.
     */
    private List<String> tabCompletions() {
        if (context == null || !context.hasCompletions()) {
            return List.of();
        }
        return tabCompletionService.complete(context.player(), context.partialCommand());
    }

    /**
     * True when the new path is in a tab-completion fallback state — i.e.
     * the completion count is zero or exceeds the configured threshold.
     * The fallback injects a text input and a body notice; the dialog type
     * is built as confirmation.
     */
    private boolean needsTabFallbackNotice() {
        if (!useNewModel) return false;
        var dt = dialogPrompt.dialogType();
        if (dt.type() != DialogType.MULTI_ACTION) return false;
        if (dt.actionsSource() != ActionsSource.TAB_COMPLETION) return false;
        int maxButtons = dialogConfig.tab().maxButtons();
        var completions = tabCompletions();
        return completions.isEmpty() || completions.size() > maxButtons;
    }

    /** Localized body notice for the tab-completion fallback. */
    private Component tabFallbackNotice() {
        var i18n = plugin.getConfigLoader().getI18n();
        var completions = tabCompletions();
        return completions.isEmpty()
                ? i18n.get("dialog.no_options", player)
                : i18n.get("dialog.too_many_options",
                        player,
                        Placeholder.of("count", String.valueOf(completions.size())));
    }

    /**
     * Click handler for the cancel-style buttons (cancel, exit). Called
     * from the player scheduler.
     */
    private void onCancelFromView(DialogResponseView view) {
        if (callback != null) callback.accept(ScreenResult.cancel());
    }

    /**
     * Click handler for the confirm button in a multi-action / tab-fallback
     * dialog. Reads the input values from the response view and submits the
     * encoded answers.
     */
    private void onMultiActionConfirm(DialogResponseView view) {
        if (!open) return;
        open = false;
        var answers = readAnswersFromDialogPrompt(view);
        plugin.getPluginLogger().debug("Dialog confirmed for " + player.getName()
                + " id=" + dialogPrompt.id() + " rows=" + inputRows.size()
                + " answers=" + answers);
        if (callback != null) callback.accept(ScreenResult.answer(encodeAnswers(answers)));
    }

    /**
     * Reads one answer per configured input row from the response view. For
     * a tab-completion fallback, an extra entry is appended for the
     * injected text input. Numeric values that have no fractional part
     * are returned as long strings so downstream substitution receives a
     * clean integer.
     */
    private List<String> readAnswersFromDialogPrompt(DialogResponseView view) {
        var answers = new ArrayList<String>(inputRows.size() + 1);
        for (int i = 0; i < inputRows.size(); i++) {
            var row = inputRows.get(i);
            var key = keyFor(i);
            answers.add(readOneAnswerForRow(view, row, key));
        }
        if (needsTabFallbackNotice()) {
            var v = view.getText("answer");
            var answer = v == null ? "" : (dialogPrompt.sanitize() ? v : ComponentUtil.miniToLegacy(v));
            answers.add(answer);
        }
        return answers;
    }

    private String readOneAnswerForRow(DialogResponseView view, DialogRow row, String key) {
        return switch (row.inputType()) {
            case TEXT -> {
                var v = view.getText(key);
                if (v == null) yield "";
                yield dialogPrompt.sanitize() ? v : ComponentUtil.miniToLegacy(v);
            }
            case CHOICE -> {
                var v = view.getText(key);
                yield v == null ? "" : v;
            }
            case NUMBER -> {
                var v = view.getFloat(key);
                if (v == null) yield "0";
                // Mirror the legacy path: integral values render as long so the
                // placeholders downstream see e.g. "1" instead of "1.0".
                if (Math.floor(v) == v) {
                    yield Long.toString(v.longValue());
                }
                yield Float.toString(v);
            }
        };
    }

    // ------------------------------------------------------------------
    // Shared answer encoding + lifecycle
    // ------------------------------------------------------------------

    /**
     * Encodes N answer strings into a single {@link ScreenResult} payload.
     * Delegates to {@link AnswerEncoding#encode} for unit-testability.
     */
    static String encodeAnswers(List<String> answers) {
        return dev.cyr1en.promptpaper.screen.dialog.AnswerEncoding.encode(answers);
    }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        plugin.getPluginLogger().debug("Dialog prompt closed for " + player.getName());
        player.closeDialog();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void onResult(Consumer<ScreenResult> callback) {
        this.callback = callback;
    }
}
