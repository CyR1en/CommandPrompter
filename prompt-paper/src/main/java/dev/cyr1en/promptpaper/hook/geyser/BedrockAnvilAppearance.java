package dev.cyr1en.promptpaper.hook.geyser;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.Material;

/** Vanilla sprite/3D-block appearances, separate from the repair recipe's carrier items. */
record BedrockAnvilAppearance(Kind kind, String value) {
  enum Kind {
    ICON,
    TEXTURE,
    BLOCK
  }

  static Map<Material, BedrockAnvilAppearance> load() throws IOException {
    var properties = new Properties();
    try (var stream =
        BedrockAnvilAppearance.class.getResourceAsStream("/bedrock-anvil-icons.properties")) {
      if (stream == null) throw new IOException("Missing Bedrock anvil appearance catalog");
      properties.load(stream);
    }
    var result = new EnumMap<Material, BedrockAnvilAppearance>(Material.class);
    for (var key : properties.stringPropertyNames()) {
      Material material = Material.matchMaterial(key);
      if (material == null || !material.isItem() || material.isAir()) continue;
      String[] entry = properties.getProperty(key).split(":", 2);
      result.put(
          material,
          new BedrockAnvilAppearance(Kind.valueOf(entry[0].toUpperCase(Locale.ROOT)), entry[1]));
    }
    return Map.copyOf(result);
  }

  String icon(Material material) {
    return kind == Kind.TEXTURE ? "commandprompter.anvil." + material.getKey().getKey() : value;
  }

  /** Creates only aliases to client-owned vanilla textures, never copies Mojang image assets. */
  static Path writePack(Path directory, Map<Material, BedrockAnvilAppearance> appearances)
      throws IOException {
    var textures = new TreeMap<String, Object>();
    appearances.forEach(
        (material, appearance) -> {
          if (appearance.kind == Kind.TEXTURE) {
            textures.put(appearance.icon(material), Map.of("textures", appearance.value));
          }
        });
    if (textures.isEmpty()) return null;
    var gson = new Gson();
    String atlas =
        gson.toJson(
            new TreeMap<>(
                Map.of(
                    "resource_pack_name",
                    "commandprompter_anvil",
                    "texture_name",
                    "atlas.items",
                    "texture_data",
                    textures)));
    UUID id = UUID.nameUUIDFromBytes(atlas.getBytes(StandardCharsets.UTF_8));
    Files.createDirectories(directory);
    Path pack = directory.resolve("anvil-icons-" + id + ".mcpack");
    if (Files.exists(pack)) return pack;
    Path temporary = Files.createTempFile(directory, "anvil-icons-", ".tmp");
    try {
      try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
        writeEntry(zip, "manifest.json", gson.toJson(manifest(id)));
        writeEntry(zip, "textures/item_texture.json", atlas);
      }
      Files.move(temporary, pack, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
    return pack;
  }

  private static Map<String, Object> manifest(UUID id) {
    return Map.of(
        "format_version",
        2,
        "header",
        Map.of(
            "name",
            "CommandPrompter anvil icons",
            "description",
            "Vanilla texture aliases for prompt buttons",
            "uuid",
            id.toString(),
            "version",
            new int[] {1, 0, 0},
            "min_engine_version",
            new int[] {1, 26, 30}),
        "modules",
        new Object[] {
          Map.of(
              "type",
              "resources",
              "uuid",
              UUID.nameUUIDFromBytes((id + "/resources").getBytes(StandardCharsets.UTF_8))
                  .toString(),
              "version",
              new int[] {1, 0, 0})
        });
  }

  private static void writeEntry(ZipOutputStream zip, String name, String content)
      throws IOException {
    var entry = new ZipEntry(name);
    entry.setTime(0);
    zip.putNextEntry(entry);
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
