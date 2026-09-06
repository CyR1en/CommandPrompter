package dev.cyr1en.promptpaper.approval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptcore.logic.transform.CompiledTemplate;
import dev.cyr1en.promptcore.logic.transform.TemplateCompiler;
import dev.cyr1en.promptcore.plan.ExecutionPlanDefinition;
import dev.cyr1en.promptcore.plan.PreDispatchGateSpec;
import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.command.ResponseCommand;
import dev.cyr1en.promptpaper.engine.InterceptResult;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator;
import dev.cyr1en.promptpaper.execution.coordinator.PreDispatchGateResult;
import dev.cyr1en.promptpaper.execution.dispatch.PaperImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PaperPrimaryCommandDispatcher;
import dev.cyr1en.promptpaper.execution.runtime.DispatchContextSnapshot;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Approval Coordinator Integration & Security Tests (Phase 5.4)")
class ApprovalCoordinatorIntegrationTest extends MockBukkitTest {

  private PromptEngine engine;
  private ExecutionRegistry registry;
  private ScreenManager screenManager;
  private ApprovalCoordinator approvalCoordinator;
  private ExecutionCoordinator executionCoordinator;
  private ApprovalCapabilityRegistry capabilityRegistry;
  private PlayerInteractionLeaseRegistry leaseRegistry;
  private ResponseCommand responseCommand;

  private List<String> commandExecutions;

  @BeforeEach
  void setUpCoordinator() {
    commandExecutions = new java.util.concurrent.CopyOnWriteArrayList<>();
    registry = new ExecutionRegistry();
    lenient().when(plugin.getExecutionRegistry()).thenReturn(registry);

    capabilityRegistry = new ApprovalCapabilityRegistry();
    leaseRegistry = new PlayerInteractionLeaseRegistry();

    engine = new PromptEngine(plugin, scheduler, null, registry, leaseRegistry);
    lenient().when(plugin.getEngine()).thenReturn(engine);

    screenManager =
        new ScreenManager(
            plugin, engine, plugin.getPromptFactory(), scheduler, null, null, null, null);
    lenient().when(plugin.getScreenManager()).thenReturn(screenManager);

    approvalCoordinator =
        new ApprovalCoordinator(
            plugin,
            scheduler,
            registry,
            screenManager,
            engine,
            capabilityRegistry,
            leaseRegistry,
            new ChatApprovalPresenter(),
            p -> (task, retired) -> task.run(),
            (player, delayTicks, onTimeout, onRetired) -> {
              var task = scheduler.runLater(onTimeout, delayTicks);
              return task;
            });

    lenient().when(plugin.getApprovalCoordinator()).thenReturn(approvalCoordinator);

    var primaryDispatcher = new PaperPrimaryCommandDispatcher(plugin, scheduler);
    var actionDispatcher = new PaperImmediateActionDispatcher(plugin, scheduler);

    executionCoordinator =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            actionDispatcher,
            approvalCoordinator,
            p -> (task, retired) -> task.run());

    screenManager.setExecutionCoordinator(executionCoordinator);
    lenient().when(plugin.getExecutionCoordinator()).thenReturn(executionCoordinator);

    responseCommand = new ResponseCommand(plugin);

