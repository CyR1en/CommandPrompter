package dev.cyr1en.promptpaper.approval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptcore.plan.PreDispatchGateSpec;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.command.ResponseCommand;
import dev.cyr1en.promptpaper.custom.PlayerExecutor;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator;
import dev.cyr1en.promptpaper.execution.dispatch.PaperImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PaperPrimaryCommandDispatcher;
import dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionPlanInstance;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionStage;
import dev.cyr1en.promptpaper.execution.runtime.InputCompletion;
import dev.cyr1en.promptpaper.preset.ApprovalGateDefinition;
import dev.cyr1en.promptpaper.preset.ExecuteAs;
import dev.cyr1en.promptpaper.preset.PresetSnapshot;
import dev.cyr1en.promptpaper.preset.SelfApprovalPolicy;
import dev.cyr1en.promptpaper.preset.TrustedPresetAction;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationRateLimiter;
import dev.cyr1en.promptpaper.util.PluginLogger;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Approval Coordinator Audit & Log-Hardening Tests")
class ApprovalCoordinatorAuditTest extends MockBukkitTest {

  private ApprovalCoordinator coordinator;
  private ApprovalCapabilityRegistry capabilityRegistry;
  private PlayerInteractionLeaseRegistry leaseRegistry;
  private ExecutionCoordinator executionCoordinator;
  private PluginLogger mockLogger;
  private List<String> capturedInfoLogs;
  private List<String> capturedWarnLogs;
  private AtomicReference<Runnable> timeoutRunnableRef;

  @BeforeEach
  void setUpAuditTest() {
    capturedInfoLogs = new java.util.concurrent.CopyOnWriteArrayList<>();
    capturedWarnLogs = new java.util.concurrent.CopyOnWriteArrayList<>();
    timeoutRunnableRef = new AtomicReference<>();

    mockLogger = mock(PluginLogger.class);
    doAnswer(
            invocation -> {
              capturedInfoLogs.add(invocation.getArgument(0));
              return null;
            })
        .when(mockLogger)
        .info(anyString(), any(Object[].class));
    doAnswer(
            invocation -> {
              capturedWarnLogs.add(invocation.getArgument(0));
              return null;
            })
        .when(mockLogger)
        .warn(anyString(), any(Object[].class));

    when(plugin.getPluginLogger()).thenReturn(mockLogger);

    capabilityRegistry = new ApprovalCapabilityRegistry();
    leaseRegistry = new PlayerInteractionLeaseRegistry();
    var executionRegistry = new ExecutionRegistry();
    when(plugin.getExecutionRegistry()).thenReturn(executionRegistry);

    var promptEngine = new PromptEngine(plugin, scheduler, null, executionRegistry, leaseRegistry);
    when(plugin.getEngine()).thenReturn(promptEngine);

    var screenManager =
        new ScreenManager(plugin, promptEngine, plugin.getPromptFactory(), scheduler, null, null, null, null);
    when(plugin.getScreenManager()).thenReturn(screenManager);

    coordinator =
        new ApprovalCoordinator(
            plugin,
            scheduler,
            executionRegistry,
            screenManager,
            promptEngine,
            capabilityRegistry,
            leaseRegistry,
            new ChatApprovalPresenter(),
            p -> (task, retired) -> task.run(),
            (player, delayTicks, onTimeout, onRetired) -> {
              timeoutRunnableRef.set(onTimeout);
              return () -> {};
            },
            ApprovalCoordinator::defaultResolveTarget);

    when(plugin.getApprovalCoordinator()).thenReturn(coordinator);

    var primaryDispatcher = new PaperPrimaryCommandDispatcher(plugin, scheduler);
    var actionDispatcher = new PaperImmediateActionDispatcher(plugin, scheduler);

    executionCoordinator =
        new ExecutionCoordinator(
            plugin,
            promptEngine,
            executionRegistry,
            primaryDispatcher,
            actionDispatcher,
            coordinator,
            p -> (task, retired) -> task.run());
    when(plugin.getExecutionCoordinator()).thenReturn(executionCoordinator);
  }

  private ApprovalGateDefinition createGate(String id, String target) {
    return new ApprovalGateDefinition(
        id,
        TemplateCompiler.compile(target),
        TemplateCompiler.compile("Approve action?"),
        30,
        SelfApprovalPolicy.REQUIRE_CONFIRM,
        null);
  }

