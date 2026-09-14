package dev.cyr1en.promptui.util;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.geysermc.floodgate.api.FloodgateApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BedrockUtilTest {

  @BeforeEach
  @AfterEach
  void cleanup() {
    BedrockUtil.reset();
    resetServer();
  }

  private void setBukkitServer(Server server) {
    try {
      Field serverField = Bukkit.class.getDeclaredField("server");
      serverField.setAccessible(true);
      serverField.set(null, server);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  private void resetServer() {
    setBukkitServer(null);
    FloodgateApi.setInstance(null);
  }

  private void setupMockServer(Map<String, Boolean> enabledPlugins) {
    PluginManager pluginManager =
        (PluginManager)
            Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[] {PluginManager.class},
                (proxy, method, args) -> {
                  if ("isPluginEnabled".equals(method.getName()) && args.length == 1) {
                    return Boolean.TRUE.equals(enabledPlugins.get(args[0]));
                  }
                  return null;
                });

    Server server =
        (Server)
            Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[] {Server.class},
                (proxy, method, args) -> {
                  if ("getPluginManager".equals(method.getName())) {
                    return pluginManager;
                  }
                  return null;
                });

    setBukkitServer(server);
  }

  @Test
  void nullPlayerReturnsFalse() {
    assertFalse(BedrockUtil.isBedrockPlayer((Player) null));
  }

  @Test
  void nullUuidReturnsFalse() {
    assertFalse(BedrockUtil.isBedrockPlayer((UUID) null));
  }

  @Test
  void withoutGeyserReturnsFalseSafely() {
    UUID randomUuid = UUID.randomUUID();
    assertFalse(BedrockUtil.isGeyserInstalled());
    assertFalse(BedrockUtil.isBedrockPlayer(randomUuid));
  }

  @Test
  void customBedrockCheckerOverridesDetection() {
    UUID bedrockUuid = UUID.randomUUID();
    UUID javaUuid = UUID.randomUUID();

    BedrockUtil.setBedrockChecker(uuid -> uuid.equals(bedrockUuid));

    assertTrue(BedrockUtil.isBedrockPlayer(bedrockUuid));
    assertFalse(BedrockUtil.isBedrockPlayer(javaUuid));

    Player mockPlayer =
        (Player)
            Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                  if ("getUniqueId".equals(method.getName())) {
                    return bedrockUuid;
                  }
                  return null;
                });

    assertTrue(BedrockUtil.isBedrockPlayer(mockPlayer));
  }

  @Test
  void resetRestoresDefaultBehavior() {
    UUID bedrockUuid = UUID.randomUUID();
    BedrockUtil.setBedrockChecker(uuid -> true);
    assertTrue(BedrockUtil.isBedrockPlayer(bedrockUuid));

    BedrockUtil.reset();
    assertFalse(BedrockUtil.isBedrockPlayer(bedrockUuid));
  }

  @Test
  void concurrentCheckerAccessIsThreadSafe() throws InterruptedException {
    UUID testUuid = UUID.randomUUID();
    int threads = 8;
    int iterations = 10000;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicBoolean errorOccurred = new AtomicBoolean(false);

    for (int i = 0; i < threads; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              for (int j = 0; j < iterations; j++) {
                if (threadId % 2 == 0) {
                  // Writer thread
                  if (j % 2 == 0) {
                    BedrockUtil.setBedrockChecker(u -> true);
                  } else {
                    BedrockUtil.reset();
                  }
                } else {
                  // Reader thread (must not NPE if writer resets checker mid-flight)
                  BedrockUtil.isBedrockPlayer(testUuid);
                }
              }
            } catch (Throwable t) {
              errorOccurred.set(true);
            }
          });
    }

    startLatch.countDown();
    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    assertFalse(errorOccurred.get(), "Thread safety violation detected during concurrent access");
  }

  @Test
  void simulatedFloodgateDetection() {
    UUID bedrockUuid = UUID.randomUUID();
    UUID javaUuid = UUID.randomUUID();

    setupMockServer(Map.of("floodgate", true));

    FloodgateApi floodgateApi = new FloodgateApi();
    floodgateApi.setPlayerChecker(bedrockUuid::equals);
    FloodgateApi.setInstance(floodgateApi);

    assertTrue(BedrockUtil.isBedrockPlayer(bedrockUuid));
    assertFalse(BedrockUtil.isBedrockPlayer(javaUuid));

    // When floodgate plugin is disabled
    setupMockServer(Map.of("floodgate", false));
    assertFalse(BedrockUtil.isBedrockPlayer(bedrockUuid));

    // When FloodgateApi instance is null
    setupMockServer(Map.of("floodgate", true));
    FloodgateApi.setInstance(null);
    assertFalse(BedrockUtil.isBedrockPlayer(bedrockUuid));
  }
}
