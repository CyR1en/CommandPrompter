package dev.cyr1en.promptpaper.config;

import dev.cyr1en.promptcore.ParserConfig;
import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptcore.config.annotations.field.ConfigNode;
import dev.cyr1en.promptcore.config.annotations.field.IntegerConstraint;
import dev.cyr1en.promptcore.config.annotations.field.NodeComment;
import dev.cyr1en.promptcore.config.annotations.field.NodeDefault;
import dev.cyr1en.promptcore.config.annotations.field.NodeName;
import dev.cyr1en.promptcore.config.annotations.type.ConfigHeader;
import dev.cyr1en.promptcore.config.annotations.type.ConfigPath;
import dev.cyr1en.promptcore.config.annotations.type.Configuration;
import dev.cyr1en.promptcore.config.annotations.type.SectionComment;
import dev.cyr1en.promptcore.config.annotations.type.SectionComments;
import dev.cyr1en.promptcore.logic.transform.TemplateSyntax;
import java.util.List;
import java.util.Objects;

/**
 * Core plugin settings loaded from {@code config.yml}.
 *
 * <p>Controls timeouts, permissions, tab-completion, debug mode, and ignored commands. The raw YAML
 * is exposed via {@link #rawConfig()} for ad-hoc section reads (e.g. permission attachments).
 */
