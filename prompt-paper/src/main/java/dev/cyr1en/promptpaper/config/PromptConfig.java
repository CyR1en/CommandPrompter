package dev.cyr1en.promptpaper.config;

import dev.cyr1en.promptpaper.config.annotations.field.*;
import dev.cyr1en.promptpaper.config.annotations.type.ConfigHeader;
import dev.cyr1en.promptpaper.config.annotations.type.ConfigPath;
import dev.cyr1en.promptpaper.config.annotations.type.Configuration;
import com.cyr1en.kiso.mc.configuration.base.Config;
import com.cyr1en.kiso.mc.configuration.base.ConfigManager;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.sub.*;
import dev.cyr1en.promptpaper.validation.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;

/**
 * Prompt syntax, screen mappings, and UI defaults loaded from {@code prompt-config.yml}.
 *
 * <p>This record holds every configurable aspect of the prompt system: Player UI layout,
 * Anvil GUI items, text-prompt cancel behaviour, sign UI material, dialog UI defaults,
 * and input-validation aliases. Grouped sub-records ({@link PlayerUiConfig},
 * {@link AnvilGuiConfig}, etc.) are produced by accessor methods for ergonomic consumption.
 *
 * <p>The {@code screen-mappings} section maps command prefixes to {@link ScreenType} values.
 */
