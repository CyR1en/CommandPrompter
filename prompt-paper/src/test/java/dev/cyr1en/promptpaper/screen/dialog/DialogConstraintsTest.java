package dev.cyr1en.promptpaper.screen.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.cyr1en.promptpaper.config.sub.DialogConfig;
import org.junit.jupiter.api.Test;

class DialogConstraintsTest {

    private final DialogConfig defaults = DialogConfig.legacy(
            "Title", "Confirm", "Tooltip", "Cancel", "Tooltip");

    @Test
    void emptyFilterUsesTextDefaults() {
        var c = DialogConstraints.from(null, defaults);
        assertEquals(DialogInputKind.TEXT, c.kind());
        assertEquals(32, c.maxLength());
        assertEquals(200, c.width());
    }

    @Test
    void emptyFilterBoolFallsBackToText() {
        // `bool` is no longer a valid kind. The parser drops it back to TEXT.
        var c = DialogConstraints.from("bool", defaults);
        assertEquals(DialogInputKind.TEXT, c.kind());
        assertEquals(200, c.width());
    }

    @Test
    void choiceNoDefaults() {
        var c = DialogConstraints.from("choice", defaults);
        assertEquals(DialogInputKind.CHOICE, c.kind());
        assertTrue(c.options().isEmpty());
    }

    @Test
    void choiceOverride() {
        var c = DialogConstraints.from("choice[set,add,query]", defaults);
        assertEquals(DialogInputKind.CHOICE, c.kind());
        assertEquals(List.of("set", "add", "query"), c.options());
    }

    @Test
    void numMinMax() {
        var c = DialogConstraints.from("num[0,100]", defaults);
        assertEquals(DialogInputKind.NUMBER, c.kind());
        assertEquals(0.0f, c.min(), 0.0001f);
        assertEquals(100.0f, c.max(), 0.0001f);
        assertEquals(1.0f, c.step(), 0.0001f); // default
        assertEquals(50.0f, c.initial(), 0.0001f); // midpoint
    }

    @Test
    void numMinMaxStep() {
        var c = DialogConstraints.from("num[0,100,5]", defaults);
        assertEquals(5.0f, c.step(), 0.0001f);
        assertEquals(50.0f, c.initial(), 0.0001f); // midpoint
    }

    @Test
    void numFullSpec() {
        var c = DialogConstraints.from("num[0,100,1,5]", defaults);
        assertEquals(0.0f, c.min(), 0.0001f);
        assertEquals(100.0f, c.max(), 0.0001f);
        assertEquals(1.0f, c.step(), 0.0001f);
        assertEquals(5.0f, c.initial(), 0.0001f);
    }

    @Test
    void numFloatStep() {
        var c = DialogConstraints.from("num[0,1,0.1,0.5]", defaults);
        assertEquals(0.1f, c.step(), 0.0001f);
        assertEquals(0.5f, c.initial(), 0.0001f);
    }

    @Test
    void numMalformedFallsBackToDefaults() {
        var c = DialogConstraints.from("num[abc,def]", defaults);
        assertEquals(0.0f, c.min(), 0.0001f);
        assertEquals(100.0f, c.max(), 0.0001f);
    }

    @Test
    void numMinGteMaxClampsMax() {
        var c = DialogConstraints.from("num[100,50]", defaults);
        assertEquals(100.0f, c.min(), 0.0001f);
        assertEquals(101.0f, c.max(), 0.0001f); // clamped
    }

    @Test
    void numZeroStepClampsToOne() {
        var c = DialogConstraints.from("num[0,100,0]", defaults);
        assertEquals(1.0f, c.step(), 0.0001f);
    }

    @Test
    void numPerTagRangeOverridesConfigDefaultInitial() {
        // Regression test: config defaults resolve `effectiveInitial()`
        // against the config range (0..100 → 50). When the per-tag supplies
        // a narrower range like num[0,24], the parser must re-resolve the
        // initial against the per-tag range, not carry the config default.
        // Paper's NumberRangeDialogInput rejects initial < min or > max,
        // so passing through 50 throws IllegalArgumentException at dialog
        // build time.
        var c = DialogConstraints.from("num[0,24]", defaults);
        assertEquals(0.0f, c.min(), 0.0001f);
        assertEquals(24.0f, c.max(), 0.0001f);
        assertEquals(12.0f, c.initial(), 0.0001f, "Initial must be per-tag midpoint, not config midpoint");
    }

    @Test
    void numPerTagRangeWithCustomConfigInitialStillUsesPerTagMidpoint() {
        // Even when the config pins a custom initial (e.g. 75), a per-tag
        // range override resets the default to the per-tag midpoint. The
        // config initial only applies when the per-tag does not override
        // the range at all.
        var cfg = new DialogConfig(
                "T",
                new DialogConfig.ConfirmButton("OK", ""),
                new DialogConfig.CancelButton("Cancel", ""),
                DialogConfig.TextDefaults.DEFAULTS,
                DialogConfig.ChoiceDefaults.DEFAULTS,
                new DialogConfig.NumberDefaults(0f, 100f, 1f, 75f),
                DialogConfig.TabDefaults.DEFAULTS);
        var c = DialogConstraints.from("num[0,24]", cfg);
        assertEquals(12.0f, c.initial(), 0.0001f, "Per-tag range win: re-resolve to per-tag midpoint");
    }

