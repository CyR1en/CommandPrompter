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
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "LOWEST",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5,
                "gui", "&8Confirm Action",
                "LIME_CONCRETE", "&aConfirm", 11,
                "RED_CONCRETE", "&cCancel", 15,
                "PAPER", "&eInformation", 13,
                "&aConfirm", "&cCancel", "");
    }

    @Test
    void screenMappingsReturnsDefaultsWhenSectionMissing() {
        var rawConfig = mock(YamlDocument.class);
        when(rawConfig.getKeys("screen-mappings")).thenReturn(Set.of());

        var cfg = new PromptConfig(rawConfig,
                "%s", 0, 54, 256, 1,
                "Feather", 0, 3, "&7◀◀ Previous",
                "Feather", 0, 7, "Next ▶▶",
                "Barrier", 0, 5, "&7Cancel ��",
                "Name_Tag", 0, 9, "&6Search ⌕",
                "&6&lPlayer Search", "PAPER", 0, "&6Enter Player Name",
                false, "&cNo players found!",
                "&6ᴀ %s", "&cᤣ %s",
                true, "", "",
                false, "Paper", false, 0, false,
                "Paper", false, 0, false,
                "Barrier", false, 0, false, "&cClick to Cancel",
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "LOWEST",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5,
                "gui", "&8Confirm Action",
                "LIME_CONCRETE", "&aConfirm", 11,
                "RED_CONCRETE", "&cCancel", 15,
                "PAPER", "&eInformation", 13,
                "&aConfirm", "&cCancel", "");

        var mappings = cfg.getScreenMappings();
        assertFalse(mappings.isEmpty());
        assertEquals(ScreenType.CHAT, mappings.get(""));
        assertEquals(ScreenType.ANVIL, mappings.get("a"));
        assertEquals(ScreenType.SIGN, mappings.get("s"));
        assertEquals(ScreenType.DIALOG, mappings.get("d"));
        assertEquals(ScreenType.PLAYER, mappings.get("p"));
        assertEquals(ScreenType.CONFIRMATION, mappings.get("c"));
        assertEquals(ScreenType.CONFIRMATION, mappings.get("confirm"));
        assertEquals(ScreenType.ITEM, mappings.get("i"));
        assertEquals(ScreenType.ITEM, mappings.get("item"));
    }

    @Test
    void screenMappingsReadsFromSectionAndPreservesBuiltins() {
        var rawConfig = mock(YamlDocument.class);
        when(rawConfig.getKeys("screen-mappings")).thenReturn(Set.of("custom1", "custom2"));
        when(rawConfig.getString("screen-mappings.custom1")).thenReturn("ANVIL");
        when(rawConfig.getString("screen-mappings.custom2")).thenReturn("SIGN");

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
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "LOWEST",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5,
                "gui", "&8Confirm Action",
                "LIME_CONCRETE", "&aConfirm", 11,
                "RED_CONCRETE", "&cCancel", 15,
                "PAPER", "&eInformation", 13,
                "&aConfirm", "&cCancel", "");

        var mappings = cfg.getScreenMappings();
        assertEquals(ScreenType.ANVIL, mappings.get("custom1"));
        assertEquals(ScreenType.SIGN, mappings.get("custom2"));
        // Preserves built-ins under partial custom mappings
        assertEquals(ScreenType.CHAT, mappings.get(""));
        assertEquals(ScreenType.ANVIL, mappings.get("a"));
        assertEquals(ScreenType.ANVIL, mappings.get("anvil"));
        assertEquals(ScreenType.SIGN, mappings.get("s"));
        assertEquals(ScreenType.SIGN, mappings.get("sign"));
        assertEquals(ScreenType.DIALOG, mappings.get("d"));
        assertEquals(ScreenType.DIALOG, mappings.get("dialog"));
        assertEquals(ScreenType.PLAYER, mappings.get("p"));
        assertEquals(ScreenType.PLAYER, mappings.get("player"));
        assertEquals(ScreenType.CONFIRMATION, mappings.get("c"));
        assertEquals(ScreenType.CONFIRMATION, mappings.get("confirm"));
        assertEquals(ScreenType.CONFIRMATION, mappings.get("confirmation"));
        assertEquals(ScreenType.ITEM, mappings.get("i"));
        assertEquals(ScreenType.ITEM, mappings.get("item"));
    }

    @Test
    void screenMappingsRejectsReservedKeyOverride() {
        var rawConfig = mock(YamlDocument.class);
        when(rawConfig.getKeys("screen-mappings")).thenReturn(Set.of("CONFIRM"));
        when(rawConfig.getString("screen-mappings.CONFIRM")).thenReturn("CHAT");

        assertThrows(IllegalArgumentException.class, () -> new PromptConfig(rawConfig,
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
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "LOWEST",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5,
                "gui", "&8Confirm Action",
                "LIME_CONCRETE", "&aConfirm", 11,
                "RED_CONCRETE", "&cCancel", 15,
                "PAPER", "&eInformation", 13,
                "&aConfirm", "&cCancel", ""));
    }

    @Test
    @SuppressWarnings("deprecation")
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
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "LOWEST",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Custom Title", "<blue>Yes</blue>", "Please confirm",
                "<red>No</red>", "Dismiss",
                512, true, 8, 200, "a,b,c", -10.0f, 10.0f, 0.5f, 7,
                "dialog", "&eAre you sure?",
                "EMERALD_BLOCK", "&aYes", 10,
                "REDSTONE_BLOCK", "&cNo", 16,
                "BOOK", "&6Details", 12,
                "&aYes", "&cNo", "minecraft:entity.experience_orb.pickup");

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
    void confirmationConfigDefaultsAndCustomValues() {
        var defaultCfg = makeDefaultCfg();
        var confirmCfg = defaultCfg.confirmationConfig();
        assertEquals(dev.cyr1en.promptcore.ConfirmationMode.GUI, confirmCfg.defaultMode());
        assertEquals("&8Confirm Action", confirmCfg.guiTitle());
        assertEquals("LIME_CONCRETE", confirmCfg.confirmItem().material());
        assertEquals("&aConfirm", confirmCfg.confirmItem().name());
        assertEquals(11, confirmCfg.confirmItem().slot());
        assertEquals("RED_CONCRETE", confirmCfg.cancelItem().material());
        assertEquals("&cCancel", confirmCfg.cancelItem().name());
        assertEquals(15, confirmCfg.cancelItem().slot());
        assertEquals("PAPER", confirmCfg.infoItem().material());
        assertEquals("&eInformation", confirmCfg.infoItem().name());
        assertEquals(13, confirmCfg.infoItem().slot());
        assertEquals("&aConfirm", confirmCfg.defaultConfirmLabel());
        assertEquals("&cCancel", confirmCfg.defaultCancelLabel());
        assertEquals("", confirmCfg.sound());
        assertEquals(confirmCfg, defaultCfg.confirmationScreenConfig());

        var customCfg = new PromptConfig(mock(YamlDocument.class),
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
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "LOWEST",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5,
                "dialog", "&eAre you sure?",
                "EMERALD_BLOCK", "&aYes", 10,
                "REDSTONE_BLOCK", "&cNo", 16,
                "BOOK", "&6Details", 12,
                "&aYes", "&cNo", "minecraft:entity.experience_orb.pickup");

        var customConfirm = customCfg.confirmationConfig();
        assertEquals(dev.cyr1en.promptcore.ConfirmationMode.DIALOG, customConfirm.defaultMode());
        assertEquals("&eAre you sure?", customConfirm.guiTitle());
        assertEquals("EMERALD_BLOCK", customConfirm.confirmItem().material());
        assertEquals(10, customConfirm.confirmItem().slot());
        assertEquals("REDSTONE_BLOCK", customConfirm.cancelItem().material());
        assertEquals(16, customConfirm.cancelItem().slot());
        assertEquals("BOOK", customConfirm.infoItem().material());
        assertEquals(12, customConfirm.infoItem().slot());
        assertEquals("&aYes", customConfirm.defaultConfirmLabel());
        assertEquals("&cNo", customConfirm.defaultCancelLabel());
        assertEquals("minecraft:entity.experience_orb.pickup", customConfirm.sound());
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
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "LOWEST",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5,
                "gui", "&8Confirm Action",
                "LIME_CONCRETE", "&aConfirm", 11,
                "RED_CONCRETE", "&cCancel", 15,
                "PAPER", "&eInformation", 13,
                "&aConfirm", "&cCancel", "");

        assertEquals("%s", cfg.getFilterFormat("World"));
    }

    @Test
    void hasValidatorReturnsTrueForConfiguredAlias() {
        var rawConfig = mock(YamlDocument.class);
        when(rawConfig.getKeys("Input-Validation")).thenReturn(Set.of("Integer-Sample"));
        when(rawConfig.getKeys("Input-Validation.Integer-Sample")).thenReturn(Set.of("Alias", "Regex", "Err-Message"));
        when(rawConfig.getString("Input-Validation.Integer-Sample.Alias")).thenReturn("is");
        when(rawConfig.getString("Input-Validation.Integer-Sample.Regex")).thenReturn("^\\d+");

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
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "LOWEST",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5,
                "gui", "&8Confirm Action",
                "LIME_CONCRETE", "&aConfirm", 11,
                "RED_CONCRETE", "&cCancel", 15,
                "PAPER", "&eInformation", 13,
                "&aConfirm", "&cCancel", "");

        assertTrue(cfg.hasValidator("is"));
        assertFalse(cfg.hasValidator("unknown"));
        assertFalse(cfg.hasValidator(null));
        assertFalse(cfg.hasValidator(""));
    }

    @Test
    void getInputValidatorThrowsOnUnknownAlias() {
        var rawConfig = mock(YamlDocument.class);
        when(rawConfig.getKeys("Input-Validation")).thenReturn(Set.of());

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
                true, "&7[&c&l✘&7]", "&7Click here to cancel command completion", "LOWEST",
                "bottom", "OAK_SIGN",
                "is", "^\\d+", "&cPlease enter a valid integer!",
                "ss", "[A-Za-z ]+", "&cInput must only consist letters of the alphabet!",
                "Prompt", "<green>Confirm</green>", "Confirm this action",
                "<red>Cancel</red>", "Cancel this action",
                256, false, 4, 200, "", 0.0f, 100.0f, 1.0f, 5,
                "gui", "&8Confirm Action",
                "LIME_CONCRETE", "&aConfirm", 11,
                "RED_CONCRETE", "&cCancel", 15,
                "PAPER", "&eInformation", 13,
                "&aConfirm", "&cCancel", "");

        assertThrows(IllegalArgumentException.class, () -> cfg.getInputValidator("unknown", null, null));
        assertInstanceOf(dev.cyr1en.promptpaper.validation.NoopValidator.class, cfg.getInputValidator(null, null, null));
        assertInstanceOf(dev.cyr1en.promptpaper.validation.NoopValidator.class, cfg.getInputValidator("", null, null));
    }

    @Test
    void commandPrompterConfigTimeoutBounds() {
        var rawConfig = mock(YamlDocument.class);

        // 0 (disabled), 1, and 3600 are valid
        var cfg0 = new CommandPrompterConfig(
                rawConfig, "[P] ", 0, "cancel", 256, true, false, true, true, true,
                "<.*?>", "<", ">", "{", "}", ":", "\\", true, List.of(), List.of(), true, 0, List.of(), "en_US");
        assertEquals(0, cfg0.promptTimeout());

        var cfg1 = new CommandPrompterConfig(
                rawConfig, "[P] ", 1, "cancel", 256, true, false, true, true, true,
                "<.*?>", "<", ">", "{", "}", ":", "\\", true, List.of(), List.of(), true, 0, List.of(), "en_US");
        assertEquals(1, cfg1.promptTimeout());

        var cfg3600 = new CommandPrompterConfig(
                rawConfig, "[P] ", 3600, "cancel", 256, true, false, true, true, true,
                "<.*?>", "<", ">", "{", "}", ":", "\\", true, List.of(), List.of(), true, 0, List.of(), "en_US");
        assertEquals(3600, cfg3600.promptTimeout());

        // Negative and > 3600 throw IllegalArgumentException
        assertThrows(
                IllegalArgumentException.class,
                () -> new CommandPrompterConfig(
                        rawConfig, "[P] ", -1, "cancel", 256, true, false, true, true, true,
                        "<.*?>", "<", ">", "{", "}", ":", "\\", true, List.of(), List.of(), true, 0, List.of(), "en_US"));

        assertThrows(
                IllegalArgumentException.class,
                () -> new CommandPrompterConfig(
                        rawConfig, "[P] ", 3601, "cancel", 256, true, false, true, true, true,
                        "<.*?>", "<", ">", "{", "}", ":", "\\", true, List.of(), List.of(), true, 0, List.of(), "en_US"));
    }
}
