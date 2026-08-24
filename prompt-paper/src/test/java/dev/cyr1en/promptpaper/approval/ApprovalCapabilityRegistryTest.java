package dev.cyr1en.promptpaper.approval;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApprovalCapabilityRegistryTest {

  private MutableClock clock;
  private ApprovalCapabilityRegistry registry;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-08-18T12:00:00Z"));
    registry = new ApprovalCapabilityRegistry(clock);
  }

  @Test
  @DisplayName("generateNonce produces namespaced tokens (a_) with >= 128-bit entropy")
  void testNonceEntropyAndFormat() {
    Set<String> generated = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      String nonce = registry.generateNonce();
      assertNotNull(nonce);
      assertTrue(nonce.startsWith("a_"), "Nonce must start with 'a_'");
      assertEquals(24, nonce.length(), "Prefix 'a_' plus 22 Base64 chars must be 24 chars");
      assertTrue(
          nonce.matches("^a_[A-Za-z0-9_-]+$"),
          "Nonce must contain 'a_' prefix and URL-safe base64 characters without padding");
      assertTrue(ApprovalCapabilityRegistry.isApprovalNonce(nonce));
      assertTrue(generated.add(nonce), "Generated nonces must be collision-free");
    }
  }

  @Test
  @DisplayName("isApprovalNonce classifies nonces correctly")
  void testIsApprovalNonce() {
    assertTrue(ApprovalCapabilityRegistry.isApprovalNonce("a_123456789"));
    assertFalse(ApprovalCapabilityRegistry.isApprovalNonce("123456789"));
    assertFalse(ApprovalCapabilityRegistry.isApprovalNonce("b_123456789"));
    assertFalse(ApprovalCapabilityRegistry.isApprovalNonce(null));
  }

  @Test
  @DisplayName("registerCapability rejects preconstructed duplicate nonce")
  void testRegisterCapabilityRejectsDuplicateNonce() {
    ExecutionId exec1 = ExecutionId.create();
    ExecutionId exec2 = ExecutionId.create();
    UUID initiator = UUID.randomUUID();
    UUID target1 = UUID.randomUUID();
    UUID target2 = UUID.randomUUID();
    String nonce = "a_customNonce12345678";

    ApprovalCapability cap1 =
        new ApprovalCapability(
            nonce, exec1, "gate1", initiator, 1L, target1, clock.instant().plusSeconds(30));
    ApprovalCapability cap2 =
        new ApprovalCapability(
            nonce, exec2, "gate2", initiator, 1L, target2, clock.instant().plusSeconds(30));

    assertTrue(registry.registerCapability(cap1));
    assertFalse(registry.registerCapability(cap2), "Duplicate nonce must be rejected");
  }

  @Test
  @DisplayName("register successfully indexes capability across nonce, initiator, target, and execution")
  void testRegisterAndIndexing() {
    ExecutionId execId = ExecutionId.create();
    UUID initiator = UUID.randomUUID();
    UUID target = UUID.randomUUID();

    Optional<ApprovalCapability> capOpt =
        registry.register(execId, "admin_gate", initiator, 1L, target, Duration.ofSeconds(30));

    assertTrue(capOpt.isPresent());
    ApprovalCapability cap = capOpt.get();

    assertEquals(execId, cap.executionId());
    assertEquals("admin_gate", cap.gateId());
    assertEquals(initiator, cap.initiator());
    assertEquals(1L, cap.initiatorIncarnation());
    assertEquals(target, cap.target());
    assertEquals(clock.instant().plusSeconds(30), cap.expiresAt());

    // Verify lookups across indexes
    assertEquals(Optional.of(cap), registry.getByNonce(cap.nonce()));
    assertEquals(Optional.of(cap), registry.getByTarget(target));
    assertEquals(Optional.of(cap), registry.getByExecution(execId));
    assertTrue(registry.isTargetBusy(target));
  }

  @Test
  @DisplayName("register fails closed when target already has an active pending capability")
  void testTargetCollisionFailsClosed() {
    ExecutionId exec1 = ExecutionId.create();
    ExecutionId exec2 = ExecutionId.create();
    UUID initiator1 = UUID.randomUUID();
    UUID initiator2 = UUID.randomUUID();
    UUID sharedTarget = UUID.randomUUID();

    Optional<ApprovalCapability> cap1 =
        registry.register(exec1, "gate_a", initiator1, 1L, sharedTarget, Duration.ofSeconds(30));
    assertTrue(cap1.isPresent());

    // Target is now busy; second registration must fail closed
    Optional<ApprovalCapability> cap2 =
        registry.register(exec2, "gate_b", initiator2, 1L, sharedTarget, Duration.ofSeconds(30));
    assertTrue(cap2.isEmpty(), "Registration must fail closed when target is busy");
    assertEquals(1, registry.size());
  }

  @Test
  @DisplayName("register succeeds for target if prior capability has expired")
  void testTargetCollisionRecoveryAfterExpiry() {
    ExecutionId exec1 = ExecutionId.create();
    ExecutionId exec2 = ExecutionId.create();
    UUID target = UUID.randomUUID();

    Optional<ApprovalCapability> cap1 =
        registry.register(exec1, "gate_a", UUID.randomUUID(), 1L, target, Duration.ofSeconds(30));
    assertTrue(cap1.isPresent());

    // Advance clock past expiration
    clock.advance(Duration.ofSeconds(31));

    // New registration for same target should now succeed and clean up stale capability
    Optional<ApprovalCapability> cap2 =
        registry.register(exec2, "gate_b", UUID.randomUUID(), 1L, target, Duration.ofSeconds(30));
    assertTrue(cap2.isPresent());
    assertEquals(exec2, cap2.get().executionId());
    assertEquals(1, registry.size());
  }

  @Test
  @DisplayName("consume succeeds for correct target and atomically removes from all indexes")
  void testConsumeSuccess() {
    ExecutionId execId = ExecutionId.create();
    UUID initiator = UUID.randomUUID();
    UUID target = UUID.randomUUID();

    ApprovalCapability cap =
        registry
            .register(execId, "gate1", initiator, 1L, target, Duration.ofSeconds(30))
            .orElseThrow();

    Optional<ApprovalCapability> consumed = registry.consume(cap.nonce(), target);
    assertTrue(consumed.isPresent());
    assertEquals(cap, consumed.get());

    // Verify completely removed from all indexes
    assertEquals(0, registry.size());
    assertTrue(registry.getByNonce(cap.nonce()).isEmpty());
    assertTrue(registry.getByTarget(target).isEmpty());
    assertTrue(registry.getByExecution(execId).isEmpty());
    assertFalse(registry.isTargetBusy(target));
  }

  @Test
  @DisplayName("consume with wrong responder target does NOT consume or remove capability")
  void testWrongTargetNoConsume() {
    ExecutionId execId = ExecutionId.create();
    UUID initiator = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    UUID attacker = UUID.randomUUID();

    ApprovalCapability cap =
        registry
            .register(execId, "gate1", initiator, 1L, target, Duration.ofSeconds(30))
            .orElseThrow();

    Optional<ApprovalCapability> consumed = registry.consume(cap.nonce(), attacker);
    assertTrue(consumed.isEmpty(), "Consume must fail when responder target does not match");

    // Capability must remain active and intact
    assertEquals(1, registry.size());
    assertEquals(Optional.of(cap), registry.getByNonce(cap.nonce()));
    assertTrue(registry.isTargetBusy(target));

    // Valid target can still consume afterwards
    Optional<ApprovalCapability> validConsumed = registry.consume(cap.nonce(), target);
    assertTrue(validConsumed.isPresent());
  }

  @Test
  @DisplayName("replay prevention: second consume fails")
  void testReplayPrevention() {
    UUID target = UUID.randomUUID();
    ApprovalCapability cap =
        registry
            .register(ExecutionId.create(), "gate1", UUID.randomUUID(), 1L, target, Duration.ofSeconds(30))
            .orElseThrow();

    Optional<ApprovalCapability> first = registry.consume(cap.nonce(), target);
    assertTrue(first.isPresent());

    Optional<ApprovalCapability> second = registry.consume(cap.nonce(), target);
    assertTrue(second.isEmpty(), "Replayed consume must return empty");
  }

  @Test
  @DisplayName("expiry prevents consume and prunes capability")
  void testExpiry() {
    UUID target = UUID.randomUUID();
    ApprovalCapability cap =
        registry
            .register(ExecutionId.create(), "gate1", UUID.randomUUID(), 1L, target, Duration.ofSeconds(30))
            .orElseThrow();

    clock.advance(Duration.ofSeconds(35));

    Optional<ApprovalCapability> consumed = registry.consume(cap.nonce(), target);
    assertTrue(consumed.isEmpty(), "Expired capability cannot be consumed");
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName("invalidateExecution removes capability and clears all secondary indexes")
  void testInvalidateExecution() {
    ExecutionId execId = ExecutionId.create();
    UUID target = UUID.randomUUID();

    ApprovalCapability cap =
        registry
            .register(execId, "gate1", UUID.randomUUID(), 1L, target, Duration.ofSeconds(30))
            .orElseThrow();

    Optional<ApprovalCapability> removed = registry.invalidateExecution(execId);
    assertTrue(removed.isPresent());
    assertEquals(cap, removed.get());
    assertEquals(0, registry.size());
    assertFalse(registry.isTargetBusy(target));
  }

  @Test
  @DisplayName("invalidateTarget removes target capability")
  void testInvalidateTarget() {
    UUID target = UUID.randomUUID();
    ApprovalCapability cap =
        registry
            .register(ExecutionId.create(), "gate1", UUID.randomUUID(), 1L, target, Duration.ofSeconds(30))
            .orElseThrow();

    Optional<ApprovalCapability> removed = registry.invalidateTarget(target);
    assertTrue(removed.isPresent());
    assertEquals(cap, removed.get());
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName("invalidateInitiator removes all capabilities for initiator across incarnations")
  void testInvalidateInitiator() {
    UUID initiator = UUID.randomUUID();
    UUID target1 = UUID.randomUUID();
    UUID target2 = UUID.randomUUID();

    registry.register(ExecutionId.create(), "gate1", initiator, 1L, target1, Duration.ofSeconds(30));
    registry.register(ExecutionId.create(), "gate2", initiator, 2L, target2, Duration.ofSeconds(30));
    assertEquals(2, registry.size());

    List<ApprovalCapability> removed = registry.invalidateInitiator(initiator);
    assertEquals(2, removed.size());
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName("invalidateInitiator with incarnation removes only matching incarnation")
  void testInvalidateInitiatorIncarnation() {
    UUID initiator = UUID.randomUUID();
    UUID target1 = UUID.randomUUID();
    UUID target2 = UUID.randomUUID();

    ApprovalCapability cap1 =
        registry
            .register(ExecutionId.create(), "gate1", initiator, 1L, target1, Duration.ofSeconds(30))
            .orElseThrow();
    ApprovalCapability cap2 =
        registry
            .register(ExecutionId.create(), "gate2", initiator, 2L, target2, Duration.ofSeconds(30))
            .orElseThrow();
    assertEquals(2, registry.size());

    List<ApprovalCapability> removed = registry.invalidateInitiator(initiator, 1L);
    assertEquals(1, removed.size());
    assertEquals(cap1, removed.get(0));

    assertEquals(1, registry.size());
    assertEquals(Optional.of(cap2), registry.getByTarget(target2));
  }

  @Test
  @DisplayName("cleanExpired prunes only expired capabilities")
  void testCleanExpired() {
    UUID target1 = UUID.randomUUID();
    UUID target2 = UUID.randomUUID();

    registry.register(ExecutionId.create(), "gate1", UUID.randomUUID(), 1L, target1, Duration.ofSeconds(10));
    registry.register(ExecutionId.create(), "gate2", UUID.randomUUID(), 1L, target2, Duration.ofSeconds(60));

    clock.advance(Duration.ofSeconds(20));

    int pruned = registry.cleanExpired();
    assertEquals(1, pruned);
    assertEquals(1, registry.size());
    assertFalse(registry.isTargetBusy(target1));
    assertTrue(registry.isTargetBusy(target2));
  }

  @Test
  @DisplayName("Concurrent consume allows exactly one winner among competing threads")
  void testConcurrentConsumeOneWinner() throws Exception {
    UUID target = UUID.randomUUID();
    ApprovalCapability cap =
        registry
            .register(ExecutionId.create(), "gate1", UUID.randomUUID(), 1L, target, Duration.ofSeconds(30))
            .orElseThrow();

    int threadCount = 16;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<Future<Optional<ApprovalCapability>>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      futures.add(
          executor.submit(
              () -> {
                startLatch.await();
                return registry.consume(cap.nonce(), target);
              }));
    }

    startLatch.countDown();

    int successCount = 0;
    for (Future<Optional<ApprovalCapability>> future : futures) {
      if (future.get().isPresent()) {
        successCount++;
      }
    }
    executor.shutdown();

    assertEquals(1, successCount, "Exactly one thread must successfully consume the capability");
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName("toString and truncateNonce do not expose raw nonce")
  void testNoRawNonceLogging() {
    String nonce = registry.generateNonce();
    String truncated = ApprovalCapabilityRegistry.truncateNonce(nonce);
    assertFalse(truncated.contains(nonce));
    assertTrue(truncated.endsWith("..."));

    String regStr = registry.toString();
    assertFalse(regStr.contains(nonce));
  }

  @Test
  @DisplayName("NONCE COLLISION: Retries >10 collisions and succeeds when unique nonce found")
  void testForceOver10NonceCollisionsAndSucceeds() {
    byte[] collisionBytes = new byte[16];
    java.util.Arrays.fill(collisionBytes, (byte) 0x42);

    byte[] uniqueBytes = new byte[16];
    java.util.Arrays.fill(uniqueBytes, (byte) 0x99);

    byte[][] sequence = new byte[13][];
    for (int i = 0; i < 12; i++) {
      sequence[i] = collisionBytes; // 12 collisions (>10)
    }
    sequence[12] = uniqueBytes; // 13th attempt succeeds

    ScriptedSecureRandom scriptedRandom = new ScriptedSecureRandom(sequence);
    ApprovalCapabilityRegistry testRegistry = new ApprovalCapabilityRegistry(scriptedRandom, clock);

    // Pre-seed the colliding capability
    String collisionNonce = "a_" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(collisionBytes);
    ApprovalCapability initialCap =
        new ApprovalCapability(
            collisionNonce,
            ExecutionId.create(),
            "gate1",
            UUID.randomUUID(),
            1L,
            UUID.randomUUID(),
            clock.instant().plusSeconds(60));
    assertTrue(testRegistry.registerCapability(initialCap));

    // Register second capability which will experience 12 collisions before unique nonce
    ExecutionId exec2 = ExecutionId.create();
    UUID target2 = UUID.randomUUID();
    Optional<ApprovalCapability> resultOpt =
        testRegistry.register(exec2, "gate2", UUID.randomUUID(), 1L, target2, Duration.ofSeconds(30));

    assertTrue(resultOpt.isPresent(), "Registration must succeed after bounded retry loop");
    assertEquals(2, testRegistry.size());
    assertEquals(initialCap, testRegistry.getByNonce(collisionNonce).orElseThrow());
    assertNotEquals(collisionNonce, resultOpt.get().nonce());
  }

  @Test
  @DisplayName("NONCE COLLISION: Exhaustion of MAX_NONCE_GENERATION_ATTEMPTS fails closed without overwriting")
  void testNonceCollisionExhaustionFailsClosedWithoutOverwriting() {
    byte[] collisionBytes = new byte[16];
    java.util.Arrays.fill(collisionBytes, (byte) 0x55);

    // Provide 20 colliding attempts (> 16 MAX_NONCE_GENERATION_ATTEMPTS)
    byte[][] sequence = new byte[20][];
    for (int i = 0; i < 20; i++) {
      sequence[i] = collisionBytes;
    }

    ScriptedSecureRandom scriptedRandom = new ScriptedSecureRandom(sequence);
    ApprovalCapabilityRegistry testRegistry = new ApprovalCapabilityRegistry(scriptedRandom, clock);

    // Pre-seed the colliding capability
    String collisionNonce = "a_" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(collisionBytes);
    ApprovalCapability initialCap =
        new ApprovalCapability(
            collisionNonce,
            ExecutionId.create(),
            "gate1",
            UUID.randomUUID(),
            1L,
            UUID.randomUUID(),
            clock.instant().plusSeconds(60));
    assertTrue(testRegistry.registerCapability(initialCap));

    // Attempt registration with all collisions
    ExecutionId exec2 = ExecutionId.create();
    UUID target2 = UUID.randomUUID();
    Optional<ApprovalCapability> resultOpt =
        testRegistry.register(exec2, "gate2", UUID.randomUUID(), 1L, target2, Duration.ofSeconds(30));

    assertTrue(resultOpt.isEmpty(), "Registration must fail closed when retry attempts exhausted");
    assertEquals(1, testRegistry.size(), "Registry size must remain unchanged");
    assertEquals(initialCap, testRegistry.getByNonce(collisionNonce).orElseThrow(), "Initial capability must remain untouched");
  }

  private static final class ScriptedSecureRandom extends java.security.SecureRandom {
    private final byte[][] sequences;
    private int index = 0;

    ScriptedSecureRandom(byte[][] sequences) {
      this.sequences = sequences;
    }

    @Override
    public void nextBytes(byte[] bytes) {
      if (index < sequences.length) {
        byte[] src = sequences[index++];
        System.arraycopy(src, 0, bytes, 0, Math.min(bytes.length, src.length));
      } else {
        super.nextBytes(bytes);
      }
    }
  }

  /**
   * Test clock fixture for deterministic instant advancement.
   */
  private static final class MutableClock extends java.time.Clock {
    private final AtomicReference<Instant> current;

    MutableClock(Instant initial) {
      this.current = new AtomicReference<>(initial);
    }

    void advance(Duration duration) {
      current.updateAndGet(i -> i.plus(duration));
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public java.time.Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return current.get();
    }
  }
}
