package com.cyr1en.commandprompter.config;

import com.cyr1en.commandprompter.config.annotations.field.*;
import com.cyr1en.commandprompter.config.annotations.type.ConfigHeader;
import com.cyr1en.commandprompter.config.annotations.type.ConfigPath;
import com.cyr1en.commandprompter.config.annotations.type.Configuration;
import com.cyr1en.kiso.mc.configuration.base.Config;

@Configuration
@ConfigPath("prompt-config.yml")
@ConfigHeader({"Prompts", "Configuration"})
public record PromptConfig(
        Config rawConfig,

        @ConfigNode
        @NodeName("PlayerUI.Skull-Name-Format")
        @NodeDefault("&6%s")
        @NodeComment({
                "PlayerUI formatting", "",
                "Skull-Name-Format - The display name format",
                "                    for the player heads", "",
                "Size - the size of the UI (multiple of 9, between 18-54)", "",
                "Cache-Size - Size for the head cache", "",
                "Cache-Delay - Delay in ticks after the player", "",
                "              joins before their head gets cached", "",
                "Sorted - Should the player heads be sorted?",
                "Per-World - Only show player in the current world?", "",
                "Empty-Message - Message to be displayed when the", "",
                "                head cache is empty", "",
        })
        String skullNameFormat,

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
        @NodeName("PlayerUI.Per-World")
        @NodeDefault("false")
        boolean isPerWorld,

        @ConfigNode
        @NodeName("PlayerUI.Empty-Message")
        @NodeDefault("&cNo players found!")
        String emptyMessage,

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
                "title is not empty, CommandPrompter will use this instead"
        })
        boolean enableTitle,

        @ConfigNode
        @NodeName("AnvilGUI.Custom-Title")
        @NodeDefault("")
        String customTitle,

        @ConfigNode
        @NodeName("AnvilGUI.Item")
        @NodeDefault("Paper")
        String anvilItem,

        @ConfigNode
        @NodeName("AnvilGUI.Enchanted")
        @NodeDefault("false")
        boolean anvilEnchanted,

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

        @ConfigNode
        @NodeName("SignUI.Input-Field-Location")
        @NodeDefault("bottom")
        @NodeComment({
                "Sign UI Settings",
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
        String inputFieldLocation
) {
}
