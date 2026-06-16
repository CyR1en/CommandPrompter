package dev.cyr1en.promptpaper.config;

import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptcore.config.annotations.field.*;
import dev.cyr1en.promptcore.config.annotations.type.ConfigHeader;
import dev.cyr1en.promptcore.config.annotations.type.ConfigPath;
import dev.cyr1en.promptcore.config.annotations.type.Configuration;

/**
 * User-facing message strings used by the prompt screens.
 *
 * <p>All fields default to MiniMessage-formatted strings. Servers can override them
 * under {@code config.yml -> messages: ...}.
 */
@Configuration
@ConfigPath("config.yml")
@ConfigHeader({"Command PrompterPaper", "Messages", "Configuration"})
public record MessageConfig(
        YamlDocument rawConfig,

        @ConfigNode
        @NodeName("Messages.Prompt-Cancelled")
        @NodeDefault("<yellow>Prompt cancelled.</yellow>")
        @NodeComment({"Messages sent to the player during a prompt", "",
                "Prompt-Cancelled - Sent when a prompt is cancelled",
                "Prompt-Timed-Out - Sent when a prompt times out",
                "Invalid-Integer - Sent when a typed integer is invalid",
                "Invalid-String - Sent when a typed string is empty"})
        String promptCancelled,

        @ConfigNode
        @NodeName("Messages.Prompt-Timed-Out")
        @NodeDefault("<red>Prompt timed out.</red>")
        String promptTimedOut,

        @ConfigNode
        @NodeName("Messages.Invalid-Integer")
        @NodeDefault("<red>Please enter a valid integer.</red>")
        String invalidInteger,

        @ConfigNode
        @NodeName("Messages.Invalid-String")
        @NodeDefault("<red>Input cannot be empty.</red>")
        String invalidString
) {
}
