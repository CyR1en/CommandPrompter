package dev.cyr1en.promptpaper.hook.geyser;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.translator.inventory.AnvilInventoryTranslator;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;

/** Version-bound replacement of the private registry entry, never an on-disk jar edit. */
public final class GeyserAnvilPatch {
  private GeyserAnvilPatch() {}

  public static Runnable install() throws Exception {
    var properties = new Properties();
    try (var jar =
        new JarFile(
            new File(
                GeyserImpl.class.getProtectionDomain().getCodeSource().getLocation().toURI()))) {
      var entry = jar.getJarEntry("git.properties");
      if (entry == null) throw new IllegalStateException("Geyser build information is missing");
      try (var input = jar.getInputStream(entry)) {
        properties.load(input);
      }
    }
    requireSupportedBuild(properties);

    var field = InventoryTranslator.class.getDeclaredField("INVENTORY_TRANSLATORS");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    var translators =
        (Map<ContainerType, InventoryTranslator<? extends Inventory>>) field.get(null);
    var original = translators.get(ContainerType.ANVIL);
    if (original == null || original.getClass() != AnvilInventoryTranslator.class) {
      throw new IllegalStateException("Another integration already replaced the anvil translator");
    }
    var replacement = new PatchedAnvilTranslator();
    translators.put(ContainerType.ANVIL, replacement);
    return () -> translators.replace(ContainerType.ANVIL, replacement, original);
  }

  static void requireSupportedBuild(Properties properties) {
    if (!"1245".equals(properties.getProperty("git.build.number"))
        || !"2808f7d21358a13019727fdf8737a5f978b23af4"
            .equals(properties.getProperty("git.commit.id"))) {
      throw new IllegalStateException(
          "Supported: official Geyser-Spigot 2.11.3 build 1245; found "
              + properties.getProperty("git.build.version", "unknown"));
    }
  }
}
