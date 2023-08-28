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
        @NodeName("Enable-Unsafe")
        @NodeDefault("false")
        @NodeComment({
                "Enable unsafe features for",
                "CommandPrompter. Enabling this",
                "allows CommandPrompter to",
                "modify the command map and",
                "catch dispatched commands"
        })
        boolean enableUnsafe,

        @ConfigNode
        @NodeName("Modification-Delay")
        @NodeDefault("1")
        @NodeComment({
                "If Enable-Unsafe is set to",
                "true, this delay (in ticks)",
                "will be used before modifying",
                "the command map to allow all",
                "plugins to register all of",
                "their commands First", "",
                "If you experience issues,",
                "increase the value of this delay.",
                "Note that 20 ticks is 1 second."
        })
        int modificationDelay,

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
        @NodeName("Permission-Attachment.permissions")
        @NodeDefault("sample.permission, sample.permission2")
        List<String> attachmentPermissions,

        @ConfigNode
        @NodeName("Command-Tab-Complete")
        @NodeComment({
                "Enable command tab complete",
                "for CommandPrompter"
        })
        @NodeDefault("true")
        boolean commandTabComplete
) {
}