  private ExecutionPlanInstance setupExecution(Player initiator, Player target, String gateId, ApprovalGateDefinition gate) {
    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of(gateId, gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give {player} diamond 64"),
            List.of(new PreDispatchGateSpec.Approval(gateId)),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 64",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    Optional<ExecutionPlanInstance> instanceOpt = executionCoordinator.coordinate(initiator, completion);
    assertTrue(instanceOpt.isPresent());
    assertEquals(ExecutionStage.PRE_DISPATCH_GATES, instanceOpt.get().getStage());
    return instanceOpt.get();
  }

  @Test
  @DisplayName("Audit: Normal approved outcome logs exactly once at INFO with initiator, target, sanitized gate, and outcome")
  void testTerminalOutcomeAudit_Approved() {
    Player initiator = createPlayer("Initiator");
    Player target = createPlayer("Approver");
    String gateId = "test_gate";
    ApprovalGateDefinition gateDef = createGate(gateId, target.getName());

    setupExecution(initiator, target, gateId, gateDef);

    ApprovalCapability cap = capabilityRegistry.getByTarget(target.getUniqueId()).orElseThrow();
    coordinator.handleResponse(target, cap.nonce(), "confirm");

    List<String> infoLogs =
        capturedInfoLogs.stream().filter(s -> s.contains("Approval gate")).toList();
    assertEquals(1, infoLogs.size(), "Must log terminal decision exactly once at INFO: " + capturedInfoLogs);
    String log = infoLogs.get(0);
    assertTrue(log.contains("test_gate"), "Must contain sanitized gate ID: " + log);
    assertTrue(log.contains("APPROVED"), "Must contain outcome APPROVED: " + log);
    assertTrue(log.contains(initiator.getUniqueId().toString()), "Must contain initiator UUID: " + log);
    assertTrue(log.contains(target.getUniqueId().toString()), "Must contain target UUID: " + log);
  }

  @Test
  @DisplayName("Audit: Denied outcome logs exactly once at INFO")
  void testTerminalOutcomeAudit_Denied() {
    Player initiator = createPlayer("Initiator2");
    Player target = createPlayer("Approver2");
    String gateId = "gate_deny";
    ApprovalGateDefinition gateDef = createGate(gateId, target.getName());

    setupExecution(initiator, target, gateId, gateDef);

    ApprovalCapability cap = capabilityRegistry.getByTarget(target.getUniqueId()).orElseThrow();
    coordinator.handleResponse(target, cap.nonce(), "decline");

    List<String> infoLogs =
        capturedInfoLogs.stream().filter(s -> s.contains("Approval gate")).toList();
    assertEquals(1, infoLogs.size(), "Must log terminal decision exactly once at INFO");
    String log = infoLogs.get(0);
    assertTrue(log.contains("DENIED"), "Must contain outcome DENIED: " + log);
    assertTrue(log.contains(initiator.getUniqueId().toString()), "Must contain initiator UUID: " + log);
    assertTrue(log.contains(target.getUniqueId().toString()), "Must contain target UUID: " + log);
  }

  @Test
  @DisplayName("Audit: Timeout outcome logs exactly once at INFO")
  void testTerminalOutcomeAudit_Timeout() {
    Player initiator = createPlayer("Initiator3");
    Player target = createPlayer("Approver3");
    String gateId = "gate_timeout";
    ApprovalGateDefinition gateDef = createGate(gateId, target.getName());

    setupExecution(initiator, target, gateId, gateDef);

    assertNotNull(timeoutRunnableRef.get());
    timeoutRunnableRef.get().run();

    List<String> infoLogs =
        capturedInfoLogs.stream().filter(s -> s.contains("Approval gate")).toList();
    assertEquals(1, infoLogs.size(), "Must log timeout terminal decision exactly once at INFO");
    String log = infoLogs.get(0);
    assertTrue(log.contains("TIMED_OUT"), "Must contain outcome TIMED_OUT: " + log);
    assertTrue(log.contains(initiator.getUniqueId().toString()), "Must contain initiator UUID: " + log);
    assertTrue(log.contains(target.getUniqueId().toString()), "Must contain target UUID: " + log);
  }

  @Test
  @DisplayName("Audit: Target quit logs exactly once at INFO")
  void testTerminalOutcomeAudit_TargetQuit() {
    Player initiator = createPlayer("Initiator4");
    Player target = createPlayer("Approver4");
    String gateId = "gate_target_quit";
    ApprovalGateDefinition gateDef = createGate(gateId, target.getName());

    setupExecution(initiator, target, gateId, gateDef);

    coordinator.onTargetQuit(target.getUniqueId());

    List<String> infoLogs =
        capturedInfoLogs.stream().filter(s -> s.contains("Approval gate")).toList();
    assertEquals(1, infoLogs.size(), "Must log target quit exactly once at INFO");
    String log = infoLogs.get(0);
    assertTrue(log.contains("TARGET_DISCONNECTED"), "Must contain outcome TARGET_DISCONNECTED: " + log);
    assertTrue(log.contains(initiator.getUniqueId().toString()), "Must contain initiator UUID: " + log);
    assertTrue(log.contains(target.getUniqueId().toString()), "Must contain target UUID: " + log);
  }

  @Test
  @DisplayName("Audit: Initiator quit logs exactly once at INFO")
  void testTerminalOutcomeAudit_InitiatorQuit() {
    Player initiator = createPlayer("Initiator5");
    Player target = createPlayer("Approver5");
    String gateId = "gate_initiator_quit";
    ApprovalGateDefinition gateDef = createGate(gateId, target.getName());

    setupExecution(initiator, target, gateId, gateDef);

    coordinator.onInitiatorQuit(initiator.getUniqueId());

    List<String> infoLogs =
        capturedInfoLogs.stream().filter(s -> s.contains("Approval gate")).toList();
    assertEquals(1, infoLogs.size(), "Must log initiator quit exactly once at INFO");
    String log = infoLogs.get(0);
    assertTrue(log.contains("INITIATOR_DISCONNECTED"), "Must contain outcome INITIATOR_DISCONNECTED: " + log);
    assertTrue(log.contains(initiator.getUniqueId().toString()), "Must contain initiator UUID: " + log);
    assertTrue(log.contains(target.getUniqueId().toString()), "Must contain target UUID: " + log);
  }

  @Test
  @DisplayName("Audit: Concurrent decision events emit terminal log exactly once via CAS")
  void testTerminalOutcomeAudit_ExactlyOnceConcurrent() throws Exception {
    Player initiator = createPlayer("Initiator6");
    Player target = createPlayer("Approver6");
    String gateId = "gate_concurrent";
    ApprovalGateDefinition gateDef = createGate(gateId, target.getName());

    setupExecution(initiator, target, gateId, gateDef);

    ApprovalCapability cap = capabilityRegistry.getByTarget(target.getUniqueId()).orElseThrow();

    int threadCount = 10;
    var executor = Executors.newFixedThreadPool(threadCount);
    var latch = new CountDownLatch(1);
    var doneLatch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      final int idx = i;
      executor.submit(
          () -> {
            try {
              latch.await();
              if (idx % 3 == 0) {
                coordinator.handleResponse(target, cap.nonce(), "confirm");
              } else if (idx % 3 == 1) {
                coordinator.onTargetQuit(target.getUniqueId());
              } else {
                coordinator.onInitiatorQuit(initiator.getUniqueId());
              }
            } catch (Exception ignored) {
            } finally {
              doneLatch.countDown();
            }
          });
    }

    latch.countDown();
    assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
    executor.shutdown();

    List<String> infoLogs =
        capturedInfoLogs.stream().filter(s -> s.contains("Approval gate")).toList();
    assertEquals(1, infoLogs.size(), "Terminal decision log must be emitted exactly once: " + infoLogs);
  }

