package dev.cyr1en.promptpaper.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cyr1en.promptpaper.MockBukkitTest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Direct unit coverage for {@link PresetRegistry} and atomic snapshot lifecycle. */
class PresetRegistryTest extends MockBukkitTest {

  private static final String SAMPLE_JSON =
      """
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
            "execute_as": "console",
            "delay_ticks": 20
          }
        ],
        "approval_gates": [
          {
            "id": "admin_gate",
            "target": "admin",
            "message": "Approve action for {player}",
            "timeout": 60
          }
        ],
        "conditional_post_commands": [
          {
            "id": "eco_reward",
            "condition": "%vault_eco_balance% >= 100",
            "execution_policy": "on_complete",
            "if_true": {
              "command": "eco give {player} 50",
              "execute_as": "console",
              "delay_ticks": 10
            }
          }
        ]
      }
      """;

  private static final String DEFAULT_RESOURCE_JSON = SAMPLE_JSON;

  @Test
  void anvilEmptyTextLoadsButMissingOrNullTextRejectsReload() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON.replace("New name", ""));
    var registry = newRegistry(null);
    registry.reload();
    assertEquals("", ((AnvilPrompt) registry.getPrompt("rename_item").orElseThrow()).promptText());
    var original = registry.getSnapshot();
    for (String replacement : List.of("", "\"prompt_text\": null,")) {
      Files.writeString(
          promptsFile.toPath(), SAMPLE_JSON.replace("\"prompt_text\": \"New name\",", replacement));
      assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
      assertSame(original, registry.getSnapshot());
    }
  }

  @TempDir File tempDir;

  private File promptsFile;

  @BeforeEach
  void setUp() throws IOException {
    promptsFile = new File(tempDir, PresetRegistry.FILE_NAME);
  }

  private PresetRegistry newRegistry(String defaultJson) {
    java.util.function.Supplier<java.io.InputStream> supplier =
        defaultJson == null
            ? () -> null
            : () -> new ByteArrayInputStream(defaultJson.getBytes(StandardCharsets.UTF_8));
    return new PresetRegistry(promptsFile, supplier);
  }

  // --------------------------------------------------------------
  // Happy path: All four categories load
  // --------------------------------------------------------------

  @Test
  void invalidPostCommandDelayPreservesCurrentSnapshot() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);
    var registry = newRegistry(null);
    registry.reload();
    var original = registry.getSnapshot();

    for (String delay : List.of("1.9", "4294967297", "-4294967295")) {
      Files.writeString(
          promptsFile.toPath(),
          """
          {"post_commands":[{"id":"post","command":"say yes",
          "execution_policy":"on_complete","execute_as":"console","delay_ticks":%s}]}
          """
              .formatted(delay),
          StandardCharsets.UTF_8);
      var error = assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
      assertTrue(error.getMessage().contains("post_commands[0]"));
      assertSame(original, registry.getSnapshot());
    }
  }

  @Test
  void loadAllFourCategoriesFromExistingFile() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    assertEquals(2, registry.promptCount());
    assertEquals(1, registry.postCommandCount());
    assertEquals(1, registry.approvalGateCount());
    assertEquals(1, registry.conditionalPostCommandCount());

    var snapshot = registry.getSnapshot();
    assertNotNull(snapshot);
    assertEquals(1L, snapshot.generation());
    assertEquals(5, snapshot.totalCount());

    // Prompt lookups
    var reason = registry.getPrompt("reason_prompt");
    assertTrue(reason.isPresent());
    assertInstanceOf(ChatPrompt.class, reason.get());

    var rename = registry.getPrompt("rename_item");
    assertTrue(rename.isPresent());
    assertInstanceOf(AnvilPrompt.class, rename.get());

    // Post command lookup
    var pc = registry.getPostCommand("log_reason");
    assertTrue(pc.isPresent());
    assertEquals("log {player} {input:1}", pc.get().command());
    assertEquals(ExecutionPolicy.ON_COMPLETE, pc.get().executionPolicy());
    assertEquals(ExecuteAs.CONSOLE, pc.get().executeAs());
    assertEquals(20, pc.get().delayTicks());

    // Approval gate lookup
    var gate = registry.getApprovalGate("admin_gate");
    assertTrue(gate.isPresent());
    assertEquals("admin", gate.get().target().source());
    assertEquals("Approve action for {player}", gate.get().message().source());
    assertEquals(60, gate.get().timeout());

    // Conditional post command lookup
    var cond = registry.getConditionalPostCommand("eco_reward");
    assertTrue(cond.isPresent());
    assertEquals(ExecutionPolicy.ON_COMPLETE, cond.get().executionPolicy());
    assertNotNull(cond.get().ifTrueAction());
    assertEquals("eco give {player} 50", cond.get().ifTrueAction().command().source());

    // ID sets
    assertEquals(List.of("reason_prompt", "rename_item"), new ArrayList<>(registry.getPromptIds()));
    assertEquals(List.of("log_reason"), new ArrayList<>(registry.getPostCommandIds()));
    assertEquals(List.of("admin_gate"), new ArrayList<>(registry.getApprovalGateIds()));
    assertEquals(List.of("eco_reward"), new ArrayList<>(registry.getConditionalPostCommandIds()));
  }

  @Test
  void getPromptReturnsEmptyForMissingId() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    assertTrue(registry.getPrompt("does_not_exist").isEmpty());
    assertTrue(registry.getPostCommand("does_not_exist").isEmpty());
    assertTrue(registry.getApprovalGate("does_not_exist").isEmpty());
    assertTrue(registry.getConditionalPostCommand("does_not_exist").isEmpty());
    assertTrue(registry.getPrompt(null).isEmpty());
    assertTrue(registry.getPostCommand(null).isEmpty());
    assertTrue(registry.getApprovalGate(null).isEmpty());
    assertTrue(registry.getConditionalPostCommand(null).isEmpty());
  }

  // --------------------------------------------------------------
  // Atomic Snapshot & Reload semantics
  // --------------------------------------------------------------

  @Test
  void preparedReloadIsInvisibleUntilPublished() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);
    var registry = newRegistry(null);
    var oldSnapshot = registry.getSnapshot();

    var prepared = registry.prepareReload();

    assertSame(oldSnapshot, registry.getSnapshot());
    assertEquals(2, prepared.promptCount());
    registry.publishReload(prepared);
    assertSame(prepared, registry.getSnapshot());
  }

  @Test
  void reloadIsIdempotentAndIncrementsGeneration() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();
    assertEquals(1L, registry.getSnapshot().generation());

    registry.reload();
    assertEquals(2L, registry.getSnapshot().generation());

    registry.reload();
    assertEquals(3L, registry.getSnapshot().generation());

    assertEquals(2, registry.promptCount());
    assertEquals(1, registry.postCommandCount());
    assertEquals(1, registry.approvalGateCount());
    assertEquals(1, registry.conditionalPostCommandCount());
  }

  @Test
  void failedReloadPreservesObjectIdenticalPreviousSnapshot() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);
    var registry = newRegistry(null);
    registry.reload();

    var initialSnapshot = registry.getSnapshot();
    assertEquals(1L, initialSnapshot.generation());

    // Corrupt JSON
    Files.writeString(promptsFile.toPath(), "{ this is not valid JSON", StandardCharsets.UTF_8);
    assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertSame(initialSnapshot, registry.getSnapshot());

    // Oversized file
    byte[] oversized = new byte[(int) PresetRegistry.MAX_FILE_SIZE_BYTES + 10];
    Files.write(promptsFile.toPath(), oversized);
    assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertSame(initialSnapshot, registry.getSnapshot());

    // Duplicate ID
    Files.writeString(
        promptsFile.toPath(),
        """
        {
          "prompts": [
            {"type": "chat", "id": "dupe", "prompt_text": "t1", "sanitize": true, "cancel": {"send": false, "message": "", "clickable": false, "hover_message": ""}},
            {"type": "chat", "id": "dupe", "prompt_text": "t2", "sanitize": true, "cancel": {"send": false, "message": "", "clickable": false, "hover_message": ""}}
          ]
        }
        """,
        StandardCharsets.UTF_8);
    assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertSame(initialSnapshot, registry.getSnapshot());

    // Cross-kind collision
    Files.writeString(
        promptsFile.toPath(),
        """
        {
          "prompts": [
            {"type": "chat", "id": "same_id", "prompt_text": "t1", "sanitize": true, "cancel": {"send": false, "message": "", "clickable": false, "hover_message": ""}}
          ],
          "post_commands": [
            {"id": "same_id", "command": "say hi", "execution_policy": "on_complete", "execute_as": "console"}
          ]
        }
        """,
        StandardCharsets.UTF_8);
    assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertSame(initialSnapshot, registry.getSnapshot());

    // Invalid delay
    Files.writeString(
        promptsFile.toPath(),
        """
        {
          "post_commands": [
            {"id": "cmd1", "command": "say hi", "execution_policy": "on_complete", "execute_as": "console", "delay_ticks": 72001}
          ]
        }
        """,
        StandardCharsets.UTF_8);
    assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertSame(initialSnapshot, registry.getSnapshot());

    // Previous good cache must still be intact and served
    assertEquals(2, registry.promptCount());
    assertTrue(registry.getPrompt("reason_prompt").isPresent());
    assertEquals(1L, registry.getSnapshot().generation());
  }

  @Test
  void initialLoadFailuresRemainFailClosed() throws IOException {
    Files.writeString(promptsFile.toPath(), "{ invalid json", StandardCharsets.UTF_8);
    var registry = newRegistry(null);

    var initialSnapshot = registry.getSnapshot();
    assertSame(PresetSnapshot.empty(), initialSnapshot);

    assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertSame(initialSnapshot, registry.getSnapshot());
    assertEquals(0, registry.promptCount());
    assertEquals(0, registry.postCommandCount());
  }

  @Test
  void retainedSnapshotIsSafeAcrossReloads() throws IOException {
    Files.writeString(promptsFile.toPath(), SAMPLE_JSON, StandardCharsets.UTF_8);
    var registry = newRegistry(null);
    registry.reload();

    var inFlightSnapshot = registry.getSnapshot();
    assertEquals(2, inFlightSnapshot.promptCount());
    assertTrue(inFlightSnapshot.getPrompt("reason_prompt").isPresent());

    // Update file to empty
    Files.writeString(promptsFile.toPath(), "{}", StandardCharsets.UTF_8);
    registry.reload();

    assertEquals(0, registry.promptCount());
    assertFalse(registry.getPrompt("reason_prompt").isPresent());

    // In-flight plan snapshot retains old definitions safely
    assertEquals(2, inFlightSnapshot.promptCount());
    assertTrue(inFlightSnapshot.getPrompt("reason_prompt").isPresent());
    assertEquals(1L, inFlightSnapshot.generation());
    assertEquals(2L, registry.getSnapshot().generation());
  }

  // --------------------------------------------------------------
  // Reject Duplicate IDs and Cross-Kind Collisions
  // --------------------------------------------------------------

  @Test
  void rejectDuplicateIdWithinSameKind() throws IOException {
    String dup =
        """
        {
          "prompts": [
            {
              "type": "chat",
              "id": "dupe",
              "prompt_text": "first",
              "sanitize": true,
              "cancel": { "send": false, "message": "m", "clickable": false, "hover_message": "h" }
            },
            {
              "type": "chat",
              "id": "dupe",
              "prompt_text": "second",
              "sanitize": true,
              "cancel": { "send": false, "message": "m", "clickable": false, "hover_message": "h" }
            }
          ]
        }
        """;
    Files.writeString(promptsFile.toPath(), dup, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    var ex = assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertTrue(ex.getMessage().contains("Duplicate prompt id 'dupe'"));
    assertTrue(ex.getMessage().contains("prompts[0]"));
    assertTrue(ex.getMessage().contains("prompts[1]"));
  }

  @Test
  void rejectCrossKindIdCollisionPromptAndPostCommand() throws IOException {
    String collision =
        """
        {
          "prompts": [
            {
              "type": "chat",
              "id": "shared_name",
              "prompt_text": "test",
              "sanitize": true,
              "cancel": { "send": false, "message": "", "clickable": false, "hover_message": "" }
            }
          ],
          "post_commands": [
            {
              "id": "shared_name",
              "command": "say hello",
              "execution_policy": "on_complete",
              "execute_as": "console"
            }
          ]
        }
        """;
    Files.writeString(promptsFile.toPath(), collision, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    var ex = assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertTrue(ex.getMessage().contains("Preset id 'shared_name'"));
    assertTrue(ex.getMessage().contains("collides"));
    assertTrue(ex.getMessage().contains("prompts[0]"));
  }

  @Test
  void rejectCrossKindIdCollisionGateAndConditional() throws IOException {
    String collision =
        """
        {
          "approval_gates": [
            {
              "id": "shared_gate",
              "target": "admin",
              "message": "msg"
            }
          ],
          "conditional_post_commands": [
            {
              "id": "shared_gate",
              "condition": "{0} == 1",
              "execution_policy": "on_complete",
              "if_true": { "command": "say hi", "execute_as": "console" }
            }
          ]
        }
        """;
    Files.writeString(promptsFile.toPath(), collision, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    var ex = assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertTrue(ex.getMessage().contains("Preset id 'shared_gate'"));
    assertTrue(ex.getMessage().contains("collides"));
    assertTrue(ex.getMessage().contains("approval_gates[0]"));
  }

  // --------------------------------------------------------------
  // Blank and Null ID validation
  // --------------------------------------------------------------

  @Test
  void blankIdIsRejected() throws IOException {
    Files.writeString(
        promptsFile.toPath(),
        """
        {
          "prompts": [
            {
              "type": "chat",
              "id": "  ",
              "prompt_text": "blank id",
              "sanitize": true,
              "cancel": { "send": false, "message": "m", "clickable": false, "hover_message": "h" }
            }
          ]
        }
        """,
        StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    var ex = assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertTrue(ex.getMessage().contains("blank"));
  }

  @Test
  void missingIdIsRejected() throws IOException {
    Files.writeString(
        promptsFile.toPath(),
        """
        {
          "prompts": [
            {
              "type": "chat",
              "prompt_text": "no id",
              "sanitize": true,
              "cancel": { "send": false, "message": "m", "clickable": false, "hover_message": "h" }
            }
          ]
        }
        """,
        StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
  }

  // --------------------------------------------------------------
  // Category entry count boundaries (0 / 1 / 256 / 257)
  // --------------------------------------------------------------

  @Test
  void boundary0EntriesEachKind() throws IOException {
    Files.writeString(
        promptsFile.toPath(),
        """
        {
          "prompts": [],
          "post_commands": [],
          "approval_gates": [],
          "conditional_post_commands": []
        }
        """,
        StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    assertEquals(0, registry.promptCount());
    assertEquals(0, registry.postCommandCount());
    assertEquals(0, registry.approvalGateCount());
    assertEquals(0, registry.conditionalPostCommandCount());
    assertTrue(registry.getSnapshot().isEmpty());
  }

  @Test
  void boundary1EntryEachKind() throws IOException {
    Files.writeString(
        promptsFile.toPath(),
        """
        {
          "prompts": [
            {"type": "chat", "id": "p1", "prompt_text": "p", "sanitize": true, "cancel": {"send": false, "message": "", "clickable": false, "hover_message": ""}}
          ],
          "post_commands": [
            {"id": "cmd1", "command": "say hi", "execution_policy": "on_complete", "execute_as": "console"}
          ],
          "approval_gates": [
            {"id": "gate1", "target": "admin", "message": "msg"}
          ],
          "conditional_post_commands": [
            {"id": "cond1", "condition": "{0} == 1", "execution_policy": "on_complete", "if_true": {"command": "say hi", "execute_as": "console"}}
          ]
        }
        """,
        StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    assertEquals(1, registry.promptCount());
    assertEquals(1, registry.postCommandCount());
    assertEquals(1, registry.approvalGateCount());
    assertEquals(1, registry.conditionalPostCommandCount());
    assertEquals(4, registry.getSnapshot().totalCount());
  }

  @Test
  void boundary256EntriesAllowed() throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"prompts\": [\n");
    for (int i = 0; i < 256; i++) {
      if (i > 0) sb.append(",\n");
      sb.append("    {\"type\": \"chat\", \"id\": \"p_")
          .append(i)
          .append(
              "\", \"prompt_text\": \"t\", \"sanitize\": true, \"cancel\": {\"send\": false, \"message\": \"\", \"clickable\": false, \"hover_message\": \"\"}}");
    }
    sb.append("\n  ]\n}");
    Files.writeString(promptsFile.toPath(), sb.toString(), StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    assertEquals(256, registry.promptCount());
    assertEquals(256, registry.getSnapshot().totalCount());
  }

  @Test
  void boundary257EntriesRejected() throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"prompts\": [\n");
    for (int i = 0; i < 257; i++) {
      if (i > 0) sb.append(",\n");
      sb.append("    {\"type\": \"chat\", \"id\": \"p_")
          .append(i)
          .append(
              "\", \"prompt_text\": \"t\", \"sanitize\": true, \"cancel\": {\"send\": false, \"message\": \"\", \"clickable\": false, \"hover_message\": \"\"}}");
    }
    sb.append("\n  ]\n}");
    Files.writeString(promptsFile.toPath(), sb.toString(), StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    var ex = assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertTrue(ex.getMessage().contains("exceeding maximum allowed limit of 256"));
  }

  @Test
  void boundary257PostCommandsRejected() throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"post_commands\": [\n");
    for (int i = 0; i < 257; i++) {
      if (i > 0) sb.append(",\n");
      sb.append("    {\"id\": \"pc_")
          .append(i)
          .append(
              "\", \"command\": \"say hi\", \"execution_policy\": \"on_complete\", \"execute_as\": \"console\"}");
    }
    sb.append("\n  ]\n}");
    Files.writeString(promptsFile.toPath(), sb.toString(), StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    var ex = assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertTrue(ex.getMessage().contains("exceeding maximum allowed limit of 256"));
  }

  @Test
  void boundary257ApprovalGatesRejected() throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"approval_gates\": [\n");
    for (int i = 0; i < 257; i++) {
      if (i > 0) sb.append(",\n");
      sb.append("    {\"id\": \"gate_")
          .append(i)
          .append("\", \"target\": \"admin\", \"message\": \"msg\"}");
    }
    sb.append("\n  ]\n}");
    Files.writeString(promptsFile.toPath(), sb.toString(), StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    var ex = assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertTrue(ex.getMessage().contains("exceeding maximum allowed limit of 256"));
  }

  @Test
  void boundary257ConditionalPostCommandsRejected() throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"conditional_post_commands\": [\n");
    for (int i = 0; i < 257; i++) {
      if (i > 0) sb.append(",\n");
      sb.append("    {\"id\": \"cond_")
          .append(i)
          .append(
              "\", \"condition\": \"{0} == 1\", \"execution_policy\": \"on_complete\", \"if_true\": {\"command\": \"say hi\", \"execute_as\": \"console\"}}");
    }
    sb.append("\n  ]\n}");
    Files.writeString(promptsFile.toPath(), sb.toString(), StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    var ex = assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertTrue(ex.getMessage().contains("exceeding maximum allowed limit of 256"));
  }

  // --------------------------------------------------------------
  // Delay ticks validation: 72000 / 72001
  // --------------------------------------------------------------

  @Test
  void delayTicks72000Allowed() throws IOException {
    Files.writeString(
        promptsFile.toPath(),
        """
        {
          "post_commands": [
            {
              "id": "max_delay",
              "command": "say done",
              "execution_policy": "on_complete",
              "execute_as": "console",
              "delay_ticks": 72000
            }
          ]
        }
        """,
        StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    var pc = registry.getPostCommand("max_delay");
    assertTrue(pc.isPresent());
    assertEquals(72000, pc.get().delayTicks());
  }

  @Test
  void delayTicks72001Rejected() throws IOException {
    Files.writeString(
        promptsFile.toPath(),
        """
        {
          "post_commands": [
            {
              "id": "over_delay",
              "command": "say done",
              "execution_policy": "on_complete",
              "execute_as": "console",
              "delay_ticks": 72001
            }
          ]
        }
        """,
        StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    var ex = assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertTrue(ex.getMessage().contains("72000"));
  }

  // --------------------------------------------------------------
  // File size limit (1 MiB)
  // --------------------------------------------------------------

  @Test
  void fileExceeding1MiBRejectedBeforeParsing() throws IOException {
    byte[] oversized = new byte[(int) PresetRegistry.MAX_FILE_SIZE_BYTES + 1];
    for (int i = 0; i < oversized.length; i++) {
      oversized[i] = ' ';
    }
    Files.write(promptsFile.toPath(), oversized);

    var registry = newRegistry(null);
    var ex = assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
    assertTrue(ex.getMessage().contains("exceeds maximum allowed size of 1 MiB"));
  }

  // --------------------------------------------------------------
  // Old JSON compatibility & defaults
  // --------------------------------------------------------------

  @Test
  void oldJsonFormatCompatibility() throws IOException {
    String legacyJson =
        """
        {
          "prompts": [
            {
              "type": "chat",
              "id": "legacy_chat",
              "prompt_text": "Question?",
              "sanitize": true,
              "cancel": {
                "send": false,
                "message": "",
                "clickable": false,
                "hover_message": ""
              }
            }
          ],
          "post_commands": [
            {
              "id": "legacy_pc",
              "command": "say hi",
              "execution_policy": "on_complete",
              "execute_as": "console"
            }
          ]
        }
        """;
    Files.writeString(promptsFile.toPath(), legacyJson, StandardCharsets.UTF_8);

    var registry = newRegistry(null);
    registry.reload();

    assertEquals(1, registry.promptCount());
    assertEquals(1, registry.postCommandCount());
    assertEquals(0, registry.approvalGateCount());
    assertEquals(0, registry.conditionalPostCommandCount());
    assertTrue(registry.getApprovalGateIds().isEmpty());
    assertTrue(registry.getConditionalPostCommandIds().isEmpty());
  }

  // --------------------------------------------------------------
  // Concurrent readers see consistent snapshot generation
  // --------------------------------------------------------------

  @Test
  void concurrentReadersSeeCompleteSnapshotGenerations() throws Exception {
    String jsonGen1 =
        """
        {
          "prompts": [
            {"type": "chat", "id": "p_gen1", "prompt_text": "t1", "sanitize": true, "cancel": {"send": false, "message": "", "clickable": false, "hover_message": ""}}
          ],
          "post_commands": [
            {"id": "c_gen1", "command": "say 1", "execution_policy": "on_complete", "execute_as": "console"}
          ]
        }
        """;
    String jsonGen2 =
        """
        {
          "prompts": [
            {"type": "chat", "id": "p_gen2", "prompt_text": "t2", "sanitize": true, "cancel": {"send": false, "message": "", "clickable": false, "hover_message": ""}}
          ],
          "post_commands": [
            {"id": "c_gen2", "command": "say 2", "execution_policy": "on_complete", "execute_as": "console"}
          ]
        }
        """;

    Files.writeString(promptsFile.toPath(), jsonGen1, StandardCharsets.UTF_8);
    var registry = newRegistry(null);
    registry.reload();

    var executor = Executors.newFixedThreadPool(4);
    var stopSignal = new AtomicBoolean(false);
    var latch = new CountDownLatch(1);
    List<Future<?>> readerFutures = new ArrayList<>();

    for (int i = 0; i < 3; i++) {
      readerFutures.add(
          executor.submit(
              () -> {
                try {
                  latch.await();
                  while (!stopSignal.get()) {
                    var snap = registry.getSnapshot();
                    long gen = snap.generation();
                    if (gen == 1L) {
                      assertTrue(snap.getPrompt("p_gen1").isPresent());
                      assertTrue(snap.getPostCommand("c_gen1").isPresent());
                      assertFalse(snap.getPrompt("p_gen2").isPresent());
                      assertFalse(snap.getPostCommand("c_gen2").isPresent());
                    } else if (gen == 2L) {
                      assertTrue(snap.getPrompt("p_gen2").isPresent());
                      assertTrue(snap.getPostCommand("c_gen2").isPresent());
                      assertFalse(snap.getPrompt("p_gen1").isPresent());
                      assertFalse(snap.getPostCommand("c_gen1").isPresent());
                    }
                  }
                } catch (InterruptedException ignored) {
                }
              }));
    }

    latch.countDown();
    Thread.sleep(20);
    Files.writeString(promptsFile.toPath(), jsonGen2, StandardCharsets.UTF_8);
    registry.reload();
    Thread.sleep(20);

    stopSignal.set(true);
    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

    for (var f : readerFutures) {
      f.get(); // propagate any assertion failure
    }
  }

  // --------------------------------------------------------------
  // Default resource extraction & missing file
  // --------------------------------------------------------------

  @Test
  void missingFileIsExtractedFromDefaultResource() {
    assertFalse(promptsFile.exists());
    var registry = newRegistry(DEFAULT_RESOURCE_JSON);
    registry.reload();

    assertTrue(promptsFile.exists());
    assertEquals(2, registry.promptCount());
    assertEquals(1, registry.postCommandCount());
    assertEquals(1, registry.approvalGateCount());
    assertEquals(1, registry.conditionalPostCommandCount());
  }

  @Test
  void missingFileWithNullDefaultResourceThrows() {
    assertFalse(promptsFile.exists());
    var registry = newRegistry(null);
    assertThrows(PresetRegistry.PresetLoadException.class, registry::reload);
  }

  @Test
  void getPromptsFileReturnsConfiguredPath() {
    var registry = newRegistry(null);
    assertNotNull(registry.getPromptsFile());
    assertEquals(promptsFile, registry.getPromptsFile());
  }
}
