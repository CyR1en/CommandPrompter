package dev.cyr1en.promptpaper.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.cyr1en.promptpaper.MockBukkitTest;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigValidationTest extends MockBukkitTest {

  @Test
  void playerUiSizeMustBeAValidChestSize() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PromptConfigTestData().copy(Map.of("playerUISize", 20)));
  }

  @Test
  void dialogNumberDefaultsRejectInvalidRanges() {
    var data = new PromptConfigTestData();
    assertThrows(
        IllegalArgumentException.class, () -> data.copy(Map.of("dialogNumberMin", Float.NaN)));
    assertThrows(
        IllegalArgumentException.class,
        () -> data.copy(Map.of("dialogNumberMin", 10.0f, "dialogNumberMax", 10.0f)));
    assertThrows(IllegalArgumentException.class, () -> data.copy(Map.of("dialogNumberStep", 0.0f)));
  }

  @Test
  void allConfiguredValidatorRegexesAreCompiledDuringConstruction() {
    var data = new PromptConfigTestData();
    assertThrows(IllegalArgumentException.class, () -> data.copy(Map.of("intSampleRegex", "[")));
  }

  @Test
  void confirmationModeRejectsInvalidModes() {
    var data = new PromptConfigTestData();
    assertThrows(
        IllegalArgumentException.class,
        () -> data.copy(Map.of("confirmationDefaultMode", "invalid_mode")));
  }

  @Test
  void confirmationGuiItemSlotsMustBeDistinct() {
    var data = new PromptConfigTestData();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            data.copy(Map.of("confirmationConfirmItemSlot", 15, "confirmationCancelItemSlot", 15)));
    assertThrows(
        IllegalArgumentException.class,
        () -> data.copy(Map.of("confirmationConfirmItemSlot", 13, "confirmationInfoItemSlot", 13)));
    assertThrows(
        IllegalArgumentException.class,
        () -> data.copy(Map.of("confirmationCancelItemSlot", 13, "confirmationInfoItemSlot", 13)));
  }

  @Test
  void confirmationGuiItemSlotsMustBeBetween0And26() {
    var data = new PromptConfigTestData();
    // Slot 26 accepted
    var cfg26 = data.copy(Map.of("confirmationConfirmItemSlot", 26));
    org.junit.jupiter.api.Assertions.assertEquals(26, cfg26.confirmationConfirmItemSlot());

    // Slot 27 rejected
    assertThrows(
        IllegalArgumentException.class, () -> data.copy(Map.of("confirmationConfirmItemSlot", 27)));
    assertThrows(
        IllegalArgumentException.class, () -> data.copy(Map.of("confirmationCancelItemSlot", 27)));
    assertThrows(
        IllegalArgumentException.class, () -> data.copy(Map.of("confirmationInfoItemSlot", 27)));

    // Negative slot rejected
    assertThrows(
        IllegalArgumentException.class, () -> data.copy(Map.of("confirmationConfirmItemSlot", -1)));
  }

  @Test
  void screenMappingsRejectsReservedKeyOverrides() {
    for (String key : PromptConfig.RESERVED_SCREEN_KEYS) {
      if (key.isEmpty()) continue;
      var rawConfig = org.mockito.Mockito.mock(dev.cyr1en.promptcore.config.YamlDocument.class);
      org.mockito.Mockito.when(rawConfig.getKeys("screen-mappings")).thenReturn(Set.of(key));
      org.mockito.Mockito.when(rawConfig.getString("screen-mappings." + key)).thenReturn("CHAT");
      assertThrows(
          IllegalArgumentException.class,
          () -> new PromptConfigTestData().copy(Map.of("rawConfig", rawConfig)),
          "Should reject overriding reserved key: " + key);
    }
  }

  /** Reflection helper keeps this test independent of the very wide flat config record. */
  private static final class PromptConfigTestData {
    private final PromptConfig base;

    private PromptConfigTestData() {
      base =
          new PromptConfig(
              org.mockito.Mockito.mock(dev.cyr1en.promptcore.config.YamlDocument.class),
              "%s",
              0,
              54,
              256,
              1,
              "Feather",
              0,
              3,
              "Previous",
              "Feather",
              0,
              7,
              "Next",
              "Barrier",
              0,
              5,
              "Cancel",
              "Name_Tag",
              0,
              9,
              "Search",
              "Player Search",
              "PAPER",
              0,
              "Enter Player Name",
              false,
              "No players",
              "World %s",
              "Radial %s",
              true,
              "",
              "",
              false,
              "Paper",
              false,
              0,
              false,
              "Paper",
              false,
              0,
              false,
              "Barrier",
              false,
              0,
              false,
              "Cancel",
              true,
              "Cancel",
              "Hover",
              "LOWEST",
              "bottom",
              "OAK_SIGN",
              "is",
              "^\\d+",
              "bad int",
              "ss",
              "[A-Za-z ]+",
              "bad string",
              "Prompt",
              "Confirm",
              "Confirm tooltip",
              "Cancel",
              "Cancel tooltip",
              256,
              false,
              4,
              200,
              "",
              0.0f,
              100.0f,
              1.0f,
              5,
              "gui",
              "&8Confirm Action",
              "LIME_CONCRETE",
              "&aConfirm",
              11,
              "RED_CONCRETE",
              "&cCancel",
              15,
              "PAPER",
              "&eInformation",
              13,
              "&aConfirm",
              "&cCancel",
              "");
    }

    private PromptConfig copy(Map<String, Object> changes) {
      try {
        var components = PromptConfig.class.getRecordComponents();
        var arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
          arguments[i] = components[i].getAccessor().invoke(base);
        }
        var byName = new HashMap<String, Integer>();
        for (int i = 0; i < components.length; i++) byName.put(components[i].getName(), i);
        for (var entry : changes.entrySet())
          arguments[byName.get(entry.getKey())] = entry.getValue();
        Class<?>[] types =
            java.util.Arrays.stream(components)
                .map(java.lang.reflect.RecordComponent::getType)
                .toArray(Class<?>[]::new);
        Constructor<PromptConfig> constructor = PromptConfig.class.getDeclaredConstructor(types);
        return constructor.newInstance(arguments);
      } catch (java.lang.reflect.InvocationTargetException e) {
        if (e.getCause() instanceof RuntimeException runtime) throw runtime;
        throw new AssertionError(e.getCause());
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }
  }
}
