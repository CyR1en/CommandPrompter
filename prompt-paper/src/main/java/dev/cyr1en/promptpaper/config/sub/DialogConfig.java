package dev.cyr1en.promptpaper.config.sub;

import java.util.List;

/** Grouped configuration for the Dialog prompt screen. */
public record DialogConfig(
        // Legacy confirm/cancel surfaces (kept for back-compat with <3.0 configs).
        // Confirm and Cancel are no longer the only buttons — see defaults below.
        String title,
        ConfirmButton confirm,
        CancelButton cancel,

        // Defaults for variants when the per-tag filter does not override.
        // Applied in DialogPromptScreen after parsing PromptTag.filter.
        TextDefaults text,
        ChoiceDefaults choice,
        NumberDefaults number,
        TabDefaults tab
) {
    /** Legacy 5-field constructor preserved as a static factory for test sites. */
    public static DialogConfig legacy(
            String title,
            String confirmLabel,
            String confirmTooltip,
            String cancelLabel,
            String cancelTooltip) {
        return new DialogConfig(
                title,
                new ConfirmButton(confirmLabel, confirmTooltip),
                new CancelButton(cancelLabel, cancelTooltip),
                TextDefaults.DEFAULTS,
                ChoiceDefaults.DEFAULTS,
                NumberDefaults.DEFAULTS,
                TabDefaults.DEFAULTS);
    }

    public record ConfirmButton(String label, String tooltip) {}

    public record CancelButton(String label, String tooltip) {}

    public record TextDefaults(
            int maxLength,        // default 32
            boolean multiline,    // default false
            int multilineMaxLines // default 1 — only used when multiline == true
    ) {
        public static final TextDefaults DEFAULTS = new TextDefaults(32, false, 1);
    }

    /**
     * Defaults for the {@code <d:choice[opt1,opt2,...]:display>} form.
     *
     * <p>Per-tag option lists always win — these are used only when a tag
     * declares the choice kind without an option list (in which case the
     * default is empty and the prompt silently falls back to a text field).
     */
    public record ChoiceDefaults(
            List<String> defaultOptions
    ) {
        public static final ChoiceDefaults DEFAULTS = new ChoiceDefaults(List.of());
    }

    public record NumberDefaults(
            float min,            // default 0.0f
            float max,            // default 100.0f
            float step,           // default 1.0f
            Float initial         // default null → resolved to (min + max) / 2 at build time
    ) {
        public static final NumberDefaults DEFAULTS = new NumberDefaults(0.0f, 100.0f, 1.0f, null);

        /** Effective initial value when no override is given: midpoint. */
        public float effectiveInitial() {
            return initial != null ? initial : (min + max) / 2.0f;
        }
    }

    /**
     * Defaults for the {@code <d:tab:display>} and {@code <d:tab[N]:display>} forms.
     *
     * <p>The {@code maxButtons} threshold controls when the screen switches from a
     * multi-action button grid to the text-input fallback dialog. A per-tag {@code N}
     * (in the bracket) always wins; the config default applies when the tag omits it.
     */
    public record TabDefaults(
            int maxButtons // default 5
    ) {
        public static final TabDefaults DEFAULTS = new TabDefaults(5);
    }
}
