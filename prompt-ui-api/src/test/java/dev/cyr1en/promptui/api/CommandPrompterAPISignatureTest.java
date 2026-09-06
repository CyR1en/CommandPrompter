package dev.cyr1en.promptui.api;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommandPrompterAPISignatureTest {

  @Test
  @DisplayName("API interface and factory lambdas compile and execute cleanly")
  void apiSignaturesCompile() {
    // Stub implementation to verify interface contract
    var registry = new java.util.concurrent.ConcurrentHashMap<String, PromptScreenFactory>();

    CommandPrompterAPI api =
        new CommandPrompterAPI() {
          @Override
          public void registerScreen(Plugin plugin, String key, PromptScreenFactory factory) {
            assertNotNull(plugin);
            assertNotNull(key);
            assertNotNull(factory);
            registry.put(key.toLowerCase(), factory);
          }

          @Override
          public void unregisterScreens(Plugin plugin) {
            assertNotNull(plugin);
            registry.clear();
          }
        };

    // Verify lambda registration
    PromptScreenFactory factory = (player, context) -> new MockInputScreen(player, context);
    api.registerScreen(createMockPlugin(), "custom_key", factory);

    assertTrue(registry.containsKey("custom_key"));

    var context = new ScreenContext("custom_key", "Display Text", Map.of("opt", "1"), true);
    var screen = registry.get("custom_key").createScreen(null, context);
    assertNotNull(screen);
    assertFalse(screen.isOpen());

    var resultDelivered = new AtomicBoolean(false);
    screen.onResult(
        res -> {
          resultDelivered.set(true);
          assertEquals(ScreenResult.Outcome.SUCCESS, res.outcome());
          assertEquals("custom answer", res.answer());
        });

    screen.open();
    assertTrue(screen.isOpen());
    assertTrue(resultDelivered.get());

    screen.close();
    assertFalse(screen.isOpen());
  }

  @Test
  @DisplayName("Documentation EcoItemInputScreen example conforms to the InputScreen contract")
  void docsEcoItemInputScreenConformsToContract() {
    Plugin mockPlugin = createMockPlugin();
    var context =
        new ScreenContext(
            "ecoitem", "Pick weapon", Map.of("glow", "true", "rarity", "legendary"), false);

    // Simulate construction exactly as documented in docs/custom-screens.md
    PromptScreenFactory factory =
        (player, ctx) -> new DocsEcoItemInputScreen(mockPlugin, player, ctx);
    InputScreen screen = factory.createScreen(null, context);

    assertNotNull(screen);
    assertFalse(screen.isOpen());

    AtomicReference<ScreenResult> resultRef = new AtomicReference<>();
    screen.onResult(resultRef::set);

    screen.open();
    assertTrue(screen.isOpen());

    // Programmatic close does not fire GUI exit
    screen.close();
    assertFalse(screen.isOpen());
    assertNull(resultRef.get(), "Programmatic close must not emit GUI exit result");
  }

  private static class DocsEcoItemInputScreen implements InputScreen {
    private final Plugin plugin;
    private final Player player;
    private final ScreenContext context;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private volatile boolean programmaticClose = false;
    private Consumer<ScreenResult> resultCallback;
    private Consumer<Throwable> failureCallback;
    private boolean open = false;

    DocsEcoItemInputScreen(Plugin plugin, Player player, ScreenContext context) {
      this.plugin = plugin;
      this.player = player;
      this.context = context;
    }

    @Override
    public void open() {
      this.open = true;
    }

    @Override
    public void close() {
      if (!open) return;
      this.open = false;
      this.programmaticClose = true;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void onResult(Consumer<ScreenResult> callback) {
      this.resultCallback = callback;
    }

    @Override
    public void onOpenFailure(Consumer<Throwable> callback) {
      this.failureCallback = callback;
    }

    void simulateUserClose() {
      if (programmaticClose) return;
      if (completed.compareAndSet(false, true)) {
        this.open = false;
        if (resultCallback != null) {
          resultCallback.accept(ScreenResult.guiExit());
        }
      }
    }
  }

  private Plugin createMockPlugin() {
    // Lightweight test stub for Plugin
    return (Plugin)
        java.lang.reflect.Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getName")) return "TestPlugin";
              if (method.getName().equals("isEnabled")) return true;
              if (method.getName().equals("equals")) return proxy == args[0];
              if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
              if (method.getName().equals("toString")) return "MockPlugin[TestPlugin]";
              return null;
            });
  }

  private static class MockInputScreen implements InputScreen {
    private final Player player;
    private final ScreenContext context;
    private Consumer<ScreenResult> callback;
    private boolean open = false;

    MockInputScreen(Player player, ScreenContext context) {
      this.player = player;
      this.context = context;
    }

    @Override
    public void open() {
      this.open = true;
      if (callback != null) {
        callback.accept(ScreenResult.answer("custom answer"));
      }
    }

    @Override
    public void close() {
      this.open = false;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void onResult(Consumer<ScreenResult> callback) {
      this.callback = callback;
    }
  }
}
