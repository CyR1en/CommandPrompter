package dev.cyr1en.promptpaper.approval;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerInteractionLeaseRegistryTest {

  private MutableClock clock;
  private PlayerInteractionLeaseRegistry registry;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-08-18T12:00:00Z"));
    registry = new PlayerInteractionLeaseRegistry(clock);
  }

  @Test
  @DisplayName("acquire establishes an active lease for player and executionId")
  void testAcquireSuccess() {
    UUID player = UUID.randomUUID();
    ExecutionId execId = ExecutionId.create();

    Optional<PlayerInteractionLease> leaseOpt =
        registry.acquire(player, execId, Duration.ofSeconds(30));

    assertTrue(leaseOpt.isPresent());
    PlayerInteractionLease lease = leaseOpt.get();
    assertEquals(player, lease.player());
    assertEquals(execId, lease.executionId());
    assertEquals(clock.instant(), lease.acquiredAt());
    assertEquals(clock.instant().plusSeconds(30), lease.expiresAt());

    assertTrue(registry.isLeased(player));
    assertEquals(Optional.of(lease), registry.getLease(player));
  }

  @Test
  @DisplayName("acquire rejects collision when another execution holds active lease")
  void testCollisionRejection() {
    UUID player = UUID.randomUUID();
    ExecutionId exec1 = ExecutionId.create();
    ExecutionId exec2 = ExecutionId.create();

    Optional<PlayerInteractionLease> lease1 =
        registry.acquire(player, exec1, Duration.ofSeconds(30));
    assertTrue(lease1.isPresent());

    // Second execution attempts to lease the same player
    Optional<PlayerInteractionLease> lease2 =
        registry.acquire(player, exec2, Duration.ofSeconds(30));
    assertTrue(lease2.isEmpty(), "Collision must be rejected");

    assertEquals(Optional.of(exec1), registry.getLease(player).map(PlayerInteractionLease::executionId));
  }

  @Test
  @DisplayName("acquire allows same execution to re-acquire and extend lease")
  void testSameExecutionReacquire() {
    UUID player = UUID.randomUUID();
    ExecutionId execId = ExecutionId.create();

    registry.acquire(player, execId, Duration.ofSeconds(30));
    clock.advance(Duration.ofSeconds(10));

    Optional<PlayerInteractionLease> extended =
        registry.acquire(player, execId, Duration.ofSeconds(30));
    assertTrue(extended.isPresent());
    assertEquals(clock.instant().plusSeconds(30), extended.get().expiresAt());
  }

  @Test
  @DisplayName("acquire replaces expired lease cleanly")
  void testExpiredLeaseReplaced() {
    UUID player = UUID.randomUUID();
    ExecutionId exec1 = ExecutionId.create();
    ExecutionId exec2 = ExecutionId.create();

    registry.acquire(player, exec1, Duration.ofSeconds(30));
    clock.advance(Duration.ofSeconds(31));

    // Exec2 attempts acquisition after exec1 lease expired
    Optional<PlayerInteractionLease> lease2 =
        registry.acquire(player, exec2, Duration.ofSeconds(30));
    assertTrue(lease2.isPresent());
    assertEquals(exec2, lease2.get().executionId());
  }

  @Test
  @DisplayName("releaseIfExact frees lease only when executionId matches exactly")
  void testExactRelease() {
    UUID player = UUID.randomUUID();
    ExecutionId exec1 = ExecutionId.create();
    ExecutionId wrongExec = ExecutionId.create();

    registry.acquire(player, exec1, Duration.ofSeconds(30));

    // Release with wrong execution ID fails
    assertFalse(registry.releaseIfExact(player, wrongExec));
    assertTrue(registry.isLeased(player));

    // Release with matching execution ID succeeds
    assertTrue(registry.releaseIfExact(player, exec1));
    assertFalse(registry.isLeased(player));
  }

  @Test
  @DisplayName("releaseAllForExecution removes all leases held by the execution")
  void testReleaseAllForExecution() {
    ExecutionId execId = ExecutionId.create();
    UUID player1 = UUID.randomUUID();
    UUID player2 = UUID.randomUUID();
    UUID player3 = UUID.randomUUID();

    registry.acquire(player1, execId, Duration.ofSeconds(30));
    registry.acquire(player2, execId, Duration.ofSeconds(30));
    registry.acquire(player3, ExecutionId.create(), Duration.ofSeconds(30));

    assertEquals(3, registry.size());

    int released = registry.releaseAllForExecution(execId);
    assertEquals(2, released);
    assertEquals(1, registry.size());
    assertFalse(registry.isLeased(player1));
    assertFalse(registry.isLeased(player2));
    assertTrue(registry.isLeased(player3));
  }

  @Test
  @DisplayName("cleanExpired prunes only expired leases")
  void testCleanExpired() {
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();

    registry.acquire(p1, ExecutionId.create(), Duration.ofSeconds(10));
    registry.acquire(p2, ExecutionId.create(), Duration.ofSeconds(60));

    clock.advance(Duration.ofSeconds(20));

    int cleaned = registry.cleanExpired();
    assertEquals(1, cleaned);
    assertFalse(registry.isLeased(p1));
    assertTrue(registry.isLeased(p2));
  }

  @Test
  @DisplayName("Concurrent acquire on same player yields exactly one winner")
  void testConcurrentAcquisition() throws Exception {
    UUID player = UUID.randomUUID();
    int threadCount = 16;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<Future<Optional<PlayerInteractionLease>>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      ExecutionId execId = ExecutionId.create();
      futures.add(
          executor.submit(
              () -> {
                startLatch.await();
                return registry.acquire(player, execId, Duration.ofSeconds(30));
              }));
    }

    startLatch.countDown();

    int successCount = 0;
    for (Future<Optional<PlayerInteractionLease>> future : futures) {
      if (future.get().isPresent()) {
        successCount++;
      }
    }
    executor.shutdown();

    assertEquals(1, successCount, "Exactly one execution must acquire the lease");
    assertEquals(1, registry.size());
  }

  @Test
  @DisplayName("acquirePrompt establishes active prompt claim and releases on exact incarnation")
  void testPromptClaimLifecycle() {
    UUID player = UUID.randomUUID();
    long inc1 = 101L;

    Optional<PlayerInteractionLease> claimOpt = registry.acquirePrompt(player, inc1);
    assertTrue(claimOpt.isPresent());
    PlayerInteractionLease claim = claimOpt.get();
    assertTrue(claim.isPrompt());
    assertFalse(claim.isApproval());
    assertEquals(player, claim.player());
    assertEquals(inc1, claim.promptIncarnation());
    assertTrue(registry.isLeased(player));

    // Collision with approval lease while prompt claim active
    ExecutionId execId = ExecutionId.create();
    Optional<PlayerInteractionLease> approvalOpt =
        registry.acquire(player, execId, Duration.ofSeconds(30));
    assertTrue(approvalOpt.isEmpty(), "Approval acquire must fail when prompt claim active");

    // Collision with different prompt incarnation
    Optional<PlayerInteractionLease> diffPromptOpt = registry.acquirePrompt(player, 102L);
    assertTrue(diffPromptOpt.isEmpty(), "Different prompt incarnation must be rejected");

    // Same incarnation re-acquire succeeds
    Optional<PlayerInteractionLease> samePromptOpt = registry.acquirePrompt(player, inc1);
    assertTrue(samePromptOpt.isPresent());

    // Wrong incarnation release fails
    assertFalse(registry.releasePromptIfExact(player, 999L));
    assertTrue(registry.isLeased(player));

    // Exact incarnation release succeeds
    assertTrue(registry.releasePromptIfExact(player, inc1));
    assertFalse(registry.isLeased(player));

    // Now approval lease can be acquired
    Optional<PlayerInteractionLease> approvalAfterRelease =
        registry.acquire(player, execId, Duration.ofSeconds(30));
    assertTrue(approvalAfterRelease.isPresent());
  }

  @Test
  @DisplayName("RACE: Concurrent prompt inception vs approval acquisition yields exactly one winner, never both")
  void testRacePromptInceptionVsApprovalAcquisition() throws Exception {
    UUID player = UUID.randomUUID();
    int iterations = 100;

    for (int iter = 0; iter < iterations; iter++) {
      PlayerInteractionLeaseRegistry reg = new PlayerInteractionLeaseRegistry(clock);
      long promptIncarnation = 1000L + iter;
      ExecutionId execId = ExecutionId.create();

      ExecutorService exec = Executors.newFixedThreadPool(2);
      CountDownLatch latch = new CountDownLatch(1);

      Future<Optional<PlayerInteractionLease>> promptFuture =
          exec.submit(
              () -> {
                latch.await();
                return reg.acquirePrompt(player, promptIncarnation);
              });

      Future<Optional<PlayerInteractionLease>> approvalFuture =
          exec.submit(
              () -> {
                latch.await();
                return reg.acquire(player, execId, Duration.ofSeconds(30));
              });

      latch.countDown();

      Optional<PlayerInteractionLease> promptRes = promptFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
      Optional<PlayerInteractionLease> approvalRes = approvalFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);

      exec.shutdown();

      boolean promptWon = promptRes.isPresent();
      boolean approvalWon = approvalRes.isPresent();

      assertTrue(
          promptWon ^ approvalWon,
          "Exactly one must win: promptWon=" + promptWon + ", approvalWon=" + approvalWon);
      assertEquals(1, reg.size());
    }
  }

  private static final class MutableClock extends Clock {
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
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return current.get();
    }
  }
}
