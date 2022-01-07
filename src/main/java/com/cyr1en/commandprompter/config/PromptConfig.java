package com.cyr1en.commandprompter.config;

import com.cyr1en.commandprompter.config.annotations.field.ConfigNode;
import com.cyr1en.commandprompter.config.annotations.field.NodeComment;
import com.cyr1en.commandprompter.config.annotations.field.NodeDefault;
import com.cyr1en.commandprompter.config.annotations.field.NodeName;
import com.cyr1en.commandprompter.config.annotations.type.ConfigHeader;
import com.cyr1en.commandprompter.config.annotations.type.ConfigPath;
import com.cyr1en.commandprompter.config.annotations.type.Configuration;

@Configuration
@ConfigPath("prompt-config.yml")
@ConfigHeader({"Prompts", "Configuration"})
public record PromptConfig(
        @ConfigNode
        @NodeName("PlayerUI.Skull-Name-Format")
        @NodeDefault("&6%s")
        @NodeComment({"PlayerUI formatting"})
        String skullNameFormat,

        @ConfigNode
        @NodeName("PlayerUI.Previous.Item")
        @NodeDefault("Feather")
        String previousItem,

        @ConfigNode
        @NodeName("PlayerUI.Previous.Text")
        @NodeDefault("&6<")
        String previousText,

        @ConfigNode
        @NodeName("PlayerUI.Next.Item")
        @NodeDefault("Feather")
        String nextItem,

        @ConfigNode
        @NodeName("PlayerUI.Next.Text")
        @NodeDefault("&6>")
        String nextText,

        @ConfigNode
        @NodeName("PlayerUI.Sorted")
        @NodeDefault("false")
        boolean sorted,

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
                "Enchanted - Do you want the item enchanted?"
        })
        boolean enableTitle,

        @ConfigNode
        @NodeName("AnvilGUI.Item")
        @NodeDefault("Paper")
        String anvilItem,

        @ConfigNode
        @NodeName("AnvilGUI.Enchanted")
        @NodeDefault("false")
        boolean anvilEnchanted

) {
}