    @Test
    void numPerTagInitialAboveOwnRangeIsClamped() {
        // The 4-arg per-tag form can pin an initial that lies above the
        // per-tag range. The clamp is the last line of defense so Paper's
        // builder does not throw at dialog open.
        var c = DialogConstraints.from("num[0,24,1,99]", defaults);
        assertEquals(24.0f, c.initial(), 0.0001f, "Initial above max must clamp to max");
    }

    @Test
    void numPerTagInitialBelowMinIsClamped() {
        var c = DialogConstraints.from("num[10,24,1,0]", defaults);
        assertEquals(10.0f, c.initial(), 0.0001f, "Initial below min must clamp to min");
    }

    @Test
    void numPerTagRangeWithFullSpecUsesPerTagInitial() {
        // The 4-arg per-tag form MUST use the per-tag initial, not the
        // per-tag midpoint and not the config default. This guards the
        // precedence rule when both per-tag range AND per-tag initial
        // are present.
        var c = DialogConstraints.from("num[0,24,1,5]", defaults);
        assertEquals(0.0f, c.min(), 0.0001f);
        assertEquals(24.0f, c.max(), 0.0001f);
        assertEquals(5.0f, c.initial(), 0.0001f, "Per-tag initial wins over midpoint re-resolution");
    }

    @Test
    void textOverrideMaxLength() {
        var c = DialogConstraints.from("text[64]", defaults);
        assertEquals(64, c.maxLength());
    }

    @Test
    void textOverrideMaxLengthAndLines() {
        var c = DialogConstraints.from("text[64,3]", defaults);
        assertEquals(64, c.maxLength());
        assertTrue(c.multiline());
        assertEquals(3, c.multilineMaxLines());
        assertEquals(200, c.width());
    }

    @Test
    void textOverrideMaxLengthLinesAndWidth() {
        var c = DialogConstraints.from("text[64,3,150]", defaults);
        assertEquals(64, c.maxLength());
        assertTrue(c.multiline());
        assertEquals(3, c.multilineMaxLines());
        assertEquals(150, c.width());
    }

    @Test
    void textOverrideKeyValues() {
        var c = DialogConstraints.from("text[max_length=128,max_lines=5,width=300]", defaults);
        assertEquals(128, c.maxLength());
        assertTrue(c.multiline());
        assertEquals(5, c.multilineMaxLines());
        assertEquals(300, c.width());
    }

    @Test
    void textOverrideMaxLengthClamped() {
        var c = DialogConstraints.from("text[99999]", defaults);
        assertEquals(8192, c.maxLength()); // clamped to upper bound
    }

    @Test
    void unknownKindFallsBackToText() {
        var c = DialogConstraints.from("bogus", defaults);
        assertEquals(DialogInputKind.TEXT, c.kind());
    }

    // ============================== Tab-completion prompt ==============================

    @Test
    void tabNoBracketUsesConfigDefault() {
        var c = DialogConstraints.from("tab", defaults);
        assertEquals(DialogInputKind.TAB, c.kind());
        assertEquals(defaults.tab().maxButtons(), c.maxButtons());
    }

    @Test
    void tabExplicitMaxOverridesConfig() {
        var c = DialogConstraints.from("tab[7]", defaults);
        assertEquals(DialogInputKind.TAB, c.kind());
        assertEquals(7, c.maxButtons());
    }

    @Test
    void tabZeroClampsToOne() {
        var c = DialogConstraints.from("tab[0]", defaults);
        assertEquals(1, c.maxButtons());
    }

    @Test
    void tabNegativeClampsToOne() {
        var c = DialogConstraints.from("tab[-5]", defaults);
        assertEquals(1, c.maxButtons());
    }

    @Test
    void tabMalformedBracketFallsBackToConfigDefault() {
        var c = DialogConstraints.from("tab[abc]", defaults);
        assertEquals(DialogInputKind.TAB, c.kind());
        assertEquals(defaults.tab().maxButtons(), c.maxButtons());
    }

    @Test
    void tabCaseInsensitive() {
        assertEquals(DialogInputKind.TAB, DialogInputKind.parse("Tab"));
        assertEquals(DialogInputKind.TAB, DialogInputKind.parse("TAB"));
        assertEquals(7, DialogConstraints.from("TAB[7]", defaults).maxButtons());
    }

    @Test
    void tabCustomConfigDefaultIsRespected() {
        // Override the config default; verify it flows through.
        var cfg = new DialogConfig(
                "T",
                new DialogConfig.ConfirmButton("OK", ""),
                new DialogConfig.CancelButton("Cancel", ""),
                DialogConfig.TextDefaults.DEFAULTS,
                DialogConfig.ChoiceDefaults.DEFAULTS,
                DialogConfig.NumberDefaults.DEFAULTS,
                new DialogConfig.TabDefaults(20));
        var c = DialogConstraints.from("tab", cfg);
        assertEquals(20, c.maxButtons());
        // And the per-tag override still wins.
        assertEquals(3, DialogConstraints.from("tab[3]", cfg).maxButtons());
    }
}
