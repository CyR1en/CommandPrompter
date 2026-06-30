package dev.cyr1en.promptpaper.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PromptConfigTest extends MockBukkitTest {

    private PromptConfig makeDefaultCfg() {
        return new PromptConfig(mock(YamlDocument.class),
                "%s", 0, 54, 256, 1,
                "Feather", 0, 3, "&7◀◀ Previous",
                "Feather", 0, 7, "Next ▶▶",
                "Barrier", 0, 5, "&7Cancel ✘",
                "Name_Tag", 0, 9, "&6Search ⌕",
                "&6&lPlayer Search", "PAPER", 0, "&6Enter Player Name",
                false, "&cNo players found!",
                "&6ᴀ %s", "&cᤣ %s",
                true, "", "",
                false, "Paper", false, 0, false,
                "Paper", false, 0, false,
                "Barrier", false, 0, false, "&cClick to Cancel",
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "DEFAULT",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5);
    }

    @Test
    void screenMappingsReturnsDefaultsWhenSectionMissing() {
        var rawConfig = mock(YamlDocument.class);
        when(rawConfig.getKeys("screen-mappings")).thenReturn(Set.of());

        var cfg = new PromptConfig(rawConfig,
                "%s", 0, 54, 256, 1,
                "Feather", 0, 3, "&7◀◀ Previous",
                "Feather", 0, 7, "Next ▶▶",
                "Barrier", 0, 5, "&7Cancel ✘",
                "Name_Tag", 0, 9, "&6Search ⌕",
                "&6&lPlayer Search", "PAPER", 0, "&6Enter Player Name",
                false, "&cNo players found!",
                "&6ᴀ %s", "&cᤣ %s",
                true, "", "",
                false, "Paper", false, 0, false,
                "Paper", false, 0, false,
                "Barrier", false, 0, false, "&cClick to Cancel",
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "DEFAULT",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5);

        var mappings = cfg.getScreenMappings();
        assertFalse(mappings.isEmpty());
        assertEquals(ScreenType.CHAT, mappings.get(""));
    }

    @Test
    void screenMappingsReadsFromSection() {
        var rawConfig = mock(YamlDocument.class);
        when(rawConfig.getKeys("screen-mappings")).thenReturn(Set.of("a", "s"));
        when(rawConfig.getString("screen-mappings.a")).thenReturn("ANVIL");
        when(rawConfig.getString("screen-mappings.s")).thenReturn("SIGN");

        var cfg = new PromptConfig(rawConfig,
                "%s", 0, 54, 256, 1,
                "Feather", 0, 3, "&7◀◀ Previous",
                "Feather", 0, 7, "Next ▶▶",
                "Barrier", 0, 5, "&7Cancel ✘",
                "Name_Tag", 0, 9, "&6Search ⌕",
                "&6&lPlayer Search", "PAPER", 0, "&6Enter Player Name",
                false, "&cNo players found!",
                "&6ᴀ %s", "&cᤣ %s",
                true, "", "",
                false, "Paper", false, 0, false,
                "Paper", false, 0, false,
                "Barrier", false, 0, false, "&cClick to Cancel",
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "DEFAULT",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5);

        var mappings = cfg.getScreenMappings();
        assertEquals(ScreenType.ANVIL, mappings.get("a"));
        assertEquals(ScreenType.SIGN, mappings.get("s"));
    }

    @Test
    void dialogDefaultsAppliedWhenSectionMissing() {
        var cfg = makeDefaultCfg();
        assertEquals("Prompt", cfg.dialogTitle());
        assertEquals("<green>Confirm</green>", cfg.dialogConfirmLabel());
        assertEquals("<red>Cancel</red>", cfg.dialogCancelLabel());
    }

    @Test
    void dialogConfigBuildsNestedRecordsFromFields() {
        var cfg = makeDefaultCfg();
        var dialog = cfg.dialogConfig();

        assertEquals("Prompt", dialog.title());
        assertEquals("<green>Confirm</green>", dialog.confirm().label());
        assertEquals("Confirm this action", dialog.confirm().tooltip());
        assertEquals("<red>Cancel</red>", dialog.cancel().label());
        assertEquals("Cancel this action", dialog.cancel().tooltip());

        var text = dialog.text();
        assertEquals(256, text.maxLength());
        assertFalse(text.multiline());
        assertEquals(4, text.multilineMaxLines());

        assertTrue(dialog.choice().defaultOptions().isEmpty(),
                "Choice defaults start empty when not configured");

        var number = dialog.number();
        assertEquals(0.0f, number.min());
        assertEquals(100.0f, number.max());
        assertEquals(1.0f, number.step());
        assertNull(number.initial());
        assertEquals(50.0f, number.effectiveInitial());
    }

    @Test
    void dialogConfigCustomValuesPropagate() {
        var cfg = new PromptConfig(mock(YamlDocument.class),
                "%s", 0, 54, 256, 1,
                "Feather", 0, 3, "&7◀◀ Previous",
                "Feather", 0, 7, "Next ▶▶",
                "Barrier", 0, 5, "&7Cancel ✘",
                "Name_Tag", 0, 9, "&6Search ⌕",
                "&6&lPlayer Search", "PAPER", 0, "&6Enter Player Name",
                false, "&cNo players found!",
                "&6ᴀ %s", "&cᤣ %s",
                true, "", "",
                false, "Paper", false, 0, false,
                "Paper", false, 0, false,
                "Barrier", false, 0, false, "&cClick to Cancel",
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "DEFAULT",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Custom Title", "<blue>Yes</blue>", "Please confirm",
                "<red>No</red>", "Dismiss",
                512, true, 8, 200, "a,b,c", -10.0f, 10.0f, 0.5f, 7);

        var dialog = cfg.dialogConfig();
        assertEquals("Custom Title", dialog.title());
        assertEquals("<blue>Yes</blue>", dialog.confirm().label());
        assertEquals("Please confirm", dialog.confirm().tooltip());
        assertEquals("<red>No</red>", dialog.cancel().label());
        assertEquals("Dismiss", dialog.cancel().tooltip());

        assertEquals(512, dialog.text().maxLength());
        assertTrue(dialog.text().multiline());
        assertEquals(8, dialog.text().multilineMaxLines());

        assertEquals(List.of("a", "b", "c"), dialog.choice().defaultOptions());

        assertEquals(-10.0f, dialog.number().min());
        assertEquals(10.0f, dialog.number().max());
        assertEquals(0.5f, dialog.number().step());
    }

    @Test
    void filterFormatDefaultsToPercentS() {
        var rawConfig = mock(YamlDocument.class);
        when(rawConfig.getKeys("PlayerUI.Filter-Format")).thenReturn(Set.of());

        var cfg = new PromptConfig(rawConfig,
                "%s", 0, 54, 256, 1,
                "Feather", 0, 3, "&7◀◀ Previous",
                "Feather", 0, 7, "Next ▶▶",
                "Barrier", 0, 5, "&7Cancel ✘",
                "Name_Tag", 0, 9, "&6Search ⌕",
                "&6&lPlayer Search", "PAPER", 0, "&6Enter Player Name",
                false, "&cNo players found!",
                "&6ᴀ %s", "&cᤣ %s",
                true, "", "",
                false, "Paper", false, 0, false,
                "Paper", false, 0, false,
                "Barrier", false, 0, false, "&cClick to Cancel",
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "DEFAULT",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5);

        assertEquals("%s", cfg.getFilterFormat("World"));
    }
}
