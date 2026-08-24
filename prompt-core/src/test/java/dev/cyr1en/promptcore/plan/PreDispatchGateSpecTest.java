package dev.cyr1en.promptcore.plan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PreDispatchGateSpecTest {

  @ParameterizedTest
  @ValueSource(strings = {"trade_gate", "approval-1", "test.preset", "valid_id_123", "a"})
  void testValidApprovalPresetIds(String presetId) {
    PreDispatchGateSpec.Approval gate = new PreDispatchGateSpec.Approval(presetId);
    assertEquals(presetId, gate.presetId());
  }

  @Test
  void testNullOrBlankPresetIdRejected() {
    assertThrows(NullPointerException.class, () -> new PreDispatchGateSpec.Approval(null));
    assertThrows(IllegalArgumentException.class, () -> new PreDispatchGateSpec.Approval(""));
    assertThrows(IllegalArgumentException.class, () -> new PreDispatchGateSpec.Approval("   "));
  }

  @Test
  void testOversizedPresetIdRejected() {
    String oversizedId = "a".repeat(PreDispatchGateSpec.Approval.MAX_PRESET_ID_LENGTH + 1);
    assertThrows(
        IllegalArgumentException.class, () -> new PreDispatchGateSpec.Approval(oversizedId));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "trade gate",
        "gate@123",
        "gate!#$",
        "gate/test",
        "<gate>",
        "gate;rm",
        "VALID_ID_123",
        "Gate",
        "tradeGate"
      })
  void testInvalidCharactersRejected(String invalidId) {
    assertThrows(IllegalArgumentException.class, () -> new PreDispatchGateSpec.Approval(invalidId));
  }
}
