package dev.cyr1en.promptpaper.preset;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresetConfigTest {

  private final Gson gson = PresetGson.presetGson();

  @Test
  void backwardCompatibleTwoArgConstructor() {
    PresetConfig config = new PresetConfig(List.of(), List.of());

    assertNotNull(config.prompts());
    assertNotNull(config.postCommands());
    assertNotNull(config.approvalGates());
    assertNotNull(config.conditionalPostCommands());
    assertTrue(config.approvalGates().isEmpty());
    assertTrue(config.conditionalPostCommands().isEmpty());
  }

  @Test
  void canonicalConstructorCoercesNullsToEmptyImmutableLists() {
    PresetConfig config = new PresetConfig(null, null, null, null);

    assertNotNull(config.prompts());
    assertNotNull(config.postCommands());
    assertNotNull(config.approvalGates());
    assertNotNull(config.conditionalPostCommands());

    assertTrue(config.prompts().isEmpty());
    assertTrue(config.postCommands().isEmpty());
    assertTrue(config.approvalGates().isEmpty());
    assertTrue(config.conditionalPostCommands().isEmpty());

    // Defensive immutability check
    assertThrows(UnsupportedOperationException.class, () -> config.prompts().add(null));
    assertThrows(UnsupportedOperationException.class, () -> config.postCommands().add(null));
    assertThrows(UnsupportedOperationException.class, () -> config.approvalGates().add(null));
    assertThrows(
        UnsupportedOperationException.class, () -> config.conditionalPostCommands().add(null));
  }

  @Test
  void jsonDeserializeAllFourArrays() {
    String json =
        """
        {
          "prompts": [
            {
              "type": "chat",
              "id": "chat_1",
              "prompt_text": "Enter name:",
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
              "id": "cmd_1",
              "command": "give {player} diamond 1",
              "execution_policy": "on_complete",
              "execute_as": "console"
            }
          ],
          "approval_gates": [
            {
              "id": "gate_1",
              "target": "admin",
              "message": "Approve action"
            }
          ],
          "conditional_post_commands": [
            {
              "id": "cond_1",
              "condition": "%vault_eco_balance% >= 100",
              "execution_policy": "on_complete",
              "if_true": {
                "command": "eco take {player} 100",
                "execute_as": "console"
              }
            }
          ]
        }
        """;

    PresetConfig config = gson.fromJson(json, PresetConfig.class);
    assertNotNull(config);
    assertEquals(1, config.prompts().size());
    assertEquals(1, config.postCommands().size());
    assertEquals(1, config.approvalGates().size());
    assertEquals(1, config.conditionalPostCommands().size());

    assertEquals("chat_1", config.prompts().get(0).id());
    assertEquals("cmd_1", config.postCommands().get(0).id());
    assertEquals("gate_1", config.approvalGates().get(0).id());
    assertEquals("cond_1", config.conditionalPostCommands().get(0).id());
  }

  @Test
  void jsonDeserializeWithOnlyLegacyArraysDefaultsNewArraysToEmpty() {
    String json =
        """
        {
          "prompts": [],
          "post_commands": []
        }
        """;

    PresetConfig config = gson.fromJson(json, PresetConfig.class);
    assertNotNull(config);
    assertTrue(config.prompts().isEmpty());
    assertTrue(config.postCommands().isEmpty());
    assertTrue(config.approvalGates().isEmpty());
    assertTrue(config.conditionalPostCommands().isEmpty());
  }
}
