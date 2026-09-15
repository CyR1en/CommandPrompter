package dev.cyr1en.promptpaper.preset;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cyr1en.promptcore.parser.CommandLineParser;
import dev.cyr1en.promptpaper.config.sub.DialogConfig;
import dev.cyr1en.promptpaper.factory.InlineTagMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresetEditTest {
  @TempDir Path directory;

  private JsonObject definition(String id, String prompt) {
    var tag = new CommandLineParser().parse(prompt).promptTags().getFirst();
    return PresetGson.presetGson()
        .toJsonTree(
            InlineTagMapper.toPromptDefinition(
                tag, Map.of(), id, DialogConfig.legacy("Dialog", "Confirm", "", "Cancel", "")))
        .getAsJsonObject();
  }

  private PresetRegistry registry(String json) throws Exception {
    var file = directory.resolve("presets.json");
    Files.writeString(file, json);
    var registry = new PresetRegistry(file.toFile(), null);
    registry.reload();
    return registry;
  }

  @Test
  void malformedDiskAndInvalidBehaviorLeaveSnapshotIntact() throws Exception {
    var registry = registry("{}");
    var snapshot = registry.getSnapshot();
    Files.writeString(registry.getPromptsFile().toPath(), "{broken");
    assertThrows(
        PresetRegistry.PresetLoadException.class,
        () -> registry.editPrompt(PresetRegistry.Edit.ADD, "test", definition("test", "<text>")));
    assertSame(snapshot, registry.getSnapshot());
    Files.writeString(registry.getPromptsFile().toPath(), "{}");
    for (var behavior :
        new String[] {
          "{\"answer_type\":\"typo\"}",
          "{\"timeout\":1.5}",
          "{\"break_if\":\"invalid???\"}",
          "{\"flags\":{\"x\":true}}"
        }) {
      var invalid = definition("bad", "<text>");
      invalid.add("behavior", JsonParser.parseString(behavior));
      assertThrows(
          PresetRegistry.PresetLoadException.class,
          () -> registry.editPrompt(PresetRegistry.Edit.ADD, "bad", invalid));
      assertSame(snapshot, registry.getSnapshot());
      assertEquals("{}", Files.readString(registry.getPromptsFile().toPath()));
    }
  }

  @Test
  void entryLimitIsEnforcedBeforeWriting() throws Exception {
    var prompts = new com.google.gson.JsonArray();
    for (int i = 0; i < PresetRegistry.MAX_ENTRIES_PER_KIND; i++)
      prompts.add(definition("id" + i, "<text>"));
    var document = new JsonObject();
    document.add("prompts", prompts);
    var registry = registry(document.toString());
    var snapshot = registry.getSnapshot();
    assertThrows(
        PresetRegistry.PresetLoadException.class,
        () ->
            registry.editPrompt(
                PresetRegistry.Edit.ADD, "overflow", definition("overflow", "<text>")));
    assertSame(snapshot, registry.getSnapshot());
  }

  @Test
  void preservesUnrelatedJsonAndRejectsCrossCategoryIds() throws Exception {
    var original =
        """
        {"$schema":"custom", "extension":{"keep":42}, "prompts":[],
         "post_commands":[{"id":"log","command":"say hi","execute_as":"player","execution_policy":"on_complete","extra":"keep"}]}
        """;
    var registry = registry(original);
    var before = JsonParser.parseString(original).getAsJsonObject();
    registry.editPrompt(PresetRegistry.Edit.ADD, "new", definition("new", "<text>"));
    var after =
        JsonParser.parseString(Files.readString(registry.getPromptsFile().toPath()))
            .getAsJsonObject();
    for (var key : new String[] {"$schema", "extension", "post_commands"})
      assertEquals(before.get(key), after.get(key));
    var snapshot = registry.getSnapshot();
    for (var edit : PresetRegistry.Edit.values()) {
      assertThrows(
          IllegalArgumentException.class,
          () -> registry.editPrompt(edit, "log", definition("log", "<text>")));
      assertSame(snapshot, registry.getSnapshot());
    }
  }

  @Test
  void validatesBeforeWritingAndRejectsStaleReloads() throws Exception {
    var registry = registry("{}");
    var prepared = registry.prepareReload();
    registry.editPrompt(PresetRegistry.Edit.ADD, "good", definition("good", "<text>"));
    assertThrows(IllegalStateException.class, () -> registry.publishReload(prepared));
    var snapshot = registry.getSnapshot();
    var bytes = Files.readAllBytes(registry.getPromptsFile().toPath());
    var invalid = definition("bad", "<text>");
    invalid.addProperty("type", "unknown");
    assertThrows(
        PresetRegistry.PresetLoadException.class,
        () -> registry.editPrompt(PresetRegistry.Edit.ADD, "bad", invalid));
    assertSame(snapshot, registry.getSnapshot());
    assertArrayEquals(bytes, Files.readAllBytes(registry.getPromptsFile().toPath()));
    var oversized = definition("huge", "<text>");
    oversized.addProperty("prompt_text", "x".repeat((int) PresetRegistry.MAX_FILE_SIZE_BYTES));
    assertThrows(
        IllegalArgumentException.class,
        () -> registry.editPrompt(PresetRegistry.Edit.ADD, "huge", oversized));
    assertArrayEquals(bytes, Files.readAllBytes(registry.getPromptsFile().toPath()));
  }

  @Test
  void concurrentEditsRetainBothDefinitions() throws Exception {
    var registry = registry("{}");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var one =
          executor.submit(
              () ->
                  registry.editPrompt(
                      PresetRegistry.Edit.ADD, "one", definition("one", "<First>")));
      var two =
          executor.submit(
              () ->
                  registry.editPrompt(
                      PresetRegistry.Edit.ADD, "two", definition("two", "<Second>")));
      one.get();
      two.get();
    }
    registry.reload();
    assertEquals(java.util.Set.of("one", "two"), registry.getPromptIds());
  }

  @Test
  void failedWriteKeepsTheActiveSnapshot() throws Exception {
    var registry = registry("{}");
    var snapshot = registry.getSnapshot();
    Files.delete(registry.getPromptsFile().toPath());
    Files.createDirectory(registry.getPromptsFile().toPath());
    assertThrows(
        PresetRegistry.PresetLoadException.class,
        () -> registry.editPrompt(PresetRegistry.Edit.ADD, "new", definition("new", "<text>")));
    assertSame(snapshot, registry.getSnapshot());
  }
}
