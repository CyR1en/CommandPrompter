package dev.cyr1en.promptpaper.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.cyr1en.promptpaper.MockBukkitTest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Direct unit coverage for {@link PresetRegistry}.
 *
 * <p>Most tests use the test-only {@link PresetRegistry#PresetRegistry(File, java.util.function.Supplier)}
 * constructor so the registry can be exercised in isolation from Bukkit. The {@code MockBukkitTest}
 * base is inherited only for the {@code plugin} field, which is not used here.
 */
class PresetRegistryTest extends MockBukkitTest {

  private static final String SAMPLE_JSON = """
      {
        "prompts": [
          {
            "type": "chat",
            "id": "reason_prompt",
            "prompt_text": "Why?",
            "sanitize": true,
            "cancel": {
              "send": true,
              "message": "Cancelled",
              "clickable": false,
              "hover_message": "Aborted"
            }
          },
          {
            "type": "anvil",
            "id": "rename_item",
            "title": "Rename",
            "prompt_text": "New name",
            "sanitize": false,
            "left_button": {
              "show": true,
              "button_text": "Cancel",
              "button_icon": "BARRIER",
              "button_hover_text": "Cancel",
              "custom_model_data": 0
            },
            "right_button": {
              "show": true,
              "button_text": "Confirm",
              "button_icon": "PAPER",
              "button_hover_text": "Confirm",
              "custom_model_data": 0
            }
          }
        ],
        "post_commands": [
          {
            "id": "log_reason",
            "command": "log {player} {input:1}",
            "execution_policy": "on_complete",
            "execute_as": "console"
          }
        ]
      }
      """;

  private static final String DEFAULT_RESOURCE_JSON = SAMPLE_JSON;

  @TempDir File tempDir;

  private File promptsFile;

  @BeforeEach
  void setUp() throws IOException {
    promptsFile = new File(tempDir, PresetRegistry.FILE_NAME);
  }

  private PresetRegistry newRegistry(String defaultJson) {
    java.util.function.Supplier<java.io.InputStream> supplier = defaultJson == null
        ? () -> null
        : () -> new ByteArrayInputStream(defaultJson.getBytes(StandardCharsets.UTF_8));
    return new PresetRegistry(promptsFile, supplier);
  }

  // --------------------------------------------------------------
  // Happy path
  // --------------------------------------------------------------

  @Test
  void loadFromExistingFile() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    assertEquals(2, registry.promptCount());
    assertEquals(1, registry.postCommandCount());
  }

  @Test
  void getPromptReturnsCorrectType() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    var reason = registry.getPrompt("reason_prompt");
    assertTrue(reason.isPresent());
    assertInstanceOf(ChatPrompt.class, reason.get());

    var rename = registry.getPrompt("rename_item");
    assertTrue(rename.isPresent());
    assertInstanceOf(AnvilPrompt.class, rename.get());
  }

  @Test
  void getPostCommandReturnsDefinition() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    var pc = registry.getPostCommand("log_reason");
    assertTrue(pc.isPresent());
    assertEquals("log {player} {input:1}", pc.get().command());
    assertEquals(ExecutionPolicy.ON_COMPLETE, pc.get().executionPolicy());
    assertEquals(ExecuteAs.CONSOLE, pc.get().executeAs());
  }

  @Test
  void getPromptReturnsEmptyForMissingId() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    assertTrue(registry.getPrompt("does_not_exist").isEmpty());
    assertTrue(registry.getPostCommand("does_not_exist").isEmpty());
    assertTrue(registry.getPrompt(null).isEmpty());
    assertTrue(registry.getPostCommand(null).isEmpty());
  }

  // --------------------------------------------------------------
  // Reload semantics
  // --------------------------------------------------------------

  @Test
  void reloadIsIdempotent() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();
    registry.reload();
    registry.reload();

    assertEquals(2, registry.promptCount());
    assertEquals(1, registry.postCommandCount());
  }

  @Test
  void reloadRebuildsFromUpdatedFile() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);
    var registry = newRegistry(null);
    registry.reload();
    assertEquals(2, registry.promptCount());

    // Replace the file with a smaller document and reload.
    Files.writeString(promptsFile.toPath(), """
        {
          "prompts": [],
          "post_commands": []
        }
        """, StandardCharsets.UTF_8);
    registry.reload();
    assertEquals(0, registry.promptCount());
    assertEquals(0, registry.postCommandCount());
  }

  @Test
  void failedReloadPreservesPreviousCache() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);
    var registry = newRegistry(null);
    registry.reload();
    assertEquals(2, registry.promptCount());

    // Corrupt the file, then attempt to reload.
    Files.writeString(promptsFile.toPath(), "{ this is not valid JSON", StandardCharsets.UTF_8);
    assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);

    // The previous good cache must still be served.
    assertEquals(2, registry.promptCount());
    assertTrue(registry.getPrompt("reason_prompt").isPresent());
  }

  // --------------------------------------------------------------
  // Default-resource extraction
  // --------------------------------------------------------------

  @Test
  void missingFileIsExtractedFromDefaultResource() {
    // No file on disk. The supplier provides the default; it must be copied to the target path.
    assertFalse(promptsFile.exists());
    var registry = newRegistry(DEFAULT_RESOURCE_JSON);
    registry.reload();

    assertTrue(promptsFile.exists());
    assertEquals(2, registry.promptCount());
    assertEquals(1, registry.postCommandCount());
  }

  @Test
  void missingFileWithNullDefaultResourceThrows() {
    // Default-resource supplier is provided but returns null, and the on-disk file is missing.
    // reload() must surface this as a PresetLoadException.
    assertFalse(promptsFile.exists());
    var registry = newRegistry(null);
    assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
  }

  // --------------------------------------------------------------
  // Edge cases
  // --------------------------------------------------------------

  @Test
  void emptyJsonObjectClearsRegistry() throws IOException {
    Files.writeString(promptsFile.toPath(), "{}", StandardCharsets.UTF_8);
    var registry = newRegistry(null);
    registry.reload();

    assertEquals(0, registry.promptCount());
    assertEquals(0, registry.postCommandCount());
  }

  @Test
  void emptyFileIsTreatedAsEmpty() throws IOException {
    Files.writeString(promptsFile.toPath(), "", StandardCharsets.UTF_8);
    var registry = newRegistry(null);
    registry.reload();

    assertEquals(0, registry.promptCount());
    assertEquals(0, registry.postCommandCount());
  }

  @Test
  void missingTopLevelArraysAreTreatedAsEmpty() throws IOException {
    Files.writeString(promptsFile.toPath(), "{\"other_field\": 42}", StandardCharsets.UTF_8);
    var registry = newRegistry(null);
    registry.reload();

    assertEquals(0, registry.promptCount());
    assertEquals(0, registry.postCommandCount());
  }

  @Test
  void duplicateIdKeepsLastDefinition() throws IOException {
    String dup = """
        {
          "prompts": [
            {
              "type": "chat",
              "id": "dupe",
              "prompt_text": "first",
              "sanitize": true,
              "cancel": {
                "send": false,
                "message": "m",
                "clickable": false,
                "hover_message": "h"
              }
            },
            {
              "type": "chat",
              "id": "dupe",
              "prompt_text": "second",
              "sanitize": true,
              "cancel": {
                "send": false,
                "message": "m",
                "clickable": false,
                "hover_message": "h"
              }
            }
          ],
          "post_commands": []
        }
        """;
    Files.writeString(promptsFile.toPath(), dup, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    assertEquals(1, registry.promptCount());
    var def = registry.getPrompt("dupe");
    assertTrue(def.isPresent());
    assertEquals("second", ((ChatPrompt) def.get()).promptText());
  }

  @Test
  void emptyIdIsSkipped() throws IOException {
    Files.writeString(promptsFile.toPath(), """
        {
          "prompts": [
            {
              "type": "chat",
              "id": "",
              "prompt_text": "no id",
              "sanitize": true,
              "cancel": {
                "send": false,
                "message": "m",
                "clickable": false,
                "hover_message": "h"
              }
            },
            {
              "type": "chat",
              "id": "valid",
              "prompt_text": "ok",
              "sanitize": true,
              "cancel": {
                "send": false,
                "message": "m",
                "clickable": false,
                "hover_message": "h"
              }
            }
          ],
          "post_commands": []
        }
        """, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    assertEquals(1, registry.promptCount());
    assertTrue(registry.getPrompt("valid").isPresent());
  }

  @Test
  void getPromptsFileReturnsConfiguredPath() {
    var registry = newRegistry(null);
    assertNotNull(registry.getPromptsFile());
    assertEquals(promptsFile, registry.getPromptsFile());
  }

  // --------------------------------------------------------------
  // Gson sanity: PresetConfig round-trips through the same Gson
  // --------------------------------------------------------------

  @Test
  void presetConfigDeserializesBothArrays() {
    Gson gson = PresetGson.presetGson();
    var config = gson.fromJson(SAMPLE_JSON, PresetConfig.class);
    assertNotNull(config);
    assertEquals(2, config.prompts().size());
    assertEquals(1, config.postCommands().size());
  }
}
