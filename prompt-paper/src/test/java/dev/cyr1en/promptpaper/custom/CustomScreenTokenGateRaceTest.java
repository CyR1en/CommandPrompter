package dev.cyr1en.promptpaper.custom;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.api.PromptScreenFactory;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomScreenTokenGateRaceTest {

  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    executor = Executors.newFixedThreadPool(4);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  @DisplayName(
      "Gate Race: Factory invocation vs Teardown - In-flight factory completes before state transition")
  void testFactoryInvocationVsTeardownRaceInFlightCompletes() throws Exception {
    CountDownLatch factoryEntered = new CountDownLatch(1);
    CountDownLatch releaseFactory = new CountDownLatch(1);
    AtomicInteger factoryCallCount = new AtomicInteger(0);

    MockInputScreen createdScreen = new MockInputScreen();
    PromptScreenFactory slowFactory =
        (player, context) -> {
          factoryCallCount.incrementAndGet();
          factoryEntered.countDown();
          try {
            releaseFactory.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return createdScreen;
        };

    CustomScreenToken token = new CustomScreenToken(1L, "slowscreen", "SlowPlugin", slowFactory);

    // Start factory invocation on Thread A
    Future<InputScreen> factoryFuture =
        executor.submit(() -> token.invokeFactory(f -> f.createScreen(null, null)).orElse(null));

    // Wait until factory has entered critical section
    assertTrue(factoryEntered.await(5, TimeUnit.SECONDS), "Factory must enter critical section");

    // Start teardown on Thread B
    Future<?> teardownFuture = executor.submit(token::teardown);

    // While factory is blocked in gate, token should still be seen as ACTIVE by the thread holding
    // the gate
    // Now release the factory
    releaseFactory.countDown();

    InputScreen resultScreen = factoryFuture.get(5, TimeUnit.SECONDS);
    assertSame(createdScreen, resultScreen);
    assertEquals(1, factoryCallCount.get());

    // Teardown should now complete
    teardownFuture.get(5, TimeUnit.SECONDS);

    // Now token is INACTIVE and factory reference is cleared
    assertEquals(ProviderState.INACTIVE, token.state());
    assertFalse(token.isActive());
    assertNull(token.factory());

    // Any subsequent factory call must be rejected with zero provider calls
    AtomicInteger subsequentCalls = new AtomicInteger(0);
    token.invokeFactory(
        f -> {
          subsequentCalls.incrementAndGet();
          return null;
        });
    assertEquals(0, subsequentCalls.get(), "No factory call permitted after INACTIVE transition");
  }

  @Test
  @DisplayName(
      "Gate Race: Factory invocation vs Teardown - Teardown wins before factory starts, factory call count zero")
  void testFactoryInvocationVsTeardownRaceTeardownWins() throws Exception {
    AtomicInteger factoryCallCount = new AtomicInteger(0);
    PromptScreenFactory factory =
        (player, context) -> {
          factoryCallCount.incrementAndGet();
          return new MockInputScreen();
        };

    CustomScreenToken token = new CustomScreenToken(1L, "racescreen", "RacePlugin", factory);

    // Teardown first
    token.teardown();

    // Attempt factory invocation
    var opt = token.invokeFactory(f -> f.createScreen(null, null));
    assertTrue(opt.isEmpty());
    assertEquals(0, factoryCallCount.get(), "Factory must not be called when token is torn down");
  }

  @Test
  @DisplayName(
      "Gate Race: Delegate open vs Teardown - In-flight open completes before state transition")
  void testDelegateOpenVsTeardownRaceInFlightCompletes() throws Exception {
    CountDownLatch openEntered = new CountDownLatch(1);
    CountDownLatch releaseOpen = new CountDownLatch(1);
    AtomicInteger openCalls = new AtomicInteger(0);

    MockInputScreen delegate =
        new MockInputScreen() {
          @Override
          public void open() {
            openCalls.incrementAndGet();
            openEntered.countDown();
            try {
              releaseOpen.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        };

    CustomScreenToken token =
        new CustomScreenToken(1L, "openscreen", "OpenPlugin", (p, c) -> delegate);

    Future<Boolean> openFuture = executor.submit(() -> token.runIfActive(delegate::open));

    assertTrue(openEntered.await(5, TimeUnit.SECONDS));

    Future<?> teardownFuture = executor.submit(token::teardown);

    releaseOpen.countDown();

    assertTrue(openFuture.get(5, TimeUnit.SECONDS));
    assertEquals(1, openCalls.get());

    teardownFuture.get(5, TimeUnit.SECONDS);
    assertEquals(ProviderState.INACTIVE, token.state());

    // Subsequent open attempt after teardown
    boolean subsequentOpen = token.runIfActive(delegate::open);
    assertFalse(subsequentOpen);
    assertEquals(1, openCalls.get(), "No open calls permitted after teardown");
  }

  @Test
  @DisplayName(
      "Gate Race: Delegate close vs Teardown - In-flight close completes before state transition")
  void testDelegateCloseVsTeardownRaceInFlightCompletes() throws Exception {
    CountDownLatch closeEntered = new CountDownLatch(1);
    CountDownLatch releaseClose = new CountDownLatch(1);
    AtomicInteger closeCalls = new AtomicInteger(0);

    MockInputScreen delegate =
        new MockInputScreen() {
          @Override
          public void close() {
            closeCalls.incrementAndGet();
            closeEntered.countDown();
            try {
              releaseClose.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        };

    CustomScreenToken token =
        new CustomScreenToken(1L, "closescreen", "ClosePlugin", (p, c) -> delegate);

    Future<Boolean> closeFuture = executor.submit(() -> token.runIfActive(delegate::close));

    assertTrue(closeEntered.await(5, TimeUnit.SECONDS));

    Future<?> teardownFuture = executor.submit(token::teardown);

    releaseClose.countDown();

    assertTrue(closeFuture.get(5, TimeUnit.SECONDS));
    assertEquals(1, closeCalls.get());

    teardownFuture.get(5, TimeUnit.SECONDS);
    assertEquals(ProviderState.INACTIVE, token.state());

    boolean subsequentClose = token.runIfActive(delegate::close);
    assertFalse(subsequentClose);
    assertEquals(1, closeCalls.get(), "No close calls permitted after teardown");
  }

  @Test
  @DisplayName(
      "Gate Race: Delegate isOpen vs Teardown - In-flight isOpen completes before state transition")
  void testDelegateIsOpenVsTeardownRaceInFlightCompletes() throws Exception {
    CountDownLatch isOpenEntered = new CountDownLatch(1);
    CountDownLatch releaseIsOpen = new CountDownLatch(1);
    AtomicInteger isOpenCalls = new AtomicInteger(0);

    MockInputScreen delegate =
        new MockInputScreen() {
          @Override
          public boolean isOpen() {
            isOpenCalls.incrementAndGet();
            isOpenEntered.countDown();
            try {
              releaseIsOpen.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return true;
          }
        };

    CustomScreenToken token =
        new CustomScreenToken(1L, "isopenscreen", "IsOpenPlugin", (p, c) -> delegate);

    Future<Boolean> isOpenFuture =
        executor.submit(() -> token.callIfActive(delegate::isOpen).orElse(false));

    assertTrue(isOpenEntered.await(5, TimeUnit.SECONDS));

    Future<?> teardownFuture = executor.submit(token::teardown);

    releaseIsOpen.countDown();

    assertTrue(isOpenFuture.get(5, TimeUnit.SECONDS));
    assertEquals(1, isOpenCalls.get());

    teardownFuture.get(5, TimeUnit.SECONDS);
    assertEquals(ProviderState.INACTIVE, token.state());

    boolean subsequentIsOpen = token.callIfActive(delegate::isOpen).orElse(false);
    assertFalse(subsequentIsOpen);
    assertEquals(1, isOpenCalls.get(), "No isOpen calls permitted after teardown");
  }

  @Test
  @DisplayName(
      "Gate Race: Delegate Callback Registration vs Teardown - In-flight callback registration completes before state transition")
  void testDelegateCallbackRegistrationVsTeardownRace() throws Exception {
    CountDownLatch regEntered = new CountDownLatch(1);
    CountDownLatch releaseReg = new CountDownLatch(1);
    AtomicInteger onResultCalls = new AtomicInteger(0);

    MockInputScreen delegate =
        new MockInputScreen() {
          @Override
          public void onResult(Consumer<ScreenResult> callback) {
            onResultCalls.incrementAndGet();
            regEntered.countDown();
            try {
              releaseReg.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        };

    CustomScreenToken token =
        new CustomScreenToken(1L, "regscreen", "RegPlugin", (p, c) -> delegate);

    Future<Boolean> regFuture =
        executor.submit(() -> token.runIfActive(() -> delegate.onResult(res -> {})));

    assertTrue(regEntered.await(5, TimeUnit.SECONDS));

    Future<?> teardownFuture = executor.submit(token::teardown);

    releaseReg.countDown();

    assertTrue(regFuture.get(5, TimeUnit.SECONDS));
    assertEquals(1, onResultCalls.get());

    teardownFuture.get(5, TimeUnit.SECONDS);
    assertEquals(ProviderState.INACTIVE, token.state());

    boolean subsequentReg = token.runIfActive(() -> delegate.onResult(res -> {}));
    assertFalse(subsequentReg);
    assertEquals(1, onResultCalls.get(), "No callback registration permitted after teardown");
  }

  @Test
  @DisplayName("CustomScreenAdapter lazy open vs Teardown race under token gate")
  void testCustomScreenAdapterLazyOpenVsTeardownRace() throws Exception {
    CountDownLatch factoryEntered = new CountDownLatch(1);
    CountDownLatch releaseFactory = new CountDownLatch(1);
    AtomicInteger factoryCalls = new AtomicInteger(0);
    AtomicInteger openCalls = new AtomicInteger(0);

    MockInputScreen delegate =
        new MockInputScreen() {
          @Override
          public void open() {
            openCalls.incrementAndGet();
          }
        };

    CustomScreenToken token =
        new CustomScreenToken(
            1L,
            "adapterrace",
            "AdapterPlugin",
            (p, ctx) -> {
              factoryCalls.incrementAndGet();
              factoryEntered.countDown();
              try {
                releaseFactory.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return delegate;
            });

    UUID uuid = UUID.randomUUID();
    SessionVerificationSnapshot snapshot = new SessionVerificationSnapshot(uuid, 1L, 1L, 0, 100L);
    DirectPlayerExecutor directExecutor = new DirectPlayerExecutor();

    CustomScreenAdapter adapter =
        CustomScreenAdapter.lazy(
            token,
            () -> token.invokeFactory(f -> f.createScreen(null, null)).orElse(null),
            directExecutor,
            snapshot,
            snap -> true);

    AtomicReference<ScreenResult> resultReceived = new AtomicReference<>();
    adapter.onResult(resultReceived::set);

    // Submit open on Thread A (executes synchronously via directExecutor on Thread A)
    Future<?> openFuture = executor.submit(adapter::open);

    assertTrue(factoryEntered.await(5, TimeUnit.SECONDS));

    // Submit teardown on Thread B
    Future<?> teardownFuture = executor.submit(token::teardown);

    releaseFactory.countDown();

    openFuture.get(5, TimeUnit.SECONDS);
    teardownFuture.get(5, TimeUnit.SECONDS);

    assertEquals(1, factoryCalls.get());
    assertEquals(ProviderState.INACTIVE, token.state());
    // Since teardown ran right after factory creation, open either executed before teardown or was
    // safely gated out (openCalls is 0 or 1, never called while inactive)
    assertTrue(openCalls.get() <= 1);
    if (openCalls.get() == 0) {
      // Teardown acquired the gate before open(); adapter aborted with MANUAL cancel
      assertNotNull(resultReceived.get());
      assertEquals(CancelReason.MANUAL, resultReceived.get().cancelReason());
    }
  }

  private static class DirectPlayerExecutor implements PlayerExecutor {
    @Override
    public void execute(Runnable task, Runnable retired) {
      if (task != null) {
        task.run();
      }
    }
  }

  private static class MockInputScreen implements InputScreen {
    @Override
    public void open() {}

    @Override
    public void close() {}

    @Override
    public boolean isOpen() {
      return false;
    }

    @Override
    public void onResult(Consumer<ScreenResult> callback) {}

    @Override
    public void onOpenFailure(Consumer<Throwable> callback) {}
  }
}
