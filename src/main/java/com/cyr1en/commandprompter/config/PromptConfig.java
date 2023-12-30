package com.cyr1en.commandprompter.config;

import com.cyr1en.commandprompter.api.prompt.InputValidator;
import com.cyr1en.commandprompter.config.annotations.field.*;
import com.cyr1en.commandprompter.config.annotations.type.ConfigHeader;
import com.cyr1en.commandprompter.config.annotations.type.ConfigPath;
import com.cyr1en.commandprompter.config.annotations.type.Configuration;
import com.cyr1en.commandprompter.prompt.ui.CacheFilter;
import com.cyr1en.commandprompter.prompt.validators.NoopValidator;
import com.cyr1en.commandprompter.prompt.validators.OnlinePlayerValidator;
import com.cyr1en.commandprompter.prompt.validators.RegexValidator;
import com.cyr1en.kiso.mc.configuration.base.Config;

import java.util.regex.Pattern;

@Configuration
@ConfigPath("prompt-config.yml")
@ConfigHeader({"Prompts", "Configuration"})
public record PromptConfig(
        Config rawConfig,

        // ============================== Player UI ==============================
        @ConfigNode
        @NodeName("PlayerUI.Skull-Name-Format")
        @NodeDefault("&6%s")
        @NodeComment({
                "PlayerUI formatting", "",
                "Skull-Name-Format - The display name format",
                "                    for the player heads", "",
                "Skull-Custom-Model-Data - The custom model data for the",
                "                          player heads", "",
                "Size - the size of the UI (multiple of 9, between 18-54)", "",
                "Cache-Size - Size for the head cache", "",
                "Cache-Delay - Delay in ticks after the player", "",
                "              joins before their head gets cached", "",
                "Sorted - Should the player heads be sorted?",
                "Empty-Message - Message to be displayed when the", "",
                "                head cache is empty", "",
                "Filter-Format - The format for the heads depending", "",
                "                on what filter is used", "",
        })
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
        @NodeComment({
                "AnvilUI formatting",
                "",
                "Enable-Title - Show the first line of the prompt",
                "(if with {br}) as title of Anvil GUI",
                "",
                "Item - The item to place on the Anvil GUI",
                "",
                "Enchanted - Do you want the item enchanted?",
                "",
                "Custom-Title - If title is enabled, and if custom",
                "title is not empty, CommandPrompter will use this instead",
                "",
                "Prompt-Message - The message to be displayed on the",
                "Anvil GUI"
        })
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
        @NodeName("AnvilGUI.Item.Material")
        @NodeDefault("Paper")
        String anvilItem,

        @ConfigNode
        @NodeName("AnvilGUI.Item.Custom-Model-Data")
        @NodeDefault("0")
        int itemCustomModelData,

        @ConfigNode
        @NodeName("AnvilGUI.Enchanted")
        @NodeDefault("false")
        boolean anvilEnchanted,

        // ============================== Text Prompt ==============================
        @ConfigNode
        @NodeName("TextPrompt.Clickable-Cancel")
        @NodeDefault("true")
        @NodeComment({
                "Text Prompt Config",
                "",
                "Clickable-Cancel - Enable clickable cancel",
                "",
                "Cancel-Message - Clickable text message",
                "",
                "Cancel-Hover-Message - Message to show when",
                "                       a player hovers over",
                "                       the clickable cancel message.",
                "",
                "Response-Listener-Priority - Change the priority of",
                "                             the response listener",
                "Available Priority - DEFAULT, LOW, LOWEST, NORMAL, HIGH",
                "                     HIGHEST"
        })
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
        @NodeComment({
                "Sign UI Settings",
                "",
                "Material - The material to use for the sign",
                "",
                "Input-Field-Location - Which line should the answer",
                "                       be read from.",
                "",
                "Valid Input Field Locations",
                "top - line 1 of the sign will be considered as the field.",
                "top-aggregate - the prompt will be placed at the lowest",
                "               possible line and the input would be",
                "               the remaining lines on top.",
                "bottom - line 4 of the sign will be considered as the",
                "        field.",
                "bottom-aggregate - the prompt will be placed at line",
                "                  1 and the input would be the",
                "                  remaining lines at the bottom",
                "",
                "Check wiki for Sign UI",
                "https://github.com/CyR1en/CommandPrompter/wiki/Prompts"
        })
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
        @NodeComment({
                "Input Validation",
                "",
                "Alias - The alias of the input validation",
                "",
                "Regex - Regex to use for the input validation"
        })
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
        String strSampleErrMessage

) implements AliasedSection {
    private String findIVRegexCheckInConfig(String alias) {
        return getIVValue("Alias", alias, "Regex");
    }

    private String getIVErrMessage(String alias) {
        return getIVValue("Alias", alias, "Err-Message");
    }

    private String getIVValue(String key, String keyVal, String query) {
        return getInputValidationValue("Input-Validation", key, keyVal, query);
    }

    public InputValidator getInputValidator(String alias) {
        if (alias == null || alias.isBlank())
            return new NoopValidator();
        var isPlayer = Boolean.parseBoolean(getIVValue("Alias", alias, "Online-Player"));
        if (isPlayer)
            return new OnlinePlayerValidator(alias, getIVErrMessage(alias));
        var regex = findIVRegexCheckInConfig(alias);
        if (regex != null && !regex.isBlank())
            return new RegexValidator(alias, Pattern.compile(regex), getIVErrMessage(alias));
        return new NoopValidator();
    }

    /**
     * Gets the format for a cache filter.
     * <p>
     * This function exists because future cache filters will not be dynamically added.
     * Instead, the user has the option to define their own cache filter format.
     *
     * <p>
     * The format for world and radial filters are pre-defined in the config as an example.
     *
     * @param filter the cache filter to get the format of
     * @return the format of the cache filter
     */
    public String getFilterFormat(CacheFilter filter) {
        var key = filter.getConfigKey();
        var format = rawConfig().getString(key);
        return format != null ? format : "";
    }

}
