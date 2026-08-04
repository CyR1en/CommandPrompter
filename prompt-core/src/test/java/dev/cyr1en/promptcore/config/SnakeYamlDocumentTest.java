package dev.cyr1en.promptcore.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnakeYamlDocumentTest {

  @TempDir Path tempDir;

  @Test
  void malformedYamlIsRejectedWithoutOverwritingTheFile() throws Exception {
    Path file = tempDir.resolve("config.yml");
    String original = "valid: [unterminated\n";
    Files.writeString(file, original, StandardCharsets.UTF_8);

    var failure =
        assertThrows(ConfigurationException.class, () -> new SnakeYamlDocument(file.toFile()));

    assertTrue(failure.getMessage().contains(file.toFile().getAbsolutePath()));
    assertEquals(original, Files.readString(file, StandardCharsets.UTF_8));
  }

  @Test
  void duplicateTopLevelKeyIsRejectedWithoutChangingTheSource() throws Exception {
    Path file = tempDir.resolve("config.yml");
    String original = "Prompt-Timeout: 10\nPrompt-Timeout: 20\n";
    byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
    Files.write(file, originalBytes);

    var failure =
        assertThrows(ConfigurationException.class, () -> new SnakeYamlDocument(file.toFile()));

    assertTrue(failure.getMessage().contains(file.toFile().getAbsolutePath()));
    assertTrue(failure.getMessage().contains("Duplicate YAML key"));
    assertArrayEquals(originalBytes, Files.readAllBytes(file));
  }

  @Test
  void duplicateNestedKeyIsRejectedWithoutChangingTheSource() throws Exception {
    Path file = tempDir.resolve("config.yml");
    String original = "PlayerUI:\n  Size: 27\n  Size: 54\n";
    byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
    Files.write(file, originalBytes);

    var failure =
        assertThrows(ConfigurationException.class, () -> new SnakeYamlDocument(file.toFile()));

    assertTrue(failure.getMessage().contains(file.toFile().getAbsolutePath()));
    assertTrue(failure.getMessage().contains("Duplicate YAML key"));
    assertArrayEquals(originalBytes, Files.readAllBytes(file));
  }

  @Test
  void scalarRootIsRejectedWithPath() throws Exception {
    Path file = tempDir.resolve("config.yml");
    Files.writeString(file, "just-a-scalar\n", StandardCharsets.UTF_8);

    var failure =
        assertThrows(ConfigurationException.class, () -> new SnakeYamlDocument(file.toFile()));

    assertTrue(failure.getMessage().contains(file.toFile().getAbsolutePath()));
    assertTrue(failure.getMessage().contains("mapping"));
  }

  @Test
  void scalarParentCannotBeReplacedBySet() throws Exception {
    Path file = tempDir.resolve("config.yml");
    Files.writeString(file, "PlayerUI: disabled\n", StandardCharsets.UTF_8);
    var document = new SnakeYamlDocument(file.toFile());

    var failure =
        assertThrows(
            ConfigurationException.class, () -> document.set("PlayerUI.Size", 54, new String[0]));

    assertTrue(failure.getMessage().contains("PlayerUI"));
    assertTrue(failure.getMessage().contains("mapping"));
    assertEquals("disabled", document.getString("PlayerUI"));
  }

  @Test
  void conversionsRejectUnsafeValues() throws Exception {
    Path file = tempDir.resolve("config.yml");
    Files.writeString(
        file,
        "integer: 2147483648\nfraction: 1.5\nboolean: maybe\nnumber: .NaN\nitems: scalar\n",
        StandardCharsets.UTF_8);
    var document = new SnakeYamlDocument(file.toFile());

    assertThrows(ConfigurationException.class, () -> document.getInt("integer"));
    assertThrows(ConfigurationException.class, () -> document.getInt("fraction"));
    assertThrows(ConfigurationException.class, () -> document.getBoolean("boolean"));
    assertThrows(ConfigurationException.class, () -> document.getDouble("number"));
    assertThrows(ConfigurationException.class, () -> document.getList("items"));
  }

  @Test
  void saveCreatesAValidTargetAndListsAreNonNull() throws Exception {
    Path file = tempDir.resolve("new.yml");
    var document = new SnakeYamlDocument(file.toFile());
    assertEquals(List.of(), document.getList("missing"));

    document.set("values", List.of("one", "two"), new String[0]);
    document.save(new String[] {"Header"});

    assertTrue(Files.exists(file));
    assertFalse(Files.readString(file, StandardCharsets.UTF_8).isBlank());
    assertEquals(List.of("one", "two"), document.getList("values"));
  }

  @Test
  void validConfigStillRoundTripsAndSaves() throws Exception {
    Path file = tempDir.resolve("config.yml");
    Files.writeString(
        file,
        "Prompt-Timeout: 15\nPlayerUI:\n  Size: 27\n  Enabled: true\n",
        StandardCharsets.UTF_8);

    var document = new SnakeYamlDocument(file.toFile());
    assertEquals(15, document.getInt("Prompt-Timeout"));
    assertEquals(27, document.getInt("PlayerUI.Size"));
    assertTrue(document.getBoolean("PlayerUI.Enabled"));

    document.set("PlayerUI.Enabled", false, new String[0]);
    document.save(null);

    var reloaded = new SnakeYamlDocument(file.toFile());
    assertEquals(15, reloaded.getInt("Prompt-Timeout"));
    assertEquals(27, reloaded.getInt("PlayerUI.Size"));
    assertFalse(reloaded.getBoolean("PlayerUI.Enabled"));
  }
}
