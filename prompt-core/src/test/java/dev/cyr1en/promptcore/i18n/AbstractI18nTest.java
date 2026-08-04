package dev.cyr1en.promptcore.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbstractI18nTest {

  @TempDir Path tempDir;

  @Test
  void failedReloadRetainsThePublishedSnapshot() throws Exception {
    var i18n =
        new StringI18n(
            "en_US",
            tempDir.toFile(),
            new ResourceClassLoader(Map.of("messages_en_US.properties", "hello=Hello\n")));
    assertEquals("Hello", i18n.get("hello"));

    Path locales = tempDir.resolve("locales");
    Files.createDirectories(locales);
    Files.writeString(
        locales.resolve("messages_en_US.properties"), "bad=\\uZZZZ\n", StandardCharsets.UTF_8);

    var failure = assertThrows(IllegalArgumentException.class, i18n::reload);

    assertEquals("Hello", i18n.get("hello"));
    assertEquals(true, failure.getMessage().contains("messages_en_US.properties"));
  }

  private static final class StringI18n extends AbstractI18n<String, Object> {
    private StringI18n(String locale, java.io.File baseDir, ClassLoader loader) {
      super(locale, baseDir, loader, Logger.getLogger("test-i18n"));
    }

    @Override
    protected String postFormat(String text, Object context) {
      return text;
    }
  }

  private static final class ResourceClassLoader extends ClassLoader {
    private final Map<String, String> resources;

    private ResourceClassLoader(Map<String, String> resources) {
      super(null);
      this.resources = resources;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      var value = resources.get(name);
      return value == null
          ? null
          : new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
  }
}
