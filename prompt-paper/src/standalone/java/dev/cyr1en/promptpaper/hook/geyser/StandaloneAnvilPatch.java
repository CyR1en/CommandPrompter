package dev.cyr1en.promptpaper.hook.geyser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import net.kyori.adventure.key.Key;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;

/** Injected into the standalone JAR. This class has no Paper or Bukkit dependencies. */
public final class StandaloneAnvilPatch {
  private final GeyserImpl geyser;
  private final Properties materials;
  private final byte[] announcement;
  private final Path pack;
  private boolean registered;
  private boolean packRegistered;
  private volatile boolean ready;
  private Runnable restore;
  private Runnable restoreLogin;

  public static void initialize(Object instance) {
    var geyser = (GeyserImpl) instance;
    try {
      new StandaloneAnvilPatch(geyser);
    } catch (Exception | LinkageError failure) {
      geyser
          .getLogger()
          .error("CommandPrompter standalone anvil patch could not initialize", failure);
    }
  }

  private StandaloneAnvilPatch(GeyserImpl geyser) throws Exception {
    this.geyser = geyser;
    materials = new Properties();
    try (var input =
        getClass()
            .getResourceAsStream("/" + AnvilPatchProtocol.RESOURCE_ROOT + "materials.properties")) {
      if (input == null) throw new IOException("Missing CommandPrompter material definitions");
      materials.load(input);
    }
    announcement = resource("announcement.bin");
    byte[] packBytes = resource("anvil-icons.mcpack");
    if (packBytes.length == 0) {
      pack = null;
    } else {
      String hash =
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(packBytes));
      var directory = geyser.configDirectory().resolve("commandprompter");
      Files.createDirectories(directory);
      pack = directory.resolve("anvil-icons-" + hash + ".mcpack");
      if (!Files.exists(pack)) Files.write(pack, packBytes);
    }
    var registrar = EventRegistrar.of(this);
    var bus = geyser.eventBus();
    bus.subscribe(registrar, GeyserDefineCustomItemsEvent.class, this::registerItems);
    bus.subscribe(registrar, GeyserDefineResourcePacksEvent.class, this::registerPack);
    bus.subscribe(registrar, GeyserPostInitializeEvent.class, event -> activate());
    bus.subscribe(registrar, GeyserPostReloadEvent.class, event -> activate());
    bus.subscribe(
        registrar,
        GeyserShutdownEvent.class,
        event -> {
          restorePatches();
          bus.unregisterAll(registrar);
        });
  }

  private byte[] resource(String name) throws IOException {
    try (var input =
        getClass().getResourceAsStream("/" + AnvilPatchProtocol.RESOURCE_ROOT + name)) {
      if (input == null) throw new IOException("Missing CommandPrompter patch resource: " + name);
      return input.readAllBytes();
    }
  }

  private void registerItems(GeyserDefineCustomItemsEvent event) {
    for (String material : materials.stringPropertyNames()) {
      event.register(
          Identifier.of("paper"),
          GeyserAnvilDefinitions.definition(material, materials.getProperty(material), true));
      event.register(
          Identifier.of("stick"),
          GeyserAnvilDefinitions.definition(material, materials.getProperty(material), false));
    }
    registered = true;
  }

  private void registerPack(GeyserDefineResourcePacksEvent event) {
    if (pack != null) event.register(ResourcePack.create(PackCodec.path(pack)));
    packRegistered = true;
  }

  private void activate() {
    try {
      if (!registered || !packRegistered || Registries.ITEMS.get().isEmpty()) {
        throw new IllegalStateException("Enable gameplay.enable-custom-content and restart Geyser");
      }
      for (var mappings : Registries.ITEMS.get().values()) {
        GeyserAnvilDefinitions.repairInputDefinitions(mappings, materials.stringPropertyNames());
      }
      if (restoreLogin == null)
        restoreLogin = StandaloneAnvilLoginTranslator.install(this::announce);
      if (restore == null) restore = GeyserAnvilPatch.install();
      ready = true;
      geyser.getLogger().info("CommandPrompter standalone anvil patch enabled");
    } catch (Exception | LinkageError failure) {
      restorePatches();
      geyser.getLogger().error("CommandPrompter standalone anvil patch was not applied", failure);
    }
  }

  private void restorePatches() {
    ready = false;
    if (restore != null) {
      restore.run();
      restore = null;
    }
    if (restoreLogin != null) {
      restoreLogin.run();
      restoreLogin = null;
    }
  }

  private void announce(GeyserSession session) {
    if (!ready) return;
    session.ensureInEventLoop(
        () ->
            session.sendDownstreamGamePacket(
                new ServerboundCustomPayloadPacket(
                    Key.key(AnvilPatchProtocol.CHANNEL), announcement.clone())));
  }
}
