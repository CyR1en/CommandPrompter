package dev.cyr1en.promptpaper.screen.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.CancelReason;
import dev.cyr1en.promptui.ScreenResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmationPromptScreenTest {

    private FakeConfirmationView primaryView;
    private FakeConfirmationView fallbackView;
    private AtomicReference<ScreenResult> resultRef;
    private AtomicInteger resultCount;

    @BeforeEach
    void setUp() {
        primaryView = new FakeConfirmationView();
        fallbackView = new FakeConfirmationView();
        resultRef = new AtomicReference<>();
        resultCount = new AtomicInteger();
    }

    @Test
    void confirmOutcomeDeliversAnswerExactlyOnce() {
        var screen = new ConfirmationPromptScreen(primaryView, "yes", "no");
        screen.onResult(result -> {
            resultRef.set(result);
            resultCount.incrementAndGet();
        });

        assertFalse(screen.isOpen());
        screen.open();
        assertTrue(screen.isOpen());
        assertTrue(primaryView.isOpen());

        primaryView.emitOutcome(ConfirmationOutcome.confirmed());

        assertFalse(screen.isOpen());
        assertFalse(primaryView.isOpen());
        assertEquals(1, resultCount.get());
        assertNotNull(resultRef.get());
        assertEquals(ScreenResult.Outcome.SUCCESS, resultRef.get().outcome());
        assertEquals("yes", resultRef.get().answer());
        assertEquals(ConfirmationOutcome.confirmed(), screen.lastOutcome().orElse(null));

        // Duplicate outcome should be ignored
        primaryView.emitOutcome(ConfirmationOutcome.confirmed());
        assertEquals(1, resultCount.get());
    }

    @Test
    void declineOutcomeDeliversCancelByDefault() {
        var screen = new ConfirmationPromptScreen(primaryView);
        screen.onResult(result -> {
            resultRef.set(result);
            resultCount.incrementAndGet();
        });

        screen.open();
        primaryView.emitOutcome(ConfirmationOutcome.declined());

        assertEquals(1, resultCount.get());
        assertNotNull(resultRef.get());
        assertTrue(resultRef.get().cancelled());
        assertEquals(CancelReason.GUI_EXIT, resultRef.get().cancelReason());
        assertEquals(ConfirmationOutcome.declined(), screen.lastOutcome().orElse(null));
    }

    @Test
    void declineOutcomeDeliversAnswerWhenConfigured() {
        var screen = new ConfirmationPromptScreen(List.of(primaryView), "accept", "reject", false);
        screen.onResult(result -> {
            resultRef.set(result);
            resultCount.incrementAndGet();
        });

        screen.open();
        primaryView.emitOutcome(ConfirmationOutcome.declined());

        assertEquals(1, resultCount.get());
        assertNotNull(resultRef.get());
        assertFalse(resultRef.get().cancelled());
        assertEquals("reject", resultRef.get().answer());
    }

    @Test
    void cancelOutcomeDeliversCancelReason() {
        var screen = new ConfirmationPromptScreen(primaryView);
        screen.onResult(result -> {
            resultRef.set(result);
            resultCount.incrementAndGet();
        });

        screen.open();
        primaryView.emitOutcome(ConfirmationOutcome.cancelled(CancelReason.TIMEOUT));

        assertEquals(1, resultCount.get());
        assertNotNull(resultRef.get());
        assertTrue(resultRef.get().cancelled());
        assertEquals(CancelReason.TIMEOUT, resultRef.get().cancelReason());
        assertEquals(ConfirmationOutcome.cancelled(CancelReason.TIMEOUT), screen.lastOutcome().orElse(null));
    }

    @Test
    void programmaticCloseInvalidatesCallbacksAndDeliversNothing() {
        var screen = new ConfirmationPromptScreen(primaryView);
        screen.onResult(result -> {
            resultRef.set(result);
            resultCount.incrementAndGet();
        });

        screen.open();
        assertTrue(screen.isOpen());
        assertTrue(primaryView.isOpen());

        screen.close();

        assertFalse(screen.isOpen());
        assertFalse(primaryView.isOpen());
        assertEquals(0, resultCount.get());
        assertNull(resultRef.get());

        // Any subsequent outcome from view delivers nothing
        primaryView.emitOutcome(ConfirmationOutcome.confirmed());
        assertEquals(0, resultCount.get());
    }

    @Test
    void fallbackAdvancesWhenPrimaryFailsSynchronously() {
        primaryView.setThrowOnOpen(new RuntimeException("Primary view unsupported"));
        var screen = new ConfirmationPromptScreen(primaryView, fallbackView);
        screen.onResult(result -> {
            resultRef.set(result);
            resultCount.incrementAndGet();
        });

        screen.open();

        assertFalse(primaryView.isOpen());
        assertTrue(fallbackView.isOpen());
        assertEquals(fallbackView, screen.activeView().orElse(null));

        fallbackView.emitOutcome(ConfirmationOutcome.confirmed());
        assertEquals(1, resultCount.get());
        assertEquals("confirm", resultRef.get().answer());
    }

    @Test
    void fallbackAdvancesWhenPrimaryFailsAsynchronously() {
        primaryView.setFailAsyncOnOpen(new RuntimeException("Async open failed"));
        var screen = new ConfirmationPromptScreen(primaryView, fallbackView);
        screen.onResult(result -> {
            resultRef.set(result);
            resultCount.incrementAndGet();
        });

        screen.open();

        assertFalse(primaryView.isOpen());
        assertTrue(fallbackView.isOpen());
        assertEquals(fallbackView, screen.activeView().orElse(null));

        fallbackView.emitOutcome(ConfirmationOutcome.declined());
        assertEquals(1, resultCount.get());
        assertTrue(resultRef.get().cancelled());
    }

    @Test
    void allViewsFailNotifiesOpenFailureCallback() {
        primaryView.setThrowOnOpen(new RuntimeException("Primary error"));
        fallbackView.setThrowOnOpen(new RuntimeException("Fallback error"));

        var screen = new ConfirmationPromptScreen(primaryView, fallbackView);
        var failureRef = new AtomicReference<Throwable>();
        screen.onOpenFailure(failureRef::set);
        screen.onResult(result -> resultCount.incrementAndGet());

        screen.open();

        assertFalse(screen.isOpen());
        assertNotNull(failureRef.get());
        assertEquals("Fallback error", failureRef.get().getMessage());
        assertEquals(0, resultCount.get());
    }

    @Test
    void metadataAndAccessors() {
        var screen = new ConfirmationPromptScreen(
                List.of(primaryView, fallbackView), "OK", "NO", true);

        assertEquals("OK", screen.confirmValue());
        assertEquals("NO", screen.declineValue());
        assertTrue(screen.isDeclinedAsCancel());
        assertEquals(2, screen.fallbackChain().size());
        assertTrue(screen.lastOutcome().isEmpty());
        assertTrue(screen.activeView().isEmpty());

        screen.open();
        assertEquals(primaryView, screen.activeView().orElse(null));
    }

    @Test
    void repeatedCloseIsNoOp() {
        var screen = new ConfirmationPromptScreen(primaryView);
        assertFalse(screen.isOpen());
        screen.close();
        assertFalse(screen.isOpen());

        screen.open();
        assertTrue(screen.isOpen());
        screen.close();
        assertFalse(screen.isOpen());
        screen.close();
        assertFalse(screen.isOpen());
    }

    private static class FakeConfirmationView implements ConfirmationView {
        private Consumer<ConfirmationOutcome> callback;
        private Consumer<Throwable> failureCallback;
        private boolean open;
        private Throwable throwOnOpen;
        private Throwable failAsyncOnOpen;

        public void setThrowOnOpen(Throwable t) {
            this.throwOnOpen = t;
        }

        public void setFailAsyncOnOpen(Throwable t) {
            this.failAsyncOnOpen = t;
        }

        @Override
        public void open(Consumer<ConfirmationOutcome> callback) {
            if (throwOnOpen != null) {
                if (throwOnOpen instanceof RuntimeException re) throw re;
                throw new RuntimeException(throwOnOpen);
            }
            this.callback = callback;
            this.open = true;
            if (failAsyncOnOpen != null && failureCallback != null) {
                this.open = false;
                failureCallback.accept(failAsyncOnOpen);
            }
        }

        @Override
        public void close() {
            this.open = false;
            this.callback = null;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void onOpenFailure(Consumer<Throwable> failureCallback) {
            this.failureCallback = failureCallback;
        }

        public void emitOutcome(ConfirmationOutcome outcome) {
            if (callback != null) {
                callback.accept(outcome);
            }
        }
    }
}