  @Test
  @DisplayName("Rejection Audit: Invalid decision logs safe reason, attempt count, truncated nonce and NO raw payload")
  void testRejectionAudit_InvalidDecision_NoRawPayload() {
    Player responder = createPlayer("BadResponder");
    String rawNonce = "a_superSecretNonce123456789";
    String maliciousPayload = "confirm\u0000\u001b<script>alert(1)</script>";

    coordinator.handleResponse(responder, rawNonce, maliciousPayload);

    List<String> warnLogs = capturedWarnLogs.stream().filter(s -> s.contains("Approval response rejected")).toList();
    assertEquals(1, warnLogs.size(), "Must log rejection warning: " + capturedWarnLogs);
    String log = warnLogs.get(0);
    assertTrue(log.contains("invalid decision"), "Must contain safe reason: " + log);
    assertTrue(log.contains(ApprovalCapabilityRegistry.truncateNonce(rawNonce)), "Must contain truncated nonce: " + log);
    assertFalse(log.contains(rawNonce), "Must not contain raw nonce: " + log);
    assertFalse(log.contains("script"), "Must not contain raw decision string: " + log);
    assertFalse(log.contains("\u0000"), "Must not contain C0 controls: " + log);
    assertTrue(log.contains("attempts="), "Must contain attempt count: " + log);
  }

  @Test
  @DisplayName("Rejection Audit: Rate bounding suppresses repeated rejection logs within the window")
  void testRejectionAudit_RateBounding() {
    Player responder = createPlayer("SpamResponder");
    ConfirmationRateLimiter rateLimiter =
        new ConfirmationRateLimiter(Clock.systemUTC(), 5, Duration.ofSeconds(10));
    when(plugin.getRateLimiter()).thenReturn(rateLimiter);

    String rawNonce = "a_unknownNonce999";

    // 1st rejection logs
    coordinator.handleResponse(responder, rawNonce, "invalid_dec");
    assertEquals(1, capturedWarnLogs.size());

    // 2nd rejection from same player in same window is suppressed by rate limiter shouldLog
    coordinator.handleResponse(responder, rawNonce, "invalid_dec2");
    assertEquals(1, capturedWarnLogs.size(), "Subsequent rejection in same window must be rate-bounded");
  }
}
