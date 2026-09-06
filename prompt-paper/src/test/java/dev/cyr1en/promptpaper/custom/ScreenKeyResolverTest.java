package dev.cyr1en.promptpaper.custom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.config.ScreenType;
import java.util.Map;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ScreenKeyResolverTest {

  private CustomScreenRegistry registry;
  private ScreenKeyResolver resolver;
  private Plugin testPlugin;
  private CustomScreenFactory dummyFactory;

  @BeforeEach
  void setUp() {
    registry =
        new CustomScreenRegistry(
            () -> true, Map::of, PromptConfig.RESERVED_SCREEN_KEYS, CustomScreenAuditLogger.noop());

    resolver =
        new ScreenKeyResolver(
            registry, Map.of("mapped_anvil", ScreenType.ANVIL, "mapped_dialog", ScreenType.DIALOG));

    testPlugin = mock(Plugin.class);
    when(testPlugin.getName()).thenReturn("TestPlugin");
    when(testPlugin.isEnabled()).thenReturn(true);

    dummyFactory = (player, tag) -> null;
  }

  @Nested
  @DisplayName("Resolution Order - Preset Syntax")
  class PresetSyntaxTests {

    @Test
    @DisplayName("Keys starting with @ resolve immediately to Preset")
    void testPresetResolution() {
      ScreenResolution res1 = resolver.resolve("@meta");
      assertTrue(res1.isPreset());
      assertEquals("meta", ((ScreenResolution.Preset) res1).presetId());

      ScreenResolution res2 = resolver.resolve("@player_confirmation_dialog");
      assertTrue(res2.isPreset());
      assertEquals("player_confirmation_dialog", ((ScreenResolution.Preset) res2).presetId());
    }
  }

  @Nested
  @DisplayName("Resolution Order - Built-in and Configured Mappings")
  class BuiltInMappingsTests {

    @Test
    @DisplayName("Empty or whitespace-only key resolves to CHAT")
    void testEmptyKeyResolvesToChat() {
      ScreenResolution resEmpty = resolver.resolve("");
      assertTrue(resEmpty.isBuiltIn());
      assertEquals(ScreenType.CHAT, ((ScreenResolution.BuiltIn) resEmpty).screenType());

      ScreenResolution resWhitespace = resolver.resolve("   ");
      assertTrue(resWhitespace.isBuiltIn());
      assertEquals(ScreenType.CHAT, ((ScreenResolution.BuiltIn) resWhitespace).screenType());
    }

    @Test
    @DisplayName("Standard built-in keys resolve to their respective ScreenType")
    void testStandardBuiltIns() {
      assertEquals(
          ScreenType.ANVIL, ((ScreenResolution.BuiltIn) resolver.resolve("a")).screenType());
      assertEquals(
          ScreenType.ANVIL, ((ScreenResolution.BuiltIn) resolver.resolve("anvil")).screenType());

      assertEquals(
          ScreenType.SIGN, ((ScreenResolution.BuiltIn) resolver.resolve("s")).screenType());
      assertEquals(
          ScreenType.SIGN, ((ScreenResolution.BuiltIn) resolver.resolve("sign")).screenType());

      assertEquals(
          ScreenType.PLAYER, ((ScreenResolution.BuiltIn) resolver.resolve("p")).screenType());
      assertEquals(
          ScreenType.PLAYER, ((ScreenResolution.BuiltIn) resolver.resolve("player")).screenType());

      assertEquals(
          ScreenType.DIALOG, ((ScreenResolution.BuiltIn) resolver.resolve("d")).screenType());
      assertEquals(
          ScreenType.DIALOG, ((ScreenResolution.BuiltIn) resolver.resolve("dialog")).screenType());

      assertEquals(
          ScreenType.CONFIRMATION, ((ScreenResolution.BuiltIn) resolver.resolve("c")).screenType());
      assertEquals(
          ScreenType.CONFIRMATION,
          ((ScreenResolution.BuiltIn) resolver.resolve("confirm")).screenType());
      assertEquals(
          ScreenType.CONFIRMATION,
          ((ScreenResolution.BuiltIn) resolver.resolve("confirmation")).screenType());

      assertEquals(
          ScreenType.ITEM, ((ScreenResolution.BuiltIn) resolver.resolve("i")).screenType());
      assertEquals(
          ScreenType.ITEM, ((ScreenResolution.BuiltIn) resolver.resolve("item")).screenType());
    }

    @Test
    @DisplayName("Case normalization handles uppercase and mixed-case built-ins")
    void testCaseNormalization() {
      assertEquals(
          ScreenType.ANVIL, ((ScreenResolution.BuiltIn) resolver.resolve("ANVIL")).screenType());
      assertEquals(
          ScreenType.ANVIL, ((ScreenResolution.BuiltIn) resolver.resolve("AnVil")).screenType());
      assertEquals(
          ScreenType.DIALOG, ((ScreenResolution.BuiltIn) resolver.resolve("DiAlOg")).screenType());
      assertEquals(
          ScreenType.SIGN, ((ScreenResolution.BuiltIn) resolver.resolve("  SIGN  ")).screenType());
      assertEquals(
          ScreenType.ITEM, ((ScreenResolution.BuiltIn) resolver.resolve("ITEM")).screenType());
      assertEquals(
          ScreenType.ITEM, ((ScreenResolution.BuiltIn) resolver.resolve("ItEm")).screenType());
    }

    @Test
    @DisplayName("Configured screen-mappings resolve to configured ScreenType")
    void testConfiguredScreenMappings() {
      ScreenResolution res1 = resolver.resolve("mapped_anvil");
      assertTrue(res1.isBuiltIn());
      assertEquals(ScreenType.ANVIL, ((ScreenResolution.BuiltIn) res1).screenType());

      ScreenResolution res2 = resolver.resolve("mapped_dialog");
      assertTrue(res2.isBuiltIn());
      assertEquals(ScreenType.DIALOG, ((ScreenResolution.BuiltIn) res2).screenType());
    }

    @Test
    @DisplayName("Built-in keys always take precedence over custom registration")
    void testBuiltInPrecedence() {
      ScreenResolution res = resolver.resolve("anvil");
      assertTrue(res.isBuiltIn());
      assertEquals(ScreenType.ANVIL, ((ScreenResolution.BuiltIn) res).screenType());
    }
  }

  @Nested
  @DisplayName("Resolution Order - Active Custom Screen")
  class CustomScreenResolutionTests {

    @Test
    @DisplayName("Active custom screen resolves to Custom with valid handle")
    void testActiveCustomScreenResolution() {
      registry.registerScreen(testPlugin, "my_custom_gui", dummyFactory);

      ScreenResolution res = resolver.resolve("my_custom_gui");
      assertTrue(res.isCustom());

      CustomScreenHandle handle = ((ScreenResolution.Custom) res).handle();
      assertEquals("my_custom_gui", handle.key());
      assertEquals("TestPlugin", handle.ownerName());
      assertEquals(ProviderState.ACTIVE, handle.state());
      assertTrue(handle.isActive());
    }

    @Test
    @DisplayName("Custom screen resolution is case-insensitive")
    void testCustomScreenCaseInsensitiveResolution() {
      registry.registerScreen(testPlugin, "my_custom_gui", dummyFactory);

      ScreenResolution res = resolver.resolve("MY_CUSTOM_GUI");
      assertTrue(res.isCustom());
      assertEquals("my_custom_gui", ((ScreenResolution.Custom) res).handle().key());
    }

    @Test
    @DisplayName("Re-resolution after provider unregistration returns Unresolved")
    void testReResolutionAfterUnregister() {
      registry.registerScreen(testPlugin, "ephemeral_screen", dummyFactory);

      ScreenResolution resBefore = resolver.resolve("ephemeral_screen");
      assertTrue(resBefore.isCustom());

      registry.unregisterScreens(testPlugin);

      ScreenResolution resAfter = resolver.resolve("ephemeral_screen");
      assertTrue(resAfter.isUnresolved());
      assertEquals("ephemeral_screen", ((ScreenResolution.Unresolved) resAfter).rawKey());
    }
  }

  @Nested
  @DisplayName("Resolution Order - Unresolved")
  class UnresolvedKeyTests {

    @Test
    @DisplayName("Unknown screen key resolves to Unresolved")
    void testUnknownKey() {
      ScreenResolution res = resolver.resolve("unknown_random_key");
      assertTrue(res.isUnresolved());
      assertEquals("unknown_random_key", ((ScreenResolution.Unresolved) res).rawKey());
    }

    @Test
    @DisplayName("Null key resolves to Unresolved with empty rawKey")
    void testNullKey() {
      ScreenResolution res = resolver.resolve(null);
      assertTrue(res.isUnresolved());
    }
  }

  @Nested
  @DisplayName("Config Reload Collision Detection")
  class CollisionDetectionTests {

    @Test
    @DisplayName("validateNoCollisions detects collision with active custom screen")
    void testCollisionDetection() {
      registry.registerScreen(testPlugin, "my_active_screen", dummyFactory);

      Map<String, ScreenType> candidateConfig =
          Map.of(
              "other_key", ScreenType.SIGN,
              "my_active_screen", ScreenType.ANVIL);

      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> resolver.validateNoCollisions(candidateConfig));
      assertTrue(ex.getMessage().contains("my_active_screen"));
      assertTrue(ex.getMessage().contains("TestPlugin"));
    }

    @Test
    @DisplayName("validateNoCollisions succeeds when candidate config has no collisions")
    void testNoCollisions() {
      registry.registerScreen(testPlugin, "my_active_screen", dummyFactory);

      Map<String, ScreenType> nonCollidingConfig =
          Map.of(
              "non_colliding_1", ScreenType.SIGN,
              "non_colliding_2", ScreenType.DIALOG);

      assertDoesNotThrow(() -> resolver.validateNoCollisions(nonCollidingConfig));
    }
  }
}
