package dev.cyr1en.promptpaper.approval;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApprovalCapabilityTest {

  @Test
  @DisplayName("ApprovalCapability validates all parameters and rejects null or blank")
  void testConstructorValidation() {
    ExecutionId execId = ExecutionId.create();
    UUID initiator = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    Instant expires = Instant.now().plusSeconds(30);

    assertThrows(
        NullPointerException.class,
        () -> new ApprovalCapability(null, execId, "gate1", initiator, 1, target, expires));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ApprovalCapability("", execId, "gate1", initiator, 1, target, expires));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ApprovalCapability(
                "invalid_no_prefix", execId, "gate1", initiator, 1, target, expires));
    assertThrows(
        NullPointerException.class,
        () -> new ApprovalCapability("a_nonce123", null, "gate1", initiator, 1, target, expires));
    assertThrows(
        NullPointerException.class,
        () -> new ApprovalCapability("a_nonce123", execId, null, initiator, 1, target, expires));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ApprovalCapability(
                "a_nonce123", execId, "a".repeat(65), initiator, 1, target, expires));
    assertThrows(
        NullPointerException.class,
        () -> new ApprovalCapability("a_nonce123", execId, "gate1", null, 1, target, expires));
    assertThrows(
        NullPointerException.class,
        () -> new ApprovalCapability("a_nonce123", execId, "gate1", initiator, 1, null, expires));
    assertThrows(
        NullPointerException.class,
        () -> new ApprovalCapability("a_nonce123", execId, "gate1", initiator, 1, target, null));
  }

  @Test
  @DisplayName("isExpired respects now instant")
  void testIsExpired() {
    Instant now = Instant.parse("2026-08-18T12:00:00Z");
    Instant expires = now.plusSeconds(30);
    ApprovalCapability cap =
        new ApprovalCapability(
            "a_nonce1234567890",
            ExecutionId.create(),
            "gate1",
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            expires);

    assertFalse(cap.isExpired(now));
    assertFalse(cap.isExpired(now.plusSeconds(29)));
    assertTrue(cap.isExpired(now.plusSeconds(30)));
    assertTrue(cap.isExpired(now.plusSeconds(31)));
  }

  @Test
  @DisplayName("matchesResponder checks target equality")
  void testMatchesResponder() {
    UUID target = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    ApprovalCapability cap =
        new ApprovalCapability(
            "a_nonce1234567890",
            ExecutionId.create(),
            "gate1",
            UUID.randomUUID(),
            1,
            target,
            Instant.now().plusSeconds(30));

    assertTrue(cap.matchesResponder(target));
    assertFalse(cap.matchesResponder(other));
    assertFalse(cap.matchesResponder(null));
  }

  @Test
  @DisplayName("safeMaskedNonce and toString do not leak full nonce")
  void testSafeMaskedNonce() {
    String rawNonce = "a_super-secret-cryptographic-nonce-token";
    ApprovalCapability cap =
        new ApprovalCapability(
            rawNonce,
            ExecutionId.create(),
            "gate1",
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            Instant.now().plusSeconds(30));

    String masked = cap.safeMaskedNonce();
    assertFalse(masked.contains(rawNonce));
    assertTrue(masked.endsWith("..."));

    String str = cap.toString();
    assertFalse(str.contains(rawNonce));
    assertTrue(str.contains(masked));
  }
}
