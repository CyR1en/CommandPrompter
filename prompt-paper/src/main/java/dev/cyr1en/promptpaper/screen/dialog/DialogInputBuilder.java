package dev.cyr1en.promptpaper.screen.dialog;

import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Static factory helpers for {@link DialogInput} instances used by
 * {@link dev.cyr1en.promptpaper.screen.DialogPromptScreen}. Internal API.
 */
public final class DialogInputBuilder {

    private static final int DEFAULT_WIDTH = 200;

    /**
     * The default single-input key. For compound dialogs each row gets its
     * own key derived from this prefix (e.g. {@code answer_0}, {@code answer_1}).
     */
    public static final String DEFAULT_INPUT_KEY = "answer";

    private DialogInputBuilder() {}

    /** Build a text input. */
    public static DialogInput buildText(DialogConstraints c, Component label) {
        return buildText(c, label, DEFAULT_INPUT_KEY);
    }

    /** Build a text input under a specific key (used for compound-dialog rows). */
    public static DialogInput buildText(DialogConstraints c, Component label, String key) {
        var builder = DialogInput.text(key, label)
                .width(c.width())
                .labelVisible(true)
                .maxLength(c.maxLength());
        if (c.multiline()) {
            builder = builder.multiline(
                    TextDialogInput.MultilineOptions.create(c.multilineMaxLines(), null));
        }
        return builder.build();
    }

    /**
     * Build a single-option (dropdown) input.
     *
     * <p>Option labels from {@link DialogConstraints#options()} are used as
     * both the option's display text and its id — the id is what
     * {@code DialogResponseView.getText(key)} returns. The first option is
     * initially selected; only one option may carry the {@code initial} flag
     * at a time, and a single-option input with no initially selected option
     * is rejected by the client, so we always force the first option to be
     * the default.
     */
    public static DialogInput buildChoice(DialogConstraints c, Component label) {
        return buildChoice(c, label, DEFAULT_INPUT_KEY);
    }

    /** Build a single-option input under a specific key. */
    public static DialogInput buildChoice(DialogConstraints c, Component label, String key) {
        var entries = new ArrayList<SingleOptionDialogInput.OptionEntry>();
        var first = true;
        for (var option : c.options()) {
            var trimmed = option.trim();
            if (trimmed.isEmpty()) continue;
            // Set id as display label to keep parsed answer identical to selection.
            entries.add(SingleOptionDialogInput.OptionEntry.create(trimmed, null, first));
            first = false;
        }
        // Fallback to empty option if zero options supplied, avoiding client rejection.
        if (entries.isEmpty()) {
            entries.add(SingleOptionDialogInput.OptionEntry.create("", null, true));
        }
        return DialogInput.singleOption(key, label, List.copyOf(entries))
                .width(DEFAULT_WIDTH)
                .labelVisible(true)
                .build();
    }

    /** Build a number-range input. */
    public static DialogInput buildNumber(DialogConstraints c, Component label) {
        return buildNumber(c, label, DEFAULT_INPUT_KEY);
    }

    /** Build a number-range input under a specific key. */
    public static DialogInput buildNumber(DialogConstraints c, Component label, String key) {
        return DialogInput.numberRange(key, label, c.min(), c.max())
                .width(DEFAULT_WIDTH)
                .step(c.step())
                .initial(c.initial())
                .build();
    }
}