@Configuration
@ConfigPath("prompt-config.yml")
@ConfigHeader({"Prompts", "Configuration"})
public record PromptConfig(
        Config rawConfig,

        // ============================== Player UI ==============================
        @ConfigNode
        @NodeName("PlayerUI.Skull-Name-Format")
        @NodeDefault("&6%s")
        @NodeComment({"PlayerUI formatting", "",
                "Skull-Name-Format - The display name format for the player heads",
                "Size - the size of the UI (multiple of 9, between 18-54)",
                "Cache-Size - Size for the head cache",
                "Cache-Delay - Delay in ticks after the player joins before their head gets cached",
                "Sorted - Should the player heads be sorted?",
                "Empty-Message - Message to be displayed when the head cache is empty"})
        String skullNameFormat,

        @ConfigNode
        @NodeName("PlayerUI.Skull-Custom-Model-Data")
        @NodeDefault("0")
        int skullCustomModelData,

        @ConfigNode
        @NodeName("PlayerUI.Size")
        @NodeDefault("54")
        int playerUISize,

        @ConfigNode
        @NodeName("PlayerUI.Cache-Size")
        @NodeDefault("256")
        int cacheSize,

        @ConfigNode
        @NodeName("PlayerUI.Cache-Delay")
        @NodeDefault("1")
        @IntegerConstraint(min = 0, max = 2400)
        int cacheDelay,

        @ConfigNode
        @NodeName("PlayerUI.Previous.Item")
        @NodeDefault("Feather")
        String previousItem,

        @ConfigNode
        @NodeName("PlayerUI.Previous.Custom-Model-Data")
        @NodeDefault("0")
        int previousCustomModelData,

        @ConfigNode
        @NodeName("PlayerUI.Previous.Column")
        @NodeDefault("3")
        int previousColumn,

        @ConfigNode
        @NodeName("PlayerUI.Previous.Text")
        @NodeDefault("&7◀◀ Previous")
        String previousText,

        @ConfigNode
        @NodeName("PlayerUI.Next.Item")
        @NodeDefault("Feather")
        String nextItem,

        @ConfigNode
        @NodeName("PlayerUI.Next.Custom-Model-Data")
        @NodeDefault("0")
        int nextCustomModelData,

        @ConfigNode
        @NodeName("PlayerUI.Next.Column")
        @NodeDefault("7")
        int nextColumn,

        @ConfigNode
        @NodeName("PlayerUI.Next.Text")
        @NodeDefault("Next ▶▶")
        String nextText,

        @ConfigNode
        @NodeName("PlayerUI.Cancel.Item")
        @NodeDefault("Barrier")
        String cancelItem,

        @ConfigNode
        @NodeName("PlayerUI.Cancel.Custom-Model-Data")
        @NodeDefault("0")
        int cancelCustomModelData,

        @ConfigNode
        @NodeName("PlayerUI.Cancel.Column")
        @NodeDefault("5")
        int cancelColumn,

        @ConfigNode
        @NodeName("PlayerUI.Cancel.Text")
        @NodeDefault("&7Cancel ✘")
        String cancelText,

        @ConfigNode
        @NodeName("PlayerUI.Search.Item")
        @NodeDefault("Name_Tag")
        String searchItem,

        @ConfigNode
        @NodeName("PlayerUI.Search.Custom-Model-Data")
        @NodeDefault("0")
        int searchCustomModelData,

        @ConfigNode
        @NodeName("PlayerUI.Search.Column")
        @NodeDefault("9")
        int searchColumn,

        @ConfigNode
        @NodeName("PlayerUI.Search.Text")
        @NodeDefault("&6Search ⌕")
        String searchText,

        @ConfigNode
        @NodeName("PlayerUI.Search.AnvilItem.Title")
        @NodeDefault("&6&lPlayer Search")
        String searchAnvilItemTitle,

        @ConfigNode
        @NodeName("PlayerUI.Search.AnvilItem.Material")
        @NodeDefault("PAPER")
        String searchAnvilItem,

        @ConfigNode
        @NodeName("PlayerUI.Search.AnvilItem.CustomModelData")
        @NodeDefault("0")
        int searchAnvilItemCustomModelData,

        @ConfigNode
        @NodeName("PlayerUI.Search.AnvilItem.Text")
        @NodeDefault("&6Enter Player Name")
        String searchAnvilItemText,

        @ConfigNode
        @NodeName("PlayerUI.Sorted")
        @NodeDefault("false")
        boolean sorted,

        @ConfigNode
        @NodeName("PlayerUI.Empty-Message")
        @NodeDefault("&cNo players found!")
        String emptyMessage,

        @ConfigNode
        @NodeName("PlayerUI.Filter-Format.World")
        @NodeDefault("&6\uD804\uDC4D %s")
        String worldFilterFormat,

        @ConfigNode
        @NodeName("PlayerUI.Filter-Format.Radial")
        @NodeDefault("&cᯤ %s")
        String radialFilterFormat,

        // ============================== Anvil UI ==============================
        @ConfigNode
        @NodeName("AnvilGUI.Enable-Title")
        @NodeDefault("true")
        @NodeComment({"AnvilUI formatting", "",
                "Enable-Title - Show the first line of the prompt (if with {br}) as title of Anvil GUI",
                "Enable-Cancel-Item - Show a cancel item on the right slot input slot.",
                "Item - The Left item to place on the Anvil GUI",
                "Enchanted - Do you want the item enchanted?",
                "ResultItem - The Result item to place on the Anvil GUI",
                "Hide-Tooltips - Hide tooltips of item (1.21.2 OR ABOVE)",
                "Custom-Title - If title is enabled, and if custom title is not empty, use this instead",
                "Prompt-Message - The message to be displayed on the Anvil GUI"})
        boolean enableTitle,

        @ConfigNode
        @NodeName("AnvilGUI.Custom-Title")
        @NodeDefault("")
        String customTitle,

        @ConfigNode
        @NodeName("AnvilGUI.Prompt-Message")
        @NodeDefault("")
        String promptMessage,

        @ConfigNode
        @NodeName("AnvilGUI.Enable-Cancel-Item")
        @NodeDefault("false")
        boolean enableCancelItem,

        @ConfigNode
        @NodeName("AnvilGUI.Item.Material")
        @NodeDefault("Paper")
        String anvilItem,

        @ConfigNode
        @NodeName("AnvilGUI.Item.HideTooltips")
        @NodeDefault("false")
        boolean itemHideTooltips,

        @ConfigNode
        @NodeName("AnvilGUI.Item.Custom-Model-Data")
        @NodeDefault("0")
        int itemCustomModelData,

        @ConfigNode
        @NodeName("AnvilGUI.Item.Enchanted")
        @NodeDefault("false")
        boolean itemAnvilEnchanted,

        @ConfigNode
        @NodeName("AnvilGUI.ResultItem.Material")
        @NodeDefault("Paper")
        String anvilResultItem,

        @ConfigNode
        @NodeName("AnvilGUI.ResultItem.HideTooltips")
        @NodeDefault("false")
        boolean resultItemHideTooltips,

        @ConfigNode
        @NodeName("AnvilGUI.ResultItem.Custom-Model-Data")
        @NodeDefault("0")
        int resultItemCustomModelData,

        @ConfigNode
        @NodeName("AnvilGUI.ResultItem.Enchanted")
        @NodeDefault("false")
        boolean resultItemAnvilEnchanted,

        @ConfigNode
        @NodeName("AnvilGUI.CancelItem.Material")
        @NodeDefault("Barrier")
        String anvilCancelItem,

        @ConfigNode
        @NodeName("AnvilGUI.CancelItem.HideTooltips")
        @NodeDefault("false")
        boolean cancelItemHideTooltips,

        @ConfigNode
        @NodeName("AnvilGUI.CancelItem.Custom-Model-Data")
        @NodeDefault("0")
        int cancelItemCustomModelData,

        @ConfigNode
        @NodeName("AnvilGUI.CancelItem.Enchanted")
        @NodeDefault("false")
        boolean cancelItemAnvilEnchanted,

        @ConfigNode
        @NodeName("AnvilGUI.CancelItem.HoverText")
        @NodeDefault("&cClick to Cancel")
        String cancelItemHoverText,

        // ============================== Text Prompt ==============================
        @ConfigNode
        @NodeName("TextPrompt.Clickable-Cancel")
        @NodeDefault("true")
        @NodeComment({"Text Prompt Config", "",
                "Clickable-Cancel - Enable clickable cancel",
                "Cancel-Message - Clickable text message",
                "Cancel-Hover-Message - Message to show when a player hovers over the clickable cancel message.",
                "Response-Listener-Priority - Change the priority of the response listener",
                "Available Priority - DEFAULT, LOW, LOWEST, NORMAL, HIGH, HIGHEST"})
        boolean sendCancelText,

        @ConfigNode
        @NodeName("TextPrompt.Cancel-Message")
        @NodeDefault("&7[&c&l✘&7]")
        String textCancelMessage,

        @ConfigNode
        @NodeName("TextPrompt.Cancel-Hover-Message")
        @NodeDefault("&7Click here to cancel command completion")
        String textCancelHoverMessage,

        @ConfigNode
        @NodeName("TextPrompt.Response-Listener-Priority")
        @NodeDefault("DEFAULT")
        String responseListenerPriority,

        // ============================== Sign UI ==============================
        @ConfigNode
        @NodeName("SignUI.Input-Field-Location")
        @NodeDefault("bottom")
        @NodeComment({"Sign UI Settings", "",
                "Input-Field-Location - Which line should the answer be read from.",
                "Valid locations: top, top-aggregate, bottom, bottom-aggregate"})
        String inputFieldLocation,

        @ConfigNode
        @NodeName("SignUI.Material")
        @NodeDefault("OAK_SIGN")
        @Match(regex = "(.*SIGN.*)")
        String signMaterial,

        // ============================== Input Validation ==============================
        @ConfigNode
        @NodeName("Input-Validation.Integer-Sample.Alias")
        @NodeDefault("is")
        @NodeComment({"Input Validation", "", "Alias - The alias of the input validation",
                "Regex - Regex to use for the input validation"})
        String intSampleAlias,

        @ConfigNode
        @NodeName("Input-Validation.Integer-Sample.Regex")
        @NodeDefault("^\\d+")
        String intSampleRegex,

        @ConfigNode
        @NodeName("Input-Validation.Integer-Sample.Err-Message")
        @NodeDefault("&cPlease enter a valid integer!")
        String intSampleErrMessage,

        @ConfigNode
        @NodeName("Input-Validation.Alpha-Sample.Alias")
        @NodeDefault("ss")
        String strSampleAlias,

        @ConfigNode
        @NodeName("Input-Validation.Alpha-Sample.Regex")
        @NodeDefault("[A-Za-z ]+")
        String strSampleRegex,

        @ConfigNode
        @NodeName("Input-Validation.Alpha-Sample.Err-Message")
        @NodeDefault("&cInput must only consist letters of the alphabet!")
        String strSampleErrMessage,

        // ============================== Dialog UI ==============================
        @ConfigNode
        @NodeName("DialogUI.Title")
        @NodeDefault("Prompt")
        @NodeComment({"Dialog UI Settings", "",
                "Used by the <d:...> prompt tag.",
                "No filter / unknown filter -> text field (single-line or multi-line).",
                "Available kinds:",
                "  <d:num[a,b]:Display>, <d:num[a,b,s]:Display>, <d:num[a,b,s,i]:Display>",
                "  <d:choice[opt1,opt2,...]:Display> - single-select dropdown",
                "Compound form: split sub-tags with ' && ' inside a single <d:...> block:",
                "  <d:choice[set,add]:Sub && d:num[0,24]:Value>",
                "  renders ONE dialog with TWO input rows; block-level flags (-ds, -iv:alias, ...)",
                "  apply to the whole block.",
                "Title - the dialog window title.",
                "Confirm-Button / Cancel-Button - submit / dismiss button label+tooltip.",
                "Defaults.Text.MaxLength - max chars for text input.",
                "Defaults.Text.Multiline - allow multi-line text input.",
                "Defaults.Text.MultilineMaxLines - line cap when multiline is true.",
                "Defaults.Number.Min/Max/Step/Initial - defaults for the <d:num...> variant.",
                "      Initial is optional; if omitted, uses (min + max) / 2.",
                "Defaults.Tab.MaxButtons - threshold for the <d:tab:Display> form. When the",
                "      completion count is at or below this number, the dialog shows a button",
                "      grid (one per completion). Above this, it falls back to a text input."})
        String dialogTitle,

        @ConfigNode
        @NodeName("DialogUI.Confirm-Button.Label")
        @NodeDefault("<green>Confirm</green>")
        String dialogConfirmLabel,

        @ConfigNode
        @NodeName("DialogUI.Confirm-Button.Tooltip")
        @NodeDefault("Confirm this action")
        String dialogConfirmTooltip,

        @ConfigNode
        @NodeName("DialogUI.Cancel-Button.Label")
        @NodeDefault("<red>Cancel</red>")
        String dialogCancelLabel,

        @ConfigNode
        @NodeName("DialogUI.Cancel-Button.Tooltip")
        @NodeDefault("Cancel this action")
        String dialogCancelTooltip,

        @ConfigNode
        @NodeName("DialogUI.Defaults.Text.MaxLength")
        @NodeDefault("256")
        @IntegerConstraint(min = 1, max = 8192)
        int dialogTextMaxLength,

        @ConfigNode
        @NodeName("DialogUI.Defaults.Text.Multiline")
        @NodeDefault("false")
        boolean dialogTextMultiline,

        @ConfigNode
        @NodeName("DialogUI.Defaults.Text.MultilineMaxLines")
        @NodeDefault("4")
        @IntegerConstraint(min = 1, max = 16)
        int dialogTextMultilineMaxLines,

        @ConfigNode
        @NodeName("DialogUI.Defaults.Choice.Default-Options")
        @NodeDefault("")
        @NodeComment({"Default options for the <d:choice[...]:...> form when no",
                "explicit list is provided. Comma-separated. Empty by default."})
        String dialogChoiceDefaults,

        @ConfigNode
        @NodeName("DialogUI.Defaults.Number.Min")
        @NodeDefault("0")
        float dialogNumberMin,

        @ConfigNode
        @NodeName("DialogUI.Defaults.Number.Max")
        @NodeDefault("100")
        float dialogNumberMax,

        @ConfigNode
        @NodeName("DialogUI.Defaults.Number.Step")
        @NodeDefault("1")
        float dialogNumberStep,

        @ConfigNode
        @NodeName("DialogUI.Defaults.Tab.MaxButtons")
        @NodeDefault("5")
        @IntegerConstraint(min = 1, max = 256)
        int dialogTabMaxButtons

) implements AliasedSection {

    // ============================== Screen Mappings ==============================

    /**
     * Reads the {@code screen-mappings} YAML section and returns an immutable prefix-to-type map.
     *
     * <p>Falls back to a sensible default mapping (empty→CHAT, a→ANVIL, s→SIGN,
     * d→DIALOG, p→PLAYER) if the section is absent or empty.
     */
    public Map<String, ScreenType> getScreenMappings() {
        var map = new HashMap<String, ScreenType>();
        var section = rawConfig.getConfigurationSection("screen-mappings");
        if (section != null) {
            for (var key : section.getKeys(false)) {
                try {
                    var val = section.getString(key);
                    if (val == null) continue;
                    map.put(key, ScreenType.valueOf(val.toUpperCase()));
                } catch (Exception e) {
                    // skip invalid entries
                }
            }
        }
        if (map.isEmpty()) {
            map.put("", ScreenType.CHAT);
            map.put("a", ScreenType.ANVIL);
            map.put("s", ScreenType.SIGN);
            map.put("d", ScreenType.DIALOG);
            map.put("p", ScreenType.PLAYER);
        }
        return Map.copyOf(map);
    }

    // ============================== Sub-records ==============================

    /** Grouped Player UI configuration. */
    public PlayerUiConfig playerUi() {
        return new PlayerUiConfig(
                skullNameFormat,
                skullCustomModelData,
                playerUISize,
                cacheSize,
                cacheDelay,
                new ControlItem(previousItem, previousCustomModelData, previousColumn, previousText),
                new ControlItem(nextItem, nextCustomModelData, nextColumn, nextText),
                new ControlItem(cancelItem, cancelCustomModelData, cancelColumn, cancelText),
                new ControlItem(searchItem, searchCustomModelData, searchColumn, searchText),
                new SearchAnvilItem(searchAnvilItemTitle, searchAnvilItem, searchAnvilItemCustomModelData, searchAnvilItemText),
                sorted,
                emptyMessage,
                worldFilterFormat,
                radialFilterFormat);
    }

    /** Grouped Anvil GUI configuration. */
    public AnvilGuiConfig anvilGui() {
        return new AnvilGuiConfig(
                enableTitle,
                customTitle,
                promptMessage,
                enableCancelItem,
                anvilItem,
                itemHideTooltips,
                itemCustomModelData,
                itemAnvilEnchanted,
                anvilResultItem,
                resultItemHideTooltips,
                resultItemCustomModelData,
                resultItemAnvilEnchanted,
                anvilCancelItem,
                cancelItemHideTooltips,
                cancelItemCustomModelData,
                cancelItemAnvilEnchanted,
                cancelItemHoverText,
                null);
    }

    /** Grouped Sign UI configuration. */
    public SignUiConfig signUi() {
        return new SignUiConfig(inputFieldLocation, signMaterial);
    }

    /** Grouped text-prompt configuration. */
    public TextPromptConfig textPrompt() {
        return new TextPromptConfig(
                sendCancelText,
                textCancelMessage,
                textCancelHoverMessage,
                responseListenerPriority);
    }

    /** Grouped dialog configuration. Reads from the new {@code DialogUI:} section. */
    public DialogConfig dialogConfig() {
        return new DialogConfig(
                dialogTitle,
                new DialogConfig.ConfirmButton(dialogConfirmLabel, dialogConfirmTooltip),
                new DialogConfig.CancelButton(dialogCancelLabel, dialogCancelTooltip),
                new DialogConfig.TextDefaults(
                        dialogTextMaxLength, dialogTextMultiline, dialogTextMultilineMaxLines),
                new DialogConfig.ChoiceDefaults(
                        dialogChoiceDefaults == null || dialogChoiceDefaults.isBlank()
                                ? java.util.List.of()
                                : java.util.Arrays.stream(dialogChoiceDefaults.split(","))
                                        .map(String::trim)
                                        .filter(s -> !s.isEmpty())
                                        .toList()),
                new DialogConfig.NumberDefaults(dialogNumberMin, dialogNumberMax, dialogNumberStep, null),
                new DialogConfig.TabDefaults(dialogTabMaxButtons));
    }

    // ============================== Dialog Settings (legacy) ==============================
    /** @deprecated use {@link #dialogConfig()} */
    @Deprecated
    public String dialogTitle() {
        return dialogConfig().title();
    }

    /** @deprecated use {@link #dialogConfig()} */
    @Deprecated
    public String dialogConfirmLabel() {
        return dialogConfig().confirm().label();
    }

    /** @deprecated use {@link #dialogConfig()} */
    @Deprecated
    public String dialogConfirmTooltip() {
        return dialogConfig().confirm().tooltip();
    }

    /** @deprecated use {@link #dialogConfig()} */
    @Deprecated
    public String dialogCancelLabel() {
        return dialogConfig().cancel().label();
    }

    /** @deprecated use {@link #dialogConfig()} */
    @Deprecated
    public String dialogCancelTooltip() {
        return dialogConfig().cancel().tooltip();
    }

    // ============================== Filter Format ==============================

    /**
     * Returns the display format for a player-UI filter key (e.g. {@code "World"}, {@code "Radial"}).
     *
     * @param key the filter key
     * @return a {@code %s} placeholder format string
     */
    public String getFilterFormat(String key) {
        var section = rawConfig.getConfigurationSection("PlayerUI.Filter-Format");
        if (section == null) return "%s";
        return section.getString(key, "%s");
    }

    @Override
    public Config rawConfig() {
        return rawConfig;
    }

    // ============================== Input Validation Factory ==============================

    /**
     * Resolves an {@link InputValidator} for the given alias, combining regex, JS-expression,
     * and online-player checks as configured in {@code Input-Validation} YAML entries.
     *
     * @param alias  the validator alias (e.g. {@code "is"}, {@code "ss"})
     * @param player the player being validated (used by JS-expression and player validators)
     * @param plugin the plugin instance (needed for JS-expression evaluation)
     * @return a compound or single validator, or a no-op if no checks are configured
     */
    public InputValidator getInputValidator(String alias, Player player, CommandPrompter plugin) {
        if (alias == null || alias.isBlank())
            return new NoopValidator();

        var validators = findValidators(alias, player, plugin);
        if (validators.length == 1)
            return validators[0];
        else if (validators.length > 1) {
            return new CompoundedValidator(alias, getIVErrMessage(alias), player, validators);
        }

        return new NoopValidator();
    }

    /** Builds validators from the YAML entries matching the given alias. */
    private InputValidator[] findValidators(String alias, Player player, CommandPrompter plugin) {
        var list = new ArrayList<InputValidator>();

        var jsExpr = getIVValue("Alias", alias, "JS-Expression");
        if (jsExpr != null && !jsExpr.isBlank())
            list.add(new JSExprValidator(alias, jsExpr, getIVErrMessage(alias), player, plugin));

        var regex = findIVRegexCheckInConfig(alias);
        if (regex != null && !regex.isBlank())
            list.add(new RegexValidator(alias, Pattern.compile(regex), getIVErrMessage(alias), player));

        var isPlayer = Boolean.parseBoolean(getIVValue("Alias", alias, "Online-Player"));
        if (isPlayer)
            list.add(new OnlinePlayerValidator(alias, getIVErrMessage(alias), player));

        return list.toArray(new InputValidator[0]);
    }

    /** Shorthand for {@link #getInputValidationValue} targeting the {@code "Input-Validation"} section. */
    private String getIVValue(String key, String keyVal, String query) {
        return getInputValidationValue("Input-Validation", key, keyVal, query);
    }

    private String getIVErrMessage(String alias) {
        return getIVValue("Alias", alias, "Err-Message");
    }

    private String findIVRegexCheckInConfig(String alias) {
        return getIVValue("Alias", alias, "Regex");
    }
}
