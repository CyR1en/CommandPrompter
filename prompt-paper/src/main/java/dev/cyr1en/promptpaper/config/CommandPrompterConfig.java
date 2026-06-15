package dev.cyr1en.promptpaper.config;

import dev.cyr1en.promptpaper.config.annotations.field.*;
import dev.cyr1en.promptpaper.config.annotations.type.ConfigHeader;
import dev.cyr1en.promptpaper.config.annotations.type.ConfigPath;
import dev.cyr1en.promptpaper.config.annotations.type.Configuration;
import com.cyr1en.kiso.mc.configuration.base.Config;
import java.util.List;
import java.util.Set;

/**
 * Core plugin settings loaded from {@code config.yml}.
 *
 * <p>Controls timeouts, permissions, tab-completion, debug mode, and ignored commands.
 * The raw YAML is exposed via {@link #rawConfig()} for ad-hoc section reads (e.g.
 * permission attachments).
 */
@Configuration
@ConfigPath("config.yml")
@ConfigHeader({"Command PrompterPaper", "Configuration"})
public record CommandPrompterConfig(
        Config rawConfig,

        @ConfigNode
        @NodeName("Prompt-Prefix")
        @NodeDefault("<gradient:gold:yellow>[Prompter]</gradient> ")
        @NodeComment({"Set the plugin prefix"})
        String promptPrefix,

        @ConfigNode
        @NodeName("Prompt-Timeout")
        @NodeDefault("300")
        @NodeComment({"After how many seconds until CommandPrompter cancels a prompt"})
        int promptTimeout,

        @ConfigNode
        @NodeName("Cancel-Keyword")
        @NodeDefault("cancel")
        @NodeComment({"Word that cancels command prompting."})
        String cancelKeyword,

        @ConfigNode
        @NodeName("Enable-Permission")
        @NodeDefault("false")
        @NodeComment({"Enable permission check before a player can use the prompting feature",
                "", "Checking for promptpaper.use"})
        boolean enablePermission,

        @ConfigNode
        @NodeName("Debug-Mode")
        @NodeDefault("false")
        @NodeComment({"Enable debug mode for CommandPrompter."})
        boolean debugMode,

        @ConfigNode
        @NodeName("Fancy-Logger")
        @NodeDefault("true")
        @NodeComment({"Enable fancy ANSI colored console output."})
        boolean fancyLogger,

        @ConfigNode
        @NodeName("Show-Complete-Command")
        @NodeDefault("true")
        @NodeComment({"Should CommandPrompter send the completed command to the player before dispatching it?"})
        boolean showCompleted,

        @ConfigNode
        @NodeName("Show-Prompt-Cancelled")
        @NodeDefault("true")
        @NodeComment({"Should CommandPrompter send a prompt cancellation message to the player."})
        boolean showCancelled,

        @ConfigNode
        @NodeName("Argument-Regex")
        @NodeDefault("<.*?>")
        @NodeComment({"This will determine if a part of a command is a prompt.",
                "", "ONLY CHANGE THE FIRST AND LAST", "I.E (.*?), {.*?}, or [.*?]"})
        String argumentRegex,

        @ConfigNode
        @NodeName("Ignored-Commands")
        @NodeDefault("sampleCommand, sampleCommand2")
        @NodeComment({"What commands should CommandPrompter ignore",
                "", "Do not include the /"})
        List<String> ignoredCommands,

        @ConfigNode
        @NodeName("Allowed-Commands-In-Prompt")
        @NodeDefault("sampleCommand, sampleCommand2")
        @NodeComment({"What commands should CommandPrompter allow while a player is completing a prompt.",
                "", "Do not include the /"})
        List<String> allowedWhileInPrompt,

        @ConfigNode
        @NodeName("Command-Tab-Complete")
        @NodeDefault("true")
        @NodeComment({"Enable command tab complete for CommandPrompter"})
        boolean commandTabComplete,

        @ConfigNode
        @NodeName("Permission-Attachment.ticks")
        @NodeDefault("1")
        @NodeComment({"Permission Attachment Config", "",
                "ticks - Set how long (in ticks) should the",
                "        permission attachment persist."})
        int permissionAttachmentTicks,

        @ConfigNode
        @NodeName("Permission-Attachment.Permissions.GAMEMODE")
        @NodeDefault("bukkit.command.gamemode, " +
                "essentials.gamemode.survival," +
                "essentials.gamemode.creative")
        @NodeComment({"Permissions to temporarily attach to the player.",
                "Usage: /playerdelegate <player> GAMEMODE <command>"})
        List<String> attachmentPermissions

) implements AliasedSection {

    @Override
    public Config rawConfig() {
        return rawConfig;
    }

    /**
     * Returns the permission list for a named permission-attachment group.
     *
     * @param key the group key (e.g. {@code "GAMEMODE"})
     * @return permission strings, or an empty array if the key is absent
     */
    public String[] getPermissionAttachment(String key) {
        var section = rawConfig.getConfigurationSection("Permission-Attachment.Permissions");
        if (section == null) return new String[0];
        var keyExist = section.getKeys(false).contains(key);
        return keyExist ? section.getStringList(key).toArray(String[]::new) : new String[0];
    }

    /**
     * Returns all group keys under {@code Permission-Attachment.Permissions}.
     *
     * @return key names including a synthetic {@code "NONE"} entry
     */
    public String[] getPermissionKeys() {
        var section = rawConfig.getConfigurationSection("Permission-Attachment.Permissions");
        if (section == null) return new String[]{"NONE"};
        var keys = new java.util.HashSet<>(section.getKeys(false));
        keys.add("NONE");
        return keys.toArray(String[]::new);
    }
}
