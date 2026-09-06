package dev.cyr1en.promptpaper.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptcore.config.YamlDocument;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommandPrompterConfigTest extends MockBukkitTest {

  @Test
  void getPermissionAttachmentReturnsConfiguredKeys() {
    var rawConfig = mock(YamlDocument.class);
    when(rawConfig.getKeys("Permission-Attachment.Permissions")).thenReturn(Set.of("GAMEMODE"));
    org.mockito.Mockito.doReturn(List.of("bukkit.command.gamemode", "essentials.gamemode.survival"))
        .when(rawConfig)
        .getList("Permission-Attachment.Permissions.GAMEMODE");

    var cfg =
        new CommandPrompterConfig(
            rawConfig,
            "[Prompter] ",
            300,
            "cancel",
            256,
            false,
            false,
            true,
            true,
            true,
            "<.*?>",
            "<",
            ">",
            "{",
            "}",
            ":",
            "\\",
            true,
            List.of(),
            List.of(),
            true,
            1,
            List.of("bukkit.command.gamemode"),
            "en_US");

    var perms = cfg.getPermissionAttachment("GAMEMODE");
    assertArrayEquals(
        new String[] {"bukkit.command.gamemode", "essentials.gamemode.survival"}, perms);
  }

  @Test
  void getPermissionKeysReturnsAvailableKeys() {
    var rawConfig = mock(YamlDocument.class);
    when(rawConfig.getKeys("Permission-Attachment.Permissions"))
        .thenReturn(Set.of("GAMEMODE", "FLY"));

    var cfg =
        new CommandPrompterConfig(
            rawConfig,
            "[Prompter] ",
            300,
            "cancel",
            256,
            false,
            false,
            true,
            true,
            true,
            "<.*?>",
            "<",
            ">",
            "{",
            "}",
            ":",
            "\\",
            true,
            List.of(),
            List.of(),
            true,
            1,
            List.of(),
            "en_US");

    var keys = cfg.getPermissionKeys();
    assertEquals(2, keys.length);
    assertTrue(java.util.Arrays.asList(keys).contains("GAMEMODE"));
    assertTrue(java.util.Arrays.asList(keys).contains("FLY"));
    assertFalse(java.util.Arrays.asList(keys).contains("NONE"));
  }

  @Test
  void getPermissionKeysReturnsEmptyArrayWhenSectionMissing() {
    var rawConfig = mock(YamlDocument.class);
    when(rawConfig.getKeys("Permission-Attachment.Permissions")).thenReturn(Set.of());

    var cfg =
        new CommandPrompterConfig(
            rawConfig,
            "[Prompter] ",
            300,
            "cancel",
            256,
            false,
            false,
            true,
            true,
            true,
            "<.*?>",
            "<",
            ">",
            "{",
            "}",
            ":",
            "\\",
            true,
            List.of(),
            List.of(),
            true,
            1,
            List.of(),
            "en_US");

    var keys = cfg.getPermissionKeys();
    assertArrayEquals(new String[0], keys);
  }

  @Test
  void getPermissionKeysReturnsEmptyArrayWhenSectionNull() {
    var rawConfig = mock(YamlDocument.class);
    when(rawConfig.getKeys("Permission-Attachment.Permissions")).thenReturn(null);

    var cfg =
        new CommandPrompterConfig(
            rawConfig,
            "[Prompter] ",
            300,
            "cancel",
            256,
            false,
            false,
            true,
            true,
            true,
            "<.*?>",
            "<",
            ">",
            "{",
            "}",
            ":",
            "\\",
            true,
            List.of(),
            List.of(),
            true,
            1,
            List.of(),
            "en_US");

    var keys = cfg.getPermissionKeys();
    assertArrayEquals(new String[0], keys);
  }

  @Test
  void defaultSyntaxBuildsExpectedParserConfigAndTemplateSyntax() {
    var rawConfig = mock(YamlDocument.class);
    var cfg =
        new CommandPrompterConfig(
            rawConfig,
            "[Prompter] ",
            300,
            "cancel",
            256,
            false,
            false,
            true,
            true,
            true,
            "<.*?>",
            "<",
            ">",
            "{",
            "}",
            ":",
            "\\",
            true,
            List.of(),
            List.of(),
            true,
            1,
            List.of(),
            "en_US");

    assertEquals("<", cfg.parserConfig().opening());
    assertEquals(">", cfg.parserConfig().closing());
    assertEquals("{", cfg.templateSyntax().open());
    assertEquals("}", cfg.templateSyntax().close());
    assertEquals(":", cfg.templateSyntax().transformSeparator());
    assertEquals("\\", cfg.templateSyntax().escape());
    assertFalse(cfg.isUsingDeprecatedArgumentRegex());
    assertFalse(cfg.hasArgumentRegexDisagreement());
  }

  @Test
  void legacyArgumentRegexDerivesPromptDelimitersWhenSyntaxPromptIsDefault() {
    var rawConfig = mock(YamlDocument.class);
    var cfg =
        new CommandPrompterConfig(
            rawConfig,
            "[Prompter] ",
            300,
            "cancel",
            256,
            false,
            false,
            true,
            true,
            true,
            "[.*?]",
            "<",
            ">",
            "{",
            "}",
            ":",
            "\\",
            true,
            List.of(),
            List.of(),
            true,
            1,
            List.of(),
            "en_US");

    assertEquals("[", cfg.parserConfig().opening());
    assertEquals("]", cfg.parserConfig().closing());
    assertTrue(cfg.isUsingDeprecatedArgumentRegex());
    assertFalse(cfg.hasArgumentRegexDisagreement());
  }

  @Test
  void explicitSyntaxPromptWinsAndReportsDisagreementWhenArgumentRegexDiffers() {
    var rawConfig = mock(YamlDocument.class);
    var cfg =
        new CommandPrompterConfig(
            rawConfig,
            "[Prompter] ",
            300,
            "cancel",
            256,
            false,
            false,
            true,
            true,
            true,
            "[.*?]",
            "(",
            ")",
            "{",
            "}",
            ":",
            "\\",
            true,
            List.of(),
            List.of(),
            true,
            1,
            List.of(),
            "en_US");

    assertEquals("(", cfg.parserConfig().opening());
    assertEquals(")", cfg.parserConfig().closing());
    assertFalse(cfg.isUsingDeprecatedArgumentRegex());
    assertTrue(cfg.hasArgumentRegexDisagreement());
  }

  @Test
  void crossSyntaxValidationRejectsOverlapAndInvalidTokens() {
    var rawConfig = mock(YamlDocument.class);

    // Prompt Open overlaps with Template Open
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CommandPrompterConfig(
                rawConfig,
                "[Prompter] ",
                300,
                "cancel",
                256,
                false,
                false,
                true,
                true,
                true,
                "<.*?>",
                "{",
                "}",
                "{",
                "}",
                ":",
                "\\",
                true,
                List.of(),
                List.of(),
                true,
                1,
                List.of(),
                "en_US"));

    // Prefix overlap: Prompt Open "{" and Template Open "{{"
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CommandPrompterConfig(
                rawConfig,
                "[Prompter] ",
                300,
                "cancel",
                256,
                false,
                false,
                true,
                true,
                true,
                "<.*?>",
                "{{",
                "}}",
                "{",
                "}",
                ":",
                "\\",
                true,
                List.of(),
                List.of(),
                true,
                1,
                List.of(),
                "en_US"));

    // Whitespace in delimiter
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CommandPrompterConfig(
                rawConfig,
                "[Prompter] ",
                300,
                "cancel",
                256,
                false,
                false,
                true,
                true,
                true,
                "<.*?>",
                "< ",
                ">",
                "{",
                "}",
                ":",
                "\\",
                true,
                List.of(),
                List.of(),
                true,
                1,
                List.of(),
                "en_US"));

    // Quote in delimiter
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CommandPrompterConfig(
                rawConfig,
                "[Prompter] ",
                300,
                "cancel",
                256,
                false,
                false,
                true,
                true,
                true,
                "<.*?>",
                "\"",
                ">",
                "{",
                "}",
                ":",
                "\\",
                true,
                List.of(),
                List.of(),
                true,
                1,
                List.of(),
                "en_US"));

    // Control char in delimiter
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CommandPrompterConfig(
                rawConfig,
                "[Prompter] ",
                300,
                "cancel",
                256,
                false,
                false,
                true,
                true,
                true,
                "<.*?>",
                "\u0000",
                ">",
                "{",
                "}",
                ":",
                "\\",
                true,
                List.of(),
                List.of(),
                true,
                1,
                List.of(),
                "en_US"));

    // Empty delimiter
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CommandPrompterConfig(
                rawConfig,
                "[Prompter] ",
                300,
                "cancel",
                256,
                false,
                false,
                true,
                true,
                true,
                "<.*?>",
                "",
                ">",
                "{",
                "}",
                ":",
                "\\",
                true,
                List.of(),
                List.of(),
                true,
                1,
                List.of(),
                "en_US"));
  }

  @Test
  void maxAnswerLengthBoundsValidation() {
    var rawConfig = mock(YamlDocument.class);

    // Valid bounds: 1, 256, 1024
    var cfg1 =
        new CommandPrompterConfig(
            rawConfig,
            "[Prompter] ",
            300,
            "cancel",
            1,
            false,
            false,
            true,
            true,
            true,
            "<.*?>",
            "<",
            ">",
            "{",
            "}",
            ":",
            "\\",
            true,
            List.of(),
            List.of(),
            true,
            1,
            List.of(),
            "en_US");
    assertEquals(1, cfg1.maxAnswerLength());

    var cfg256 =
        new CommandPrompterConfig(
            rawConfig,
            "[Prompter] ",
            300,
            "cancel",
            256,
            false,
            false,
            true,
            true,
            true,
            "<.*?>",
            "<",
            ">",
            "{",
            "}",
            ":",
            "\\",
            true,
            List.of(),
            List.of(),
            true,
            1,
            List.of(),
            "en_US");
    assertEquals(256, cfg256.maxAnswerLength());

    var cfg1024 =
        new CommandPrompterConfig(
            rawConfig,
            "[Prompter] ",
            300,
            "cancel",
            1024,
            false,
            false,
            true,
            true,
            true,
            "<.*?>",
            "<",
            ">",
            "{",
            "}",
            ":",
            "\\",
            true,
            List.of(),
            List.of(),
            true,
            1,
            List.of(),
            "en_US");
    assertEquals(1024, cfg1024.maxAnswerLength());

    // Invalid: 0 and negative
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CommandPrompterConfig(
                rawConfig,
                "[Prompter] ",
                300,
                "cancel",
                0,
                false,
                false,
                true,
                true,
                true,
                "<.*?>",
                "<",
                ">",
                "{",
                "}",
                ":",
                "\\",
                true,
                List.of(),
                List.of(),
                true,
                1,
                List.of(),
                "en_US"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CommandPrompterConfig(
                rawConfig,
                "[Prompter] ",
                300,
                "cancel",
                -5,
                false,
                false,
                true,
                true,
                true,
                "<.*?>",
                "<",
                ">",
                "{",
                "}",
                ":",
                "\\",
                true,
                List.of(),
                List.of(),
                true,
                1,
                List.of(),
                "en_US"));

    // Invalid: > 1024
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CommandPrompterConfig(
                rawConfig,
                "[Prompter] ",
                300,
                "cancel",
                1025,
                false,
                false,
                true,
                true,
                true,
                "<.*?>",
                "<",
                ">",
                "{",
                "}",
                ":",
                "\\",
                true,
                List.of(),
                List.of(),
                true,
                1,
                List.of(),
                "en_US"));
  }
}
