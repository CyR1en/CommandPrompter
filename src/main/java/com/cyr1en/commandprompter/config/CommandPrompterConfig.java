package com.cyr1en.commandprompter.config;

import com.cyr1en.commandprompter.config.annotations.field.ConfigNode;
import com.cyr1en.commandprompter.config.annotations.field.NodeComment;
import com.cyr1en.commandprompter.config.annotations.field.NodeDefault;
import com.cyr1en.commandprompter.config.annotations.field.NodeName;
import com.cyr1en.commandprompter.config.annotations.type.ConfigHeader;
import com.cyr1en.commandprompter.config.annotations.type.ConfigPath;
import com.cyr1en.commandprompter.config.annotations.type.Configuration;
import com.cyr1en.kiso.mc.configuration.base.Config;

import java.util.List;
import java.util.Set;

@Configuration
@ConfigPath("config.yml")
@ConfigHeader({"Command Prompter", "Configuration"})
public record CommandPrompterConfig(
        Config rawConfig,

        @ConfigNode
        @NodeName("Prompt-Prefix")
        @NodeDefault("[&3Prompter&r] ")
        @NodeComment({"Set the plugin prefix"})
        String promptPrefix,

        @ConfigNode
        @NodeName("Prompt-Timeout")
        @NodeDefault("300")
        @NodeComment({
                "After how many seconds until",
                "CommandPrompter cancels a",
                "prompt"})
        int promptTimeout,

        @ConfigNode
        @NodeName("Cancel-Keyword")
        @NodeDefault("cancel")
        @NodeComment({
                "Word that cancels command",
                "prompting."})
        String cancelKeyword,

        @ConfigNode
        @NodeName("Enable-Permission")
        @NodeDefault("false")
        @NodeComment({
                "Enable permission check",
                "before a player can use",
                "the prompting feature", "",
                "Checking for commandprompter.use"})
        boolean enablePermission,

        @ConfigNode
        @NodeName("Update-Checker")
        @NodeDefault("true")
        @NodeComment({
                "Allow CommandPrompter to",
                "check if it's up to date."})
        boolean updateChecker,

        @ConfigNode
        @NodeName("Argument-Regex")
        @NodeDefault("<.*?>")
        @NodeComment({
                "This will determine if",
                "a part of a command is",
                "a prompt.", "",
                "ONLY CHANGE THE FIRST AND LAST",
                "I.E (.*?), {.*?}, or [.*?]"})
        String argumentRegex,

        @ConfigNode
        @NodeName("Debug-Mode")
        @NodeDefault("false")
        @NodeComment({
                "Enable debug mode for CommandPrompter."
        })
        boolean debugMode,

        @ConfigNode
        @NodeName("Show-Complete-Command")
        @NodeDefault("true")
        @NodeComment({
                "Should CommandPrompter send",
                "the completed command to the",
                "player before dispatching it?"
        })
        boolean showCompleted,

        @ConfigNode
        @NodeName("Fancy-Logger")
        @NodeDefault("true")
        @NodeComment({
                "Enable fancy logger",
                "Do /commandprompter reload to",
                "apply the change"
        })
        boolean fancyLogger,

        @ConfigNode
        @NodeName("Ignored-Commands")
        @NodeDefault("sampleCommand, sampleCommand2")
        @NodeComment({
                "What commands should CommandPrompter ignore",
                "",
                "When a command is ignored, CommandPrompter",
                "will not proceed to check if the command",
                "contains a prompt.",
                "",
                "Do not include the /",
                "",
                "VentureChat channels are automatically ignored."
        })
        List<String> ignoredCommands,

        @ConfigNode
        @NodeName("Allowed-Commands-In-Prompt")
        @NodeDefault("sampleCommand, sampleCommand2")
        @NodeComment({
                "What commands should CommandPrompter allow",
                "",
                "When the player executes another command while",
                "completing a prompt.",
                "",
                "Do not include the /",
                "",
        })
        List<String> allowedWhileInPrompt,

        @ConfigNode
        @NodeName("Permission-Attachment.ticks")
        @NodeDefault("1")
        @NodeComment({
                "Permission Attachment Config",
                "",
                "Permission attachments allow players",
                "to have temporary permissions.",
                "",
                "ticks - Set how long (in ticks) should the",
                "        permission attachment persist.",
                "",
                "permissions - permissions to temporarily",
                "              attach to the players."
        })
        int permissionAttachmentTicks,

        @ConfigNode
        @NodeName("Permission-Attachment.Permissions.GAMEMODE")
        @NodeDefault("bukkit.command.gamemode, " +
                "essentials.gamemode.survival," +
                "essentials.gamemode.creative")
        List<String> attachmentPermissions,

        @ConfigNode
        @NodeName("Command-Tab-Complete")
        @NodeComment({
                "Enable command tab complete",
                "for CommandPrompter"
        })
        @NodeDefault("true")
        boolean commandTabComplete,

        @ConfigNode
        @NodeName("Ignore-MiniMessage")
        @NodeComment({
                "If Prompt-Regex is left to default",
                "this will ignore MiniMessage syntax."
        })
        boolean ignoreMiniMessage


) implements AliasedSection {

    public String[] getPermissionAttachment(String key) {
        var section = rawConfig.getConfigurationSection("Permission-Attachment.Permissions");
        var keyExist = section.getKeys(false).contains(key);
        return keyExist ? section.getStringList(key).toArray(String[]::new) : new String[]{};
    }

    public String[] getPermissionKeys() {
        var section = rawConfig.getConfigurationSection("Permission-Attachment.Permissions");
        Set<String> keys = section == null ? Set.of() : section.getKeys(false);
        keys.add("NONE");
        return keys.toArray(String[]::new);
    }


}