@Configuration
@ConfigPath("config.yml")
@ConfigHeader({"Command PrompterPaper", "Configuration"})
@SectionComments({
  @SectionComment(
      path = "Syntax",
      comments = {"Syntax Configuration"}),
  @SectionComment(
      path = "Syntax.Prompt",
      comments = {"Prompt tag delimiter configuration"}),
  @SectionComment(
      path = "Syntax.Template",
      comments = {"Template placeholder syntax configuration"}),
  @SectionComment(
      path = "Permission-Attachment",
      comments = {"Permission Attachment Config"}),
  @SectionComment(
      path = "Permission-Attachment.Permissions",
      comments = {"Permissions to temporarily attach to the player."})
})
public record CommandPrompterConfig(
    YamlDocument rawConfig,
    @ConfigNode
        @NodeName("Prompt-Prefix")
        @NodeDefault("<gradient:gold:yellow>[Prompter]</gradient> ")
        @NodeComment({"Set the plugin prefix"})
        String promptPrefix,
    @ConfigNode
        @NodeName("Prompt-Timeout")
        @NodeDefault("300")
        @IntegerConstraint(min = 0, max = 3600)
        @NodeComment({"After how many seconds until CommandPrompter cancels a prompt"})
        int promptTimeout,
    @ConfigNode
        @NodeName("Cancel-Keyword")
        @NodeDefault("cancel")
        @NodeComment({"Word that cancels command prompting."})
        String cancelKeyword,
    @ConfigNode
        @NodeName("Max-Answer-Length")
        @NodeDefault("256")
        @IntegerConstraint(
            min = 1,
            max = dev.cyr1en.promptcore.session.PromptSession.MAX_ANSWER_LENGTH)
        @NodeComment({"Maximum allowed character length for prompt answers."})
        int maxAnswerLength,
    @ConfigNode
        @NodeName("Enable-Permission")
        @NodeDefault("true")
        @NodeComment({
          "Enable permission check before a player can use the prompting feature",
          "",
          "Checking for promptpaper.use"
        })
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
        @NodeComment({
          "Should CommandPrompter send the completed command to the player before dispatching it?"
        })
        boolean showCompleted,
    @ConfigNode
        @NodeName("Show-Prompt-Cancelled")
        @NodeDefault("true")
        @NodeComment({"Should CommandPrompter send a prompt cancellation message to the player."})
        boolean showCancelled,
    @ConfigNode
        @NodeName("Argument-Regex")
        @NodeDefault("<.*?>")
        @NodeComment({
          "DEPRECATED: Use Syntax.Prompt instead.",
          "This will determine if a part of a command is a prompt.",
          "",
          "ONLY CHANGE THE FIRST AND LAST. The opening and closing delimiters",
          "must each be a single character (e.g. (.*?), {.*?}, or [.*?]).",
          "Changes take effect on /commandprompter reload."
        })
        String argumentRegex,
    @ConfigNode
        @NodeName("Syntax.Prompt.Open")
        @NodeDefault("<")
        @NodeComment({"Opening delimiter for prompt tags in commands."})
        String syntaxPromptOpen,
    @ConfigNode
        @NodeName("Syntax.Prompt.Close")
        @NodeDefault(">")
        @NodeComment({"Closing delimiter for prompt tags in commands."})
        String syntaxPromptClose,
    @ConfigNode
        @NodeName("Syntax.Template.Open")
        @NodeDefault("{")
        @NodeComment({"Opening delimiter for template placeholders."})
        String syntaxTemplateOpen,
    @ConfigNode
        @NodeName("Syntax.Template.Close")
        @NodeDefault("}")
        @NodeComment({"Closing delimiter for template placeholders."})
        String syntaxTemplateClose,
    @ConfigNode
        @NodeName("Syntax.Template.Transform-Separator")
        @NodeDefault(":")
        @NodeComment({"Separator between reference key and transformer specification."})
        String syntaxTransformSeparator,
    @ConfigNode
        @NodeName("Syntax.Template.Escape")
        @NodeDefault("\\")
        @NodeComment({"Escape character for template syntax."})
        String syntaxTemplateEscape,
    @ConfigNode
        @NodeName("Ignore-MiniMessage")
        @NodeDefault("true")
        @NodeComment({
          "When the prompt delimiters are angle brackets (< >), MiniMessage",
          "formatting tags (e.g. <red>, </red>, <gradient:gold:yellow>) use the same",
          "syntax. When enabled, CommandPrompter detects and ignores MiniMessage tags",
          "so they are not treated as prompts."
        })
        boolean ignoreMiniMessage,
    @ConfigNode
        @NodeName("Ignored-Commands")
        @NodeDefault("sampleCommand, sampleCommand2")
        @NodeComment({"What commands should CommandPrompter ignore", "", "Do not include the /"})
        List<String> ignoredCommands,
    @ConfigNode
        @NodeName("Allowed-Commands-In-Prompt")
        @NodeDefault("sampleCommand, sampleCommand2")
        @NodeComment({
          "What commands should CommandPrompter allow while a player is completing a prompt.",
          "",
          "Do not include the /"
        })
        List<String> allowedWhileInPrompt,
    @ConfigNode
        @NodeName("Command-Tab-Complete")
        @NodeDefault("true")
        @NodeComment({"Enable command tab complete for CommandPrompter"})
        boolean commandTabComplete,
    @ConfigNode
        @NodeName("Permission-Attachment.ticks")
        @NodeDefault("0")
        @NodeComment({
          "Deprecated and ignored. Temporary permission attachments are always",
          "removed immediately after the delegated command returns."
        })
        int permissionAttachmentTicks,
    @ConfigNode
        @NodeName("Permission-Attachment.Permissions.GAMEMODE")
        @NodeDefault(
            "bukkit.command.gamemode, "
                + "essentials.gamemode.survival,"
                + "essentials.gamemode.creative")
        @NodeComment({"Usage - /playerdelegate <player> GAMEMODE <command>"})
        List<String> attachmentPermissions,
    @ConfigNode
        @NodeName("Locale")
        @NodeDefault("en_US")
        @NodeComment({
          "Language locale for plugin messages (e.g. en_US, es_ES).",
          "Bundled locales are loaded from the plugin JAR.",
          "Custom overrides go in plugins/CommandPrompterPaper/locales/"
        })
        String locale)
    implements AliasedSection {

  /** Validate snapshot constraints and syntax consistency when constructed. */
  public CommandPrompterConfig {
    Objects.requireNonNull(rawConfig, "rawConfig must not be null");
    if (promptTimeout < 0 || promptTimeout > 3600) {
      throw new IllegalArgumentException(
          "Prompt-Timeout must be between 0 and 3600 (0=disabled); got: " + promptTimeout);
    }
    if (maxAnswerLength < 1
        || maxAnswerLength > dev.cyr1en.promptcore.session.PromptSession.MAX_ANSWER_LENGTH) {
      throw new IllegalArgumentException(
          "Max-Answer-Length must be between 1 and "
              + dev.cyr1en.promptcore.session.PromptSession.MAX_ANSWER_LENGTH
              + "; got: "
              + maxAnswerLength);
    }

    String effectivePromptOpen = syntaxPromptOpen;
    String effectivePromptClose = syntaxPromptClose;

    if (argumentRegex != null && !argumentRegex.isBlank() && !argumentRegex.equals("<.*?>")) {
      if ("<".equals(syntaxPromptOpen) && ">".equals(syntaxPromptClose)) {
        try {
          var derived = ParserConfig.fromArgumentRegex(argumentRegex);
          effectivePromptOpen = derived.opening();
          effectivePromptClose = derived.closing();
        } catch (IllegalArgumentException ignored) {
        }
      }
    }

    SyntaxValidator.validateCrossSyntax(
        effectivePromptOpen,
        effectivePromptClose,
        syntaxTemplateOpen,
        syntaxTemplateClose,
        syntaxTransformSeparator,
        syntaxTemplateEscape);
  }

  /**
   * Builds the {@link ParserConfig} derived from this configuration.
   *
   * <p>If {@code Argument-Regex} is set to a non-default value while {@code Syntax.Prompt} remains
   * at its defaults, prompt delimiters are derived from {@code Argument-Regex} for backward
   * compatibility. Otherwise, {@code Syntax.Prompt} is authoritative.
   */
  public ParserConfig parserConfig() {
    String effectivePromptOpen = syntaxPromptOpen;
    String effectivePromptClose = syntaxPromptClose;

    if (argumentRegex != null && !argumentRegex.isBlank() && !argumentRegex.equals("<.*?>")) {
      if ("<".equals(syntaxPromptOpen) && ">".equals(syntaxPromptClose)) {
        try {
          var derived = ParserConfig.fromArgumentRegex(argumentRegex);
          effectivePromptOpen = derived.opening();
          effectivePromptClose = derived.closing();
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    return new ParserConfig(effectivePromptOpen, effectivePromptClose, syntaxTemplateEscape);
  }

  /** Builds the {@link TemplateSyntax} derived from this configuration. */
  public TemplateSyntax templateSyntax() {
    return new TemplateSyntax(
        syntaxTemplateOpen, syntaxTemplateClose, syntaxTransformSeparator, syntaxTemplateEscape);
  }

  /** Returns whether old-only {@code Argument-Regex} was used to derive prompt delimiters. */
  public boolean isUsingDeprecatedArgumentRegex() {
    if (argumentRegex == null || argumentRegex.isBlank() || argumentRegex.equals("<.*?>")) {
      return false;
    }
    return "<".equals(syntaxPromptOpen) && ">".equals(syntaxPromptClose);
  }

  /** Returns whether explicit {@code Syntax.Prompt} and {@code Argument-Regex} disagree. */
  public boolean hasArgumentRegexDisagreement() {
    if (argumentRegex == null || argumentRegex.isBlank() || argumentRegex.equals("<.*?>")) {
      return false;
    }
    if (!("<".equals(syntaxPromptOpen) && ">".equals(syntaxPromptClose))) {
      try {
        var derived = ParserConfig.fromArgumentRegex(argumentRegex);
        return !derived.opening().equals(syntaxPromptOpen)
            || !derived.closing().equals(syntaxPromptClose);
      } catch (IllegalArgumentException e) {
        return true;
      }
    }
    return false;
  }

  @Override
  public YamlDocument rawConfig() {
    return rawConfig;
  }

  /**
   * Returns the permission list for a named permission-attachment group.
   *
   * @param key the group key (e.g. {@code "GAMEMODE"})
   * @return permission strings, or an empty array if the key is absent
   */
  public String[] getPermissionAttachment(String key) {
    var configKeys = rawConfig.getKeys("Permission-Attachment.Permissions");
    if (configKeys == null || configKeys.isEmpty()) return new String[0];
    var keyExist = configKeys.contains(key);
    return keyExist
        ? rawConfig.getList("Permission-Attachment.Permissions." + key).stream()
            .map(Object::toString)
            .toArray(String[]::new)
        : new String[0];
  }

  /**
   * Returns all group keys under {@code Permission-Attachment.Permissions}.
   *
   * @return key names, or an empty array if none are configured
   */
  public String[] getPermissionKeys() {
    var configKeys = rawConfig.getKeys("Permission-Attachment.Permissions");
    if (configKeys == null || configKeys.isEmpty()) return new String[0];
    return configKeys.toArray(String[]::new);
  }
}
