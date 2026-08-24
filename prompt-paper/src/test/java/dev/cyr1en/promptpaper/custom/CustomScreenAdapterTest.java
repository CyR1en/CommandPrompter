package dev.cyr1en.promptpaper.custom;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.api.PromptScreenFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CustomScreenAdapterTest {

    private UUID playerUuid;
    private SessionVerificationSnapshot validSnapshot;
    private FakeScreenHandle handle;
    private FakeInputScreen delegate;
    private FakePlayerExecutor executor;
    private List<String> eventOrder;
    private TrackingVerifier verifier;
    private CustomScreenAdapter adapter;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();
        validSnapshot = new SessionVerificationSnapshot(playerUuid, 1L, 1L, 0, 100L);
        handle = new FakeScreenHandle(1L, "custom_screen", "TestPlugin", ProviderState.ACTIVE);
        delegate = new FakeInputScreen();
        executor = new FakePlayerExecutor();
        eventOrder = Collections.synchronizedList(new ArrayList<>());
        verifier = new TrackingVerifier(eventOrder, validSnapshot);
        adapter = new CustomScreenAdapter(handle, delegate, executor, validSnapshot, verifier);
    }

    @Nested
    @DisplayName("Thread Boundary and Queueing Tests")
    class ThreadBoundaryTests {

        @Test
        @DisplayName("Delegate callback from foreign thread queues work and does not deliver before executor runs")
        void testForeignThreadQueuesAndDoesNotDeliverBeforeExecution() throws InterruptedException {
            AtomicReference<ScreenResult> deliveredResult = new AtomicReference<>();
            adapter.onResult(deliveredResult::set);

            CountDownLatch latch = new CountDownLatch(1);
            Thread foreignThread = new Thread(() -> {
                delegate.fireResult(ScreenResult.answer("foreign_input"));
                latch.countDown();
            });
            foreignThread.start();
            assertTrue(latch.await(2, TimeUnit.SECONDS));

            // Before executor runs, result must not have been delivered to adapter consumer
            assertNull(deliveredResult.get(), "Result should not be delivered before executor runs");
            assertEquals(1, executor.queueSize(), "One task should be queued in player executor");

            // After executor runs queued task
            executor.runNext();
            assertNotNull(deliveredResult.get(), "Result should be delivered after executor runs");
            assertEquals("foreign_input", deliveredResult.get().answer());
        }

        @Test
        @DisplayName("Exact event order: scheduler hop -> provider active check -> live verifier -> once -> delivery")
        void testAuthoritativeEventOrder() {
            List<String> trace = Collections.synchronizedList(new ArrayList<>());
            TrackingVerifier trackingVerifier = new TrackingVerifier(trace, validSnapshot);

            FakeScreenHandle trackingHandle = new FakeScreenHandle(1L, "test", "plugin", ProviderState.ACTIVE) {
                @Override
                public boolean isActive() {
                    trace.add("provider.isActive");
                    return super.isActive();
                }
            };

            PlayerExecutor trackingExecutor = (task, retired) -> {
                trace.add("executor.schedule");
                executor.execute(task, retired);
            };

            CustomScreenAdapter trackingAdapter = new CustomScreenAdapter(
                    trackingHandle,
                    delegate,
                    trackingExecutor,
                    validSnapshot,
                    trackingVerifier
            );

            AtomicReference<ScreenResult> received = new AtomicReference<>();
            trackingAdapter.onResult(result -> {
                trace.add("consumer.accept");
                received.set(result);
            });

            // Delegate fires result
            trace.clear();
            trace.add("delegate.fireResult");
            delegate.fireResult(ScreenResult.answer("sample"));

            // At this point before executor hop:
            assertEquals(List.of("delegate.fireResult", "provider.isActive", "executor.schedule"), trace);

            // Now run the executor task
            trace.add("executor.run");
            executor.runNext();

            assertEquals(
                    List.of(
                            "delegate.fireResult",
                            "provider.isActive", // advisory check before hop
                            "executor.schedule",
                            "executor.run",
                            "provider.isActive", // authoritative post-hop check
                            "verifier.verify",   // live state check
                            "consumer.accept"    // exactly-once delivery
                    ),
                    trace
            );
            assertEquals("sample", received.get().answer());
        }
    }

    @Nested
    @DisplayName("Authoritative State Verification Tests")
    class AuthoritativeVerificationTests {

        @Test
        @DisplayName("Stale incarnation is rejected and discarded")
        void testStaleIncarnationRejected() {
            AtomicReference<ScreenResult> deliveredResult = new AtomicReference<>();
            adapter.onResult(deliveredResult::set);

            // Verifier expects incarnation 2L instead of 1L
            verifier.setLiveState(new SessionVerificationSnapshot(playerUuid, 2L, 1L, 0, 100L));

            delegate.fireResult(ScreenResult.answer("stale_inc"));
            assertEquals(1, executor.queueSize());
            executor.runNext();

            assertNull(deliveredResult.get(), "Stale incarnation must be discarded");
            assertFalse(adapter.isTerminalDelivered());
        }

        @Test
        @DisplayName("Stale generation is rejected and discarded")
        void testStaleGenerationRejected() {
            AtomicReference<ScreenResult> deliveredResult = new AtomicReference<>();
            adapter.onResult(deliveredResult::set);

            // Generation advanced to 2L
            verifier.setLiveState(new SessionVerificationSnapshot(playerUuid, 1L, 2L, 0, 100L));

            delegate.fireResult(ScreenResult.answer("stale_gen"));
            assertEquals(1, executor.queueSize());
            executor.runNext();

            assertNull(deliveredResult.get(), "Stale generation must be discarded");
            assertFalse(adapter.isTerminalDelivered());
        }

        @Test
        @DisplayName("Stale attempt token is rejected and discarded")
        void testStaleAttemptRejected() {
            AtomicReference<ScreenResult> deliveredResult = new AtomicReference<>();
            adapter.onResult(deliveredResult::set);

            // New attempt token 200L
            verifier.setLiveState(new SessionVerificationSnapshot(playerUuid, 1L, 1L, 0, 200L));

            delegate.fireResult(ScreenResult.answer("stale_attempt"));
            assertEquals(1, executor.queueSize());
            executor.runNext();

            assertNull(deliveredResult.get(), "Stale attempt token must be discarded");
            assertFalse(adapter.isTerminalDelivered());
        }

        @Test
        @DisplayName("Wrong player UUID is rejected and discarded")
        void testWrongUuidRejected() {
            AtomicReference<ScreenResult> deliveredResult = new AtomicReference<>();
            adapter.onResult(deliveredResult::set);

            // Mismatched UUID
            verifier.setLiveState(new SessionVerificationSnapshot(UUID.randomUUID(), 1L, 1L, 0, 100L));

            delegate.fireResult(ScreenResult.answer("wrong_uuid"));
            assertEquals(1, executor.queueSize());
            executor.runNext();

            assertNull(deliveredResult.get(), "Wrong UUID must be discarded");
            assertFalse(adapter.isTerminalDelivered());
        }

        @Test
        @DisplayName("Generation advance before queued callback execution causes discard")
        void testGenerationAdvanceBeforeExecutionCausesDiscard() {
            AtomicReference<ScreenResult> deliveredResult = new AtomicReference<>();
            adapter.onResult(deliveredResult::set);

            // Callback fired while generation was 1L
            delegate.fireResult(ScreenResult.answer("hello"));
            assertEquals(1, executor.queueSize());

            // Generation advances before executor task runs
            verifier.setLiveState(new SessionVerificationSnapshot(playerUuid, 1L, 2L, 0, 100L));

            executor.runNext();
            assertNull(deliveredResult.get(), "Result should be discarded when generation advances before execution");
            assertFalse(adapter.isTerminalDelivered());
        }
    }

    @Nested
    @DisplayName("Exactly-Once Delivery and Concurrency Tests")
    class ExactlyOnceDeliveryTests {

        @Test
        @DisplayName("Two callbacks queued before execution deliver exactly one result")
        void testTwoCallbacksDeliverExactlyOne() {
            List<ScreenResult> results = new ArrayList<>();
            adapter.onResult(results::add);

            // Two results fired in succession
            delegate.fireResult(ScreenResult.answer("first"));
            delegate.fireResult(ScreenResult.answer("second"));

            assertEquals(2, executor.queueSize());
            executor.runAll();

            assertEquals(1, results.size(), "Only one result should be delivered");
            assertEquals("first", results.get(0).answer());
            assertTrue(adapter.isTerminalDelivered());
        }

        @Test
        @DisplayName("Result followed by open failure delivers only the first result")
        void testResultFollowedByFailureDeliversOnlyFirst() {
            List<ScreenResult> results = new ArrayList<>();
            List<Throwable> errors = new ArrayList<>();
            adapter.onResult(results::add);
            adapter.onOpenFailure(errors::add);

            delegate.fireResult(ScreenResult.answer("first"));
            delegate.fireOpenFailure(new RuntimeException("subsequent error"));

            executor.runAll();

            assertEquals(1, results.size());
            assertEquals("first", results.get(0).answer());
            assertEquals(0, errors.size(), "Subsequent error should be ignored after result is delivered");
        }
    }

    @Nested
    @DisplayName("Provider State and Teardown Detach Tests")
    class ProviderStateAndTeardownTests {

        @Test
        @DisplayName("Provider marked teardown before queued hop causes discard")
        void testProviderMarkedTeardownBeforeQueuedHopCausesDiscard() {
            AtomicReference<ScreenResult> deliveredResult = new AtomicReference<>();
            adapter.onResult(deliveredResult::set);

            delegate.fireResult(ScreenResult.answer("payload"));
            assertEquals(1, executor.queueSize());

            // Transition provider state to TEARING_DOWN before executor runs
            handle.setState(ProviderState.TEARING_DOWN);

            executor.runNext();
            assertNull(deliveredResult.get(), "Result must be discarded when provider is tearing down");
            assertFalse(adapter.isTerminalDelivered());
        }

        @Test
        @DisplayName("Provider teardown detach invokes zero delegate methods and clears references")
        void testTeardownDetachInvokesZeroDelegateMethods() {
            adapter.onResult(res -> {});
            adapter.onOpenFailure(err -> {});

            int initialOpenCalls = delegate.openCalls.get();
            int initialCloseCalls = delegate.closeCalls.get();
            int initialIsOpenCalls = delegate.isOpenCalls.get();

            adapter.teardownDetach();

            assertTrue(adapter.isDetached());
            assertNull(adapter.delegate(), "Delegate reference should be cleared");

            // Verify NO delegate lifecycle methods were called
            assertEquals(initialOpenCalls, delegate.openCalls.get());
            assertEquals(initialCloseCalls, delegate.closeCalls.get());
            assertEquals(initialIsOpenCalls, delegate.isOpenCalls.get());

            // Subsequent open/close/isOpen calls do nothing and do not touch delegate
            adapter.open();
            adapter.close();
            assertFalse(adapter.isOpen());
            executor.runAll();

            assertEquals(initialOpenCalls, delegate.openCalls.get());
            assertEquals(initialCloseCalls, delegate.closeCalls.get());
            assertEquals(initialIsOpenCalls, delegate.isOpenCalls.get());
        }

        @Test
        @DisplayName("Normal close calls delegate close only when provider is ACTIVE")
        void testNormalCloseCallsProviderOnlyWhenActive() {
            adapter.open();
            executor.runAll();
            assertEquals(1, delegate.openCalls.get());

            // Normal close when ACTIVE
            adapter.close();
            executor.runAll();
            assertEquals(1, delegate.closeCalls.get());

            // If provider is INACTIVE, close does not touch delegate
            handle.setState(ProviderState.INACTIVE);
            adapter.close();
            executor.runAll();
            assertEquals(1, delegate.closeCalls.get(), "Close should not be called when provider is INACTIVE");
        }
    }

    @Nested
    @DisplayName("Exception Containment Tests")
    class ExceptionContainmentTests {

        @Test
        @DisplayName("Delegate open exception is contained and delivers open failure error")
        void testDelegateOpenExceptionContained() {
            delegate.throwOnOpen = new RuntimeException("Simulated open exception");

            AtomicReference<Throwable> failure = new AtomicReference<>();
            adapter.onOpenFailure(failure::set);

            assertDoesNotThrow(() -> adapter.open());
            executor.runAll();

            assertNotNull(failure.get());
            assertEquals("Simulated open exception", failure.get().getMessage());
            assertTrue(adapter.isTerminalDelivered());
        }

        @Test
        @DisplayName("Delegate open exception falls back to ScreenResult.error() if no openFailure consumer")
        void testDelegateOpenExceptionFallsBackToResultError() {
            delegate.throwOnOpen = new RuntimeException("Simulated open exception");

            AtomicReference<ScreenResult> result = new AtomicReference<>();
            adapter.onResult(result::set);

            assertDoesNotThrow(() -> adapter.open());
            executor.runAll();

            assertNotNull(result.get());
            assertTrue(result.get().cancelled());
            assertEquals(CancelReason.ERROR, result.get().cancelReason());
            assertTrue(adapter.isTerminalDelivered());
        }

        @Test
        @DisplayName("Null delegate produces safe open failure")
        void testNullDelegateProducesSafeOpenFailure() {
            CustomScreenAdapter nullDelegateAdapter = new CustomScreenAdapter(
                    handle,
                    null,
                    executor,
                    validSnapshot,
                    verifier
            );

            AtomicReference<Throwable> failure = new AtomicReference<>();
            nullDelegateAdapter.onOpenFailure(failure::set);

            assertDoesNotThrow(nullDelegateAdapter::open);
            executor.runAll();

            assertNotNull(failure.get());
            assertTrue(failure.get().getMessage().contains("null"));
            assertTrue(nullDelegateAdapter.isTerminalDelivered());
        }

        @Test
        @DisplayName("Delegate onResult registration exception is contained and delivers error")
        void testDelegateOnResultRegistrationExceptionContained() {
            delegate.throwOnOnResult = new RuntimeException("Registration failed");

            AtomicReference<Throwable> failure = new AtomicReference<>();
            adapter.onOpenFailure(failure::set);
            adapter.onResult(r -> {});

            executor.runAll();

            assertNotNull(failure.get());
            assertEquals("Registration failed", failure.get().getMessage());
        }

        @Test
        @DisplayName("Delegate onOpenFailure registration exception is contained and delivers error")
        void testDelegateOnOpenFailureRegistrationExceptionContained() {
            delegate.throwOnOnOpenFailure = new RuntimeException("OpenFailure registration failed");

            AtomicReference<Throwable> failure = new AtomicReference<>();
            adapter.onOpenFailure(failure::set);

            executor.runAll();

            assertNotNull(failure.get());
            assertEquals("OpenFailure registration failed", failure.get().getMessage());
        }

        @Test
        @DisplayName("Delegate close exception is safely contained without crashing executor")
        void testDelegateCloseExceptionContained() {
            delegate.throwOnClose = new RuntimeException("Simulated close crash");

            assertDoesNotThrow(() -> {
                adapter.close();
                executor.runAll();
            });
            assertEquals(1, delegate.closeCalls.get());
        }

        @Test
        @DisplayName("Delegate isOpen exception is safely contained and returns false")
        void testDelegateIsOpenExceptionContained() {
            delegate.throwOnIsOpen = new RuntimeException("Simulated isOpen crash");

            assertDoesNotThrow(() -> {
                boolean open = adapter.isOpen();
                assertFalse(open, "isOpen should safely return false on exception");
            });
        }
    }

    @Nested
    @DisplayName("Scheduler Retirement Tests")
    class SchedulerRetirementTests {

        @Test
        @DisplayName("Scheduler retirement invokes internal retirement callback and no provider methods")
        void testSchedulerRetirementInvokesNoProviderMethods() {
            AtomicBoolean retiredCalled = new AtomicBoolean(false);
            adapter.setRetirementCallback(() -> retiredCalled.set(true));

            int initialOpenCalls = delegate.openCalls.get();
            int initialCloseCalls = delegate.closeCalls.get();
            int initialIsOpenCalls = delegate.isOpenCalls.get();

            // Trigger retirement on executor
            adapter.open();
            executor.retireAll();

            assertTrue(retiredCalled.get(), "Retirement callback must be called");
            assertTrue(adapter.isDetached(), "Adapter should be detached on retirement");
            assertNull(adapter.delegate(), "Delegate reference should be cleared");

            // Zero provider methods should be invoked
            assertEquals(initialOpenCalls, delegate.openCalls.get());
            assertEquals(initialCloseCalls, delegate.closeCalls.get());
            assertEquals(initialIsOpenCalls, delegate.isOpenCalls.get());
        }
    }

    // =========================================================================
    // Test Helpers / Fakes
    // =========================================================================

    private static class FakePlayerExecutor implements PlayerExecutor {
        private final Queue<Runnable> taskQueue = new ArrayDeque<>();
        private final Queue<Runnable> retiredQueue = new ArrayDeque<>();

        @Override
        public void execute(Runnable task, Runnable retired) {
            taskQueue.add(task);
            if (retired != null) {
                retiredQueue.add(retired);
            }
        }

        public int queueSize() {
            return taskQueue.size();
        }

        public void runNext() {
            Runnable task = taskQueue.poll();
            if (task != null) {
                task.run();
            }
        }

        public void runAll() {
            while (!taskQueue.isEmpty()) {
                taskQueue.poll().run();
            }
        }

        public void retireAll() {
            taskQueue.clear();
            while (!retiredQueue.isEmpty()) {
                retiredQueue.poll().run();
            }
        }
    }

    private static class FakeScreenHandle implements CustomScreenHandle {
        private final long providerId;
        private final String key;
        private final String ownerName;
        private ProviderState state;
        private PromptScreenFactory factory;

        public FakeScreenHandle(long providerId, String key, String ownerName, ProviderState state) {
            this(providerId, key, ownerName, state, (player, tag) -> null);
        }

        public FakeScreenHandle(long providerId, String key, String ownerName, ProviderState state, PromptScreenFactory factory) {
            this.providerId = providerId;
            this.key = key;
            this.ownerName = ownerName;
            this.state = state;
            this.factory = factory;
        }

        public void setState(ProviderState state) {
            this.state = state;
        }

        public void setFactory(PromptScreenFactory factory) {
            this.factory = factory;
        }

        @Override
        public long providerId() {
            return providerId;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String ownerName() {
            return ownerName;
        }

        @Override
        public ProviderState state() {
            return state;
        }

        @Override
        public PromptScreenFactory factory() {
            return factory;
        }
    }

    private static class FakeInputScreen implements InputScreen {
        final AtomicInteger openCalls = new AtomicInteger(0);
        final AtomicInteger closeCalls = new AtomicInteger(0);
        final AtomicInteger isOpenCalls = new AtomicInteger(0);
        final AtomicInteger onResultCalls = new AtomicInteger(0);
        final AtomicInteger onOpenFailureCalls = new AtomicInteger(0);

        Consumer<ScreenResult> resultConsumer;
        Consumer<Throwable> openFailureConsumer;

        RuntimeException throwOnOpen;
        RuntimeException throwOnClose;
        RuntimeException throwOnIsOpen;
        RuntimeException throwOnOnResult;
        RuntimeException throwOnOnOpenFailure;

        @Override
        public void open() {
            openCalls.incrementAndGet();
            if (throwOnOpen != null) {
                throw throwOnOpen;
            }
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            if (throwOnClose != null) {
                throw throwOnClose;
            }
        }

        @Override
        public boolean isOpen() {
            isOpenCalls.incrementAndGet();
            if (throwOnIsOpen != null) {
                throw throwOnIsOpen;
            }
            return true;
        }

        @Override
        public void onResult(Consumer<ScreenResult> callback) {
            onResultCalls.incrementAndGet();
            if (throwOnOnResult != null) {
                throw throwOnOnResult;
            }
            this.resultConsumer = callback;
        }

        @Override
        public void onOpenFailure(Consumer<Throwable> callback) {
            onOpenFailureCalls.incrementAndGet();
            if (throwOnOnOpenFailure != null) {
                throw throwOnOnOpenFailure;
            }
            this.openFailureConsumer = callback;
        }

        void fireResult(ScreenResult result) {
            if (resultConsumer != null) {
                resultConsumer.accept(result);
            }
        }

        void fireOpenFailure(Throwable error) {
            if (openFailureConsumer != null) {
                openFailureConsumer.accept(error);
            }
        }
    }

    private static class TrackingVerifier implements ScreenAttemptVerifier {
        private final List<String> eventTrace;
        private volatile SessionVerificationSnapshot liveState;

        TrackingVerifier(List<String> eventTrace, SessionVerificationSnapshot liveState) {
            this.eventTrace = eventTrace;
            this.liveState = liveState;
        }

        void setLiveState(SessionVerificationSnapshot liveState) {
            this.liveState = liveState;
        }

        @Override
        public boolean verify(SessionVerificationSnapshot snapshot) {
            if (eventTrace != null) {
                eventTrace.add("verifier.verify");
            }
            return liveState != null
                    && liveState.playerUuid().equals(snapshot.playerUuid())
                    && liveState.expectedIncarnation() == snapshot.expectedIncarnation()
                    && liveState.expectedGeneration() == snapshot.expectedGeneration()
                    && liveState.attemptToken() == snapshot.attemptToken();
        }
    }
}
