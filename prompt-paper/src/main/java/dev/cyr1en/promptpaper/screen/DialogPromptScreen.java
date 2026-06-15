package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.sub.DialogConfig;
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
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/**
 * Dialog-based prompt screen using Paper's native dialog system. Supports
 * standard text/number/choice inputs, compound dialogs (multiple rows via
 * {@code &&}-delimited sub-tags), and the {@code d:tab} completion picker
 * that branches between a multi-action button list and a text-input fallback
 * based on the configured {@code MaxButtons} threshold.
 */
public class DialogPromptScreen implements InputScreen, DialogScreen {

    private static final int DEFAULT_BUTTON_WIDTH = 200;

    private final CommandPrompter plugin;
    private final Player player;
    private final PromptTag tag;
    private final List<PromptTag> rows; // 1 row for single, N rows for compound
    private final PromptConfig promptConfig;
    private final DialogConfig dialogConfig;
    private final DialogCompletionContext context;
    private final TabCompletionService tabCompletionService;
    private final DialogInputKind kind;
    private final String customTitle;
    private Consumer<ScreenResult> callback;
    private boolean open;

    public DialogPromptScreen(
            CommandPrompter plugin,
            Player player,
            PromptTag tag,
            PromptConfig promptConfig,
            DialogCompletionContext context) {
        this.plugin = plugin;
        this.player = player;
        this.tag = tag;
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

    private DialogInputKind computeKind() {
        if (rows.size() == 1) {
            return DialogInputKind.parse(rows.get(0).filter());
        }
        return DialogInputKind.TEXT;
    }

    @Override
    public void open() {
        if (kind == DialogInputKind.TAB && context != null && context.hasCompletions()) {
            openTab();
        } else {
            openStandard();
        }
    }

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
        var title = ComponentUtil.mini(effectiveTitle());
        AtomicReference<String> captured = new AtomicReference<>();

        var buttons = new ArrayList<ActionButton>(completions.size());
        for (var completion : completions) {
            var label = Component.text(completion);
            var completionCaptured = completion;
            var button = ActionButton.builder(label)
                    .tooltip(label)
                    .width(DEFAULT_BUTTON_WIDTH)
                    .action(DialogAction.customClick(
                            (view, audience) -> {
                                captured.set(completionCaptured);
                                onTabClick(completionCaptured);
                            },
                            clickOptions()))
                    .build();
            buttons.add(button);
        }

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(List.copyOf(buttons), buildExitButton(), 1)));
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
        var notice = ComponentUtil.mini(completionCount == 0
                ? "<yellow>No options available, enter argument manually.</yellow>"
                : "<yellow>Too many options (" + completionCount
                        + "), enter argument manually.</yellow>");
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
                .type(DialogType.confirmation(confirmBtn, cancelBtn)));
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
     * row if one was supplied, otherwise the configured default title.
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
                .type(DialogType.confirmation(confirmBtn, cancelBtn))
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