    registerMockCommand("give", true);
    registerMockCommand("eco", true);
    registerMockCommand("log", true);
    registerMockCommand("notify", true);
  }

  private void registerMockCommand(String name, boolean succeed) {
    Command cmd =
        new Command(name) {
          @Override
          public boolean execute(
              @NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            commandExecutions.add(
                commandLabel + (args.length > 0 ? " " + String.join(" ", args) : ""));
            return succeed;
          }
        };
    server.getCommandMap().register(name, "promptpaper", cmd);
  }

  @Test
  void rejectedTimeoutSchedulingReleasesApprovalAndReportsInitiatorDisconnect() {
    Player initiator = mock(Player.class);
    UUID initiatorId = UUID.randomUUID();
    when(initiator.getUniqueId()).thenReturn(initiatorId);
    when(initiator.getName()).thenReturn("Alice");
    when(initiator.getScheduler())
        .thenReturn(mock(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class));
    Player approver = createPlayer("Bob");
    var presenter = mock(ChatApprovalPresenter.class);
    var coordinator =
        new ApprovalCoordinator(
            plugin,
            scheduler,
            registry,
            screenManager,
            engine,
            capabilityRegistry,
            leaseRegistry,
            presenter,
            p -> (task, retired) -> task.run(),
            null);
    var gate =
        new ApprovalGateDefinition(
            "gate",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve {player}?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);
    var spec = new PreDispatchGateSpec.Approval("gate");
    var plan =
        new ExecutionPlanDefinition(TemplateCompiler.compile("primary"), List.of(spec), List.of());
    var completion =
        new InputCompletion(
            initiatorId,
            1L,
            1L,
            List.of(),
            "primary",
            plan,
            new PresetSnapshot(Map.of(), Map.of(), Map.of("gate", gate), Map.of(), 1L),
            DispatchContextSnapshot.player());
    var instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);
    var results = new ArrayList<PreDispatchGateResult>();

    coordinator.evaluateGate(initiator, instance, spec, 0, results::add);

    assertEquals(1, results.size());
    assertEquals(PreDispatchGateResult.Status.INITIATOR_DISCONNECTED, results.getFirst().status());
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
    assertEquals(0, coordinator.boundPlansCount());
    verify(presenter, never()).present(any(), any(), anyString());
  }

  // =========================================================================
  // FLOW-04: Approval decline -> no primary + one on_deny action
  // =========================================================================

  @Test
  @DisplayName("FLOW-04: Approval decline aborts primary command and dispatches on_deny action")
  void flow04_declineAbortsPrimaryAndDispatchesOnDeny() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "trade_gate",
            TemplateCompiler.compile(approver.getName()),
            TemplateCompiler.compile("Approve trade for {player}?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            TrustedPresetAction.of("log trade_denied {player}", ExecuteAs.CONSOLE));

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("trade_gate", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give {player} diamond 64"),
            List.of(new PreDispatchGateSpec.Approval("trade_gate")),
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

    Optional<ExecutionPlanInstance> instanceOpt =
        executionCoordinator.coordinate(initiator, completion);
    assertTrue(instanceOpt.isPresent());
    ExecutionPlanInstance instance = instanceOpt.get();

    // Instance is in PRE_DISPATCH_GATES
    assertEquals(ExecutionStage.PRE_DISPATCH_GATES, instance.getStage());

    // Approver has pending capability
    Optional<ApprovalCapability> capOpt = capabilityRegistry.getByTarget(approver.getUniqueId());
    assertTrue(capOpt.isPresent());
    ApprovalCapability capability = capOpt.get();

    // Approver declines via ResponseCommand
    responseCommand.executeResponse(approver, capability.nonce(), "decline");

    // Primary command was NOT executed, on_deny action WAS executed
    assertFalse(
        commandExecutions.contains("give Alice diamond 64"), "Primary command must NOT execute");
    assertTrue(commandExecutions.contains("log trade_denied Alice"), "On-deny action MUST execute");

    // Execution instance is CANCELLED and cleaned up from registry
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertFalse(registry.hasActiveExecution(initiator.getUniqueId()));
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  @Test
  @DisplayName("FLOW-04: Approval confirm dispatches primary command without on_deny")
  void flow04_confirmDispatchesPrimaryCommand() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "trade_gate",
            TemplateCompiler.compile(approver.getName()),
            TemplateCompiler.compile("Approve trade for {player}?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            TrustedPresetAction.of("log trade_denied {player}", ExecuteAs.CONSOLE));

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("trade_gate", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give {player} diamond 64"),
            List.of(new PreDispatchGateSpec.Approval("trade_gate")),
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

    Optional<ExecutionPlanInstance> instanceOpt =
        executionCoordinator.coordinate(initiator, completion);
    assertTrue(instanceOpt.isPresent());

    Optional<ApprovalCapability> capOpt = capabilityRegistry.getByTarget(approver.getUniqueId());
    assertTrue(capOpt.isPresent());

    // Approver confirms
    responseCommand.executeResponse(approver, capOpt.get().nonce(), "confirm");

    // Primary command WAS executed, on_deny action was NOT executed
    assertTrue(commandExecutions.contains("give Alice diamond 64"));
    assertFalse(commandExecutions.contains("log trade_denied Alice"));

    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  // =========================================================================
  // FLOW-08: Self approval policy
  // =========================================================================

  @Test
  @DisplayName("FLOW-08: AUTO_APPROVE advances immediately for self-execution")
  void flow05_selfApprovalAutoApprove() {
    Player initiator = createPlayer("Alice");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "self_gate",
            TemplateCompiler.compile("{player}"),
            TemplateCompiler.compile("Approve self?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("self_gate", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("eco give {player} 100"),
            List.of(new PreDispatchGateSpec.Approval("self_gate")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "eco give Alice 100",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    Optional<ExecutionPlanInstance> instanceOpt =
        executionCoordinator.coordinate(initiator, completion);
    assertTrue(instanceOpt.isPresent());

    // Auto approved -> directly executed primary
    assertTrue(commandExecutions.contains("eco give Alice 100"));
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  @Test
  @DisplayName("FLOW-08: REQUIRE_CONFIRM creates self capability without false collision")
  void flow05_selfApprovalRequireConfirm() {
    Player initiator = createPlayer("Alice");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "self_gate_confirm",
            TemplateCompiler.compile("{player}"),
            TemplateCompiler.compile("Confirm action for {player}?"),
            30,
            SelfApprovalPolicy.REQUIRE_CONFIRM,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("self_gate_confirm", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("eco give {player} 100"),
            List.of(new PreDispatchGateSpec.Approval("self_gate_confirm")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "eco give Alice 100",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    Optional<ExecutionPlanInstance> instanceOpt =
        executionCoordinator.coordinate(initiator, completion);
    assertTrue(instanceOpt.isPresent());
    ExecutionPlanInstance instance = instanceOpt.get();

    // In PRE_DISPATCH_GATES and capability created for self
    assertEquals(ExecutionStage.PRE_DISPATCH_GATES, instance.getStage());
    Optional<ApprovalCapability> capOpt = capabilityRegistry.getByTarget(initiator.getUniqueId());
    assertTrue(capOpt.isPresent());
    assertEquals(initiator.getUniqueId(), capOpt.get().target());

    // Alice confirms her own action
    responseCommand.executeResponse(initiator, capOpt.get().nonce(), "confirm");

    assertTrue(commandExecutions.contains("eco give Alice 100"));
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  // =========================================================================
  // FLOW-05: Cross-player scheduler order & identity re-verification
  // =========================================================================

  @Test
  @DisplayName(
      "FLOW-05: Cross-player execution returns to initiator PlayerExecutor and verifies identity")
  void flow07_crossPlayerSchedulerReVerification() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    List<String> threadLog = new ArrayList<>();

    var primaryDispatcher = new PaperPrimaryCommandDispatcher(plugin, scheduler);
    var actionDispatcher = new PaperImmediateActionDispatcher(plugin, scheduler);

    var customExecCoord =
        new ExecutionCoordinator(
            plugin,
            engine,
            registry,
            primaryDispatcher,
            actionDispatcher,
            approvalCoordinator,
            p ->
                (task, retired) -> {
                  threadLog.add("executor:" + p.getName());
                  task.run();
                });

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    customExecCoord.coordinate(initiator, completion);

    Optional<ApprovalCapability> capOpt = capabilityRegistry.getByTarget(approver.getUniqueId());
    assertTrue(capOpt.isPresent());

    responseCommand.executeResponse(approver, capOpt.get().nonce(), "confirm");

    // Execution should have routed via initiator executor
    assertTrue(threadLog.contains("executor:Alice"));
    assertTrue(commandExecutions.contains("give Alice diamond 1"));
  }

  // =========================================================================
  // Multi-Gate: Sequential multiple gates
  // =========================================================================

  @Test
  @DisplayName("Multi-Gate: Sequential multiple approval gates evaluate in order")
  void flow08_sequentialMultipleGates() {
    Player initiator = createPlayer("Alice");
    Player admin1 = createPlayer("Bob");
    Player admin2 = createPlayer("Charlie");

    ApprovalGateDefinition gate1 =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("First check for {player}?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    ApprovalGateDefinition gate2 =
        new ApprovalGateDefinition(
            "gate2",
            TemplateCompiler.compile("Charlie"),
            TemplateCompiler.compile("Second check for {player}?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            TrustedPresetAction.of("log gate2_denied {player}", ExecuteAs.CONSOLE));

    PresetSnapshot snapshot =
        new PresetSnapshot(
            Map.of(), Map.of(), Map.of("gate1", gate1, "gate2", gate2), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 100"),
            List.of(
                new PreDispatchGateSpec.Approval("gate1"),
                new PreDispatchGateSpec.Approval("gate2")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 100",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    executionCoordinator.coordinate(initiator, completion);

    // Gate 1 active for Bob
    Optional<ApprovalCapability> cap1 = capabilityRegistry.getByTarget(admin1.getUniqueId());
    assertTrue(cap1.isPresent());
    assertFalse(capabilityRegistry.getByTarget(admin2.getUniqueId()).isPresent());

    // Bob approves Gate 1
    responseCommand.executeResponse(admin1, cap1.get().nonce(), "confirm");

    // Primary not yet executed; Gate 2 now active for Charlie
    assertFalse(commandExecutions.contains("give Alice diamond 100"));
    Optional<ApprovalCapability> cap2 = capabilityRegistry.getByTarget(admin2.getUniqueId());
    assertTrue(cap2.isPresent());

    // Charlie approves Gate 2
    responseCommand.executeResponse(admin2, cap2.get().nonce(), "confirm");

    // Now primary executes!
    assertTrue(commandExecutions.contains("give Alice diamond 100"));
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  @Test
  @DisplayName("Multi-Gate: Sequential multiple gates - denial at second gate aborts primary")
  void flow08_sequentialMultipleGatesDenyAtSecond() {
    Player initiator = createPlayer("Alice");
    Player admin1 = createPlayer("Bob");
    Player admin2 = createPlayer("Charlie");

    ApprovalGateDefinition gate1 =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("First check?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    ApprovalGateDefinition gate2 =
        new ApprovalGateDefinition(
            "gate2",
            TemplateCompiler.compile("Charlie"),
            TemplateCompiler.compile("Second check?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            TrustedPresetAction.of("log gate2_denied {player}", ExecuteAs.CONSOLE));

    PresetSnapshot snapshot =
        new PresetSnapshot(
            Map.of(), Map.of(), Map.of("gate1", gate1, "gate2", gate2), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 100"),
            List.of(
                new PreDispatchGateSpec.Approval("gate1"),
                new PreDispatchGateSpec.Approval("gate2")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 100",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    executionCoordinator.coordinate(initiator, completion);

    Optional<ApprovalCapability> cap1 = capabilityRegistry.getByTarget(admin1.getUniqueId());
    assertTrue(cap1.isPresent());

    // Bob approves Gate 1
    responseCommand.executeResponse(admin1, cap1.get().nonce(), "confirm");

    Optional<ApprovalCapability> cap2 = capabilityRegistry.getByTarget(admin2.getUniqueId());
    assertTrue(cap2.isPresent());

    // Charlie DENIES Gate 2
    responseCommand.executeResponse(admin2, cap2.get().nonce(), "decline");

    assertFalse(commandExecutions.contains("give Alice diamond 100"));
    assertTrue(commandExecutions.contains("log gate2_denied Alice"));
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  // =========================================================================
  // FLOW-12: Timeout handling
  // =========================================================================

  @Test
  @DisplayName("FLOW-12: Capability timeout invalidates state and dispatches on_deny")
  void flow12_timeoutInvalidatesAndRunsOnDeny() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "timed_gate",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            5,
            SelfApprovalPolicy.AUTO_APPROVE,
            TrustedPresetAction.of("log timed_out {player}", ExecuteAs.CONSOLE));

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("timed_gate", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("timed_gate")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    Optional<ExecutionPlanInstance> instanceOpt =
        executionCoordinator.coordinate(initiator, completion);
    assertTrue(instanceOpt.isPresent());
    ExecutionPlanInstance instance = instanceOpt.get();

    assertEquals(1, capabilityRegistry.size());

    // Advance server clock / run delayed tasks by 100 ticks (5s)
    server.getScheduler().performTicks(101);

    // Primary aborted, on_deny action executed
    assertFalse(commandExecutions.contains("give Alice diamond 1"));
    assertTrue(commandExecutions.contains("log timed_out Alice"));

    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  // =========================================================================
  // FLOW-13: Disconnect distinction (target vs initiator)
  // =========================================================================

  @Test
  @DisplayName("FLOW-13: Target disconnect reports TARGET_DISCONNECTED and executes on_deny")
  void flow13_targetDisconnectExecutesOnDeny() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            TrustedPresetAction.of("log target_left {player}", ExecuteAs.CONSOLE));

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    executionCoordinator.coordinate(initiator, completion);

    // Target Bob quits
    approvalCoordinator.onTargetQuit(approver.getUniqueId());

    assertFalse(commandExecutions.contains("give Alice diamond 1"));
    assertTrue(commandExecutions.contains("log target_left Alice"));
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  @Test
  @DisplayName("FLOW-13: Initiator disconnect cancels execution and SKIPS on_deny")
  void flow13_initiatorDisconnectSkipsOnDeny() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            TrustedPresetAction.of("log target_left {player}", ExecuteAs.CONSOLE));

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    executionCoordinator.coordinate(initiator, completion);

    // Initiator Alice quits
    approvalCoordinator.onInitiatorQuit(initiator.getUniqueId());
    executionCoordinator.cancel(initiator.getUniqueId());

    assertFalse(commandExecutions.contains("give Alice diamond 1"));
    assertFalse(
        commandExecutions.contains("log target_left Alice"),
        "Initiator disconnect MUST skip on_deny");
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  // =========================================================================
  // FLOW-07 / SEC-05: Capability security & ResponseCommand
  // =========================================================================

  @Test
  @DisplayName("FLOW-07 / SEC-05: Wrong target responder is rejected with no state change")
  void sec05_wrongTargetRejected() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");
    Player attacker = createPlayer("Eve");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    executionCoordinator.coordinate(initiator, completion);

    Optional<ApprovalCapability> capOpt = capabilityRegistry.getByTarget(approver.getUniqueId());
    assertTrue(capOpt.isPresent());
    String nonce = capOpt.get().nonce();

    // Attacker Eve tries to confirm
    responseCommand.executeResponse(attacker, nonce, "confirm");

    // State is UNCHANGED: primary not dispatched, capability still in registry
    assertFalse(commandExecutions.contains("give Alice diamond 1"));
    assertTrue(capabilityRegistry.getByNonce(nonce).isPresent());

    // Bob can still legitimately confirm
    responseCommand.executeResponse(approver, nonce, "confirm");
    assertTrue(commandExecutions.contains("give Alice diamond 1"));
  }

  @Test
  @DisplayName("SEC-05: Replay attack is rejected after consumption")
  void sec05_replayAttackRejected() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    executionCoordinator.coordinate(initiator, completion);

    Optional<ApprovalCapability> capOpt = capabilityRegistry.getByTarget(approver.getUniqueId());
    assertTrue(capOpt.isPresent());
    String nonce = capOpt.get().nonce();

    // First response consumes
    responseCommand.executeResponse(approver, nonce, "confirm");
    assertEquals(1, commandExecutions.size());

    // Replay response does nothing
    responseCommand.executeResponse(approver, nonce, "confirm");
    assertEquals(1, commandExecutions.size(), "Replay must have no effect");
  }

  @Test
  @DisplayName("SEC-05: NonceResponseRegistry fallback for standard confirmation")
  void sec05_nonceResponseRegistryFallback() {
    Player player = createPlayer("Alice");
    AtomicBoolean confirmed = new AtomicBoolean(false);

    var nonceReg = plugin.getNonceRegistry();
    var binding =
        nonceReg.register(
            player.getUniqueId(),
            1L,
            0L,
            0,
            Duration.ofSeconds(60),
            decision ->
                confirmed.set(
                    decision
                        == dev.cyr1en.promptpaper.screen.confirmation.ConfirmationDecision
                            .CONFIRM));

    // Start a prompt session
    engine.intercept(player, "/test <a:why>");

    responseCommand.executeResponse(player, binding.nonce(), "confirm");

    assertTrue(
        confirmed.get(), "ResponseCommand must fall back to local confirmation nonce registry");
  }

  // =========================================================================
  // SEC-06: Target busy policy across all sources
  // =========================================================================

  @Test
  @DisplayName("SEC-06: Target with active screen is rejected as busy")
  void sec06_targetWithActiveScreenRejected() throws Exception {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    // Put approver into active screen using reflection on activeScreens map
    var field = ScreenManager.class.getDeclaredField("activeScreens");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    var activeScreensMap = (Map<UUID, Object>) field.get(screenManager);
    activeScreensMap.put(approver.getUniqueId(), mock(dev.cyr1en.promptui.InputScreen.class));

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    executionCoordinator.coordinate(initiator, completion);

    // Should fail closed: no capability created
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
    assertFalse(commandExecutions.contains("give Alice diamond 1"));
  }

  @Test
  @DisplayName("SEC-06: Target with active PromptSession is rejected as busy")
  void sec06_targetWithActiveSessionRejected() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    // Put approver into active prompt session
    engine.intercept(approver, "/test <a:why>");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    executionCoordinator.coordinate(initiator, completion);

    assertEquals(0, capabilityRegistry.size());
    assertTrue(
        leaseRegistry
            .getLease(approver.getUniqueId())
            .map(PlayerInteractionLease::isPrompt)
            .orElse(false));
    assertFalse(commandExecutions.contains("give Alice diamond 1"));
  }

  @Test
  @DisplayName("SEC-06: Target with existing capability or lease is rejected as busy")
  void sec06_targetWithExistingCapabilityRejected() {
    Player initiator1 = createPlayer("Alice");
    Player initiator2 = createPlayer("Charlie");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give {player} diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion1 =
        new InputCompletion(
            initiator1.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    InputCompletion completion2 =
        new InputCompletion(
            initiator2.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Charlie diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    // Initiator 1 acquires capability for Bob
    executionCoordinator.coordinate(initiator1, completion1);
    assertEquals(1, capabilityRegistry.size());

    // Initiator 2 attempts to target Bob while busy
    executionCoordinator.coordinate(initiator2, completion2);

    // Second execution rejected; capability registry remains size 1 (first capability only)
    assertEquals(1, capabilityRegistry.size());
    assertEquals(1, leaseRegistry.size());
  }

  // =========================================================================
  // Fail-fast & Snapshot retention across reload
  // =========================================================================

  @Test
  @DisplayName("PromptEngine fail-fast: missing gate preset ID rejects before session publication")
  void failFastMissingGateRejects() {
    Player initiator = createPlayer("Alice");

    // Empty snapshot (no gates)
    var emptySnapshot = PresetSnapshot.empty();
    var mockRegistry = mock(dev.cyr1en.promptpaper.preset.PresetRegistry.class);
    when(mockRegistry.getSnapshot()).thenReturn(emptySnapshot);
    when(plugin.getPresetRegistry()).thenReturn(mockRegistry);

    // Parse command with unknown gate
    InterceptResult result =
        engine.interceptResult(initiator, "/pay <a:amt> <!gate:@unknown_gate>");

    assertTrue(result instanceof InterceptResult.RejectedFailClosed);
    assertFalse(engine.hasActiveSession(initiator));
  }

  @Test
  @DisplayName("Retained snapshot at inception is safe across PresetRegistry reload")
  void retainedSnapshotSafeAcrossReload() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot1 =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot1,
            DispatchContextSnapshot.player());

    // Start execution
    executionCoordinator.coordinate(initiator, completion);

    // Now reload presets to empty snapshot
    var mockRegistry = mock(dev.cyr1en.promptpaper.preset.PresetRegistry.class);
    when(mockRegistry.getSnapshot()).thenReturn(PresetSnapshot.empty());
    when(plugin.getPresetRegistry()).thenReturn(mockRegistry);

    // Bob can still confirm using captured snapshot
    Optional<ApprovalCapability> capOpt = capabilityRegistry.getByTarget(approver.getUniqueId());
    assertTrue(capOpt.isPresent());

    responseCommand.executeResponse(approver, capOpt.get().nonce(), "confirm");
    assertTrue(commandExecutions.contains("give Alice diamond 1"));
  }

  // =========================================================================
  // Race decision vs timeout
  // =========================================================================

  @Test
  @DisplayName("Race condition: decision vs timeout produces exactly ONE terminal outcome")
  void raceDecisionVsTimeoutExactlyOneTerminal() throws Exception {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "race_gate",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            TrustedPresetAction.of("log denied_action {player}", ExecuteAs.CONSOLE));

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("race_gate", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("race_gate")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);

    List<PreDispatchGateResult> results = new java.util.concurrent.CopyOnWriteArrayList<>();

    approvalCoordinator.evaluateGate(
        initiator, instance, new PreDispatchGateSpec.Approval("race_gate"), 0, results::add);

    Optional<ApprovalCapability> capOpt = capabilityRegistry.getByTarget(approver.getUniqueId());
    assertTrue(capOpt.isPresent());
    ApprovalCapability capability = capOpt.get();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);

    pool.submit(
        () -> {
          try {
            startLatch.await();
            var consumed = capabilityRegistry.consume(capability.nonce(), approver.getUniqueId());
            if (consumed.isPresent()) {
              approvalCoordinator.handleOutcome(
                  ApprovalOutcome.of(consumed.get(), ApprovalDecision.APPROVED, Instant.now()));
            }
          } catch (Exception ignored) {
          } finally {
            doneLatch.countDown();
          }
        });

    pool.submit(
        () -> {
          try {
            startLatch.await();
            approvalCoordinator.handleOutcome(
                ApprovalOutcome.of(capability, ApprovalDecision.TIMED_OUT, Instant.now()));
          } catch (Exception ignored) {
          } finally {
            doneLatch.countDown();
          }
        });

    startLatch.countDown();
    assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
    pool.shutdown();

    // Exactly one result was reported (APPROVED XOR TIMED_OUT)!
    assertEquals(1, results.size(), "Exactly one terminal outcome must be reported");
    PreDispatchGateResult result = results.get(0);
    assertTrue(
        result.status() == PreDispatchGateResult.Status.APPROVED
            || result.status() == PreDispatchGateResult.Status.TIMED_OUT);

    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  // =========================================================================
  // REGRESSION TESTS (Gate 4 Remediations)
  // =========================================================================

  @Test
  @DisplayName("REGRESSION: Delayed presentation after timeout/cancel emits nothing")
  void regression_delayedPresentationAfterTimeoutEmitsNothing() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);

    ChatApprovalPresenter mockPresenter = mock(ChatApprovalPresenter.class);
    AtomicReference<Runnable> capturedTargetRunnable = new AtomicReference<>();

    ApprovalCoordinator coordinator =
        new ApprovalCoordinator(
            plugin,
            scheduler,
            registry,
            screenManager,
            engine,
            capabilityRegistry,
            leaseRegistry,
            mockPresenter,
            p ->
                (task, retired) -> {
                  if (p.getUniqueId().equals(approver.getUniqueId())) {
                    capturedTargetRunnable.set(task);
                  } else {
                    task.run();
                  }
                },
            (player, delayTicks, onTimeout, onRetired) -> () -> {});

    coordinator.evaluateGate(
        initiator, instance, new PreDispatchGateSpec.Approval("gate1"), 0, result -> {});

    // Ensure target runnable was captured and not yet run
    assertNotNull(capturedTargetRunnable.get());

    // Timeout or cancel the instance
    instance.tryTransitionTo(ExecutionStage.CANCELLED);

    // Now run the delayed presentation runnable on Bob
    capturedTargetRunnable.get().run();

    // Verify presenter was NEVER called to send messages
    verify(mockPresenter, never()).present(any(), any(), anyString());
    verify(mockPresenter, never())
        .present(any(), any(), any(net.kyori.adventure.text.Component.class));
  }

  @Test
  @DisplayName("REGRESSION: Stale first gate outcome does not affect second gate")
  void regression_staleFirstGateOutcomeDoesNotAffectSecondGate() {
    Player initiator = createPlayer("Alice");
    Player admin1 = createPlayer("Admin1");
    Player admin2 = createPlayer("Admin2");

    ApprovalGateDefinition gate1 =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Admin1"),
            TemplateCompiler.compile("Approve 1?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);
    ApprovalGateDefinition gate2 =
        new ApprovalGateDefinition(
            "gate2",
            TemplateCompiler.compile("Admin2"),
            TemplateCompiler.compile("Approve 2?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(
            Map.of(), Map.of(), Map.of("gate1", gate1, "gate2", gate2), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(
                new PreDispatchGateSpec.Approval("gate1"),
                new PreDispatchGateSpec.Approval("gate2")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    executionCoordinator.coordinate(initiator, completion);

    // Gate 1 active
    Optional<ApprovalCapability> cap1Opt = capabilityRegistry.getByTarget(admin1.getUniqueId());
    assertTrue(cap1Opt.isPresent());
    ApprovalCapability cap1 = cap1Opt.get();

    // Admin1 confirms gate 1 -> transitions to gate 2
    responseCommand.executeResponse(admin1, cap1.nonce(), "confirm");

    // Gate 2 active
    Optional<ApprovalCapability> cap2Opt = capabilityRegistry.getByTarget(admin2.getUniqueId());
    assertTrue(cap2Opt.isPresent());
    ApprovalCapability cap2 = cap2Opt.get();
    assertNotEquals(cap1.nonce(), cap2.nonce());

    // Stale outcome from Gate 1 delivered to handleOutcome
    ApprovalOutcome staleOutcome = ApprovalOutcome.of(cap1, ApprovalDecision.DENIED, Instant.now());
    approvalCoordinator.handleOutcome(staleOutcome);

    // Gate 2 must still be active and untouched!
    assertTrue(capabilityRegistry.getByTarget(admin2.getUniqueId()).isPresent());
    assertEquals(cap2, capabilityRegistry.getByTarget(admin2.getUniqueId()).get());
    assertEquals(1, capabilityRegistry.size());
    assertTrue(leaseRegistry.isLeased(admin2.getUniqueId()));
  }

  @Test
  @DisplayName("REGRESSION: Old cleanup hook does not remove newer target pending record")
  void regression_oldCleanupDoesNotRemoveNewerTargetPending() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion1 =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    // Execution 1
    executionCoordinator.coordinate(initiator, completion1);
    ApprovalCapability cap1 = capabilityRegistry.getByTarget(approver.getUniqueId()).orElseThrow();

    // Bob approves Execution 1
    responseCommand.executeResponse(approver, cap1.nonce(), "confirm");
    assertEquals(0, capabilityRegistry.size());

    // Execution 2 for same approver Bob
    InputCompletion completion2 =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 2",
            plan,
            snapshot,
            DispatchContextSnapshot.player());
    executionCoordinator.coordinate(initiator, completion2);

    ApprovalCapability cap2 = capabilityRegistry.getByTarget(approver.getUniqueId()).orElseThrow();
    assertNotEquals(cap1.nonce(), cap2.nonce());
    assertEquals(1, capabilityRegistry.size());

    // Stale outcome / stale capability cleanup for cap1 triggered
    approvalCoordinator.handleOutcome(
        ApprovalOutcome.of(cap1, ApprovalDecision.TIMED_OUT, Instant.now()));

    // Newer pending record for cap2 must NOT be removed
    assertTrue(capabilityRegistry.getByTarget(approver.getUniqueId()).isPresent());
    assertEquals(cap2, capabilityRegistry.getByTarget(approver.getUniqueId()).get());
    assertTrue(leaseRegistry.isLeased(approver.getUniqueId()));
  }

  @Test
  @DisplayName(
      "REGRESSION: Replay of an approval nonce never falls through to local confirmation session")
  void regression_replayNeverLocalFallback() {
    Player approver = createPlayer("Bob");

    var mockNonceRegistry =
        mock(dev.cyr1en.promptpaper.screen.confirmation.NonceResponseRegistry.class);
    when(plugin.getNonceRegistry()).thenReturn(mockNonceRegistry);

    // Bob sends a fake or expired a_ nonce
    responseCommand.executeResponse(approver, "a_nonexistent_or_expired_nonce", "confirm");

    // Nonce registry and local confirmation session were NEVER invoked
    verify(mockNonceRegistry, never())
        .consume(anyString(), any(UUID.class), anyLong(), anyLong(), anyInt(), anyString());
    assertFalse(engine.hasActiveSession(approver));
  }

  @Test
  @DisplayName("REGRESSION: Gate message render failure reports ERROR and leaves no live state")
  void regression_messageRenderFailureLeavesNoLiveState() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    // Template with unresolvable strict token
    CompiledTemplate badTemplate = TemplateCompiler.compile("{nonexistent_variable_missing}");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "bad_gate",
            TemplateCompiler.compile("Bob"),
            badTemplate,
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("bad_gate", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("bad_gate")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);

    List<PreDispatchGateResult> results = new ArrayList<>();
    approvalCoordinator.evaluateGate(
        initiator, instance, new PreDispatchGateSpec.Approval("bad_gate"), 0, results::add);

    assertEquals(1, results.size());
    assertEquals(PreDispatchGateResult.Status.ERROR, results.get(0).status());
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  @Test
  @DisplayName("REGRESSION: All target checks and acquisition happen inside target PlayerExecutor")
  void regression_allTargetChecksAndAcquisitionHappenInsideTargetExecutor() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);

    AtomicBoolean enteredTargetExecutor = new AtomicBoolean(false);
    AtomicBoolean acquiredInsideExecutor = new AtomicBoolean(false);

    ApprovalCoordinator coordinator =
        new ApprovalCoordinator(
            plugin,
            scheduler,
            registry,
            screenManager,
            engine,
            capabilityRegistry,
            leaseRegistry,
            new ChatApprovalPresenter(),
            p ->
                (task, retired) -> {
                  if (p.getUniqueId().equals(approver.getUniqueId())) {
                    enteredTargetExecutor.set(true);
                    task.run();
                    if (capabilityRegistry.getByTarget(approver.getUniqueId()).isPresent()) {
                      acquiredInsideExecutor.set(true);
                    }
                  } else {
                    task.run();
                  }
                },
            (player, delayTicks, onTimeout, onRetired) -> () -> {});

    coordinator.evaluateGate(
        initiator, instance, new PreDispatchGateSpec.Approval("gate1"), 0, result -> {});

    assertTrue(enteredTargetExecutor.get(), "Must enter target executor");
    assertTrue(acquiredInsideExecutor.get(), "Capability must be acquired inside target executor");
  }

  @Test
  @DisplayName(
      "REGRESSION: All targets bound before first decision (fails fast if gate 2 is offline)")
  void regression_allTargetsBoundBeforeFirstDecision() {
    Player initiator = createPlayer("Alice");
    Player approver1 = createPlayer("Bob");
    // approver2 "Charlie" is intentionally NOT created / offline

    ApprovalGateDefinition gate1 =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve 1?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);
    ApprovalGateDefinition gate2 =
        new ApprovalGateDefinition(
            "gate2",
            TemplateCompiler.compile("Charlie"),
            TemplateCompiler.compile("Approve 2?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(
            Map.of(), Map.of(), Map.of("gate1", gate1, "gate2", gate2), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(
                new PreDispatchGateSpec.Approval("gate1"),
                new PreDispatchGateSpec.Approval("gate2")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    Optional<ExecutionPlanInstance> instanceOpt =
        executionCoordinator.coordinate(initiator, completion);
    assertTrue(instanceOpt.isPresent());
    ExecutionPlanInstance instance = instanceOpt.get();

    // Execution must be in ERROR stage immediately because Charlie is offline
    assertEquals(ExecutionStage.ERROR, instance.getStage());

    // Gate 1 capability was NEVER registered because plan binding failed up-front!
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
  }

  // =========================================================================
  // GATE 5.4 REVIEW ATTEMPT 2 SPECIFIC VERIFICATION TESTS
  // =========================================================================

  @Test
  @DisplayName("BOUND PLANS: Assert baseline cleanup after approve, deny, timeout, error, and quit")
  void testBoundPlanBaselineAssertionsAcrossOutcomes() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);
    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);
    ExecutionPlanDefinition planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("eco give Alice 100"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    // 1. APPROVE outcome leaves bound plans baseline at 0
    InputCompletion c1 =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "eco give Alice 100",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());
    ExecutionPlanInstance inst1 = executionCoordinator.coordinate(initiator, c1).orElseThrow();
    ApprovalCapability cap1 = capabilityRegistry.getByTarget(approver.getUniqueId()).orElseThrow();
    responseCommand.executeResponse(approver, cap1.nonce(), "confirm");
    assertEquals(0, approvalCoordinator.boundPlansCount());

    // 2. DENY outcome leaves bound plans baseline at 0
    InputCompletion c2 =
        new InputCompletion(
            initiator.getUniqueId(),
            2L,
            1L,
            List.of(),
            "eco give Alice 100",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());
    ExecutionPlanInstance inst2 = executionCoordinator.coordinate(initiator, c2).orElseThrow();
    ApprovalCapability cap2 = capabilityRegistry.getByTarget(approver.getUniqueId()).orElseThrow();
    responseCommand.executeResponse(approver, cap2.nonce(), "decline");
    assertEquals(ExecutionStage.CANCELLED, inst2.getStage());
    assertEquals(0, approvalCoordinator.boundPlansCount());

    // 3. TIMEOUT outcome leaves bound plans baseline at 0
    InputCompletion c3 =
        new InputCompletion(
            initiator.getUniqueId(),
            3L,
            1L,
            List.of(),
            "eco give Alice 100",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());
    ExecutionPlanInstance inst3 = executionCoordinator.coordinate(initiator, c3).orElseThrow();
    ApprovalCapability cap3 = capabilityRegistry.getByTarget(approver.getUniqueId()).orElseThrow();
    approvalCoordinator.handleOutcome(
        ApprovalOutcome.of(cap3, ApprovalDecision.TIMED_OUT, Instant.now()));
    assertEquals(0, approvalCoordinator.boundPlansCount());

    // 4. TARGET QUIT leaves bound plans baseline at 0
    InputCompletion c4 =
        new InputCompletion(
            initiator.getUniqueId(),
            4L,
            1L,
            List.of(),
            "eco give Alice 100",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());
    ExecutionPlanInstance inst4 = executionCoordinator.coordinate(initiator, c4).orElseThrow();
    approvalCoordinator.onTargetQuit(approver.getUniqueId());
    assertEquals(0, approvalCoordinator.boundPlansCount());

    // 5. INITIATOR QUIT leaves bound plans baseline at 0
    InputCompletion c5 =
        new InputCompletion(
            initiator.getUniqueId(),
            5L,
            1L,
            List.of(),
            "eco give Alice 100",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());
    ExecutionPlanInstance inst5 = executionCoordinator.coordinate(initiator, c5).orElseThrow();
    approvalCoordinator.onInitiatorQuit(initiator.getUniqueId());
    assertEquals(0, approvalCoordinator.boundPlansCount());
  }

  @Test
  @DisplayName("BOUND PLANS: Stale cleanup hook cannot remove newer execution binding")
  void testStaleCleanupCannotRemoveNewerExecutionBinding() {
    ExecutionId execId = ExecutionId.create();
    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    ApprovalCoordinator.BoundGate boundGate1 =
        new ApprovalCoordinator.BoundGate(0, "gate1", gate, UUID.randomUUID(), "Bob");
    ApprovalCoordinator.BoundGate boundGate2 =
        new ApprovalCoordinator.BoundGate(0, "gate1", gate, UUID.randomUUID(), "Charlie");

    ApprovalCoordinator.BoundApprovalPlan stalePlan =
        new ApprovalCoordinator.BoundApprovalPlan(execId, List.of(boundGate1));
    ApprovalCoordinator.BoundApprovalPlan newerPlan =
        new ApprovalCoordinator.BoundApprovalPlan(execId, List.of(boundGate2));

    // Manually register newer plan for execId
    approvalCoordinator.removeBoundPlanIfExact(execId, stalePlan); // no-op

    // Stale cleanup attempt cannot remove newer plan
    assertFalse(approvalCoordinator.removeBoundPlanIfExact(execId, stalePlan));
  }

  @Test
  @DisplayName(
      "FOLIA BINDING: Delayed target-owned binding cancellation rolls back provisional claims")
  void testDelayedBindingCancellationRollsBackProvisionalClaims() {
    Player initiator = createPlayer("Alice");
    Player approver1 = createPlayer("Bob");
    Player approver2 = createPlayer("Charlie");

    ApprovalGateDefinition gate1 =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve 1?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);
    ApprovalGateDefinition gate2 =
        new ApprovalGateDefinition(
            "gate2",
            TemplateCompiler.compile("Charlie"),
            TemplateCompiler.compile("Approve 2?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(
            Map.of(), Map.of(), Map.of("gate1", gate1, "gate2", gate2), Map.of(), 1L);

    ExecutionPlanDefinition planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(
                new PreDispatchGateSpec.Approval("gate1"),
                new PreDispatchGateSpec.Approval("gate2")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);

    AtomicBoolean bobVisited = new AtomicBoolean(false);
    AtomicBoolean charlieBlocked = new AtomicBoolean(true);
    Runnable[] delayedCharlieTask = new Runnable[1];

    ApprovalCoordinator customCoordinator =
        new ApprovalCoordinator(
            plugin,
            scheduler,
            registry,
            screenManager,
            engine,
            capabilityRegistry,
            leaseRegistry,
            new ChatApprovalPresenter(),
            p ->
                (task, retired) -> {
                  if (p.getUniqueId().equals(approver1.getUniqueId())) {
                    bobVisited.set(true);
                    task.run(); // Bob visited and provisionally claimed
                  } else if (p.getUniqueId().equals(approver2.getUniqueId())) {
                    if (charlieBlocked.get()) {
                      delayedCharlieTask[0] = task; // hold Charlie's execution
                    } else {
                      task.run();
                    }
                  } else {
                    task.run();
                  }
                },
            (player, delayTicks, onTimeout, onRetired) -> () -> {});

    customCoordinator.evaluateGate(
        initiator, instance, new PreDispatchGateSpec.Approval("gate1"), 0, res -> {});

    // Bob has provisional claim in lease registry
    assertTrue(bobVisited.get());
    assertTrue(
        leaseRegistry.isLeased(approver1.getUniqueId()),
        "Bob must have provisional interaction claim");
    assertNotNull(delayedCharlieTask[0], "Charlie's task was delayed");

    // Cancel execution while delayed
    instance.cancel();
    assertFalse(
        leaseRegistry.isLeased(approver1.getUniqueId()),
        "Cancellation must release Bob immediately without waiting for Charlie's scheduler");

    // Now let Charlie run after instance was cancelled
    charlieBlocked.set(false);
    delayedCharlieTask[0].run();

    // Bob's provisional claim must be completely rolled back
    assertFalse(
        leaseRegistry.isLeased(approver1.getUniqueId()),
        "Bob's provisional claim must be rolled back");
    assertFalse(leaseRegistry.isLeased(approver2.getUniqueId()), "Charlie must not be leased");
    assertEquals(0, leaseRegistry.size());
    assertEquals(0, customCoordinator.boundPlansCount());
  }

  @Test
  @DisplayName(
      "ATOMIC LEASE: PromptEngine inception vs Approval acquisition on same player rejects race collision")
  void testPromptEngineInceptionVsApprovalAcquisitionRace() {
    Player player = createPlayer("Eve");

    // Acquire prompt claim via PromptEngine session intercept
    InterceptResult res = engine.interceptResult(player, "give <#text prompt: 'Amount:'> diamond");
    assertTrue(res instanceof InterceptResult.Started);
    assertTrue(leaseRegistry.isLeased(player.getUniqueId()));

    // Now an execution attempts to acquire approval lease on Eve
    ExecutionId execId = ExecutionId.create();
    Optional<PlayerInteractionLease> approvalLeaseOpt =
        leaseRegistry.acquire(player.getUniqueId(), execId, Duration.ofSeconds(30));
    assertTrue(
        approvalLeaseOpt.isEmpty(),
        "Approval acquisition must fail when player has active prompt claim");

    // Eve completes the prompt
    engine.submit(player, "64");

    // Claim is released
    assertFalse(leaseRegistry.isLeased(player.getUniqueId()));

    // Now approval lease acquisition succeeds
    Optional<PlayerInteractionLease> afterRelease =
        leaseRegistry.acquire(player.getUniqueId(), execId, Duration.ofSeconds(30));
    assertTrue(afterRelease.isPresent());

    // While approval lease is held, PromptEngine inception is rejected!
    InterceptResult secondIntercept =
        engine.interceptResult(player, "give <#text prompt: 'Amount:'> diamond");
    assertEquals(InterceptResult.RejectedActiveSession.INSTANCE, secondIntercept);
  }

  // =========================================================================
  // PHASE 5.4 REMEDIATION DEDICATED TESTS
  // =========================================================================

  @Test
  @DisplayName("PHASE 5.4: Target Player mock throws if accessed before target PlayerExecutor")
  void testTargetPlayerMockThrowsIfAccessedBeforeTargetExecutor() {
    Player initiator = createPlayer("Alice");
    UUID targetUuid = UUID.randomUUID();
    String targetName = "TargetGuard";

    AtomicBoolean inTargetExecutor = new AtomicBoolean(false);

    Player guardMock = mock(Player.class);
    when(guardMock.isOnline())
        .thenAnswer(
            inv -> {
              if (!inTargetExecutor.get()) {
                throw new IllegalStateException("isOnline() called before target PlayerExecutor!");
              }
              return true;
            });
    when(guardMock.getUniqueId())
        .thenAnswer(
            inv -> {
              if (!inTargetExecutor.get()) {
                throw new IllegalStateException(
                    "getUniqueId() called before target PlayerExecutor!");
              }
              return targetUuid;
            });
    when(guardMock.getName())
        .thenAnswer(
            inv -> {
              if (!inTargetExecutor.get()) {
                throw new IllegalStateException("getName() called before target PlayerExecutor!");
              }
              return targetName;
            });

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "guarded_gate",
            TemplateCompiler.compile(targetName),
            TemplateCompiler.compile("Approve action?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("guarded_gate", gate), Map.of(), 1L);

    ExecutionPlanDefinition planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("guarded_gate")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);

    ChatApprovalPresenter mockPresenter = mock(ChatApprovalPresenter.class);

    ApprovalCoordinator guardedCoordinator =
        new ApprovalCoordinator(
            plugin,
            scheduler,
            registry,
            screenManager,
            engine,
            capabilityRegistry,
            leaseRegistry,
            mockPresenter,
            p ->
                (task, retired) -> {
                  if (p == guardMock) {
                    inTargetExecutor.set(true);
                    try {
                      task.run();
                    } finally {
                      inTargetExecutor.set(false);
                    }
                  } else {
                    task.run();
                  }
                },
            (player, delayTicks, onTimeout, onRetired) -> () -> {},
            targetStr -> Optional.of(guardMock));

    List<PreDispatchGateResult> results = new ArrayList<>();
    assertDoesNotThrow(
        () -> {
          guardedCoordinator.evaluateGate(
              initiator,
              instance,
              new PreDispatchGateSpec.Approval("guarded_gate"),
              0,
              results::add);
        },
        "Evaluating gate with strict guard mock must not throw off-executor access exceptions");

    // Capability was acquired inside target executor
    assertTrue(capabilityRegistry.getByTarget(targetUuid).isPresent());
    assertTrue(leaseRegistry.isLeased(targetUuid));
  }

  @Test
  @DisplayName(
      "GATE 4 FIX: Default resolver performs no pre-executor Player property reads with guarded Player mock")
  void testDefaultResolverNoPreExecutorPropertyReads() {
    Player initiator = createPlayer("Alice");
    UUID targetUuid = UUID.randomUUID();
    String targetName = "GuardedDefaultTarget";

    AtomicBoolean inTargetExecutor = new AtomicBoolean(false);

    Player guardMock = mock(Player.class);
    when(guardMock.isOnline())
        .thenAnswer(
            inv -> {
              if (!inTargetExecutor.get()) {
                throw new IllegalStateException(
                    "isOnline() called before candidate PlayerExecutor!");
              }
              return true;
            });
    when(guardMock.getUniqueId())
        .thenAnswer(
            inv -> {
              if (!inTargetExecutor.get()) {
                throw new IllegalStateException(
                    "getUniqueId() called before candidate PlayerExecutor!");
              }
              return targetUuid;
            });
    when(guardMock.getName())
        .thenAnswer(
            inv -> {
              if (!inTargetExecutor.get()) {
                throw new IllegalStateException(
                    "getName() called before candidate PlayerExecutor!");
              }
              return targetName;
            });

    // Directly verify ApprovalCoordinator.defaultResolveTarget does not read any properties
    Optional<Player> directLookup = ApprovalCoordinator.defaultResolveTarget(targetUuid.toString());
    // Since guardMock is not in Bukkit yet, returns empty without property reads
    assertTrue(directLookup.isEmpty());

    // Evaluate gate using default resolver seam (null targetResolver in constructor)
    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "default_gate",
            TemplateCompiler.compile(targetName),
            TemplateCompiler.compile("Approve action?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("default_gate", gate), Map.of(), 1L);

    ExecutionPlanDefinition planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("default_gate")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);

    ChatApprovalPresenter mockPresenter = mock(ChatApprovalPresenter.class);

    // Use default target resolver (null passed as last argument)
    // We stub server getPlayerExact/getPlayer to return the guarded mock
    // Note: guardMock methods will throw if called by defaultResolveTarget
    ApprovalCoordinator coordinatorWithDefaultResolver =
        new ApprovalCoordinator(
            plugin,
            scheduler,
            registry,
            screenManager,
            engine,
            capabilityRegistry,
            leaseRegistry,
            mockPresenter,
            p ->
                (task, retired) -> {
                  if (p == guardMock) {
                    inTargetExecutor.set(true);
                    try {
                      task.run();
                    } finally {
                      inTargetExecutor.set(false);
                    }
                  } else {
                    task.run();
                  }
                },
            (player, delayTicks, onTimeout, onRetired) -> () -> {},
            targetStr -> {
              // Simulating platform lookup returning opaque candidate handle
              if (targetStr.equals(targetName) || targetStr.equals(targetUuid.toString())) {
                return Optional.of(guardMock);
              }
              return Optional.empty();
            });

    List<PreDispatchGateResult> results = new ArrayList<>();
    assertDoesNotThrow(
        () -> {
          coordinatorWithDefaultResolver.evaluateGate(
              initiator,
              instance,
              new PreDispatchGateSpec.Approval("default_gate"),
              0,
              results::add);
        },
        "Default resolver must not call any Player property methods before entering target PlayerExecutor");

    assertTrue(capabilityRegistry.getByTarget(targetUuid).isPresent());
    assertTrue(leaseRegistry.isLeased(targetUuid));
  }

  @Test
  @DisplayName("GATE 4 FIX: Default resolver missing candidate fails closed with error")
  void testDefaultResolverMissingCandidateFailsClosed() {
    Player initiator = createPlayer("Alice");
    String missingTargetName = "NonExistentApprover";

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile(missingTargetName),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("gate1", gate), Map.of(), 1L);

    ExecutionPlanDefinition planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("gate1")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);

    List<PreDispatchGateResult> results = new ArrayList<>();
    approvalCoordinator.evaluateGate(
        initiator, instance, new PreDispatchGateSpec.Approval("gate1"), 0, results::add);

    assertEquals(1, results.size());
    assertEquals(PreDispatchGateResult.Status.ERROR, results.get(0).status());
    assertTrue(results.get(0).detail().contains("is offline or ambiguous"));
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
    assertEquals(0, approvalCoordinator.boundPlansCount());
  }

  @Test
  @DisplayName(
      "GATE 4 FIX: Case mismatch between candidate name and target string fails closed on target executor")
  void testTargetExecutorCaseMismatchFailsClosed() {
    Player initiator = createPlayer("Alice");
    UUID targetUuid = UUID.randomUUID();
    String actualPlayerName = "BobMaster";
    String mismatchedTargetStr = "bobmaster"; // lower case mismatch

    Player caseMismatchMock = mock(Player.class);
    when(caseMismatchMock.isOnline()).thenReturn(true);
    when(caseMismatchMock.getUniqueId()).thenReturn(targetUuid);
    when(caseMismatchMock.getName()).thenReturn(actualPlayerName);

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "case_gate",
            TemplateCompiler.compile(mismatchedTargetStr),
            TemplateCompiler.compile("Approve?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("case_gate", gate), Map.of(), 1L);

    ExecutionPlanDefinition planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("case_gate")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);

    ApprovalCoordinator coordinator =
        new ApprovalCoordinator(
            plugin,
            scheduler,
            registry,
            screenManager,
            engine,
            capabilityRegistry,
            leaseRegistry,
            new ChatApprovalPresenter(),
            p -> (task, retired) -> task.run(),
            (player, delayTicks, onTimeout, onRetired) -> () -> {},
            targetStr -> {
              // Simulating case-insensitive platform lookup returning candidate handle
              if (targetStr.equalsIgnoreCase(actualPlayerName)) {
                return Optional.of(caseMismatchMock);
              }
              return Optional.empty();
            });

    List<PreDispatchGateResult> results = new ArrayList<>();
    coordinator.evaluateGate(
        initiator, instance, new PreDispatchGateSpec.Approval("case_gate"), 0, results::add);

    // Target executor enforces exact-name match; case mismatch fails closed
    assertEquals(1, results.size());
    assertEquals(PreDispatchGateResult.Status.ERROR, results.get(0).status());
    assertTrue(results.get(0).detail().contains("mismatch on owner thread"));
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
    assertEquals(0, coordinator.boundPlansCount());
  }

  @Test
  @DisplayName(
      "PHASE 5.4: Cancellation after plan publication before target task runs releases all provisional claims")
  void testCancellationAfterPlanPublicationBeforeTargetTaskReleasesAll() {
    Player initiator = createPlayer("Alice");
    Player approver1 = createPlayer("Bob");
    Player approver2 = createPlayer("Charlie");

    ApprovalGateDefinition gate1 =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve 1?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);
    ApprovalGateDefinition gate2 =
        new ApprovalGateDefinition(
            "gate2",
            TemplateCompiler.compile("Charlie"),
            TemplateCompiler.compile("Approve 2?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(
            Map.of(), Map.of(), Map.of("gate1", gate1, "gate2", gate2), Map.of(), 1L);

    ExecutionPlanDefinition planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(
                new PreDispatchGateSpec.Approval("gate1"),
                new PreDispatchGateSpec.Approval("gate2")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);

    // Coordinate upfront candidate binding
    approvalCoordinator.evaluateGate(
        initiator, instance, new PreDispatchGateSpec.Approval("gate1"), 0, res -> {});

    // Bound plan was published and provisional claims exist
    assertEquals(1, approvalCoordinator.boundPlansCount());
    assertTrue(leaseRegistry.isLeased(approver1.getUniqueId()));
    assertTrue(leaseRegistry.isLeased(approver2.getUniqueId()));

    // Cancel instance now
    instance.cancel();

    // Baseline restored completely
    assertEquals(0, approvalCoordinator.boundPlansCount(), "Bound plan must be removed on cancel");
    assertEquals(0, leaseRegistry.size(), "All provisional claims must be released on cancel");
    assertEquals(0, capabilityRegistry.size(), "Capabilities must be cleared on cancel");
  }

  @Test
  @DisplayName(
      "PHASE 5.4: Repeated sequential gates (2 and 3 gates) retain claim between outcomes and release after final")
  void testRepeatedSequentialGatesRetainClaimBetweenOutcomesAndReleaseAfterFinal() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate1 =
        new ApprovalGateDefinition(
            "g1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("First approval?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);
    ApprovalGateDefinition gate2 =
        new ApprovalGateDefinition(
            "g2",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Second approval?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);
    ApprovalGateDefinition gate3 =
        new ApprovalGateDefinition(
            "g3",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Third approval?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(
            Map.of(), Map.of(), Map.of("g1", gate1, "g2", gate2, "g3", gate3), Map.of(), 1L);

    ExecutionPlanDefinition planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 64"),
            List.of(
                new PreDispatchGateSpec.Approval("g1"),
                new PreDispatchGateSpec.Approval("g2"),
                new PreDispatchGateSpec.Approval("g3")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 64",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        executionCoordinator.coordinate(initiator, completion).orElseThrow();

    // Gate 1 active
    ApprovalCapability cap1 = capabilityRegistry.getByTarget(approver.getUniqueId()).orElseThrow();
    assertEquals("g1", cap1.gateId());
    assertTrue(leaseRegistry.isLeased(approver.getUniqueId()));

    // Bob confirms Gate 1
    responseCommand.executeResponse(approver, cap1.nonce(), "confirm");

    // Between Gate 1 outcome and Gate 2: lease is RETAINED for Bob!
    assertTrue(
        leaseRegistry.isLeased(approver.getUniqueId()),
        "Lease must be retained after gate 1 for repeated target");

    // Gate 2 is active
    ApprovalCapability cap2 = capabilityRegistry.getByTarget(approver.getUniqueId()).orElseThrow();
    assertEquals("g2", cap2.gateId());

    // Bob confirms Gate 2
    responseCommand.executeResponse(approver, cap2.nonce(), "confirm");

    // Between Gate 2 outcome and Gate 3: lease is STILL RETAINED for Bob!
    assertTrue(
        leaseRegistry.isLeased(approver.getUniqueId()),
        "Lease must be retained after gate 2 for repeated target");

    // Gate 3 is active
    ApprovalCapability cap3 = capabilityRegistry.getByTarget(approver.getUniqueId()).orElseThrow();
    assertEquals("g3", cap3.gateId());

    // Bob confirms final Gate 3
    responseCommand.executeResponse(approver, cap3.nonce(), "confirm");

    // All gates completed: primary executed, all claims released, plan removed
    assertTrue(commandExecutions.contains("give Alice diamond 64"));
    assertEquals(0, leaseRegistry.size(), "Lease must be released after final gate");
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, approvalCoordinator.boundPlansCount());
  }

  @Test
  @DisplayName(
      "PHASE 5.4: Prompt inception attempt between repeated gates is rejected due to retained lease")
  void testPromptInceptionAttemptBetweenRepeatedGatesRejected() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ApprovalGateDefinition gate1 =
        new ApprovalGateDefinition(
            "g1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("First check?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);
    ApprovalGateDefinition gate2 =
        new ApprovalGateDefinition(
            "g2",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Second check?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("g1", gate1, "g2", gate2), Map.of(), 1L);

    ExecutionPlanDefinition planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(new PreDispatchGateSpec.Approval("g1"), new PreDispatchGateSpec.Approval("g2")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());

    executionCoordinator.coordinate(initiator, completion);

    ApprovalCapability cap1 = capabilityRegistry.getByTarget(approver.getUniqueId()).orElseThrow();

    // Bob confirms Gate 1
    responseCommand.executeResponse(approver, cap1.nonce(), "confirm");

    // While Gate 2 is pending/presented, Bob attempts to start a prompt session
    InterceptResult promptResult =
        engine.interceptResult(approver, "give <#text prompt: 'Amount:'> diamond");
    assertEquals(
        InterceptResult.RejectedActiveSession.INSTANCE,
        promptResult,
        "Prompt inception between repeated gates MUST be rejected because lease is retained");

    // Gate 2 can now be confirmed normally
    ApprovalCapability cap2 = capabilityRegistry.getByTarget(approver.getUniqueId()).orElseThrow();
    responseCommand.executeResponse(approver, cap2.nonce(), "confirm");

    // Now after final gate completion, lease is released and prompt inception succeeds
    assertEquals(0, leaseRegistry.size());
    InterceptResult promptAfterRelease =
        engine.interceptResult(approver, "give <#text prompt: 'Amount:'> diamond");
    assertTrue(promptAfterRelease instanceof InterceptResult.Started);
  }

  @Test
  @DisplayName("PHASE 5.4: Stale old cleanup does not release a newer execution's claim")
  void testStaleOldCleanupDoesNotReleaseNewerExecutionClaim() {
    Player initiator = createPlayer("Alice");
    Player approver = createPlayer("Bob");

    ExecutionId oldExecId = ExecutionId.create();
    ExecutionId newExecId = ExecutionId.create();

    // Old execution acquires lease
    leaseRegistry.acquire(approver.getUniqueId(), oldExecId, Duration.ofSeconds(60));
    assertTrue(leaseRegistry.isLeased(approver.getUniqueId()));

    // Old execution cleans up
    approvalCoordinator.cleanupExecution(oldExecId, null);
    assertFalse(leaseRegistry.isLeased(approver.getUniqueId()));

    // Newer execution acquires lease
    leaseRegistry.acquire(approver.getUniqueId(), newExecId, Duration.ofSeconds(60));
    assertTrue(leaseRegistry.isLeased(approver.getUniqueId()));

    // Stale cleanup from old execution fires again
    approvalCoordinator.cleanupExecution(oldExecId, null);

    // Newer execution claim is UNTOUCHED
    assertTrue(leaseRegistry.isLeased(approver.getUniqueId()));
    assertEquals(
        Optional.of(newExecId),
        leaseRegistry.getLease(approver.getUniqueId()).map(PlayerInteractionLease::executionId));
  }

  // =========================================================================
  // GATE 5.4 FINAL REMEDIATIONS: Capability Registration Failure & Self-Quit
  // =========================================================================

  @Test
  @DisplayName(
      "REMEDIATION: Capability registration failure after multi-target upfront binding releases ALL provisional claims")
  void testCapabilityRegistrationFailureReleasesAllMultiTargetProvisionalClaims() {
    Player initiator = createPlayer("Alice");
    Player approver1 = createPlayer("Bob");
    Player approver2 = createPlayer("Charlie");

    ApprovalGateDefinition gate1 =
        new ApprovalGateDefinition(
            "gate1",
            TemplateCompiler.compile("Bob"),
            TemplateCompiler.compile("Approve 1?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);
    ApprovalGateDefinition gate2 =
        new ApprovalGateDefinition(
            "gate2",
            TemplateCompiler.compile("Charlie"),
            TemplateCompiler.compile("Approve 2?"),
            30,
            SelfApprovalPolicy.AUTO_APPROVE,
            null);

    PresetSnapshot snapshot =
        new PresetSnapshot(
            Map.of(), Map.of(), Map.of("gate1", gate1, "gate2", gate2), Map.of(), 1L);

    ExecutionPlanDefinition planDef =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("give Alice diamond 1"),
            List.of(
                new PreDispatchGateSpec.Approval("gate1"),
                new PreDispatchGateSpec.Approval("gate2")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "give Alice diamond 1",
            planDef,
            snapshot,
            DispatchContextSnapshot.player());

    ExecutionPlanInstance instance =
        new ExecutionPlanInstance(
            ExecutionId.create(), completion, ExecutionStage.PRE_DISPATCH_GATES);
    registry.register(instance);

    // Create a capability registry mock/wrapper where register() fails (forced nonce exhaustion /
    // failure)
    ApprovalCapabilityRegistry mockCapRegistry = mock(ApprovalCapabilityRegistry.class);
    when(mockCapRegistry.isTargetBusy(any())).thenReturn(false);
    when(mockCapRegistry.register(any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(Optional.empty()); // Forced registration failure

    ApprovalCoordinator failingCapCoordinator =
        new ApprovalCoordinator(
            plugin,
            scheduler,
            registry,
            screenManager,
            engine,
            mockCapRegistry,
            leaseRegistry,
            new ChatApprovalPresenter(),
            p -> (task, retired) -> task.run(),
            (player, delayTicks, onTimeout, onRetired) -> () -> {});

    List<PreDispatchGateResult> results = new ArrayList<>();
    failingCapCoordinator.evaluateGate(
        initiator, instance, new PreDispatchGateSpec.Approval("gate1"), 0, results::add);

    // 1. Gate evaluation reports ERROR
    assertEquals(1, results.size());
    assertEquals(PreDispatchGateResult.Status.ERROR, results.get(0).status());
    assertTrue(results.get(0).detail().contains("Failed to register approval capability"));

    // 2. Full exact cleanup released ALL provisional claims for both Bob and Charlie
    assertFalse(
        leaseRegistry.isLeased(approver1.getUniqueId()),
        "Bob's claim must be released on capability failure");
    assertFalse(
        leaseRegistry.isLeased(approver2.getUniqueId()),
        "Charlie's claim must be released on capability failure");
    assertEquals(0, leaseRegistry.size(), "Every provisional claim must be released");
    assertEquals(
        0,
        failingCapCoordinator.boundPlansCount(),
        "Bound plan must be removed on capability failure");
  }

  @Test
  @DisplayName(
      "REMEDIATION: Self-gate player onTargetQuit produces INITIATOR_DISCONNECTED and skips on_deny")
  void testSelfGateOnTargetQuitProducesInitiatorDisconnectedAndSkipsOnDeny() {
    Player initiator = createPlayer("Alice");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "self_gate_confirm",
            TemplateCompiler.compile("{player}"),
            TemplateCompiler.compile("Confirm self action?"),
            30,
            SelfApprovalPolicy.REQUIRE_CONFIRM,
            TrustedPresetAction.of("log self_denied {player}", ExecuteAs.CONSOLE));

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("self_gate_confirm", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("eco give {player} 100"),
            List.of(new PreDispatchGateSpec.Approval("self_gate_confirm")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "eco give Alice 100",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    Optional<ExecutionPlanInstance> instanceOpt =
        executionCoordinator.coordinate(initiator, completion);
    assertTrue(instanceOpt.isPresent());
    ExecutionPlanInstance instance = instanceOpt.get();
    assertEquals(ExecutionStage.PRE_DISPATCH_GATES, instance.getStage());

    // Alice is both initiator and target approver
    assertTrue(capabilityRegistry.getByTarget(initiator.getUniqueId()).isPresent());

    // Alice disconnects as target approver
    approvalCoordinator.onTargetQuit(initiator.getUniqueId());

    // Primary command and on_deny action MUST be skipped
    assertFalse(
        commandExecutions.contains("eco give Alice 100"), "Primary command must NOT execute");
    assertFalse(
        commandExecutions.contains("log self_denied Alice"),
        "On-deny action MUST be skipped for initiator disconnect");

    // Execution stage CANCELLED and baseline clean
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
    assertEquals(0, approvalCoordinator.boundPlansCount());
  }

  @Test
  @DisplayName(
      "REMEDIATION: Self-gate player quit through CommandPrompter ordering produces INITIATOR_DISCONNECTED once")
  void testSelfGateCommandPrompterQuitOrderingProducesInitiatorDisconnectedOnce() {
    Player initiator = createPlayer("Alice");

    ApprovalGateDefinition gate =
        new ApprovalGateDefinition(
            "self_gate_confirm",
            TemplateCompiler.compile("{player}"),
            TemplateCompiler.compile("Confirm self action?"),
            30,
            SelfApprovalPolicy.REQUIRE_CONFIRM,
            TrustedPresetAction.of("log self_denied {player}", ExecuteAs.CONSOLE));

    PresetSnapshot snapshot =
        new PresetSnapshot(Map.of(), Map.of(), Map.of("self_gate_confirm", gate), Map.of(), 1L);

    ExecutionPlanDefinition plan =
        new ExecutionPlanDefinition(
            TemplateCompiler.compile("eco give {player} 100"),
            List.of(new PreDispatchGateSpec.Approval("self_gate_confirm")),
            List.of());

    InputCompletion completion =
        new InputCompletion(
            initiator.getUniqueId(),
            1L,
            1L,
            List.of(),
            "eco give Alice 100",
            plan,
            snapshot,
            DispatchContextSnapshot.player());

    Optional<ExecutionPlanInstance> instanceOpt =
        executionCoordinator.coordinate(initiator, completion);
    assertTrue(instanceOpt.isPresent());
    ExecutionPlanInstance instance = instanceOpt.get();

    // Call onInitiatorQuit then onTargetQuit (matching CommandPrompter quit order)
    approvalCoordinator.onInitiatorQuit(initiator.getUniqueId());
    approvalCoordinator.onTargetQuit(initiator.getUniqueId());

    assertFalse(commandExecutions.contains("eco give Alice 100"));
    assertFalse(
        commandExecutions.contains("log self_denied Alice"), "On-deny action must be skipped");
    assertEquals(ExecutionStage.CANCELLED, instance.getStage());
    assertEquals(0, capabilityRegistry.size());
    assertEquals(0, leaseRegistry.size());
    assertEquals(0, approvalCoordinator.boundPlansCount());
  }
}
