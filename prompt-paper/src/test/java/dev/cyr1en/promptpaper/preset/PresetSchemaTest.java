package dev.cyr1en.promptpaper.preset;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Validates the presets JSON schema integrity, keyword syntax ($ref / $defs), reference resolution,
 * and byte-for-byte synchronization between repo copies.
 */
class PresetSchemaTest {

  private static final Path ROOT_SCHEMA_PATH = Path.of("../schema/presets.schema.json").normalize();
  private static final String RESOURCE_PATH = "/schema/presets.schema.json";

  private String readResourceSchema() throws Exception {
    try (InputStream in = getClass().getResourceAsStream(RESOURCE_PATH)) {
      assertNotNull(in, "Schema resource must be found on classpath at " + RESOURCE_PATH);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String readRootSchema() throws Exception {
    Path p = ROOT_SCHEMA_PATH;
    if (!Files.exists(p)) {
      p = Path.of("schema/presets.schema.json");
    }
    assertTrue(Files.exists(p), "Root schema must exist at " + p.toAbsolutePath());
    return Files.readString(p, StandardCharsets.UTF_8);
  }

  @Test
  void schemaCopiesAreByteForByteSynchronized() throws Exception {
    String rootContent = readRootSchema();
    String resourceContent = readResourceSchema();
    assertEquals(
        rootContent,
        resourceContent,
        "Both presets.schema.json copies must be byte-for-byte identical");
  }

  @Test
  void schemaIsValidJsonWithCorrectDefsKeyword() throws Exception {
    String content = readResourceSchema();
    JsonObject root = JsonParser.parseString(content).getAsJsonObject();

    assertNotNull(root.get("$schema"), "Schema must declare $schema");
    assertTrue(root.has("$defs"), "Schema definitions must use '$defs' keyword");
    assertFalse(root.has("defs"), "Schema definitions must NOT use bare 'defs' keyword");
  }

  @Test
  void noAccidentalBareRefOrDefsInSchema() throws Exception {
    String content = readResourceSchema();
    JsonElement root = JsonParser.parseString(content);

    List<String> violations = new ArrayList<>();
    findBareRefOrDefsViolations(root, "", violations);

    assertTrue(violations.isEmpty(), "Found bare 'ref' or 'defs' in schema: " + violations);
  }

  private void findBareRefOrDefsViolations(
      JsonElement element, String path, List<String> violations) {
    if (element.isJsonObject()) {
      JsonObject obj = element.getAsJsonObject();
      for (var entry : obj.entrySet()) {
        String key = entry.getKey();
        String currentPath = path.isEmpty() ? key : path + "." + key;
        if ("ref".equals(key)) {
          violations.add(currentPath + " (should be '$ref')");
        }
        if ("defs".equals(key)) {
          violations.add(currentPath + " (should be '$defs')");
        }
        findBareRefOrDefsViolations(entry.getValue(), currentPath, violations);
      }
    } else if (element.isJsonArray()) {
      JsonArray arr = element.getAsJsonArray();
      for (int i = 0; i < arr.size(); i++) {
        findBareRefOrDefsViolations(arr.get(i), path + "[" + i + "]", violations);
      }
    }
  }

  @Test
  void allSchemaReferencesResolveToDefs() throws Exception {
    String content = readResourceSchema();
    JsonObject root = JsonParser.parseString(content).getAsJsonObject();
    JsonObject defs = root.getAsJsonObject("$defs");
    assertNotNull(defs, "Must have $defs");

    Set<String> definedKeys = defs.keySet();
    List<String> refs = new ArrayList<>();
    collectRefs(root, refs);

    assertFalse(refs.isEmpty(), "Schema should contain $ref pointers");
    for (String ref : refs) {
      assertTrue(ref.startsWith("#/$defs/"), "Ref should target #/$defs/..., got: " + ref);
      String target = ref.substring("#/$defs/".length());
      assertTrue(
          definedKeys.contains(target),
          "Unresolved reference: " + ref + " (available: " + definedKeys + ")");
    }
  }

  private void collectRefs(JsonElement element, List<String> refs) {
    if (element.isJsonObject()) {
      JsonObject obj = element.getAsJsonObject();
      if (obj.has("$ref")) {
        refs.add(obj.get("$ref").getAsString());
      }
      for (var entry : obj.entrySet()) {
        collectRefs(entry.getValue(), refs);
      }
    } else if (element.isJsonArray()) {
      for (JsonElement item : element.getAsJsonArray()) {
        collectRefs(item, refs);
      }
    }
  }

  @Test
  void rootArraysHaveMaxItemsBounds() throws Exception {
    String content = readResourceSchema();
    JsonObject root = JsonParser.parseString(content).getAsJsonObject();
    JsonObject props = root.getAsJsonObject("properties");

    for (String arrayName :
        List.of("prompts", "post_commands", "approval_gates", "conditional_post_commands")) {
      assertTrue(props.has(arrayName), "Root schema must have property: " + arrayName);
      JsonObject arrProp = props.getAsJsonObject(arrayName);
      assertEquals("array", arrProp.get("type").getAsString());
      assertEquals(256, arrProp.get("maxItems").getAsInt(), arrayName + " must have maxItems: 256");
    }
  }

  @Test
  void approvalGateSchemaDefinitions() throws Exception {
    String content = readResourceSchema();
    JsonObject root = JsonParser.parseString(content).getAsJsonObject();
    JsonObject defs = root.getAsJsonObject("$defs");
    JsonObject gate = defs.getAsJsonObject("approvalGate");
    assertNotNull(gate);

    JsonObject props = gate.getAsJsonObject("properties");
    assertEquals("^[a-z0-9_.-]{1,64}$", props.getAsJsonObject("id").get("pattern").getAsString());
    assertEquals(1, props.getAsJsonObject("target").get("minLength").getAsInt());
    assertEquals(1024, props.getAsJsonObject("target").get("maxLength").getAsInt());
    assertEquals(1, props.getAsJsonObject("message").get("minLength").getAsInt());
    assertEquals(1024, props.getAsJsonObject("message").get("maxLength").getAsInt());
    assertEquals(1, props.getAsJsonObject("timeout").get("minimum").getAsInt());
    assertEquals(3600, props.getAsJsonObject("timeout").get("maximum").getAsInt());
    assertEquals(30, props.getAsJsonObject("timeout").get("default").getAsInt());

    JsonObject onDeny = props.getAsJsonObject("on_deny");
    assertNotNull(onDeny);
    JsonArray delayEnum =
        onDeny.getAsJsonObject("properties").getAsJsonObject("delay_ticks").getAsJsonArray("enum");
    assertEquals(1, delayEnum.size());
    assertEquals(0, delayEnum.get(0).getAsInt());
  }

  @Test
  void conditionalPostCommandSchemaDefinitions() throws Exception {
    String content = readResourceSchema();
    JsonObject root = JsonParser.parseString(content).getAsJsonObject();
    JsonObject defs = root.getAsJsonObject("$defs");
    JsonObject cmd = defs.getAsJsonObject("conditionalPostCommand");
    assertNotNull(cmd);

    JsonObject props = cmd.getAsJsonObject("properties");
    assertEquals("^[a-z0-9_.-]{1,64}$", props.getAsJsonObject("id").get("pattern").getAsString());
    assertEquals(1, props.getAsJsonObject("condition").get("minLength").getAsInt());
    assertEquals(1024, props.getAsJsonObject("condition").get("maxLength").getAsInt());
    assertEquals(
        "#/$defs/trustedPresetAction", props.getAsJsonObject("if_true").get("$ref").getAsString());
    assertEquals(
        "#/$defs/trustedPresetAction", props.getAsJsonObject("if_false").get("$ref").getAsString());

    assertTrue(
        cmd.has("anyOf"),
        "conditionalPostCommand must have anyOf constraint for branch requirement");
  }

  @Test
  void trustedPresetActionSchemaDefinitions() throws Exception {
    String content = readResourceSchema();
    JsonObject root = JsonParser.parseString(content).getAsJsonObject();
    JsonObject defs = root.getAsJsonObject("$defs");
    JsonObject action = defs.getAsJsonObject("trustedPresetAction");
    assertNotNull(action);

    JsonObject props = action.getAsJsonObject("properties");
    assertEquals(1, props.getAsJsonObject("command").get("minLength").getAsInt());
    assertEquals(1024, props.getAsJsonObject("command").get("maxLength").getAsInt());
    assertEquals(0, props.getAsJsonObject("delay_ticks").get("minimum").getAsInt());
    assertEquals(72000, props.getAsJsonObject("delay_ticks").get("maximum").getAsInt());
  }

  @Test
  void legacyPostCommandDelayBoundsInSchema() throws Exception {
    String content = readResourceSchema();
    JsonObject root = JsonParser.parseString(content).getAsJsonObject();
    JsonObject defs = root.getAsJsonObject("$defs");
    JsonObject postCmd = defs.getAsJsonObject("postCommand");
    assertNotNull(postCmd, "Schema must contain postCommand definition in $defs");

    JsonObject props = postCmd.getAsJsonObject("properties");
    JsonObject delay = props.getAsJsonObject("delay_ticks");
    assertNotNull(delay, "Legacy postCommand must define delay_ticks property");
    assertEquals("integer", delay.get("type").getAsString());
    assertEquals(0, delay.get("minimum").getAsInt(), "delay_ticks minimum must be 0");
    assertEquals(72000, delay.get("maximum").getAsInt(), "delay_ticks maximum must be 72000");
    assertEquals(0, delay.get("default").getAsInt(), "delay_ticks default must be 0");
  }

  @Test
  void confirmationModePatternMatchesMixedCaseVariantsAndRejectsInvalid() throws Exception {
    String content = readResourceSchema();
    JsonObject root = JsonParser.parseString(content).getAsJsonObject();
    JsonObject defs = root.getAsJsonObject("$defs");
    JsonObject confPrompt = defs.getAsJsonObject("confirmationPrompt");
    JsonObject modeProp = confPrompt.getAsJsonObject("properties").getAsJsonObject("mode");
    String patternStr = modeProp.get("pattern").getAsString();

    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr);

    // Accepted case variations
    List<String> validModes =
        List.of(
            "gui", "chat", "dialog", "GUI", "CHAT", "DIALOG", "Gui", "Chat", "Dialog", "gUi",
            "cHaT", "dIaLoG");
    for (String m : validModes) {
      assertTrue(pattern.matcher(m).matches(), "Pattern should match mode: " + m);
    }

    // Rejected invalid modes
    List<String> invalidModes =
        List.of("invalid", "unknown", "telepathy", "", "gui1", "dialogs", "chat_mode");
    for (String m : invalidModes) {
      assertFalse(pattern.matcher(m).matches(), "Pattern should reject mode: " + m);
    }
  }

  @Test
  void confirmationTimeoutPropertyHasValidBoundsInSchema() throws Exception {
    String content = readResourceSchema();
    JsonObject root = JsonParser.parseString(content).getAsJsonObject();
    JsonObject defs = root.getAsJsonObject("$defs");
    JsonObject confPrompt = defs.getAsJsonObject("confirmationPrompt");
    JsonObject timeoutProp = confPrompt.getAsJsonObject("properties").getAsJsonObject("timeout");

    assertNotNull(timeoutProp, "Schema must contain 'timeout' property for confirmationPrompt");
    assertEquals("integer", timeoutProp.get("type").getAsString());
    assertEquals(1, timeoutProp.get("minimum").getAsInt());
    assertEquals(3600, timeoutProp.get("maximum").getAsInt());
  }
}
