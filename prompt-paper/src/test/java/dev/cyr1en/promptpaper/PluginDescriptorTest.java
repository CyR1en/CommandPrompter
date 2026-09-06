package dev.cyr1en.promptpaper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {

  @Test
  void cancelPermissionDefaultsToTrueInShippedDescriptor() throws IOException {
    try (var descriptorStream = getClass().getResourceAsStream("/paper-plugin.yml")) {
      assertNotNull(descriptorStream, "processed paper-plugin.yml should be on the test classpath");

      var descriptor =
          YamlConfiguration.loadConfiguration(
              new InputStreamReader(descriptorStream, StandardCharsets.UTF_8));

      assertEquals(
          "Allows cancelling your own active prompt",
          descriptor.getString("permissions.promptpaper.cancel.description"));
      assertTrue(descriptor.getBoolean("permissions.promptpaper.cancel.default"));
    }
  }
}
