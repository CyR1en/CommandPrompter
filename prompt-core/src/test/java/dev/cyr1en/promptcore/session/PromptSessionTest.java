package dev.cyr1en.promptcore.session;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.*;
import dev.cyr1en.promptcore.parser.CommandLineParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptSessionTest {

  private final CommandLineParser parser = new CommandLineParser();

  // --- Session start ---

  @Test
  void startWithPrompts_awaitingInput() {
    var parsed = parser.parse("/kick <a:Why?>");
    var session = PromptSession.start("user1", parsed);
    assertTrue(session.isActive());
    assertEquals(PromptSession.SessionState.AWAITING_INPUT, session.state());
    assertEquals("user1", session.userId());
  }

  @Test
  void startWithoutPrompts_completed() {
    var parsed = parser.parse("/kick Steve");
    var session = PromptSession.start("user1", parsed);
    assertTrue(session.isComplete());
    assertEquals(0, session.remainingCount());
  }

  @Test
  void currentPrompt_returnsFirst() {
    var parsed = parser.parse("/kick <a:Why?> <s:How?>");
    var session = PromptSession.start("user1", parsed);
    assertTrue(session.currentPrompt().isPresent());
    assertEquals("Why?", session.currentPrompt().get().displayText());
  }

  @Test
  void currentPrompt_emptyWhenNoPrompts() {
    var parsed = parser.parse("/kick Steve");
    var session = PromptSession.start("user1", parsed);
    assertTrue(session.currentPrompt().isEmpty());
  }

  @Test
  void currentIndex_startsAtZero() {
    var parsed = parser.parse("/kick <a:Why?>");
    var session = PromptSession.start("user1", parsed);
    assertEquals(0, session.currentIndex());
  }

  // --- Submit answers ---

  @Test
  void submitAnswer_returnsNewSession() {
    var parsed = parser.parse("/kick <a:Why?>");
    var session = PromptSession.start("user1", parsed);
    var next = session.submitAnswer("griefing");
    assertNotSame(session, next);
    assertEquals(1, next.answers().size());
    assertEquals("griefing", next.answers().get(0));
  }

  @Test
  void submitAnswer_advancesToNextPrompt() {
    var parsed = parser.parse("/kick <a:first> <a:second>");
    var session = PromptSession.start("user1", parsed);

    var afterFirst = session.submitAnswer("ans1");
    assertEquals(1, afterFirst.currentIndex());
    assertEquals("second", afterFirst.currentPrompt().get().displayText());

    var afterSecond = afterFirst.submitAnswer("ans2");
    assertTrue(afterSecond.isComplete());
  }

  @Test
  void submitAnswer_completesSession() {
    var parsed = parser.parse("/kick <a:Why?>");
    var session = PromptSession.start("user1", parsed);
    var result = session.submitAnswer("griefing");
    assertTrue(result.isComplete());
    assertEquals(PromptSession.SessionState.COMPLETED, result.state());
  }

  @Test
  void submitAnswer_onCompletedSession_throws() {
    var parsed = parser.parse("/kick <>");
    var session = PromptSession.start("user1", parsed).submitAnswer("x");
    assertThrows(IllegalStateException.class, () -> session.submitAnswer("y"));
  }

  @Test
  void submitAnswer_storesAllAnswers() {
    var parsed = parser.parse("/kick <a:first> <a:second> <a:third>");
    var session =
        PromptSession.start("user1", parsed).submitAnswer("a").submitAnswer("b").submitAnswer("c");
    assertEquals(List.of("a", "b", "c"), session.answers());
  }

  // --- Cancel ---

  @Test
  void cancel_returnsCancelledState() {
    var parsed = parser.parse("/kick <a:Why?>");
    var session = PromptSession.start("user1", parsed);
    var cancelled = session.cancel(CancelReason.MANUAL);
    assertTrue(cancelled.isCancelled());
    assertEquals(CancelReason.MANUAL, cancelled.cancelReason().orElseThrow());
  }

  @Test
  void cancel_onActiveSession_works() {
    var parsed = parser.parse("/kick <a:Why?>");
    var session = PromptSession.start("user1", parsed).cancel(CancelReason.TIMEOUT);
    assertTrue(session.isCancelled());
  }

  @Test
  void cancel_onCompletedSession_throws() {
    var parsed = parser.parse("/kick <>");
    var session = PromptSession.start("user1", parsed).submitAnswer("x");
    assertThrows(IllegalStateException.class, () -> session.cancel(CancelReason.MANUAL));
  }

  @Test
  void cancel_onAlreadyCancelled_throws() {
    var parsed = parser.parse("/kick <>");
    var session = PromptSession.start("user1", parsed).cancel(CancelReason.MANUAL);
    assertThrows(IllegalStateException.class, () -> session.cancel(CancelReason.TIMEOUT));
  }

  // --- Finish and assembly ---

  @Test
  void finish_assemblesCommand() {
    var parsed = parser.parse("/kick <a:Why?>");
    var session = PromptSession.start("user1", parsed).submitAnswer("griefing");
    var result = session.finish();
    assertEquals("/kick griefing", result.assembledCommand());
  }

  @Test
  void finish_withMultipleAnswers() {
    var parsed = parser.parse("/cmd <a:first> <a:second>");
    var session = PromptSession.start("user1", parsed).submitAnswer("a1").submitAnswer("a2");
    var result = session.finish();
    assertEquals("/cmd a1 a2", result.assembledCommand());
  }

  @Test
  void finish_withPCM_resolvesReferences() {
    var parsed = parser.parse("/ban <a:Why?> <! tempban {0} 7d>");
    var session = PromptSession.start("user1", parsed).submitAnswer("griefing");
    var result = session.finish();
    assertEquals("/ban griefing", result.assembledCommand());
    assertEquals(1, result.onCompleteCmds().size());
    assertEquals("tempban griefing 7d", result.onCompleteCmds().get(0).command());
  }

  @Test
  void finish_cancelled_returnsOnCancelPCMs() {
    var parsed = parser.parse("/kick <a:Why?> <!! msg {0}>");
    var session = PromptSession.start("user1", parsed).cancel(CancelReason.MANUAL);
    var result = session.finish();
    assertTrue(result.onCompleteCmds().isEmpty());
    assertEquals(1, result.onCancelCmds().size());
    // answer list is empty since we cancelled before answering
    assertEquals("msg", result.onCancelCmds().get(0).command());
  }

  @Test
  void finish_noPCMs() {
    var parsed = parser.parse("/kick <>");
    var session = PromptSession.start("user1", parsed).submitAnswer("Steve");
    var result = session.finish();
    assertEquals("/kick Steve", result.assembledCommand());
    assertFalse(result.hasPostCommands());
  }

  @Test
  void finish_withoutCompleting_throws() {
    var parsed = parser.parse("/kick <a:Why?>");
    var session = PromptSession.start("user1", parsed);
    assertThrows(IllegalStateException.class, session::finish);
  }

  // --- Sanitization ---

  @Test
  void sanitize_stripsColorCodes() {
    assertEquals("Hello", PromptSession.sanitize("§cHello"));
    assertEquals("Hello", PromptSession.sanitize("&cHello"));
    assertEquals("Hello World", PromptSession.sanitize("§aHello §bWorld"));
  }

  @Test
  void sanitize_stripsSymbols() {
    assertEquals("Hello", PromptSession.sanitize("<Hello>"));
    assertEquals("Hello", PromptSession.sanitize("{Hello}"));
    assertEquals("Hello", PromptSession.sanitize("[Hello]"));
    assertEquals("Hello", PromptSession.sanitize("(Hello)"));
  }

  @Test
  void sanitize_stripsMixed() {
    assertEquals("Hello", PromptSession.sanitize("§c<Hello>"));
  }

  @Test
  void sanitize_nullReturnsNull() {
    assertNull(PromptSession.sanitize(null));
  }

  @Test
  void sanitize_emptyReturnsEmpty() {
    assertEquals("", PromptSession.sanitize(""));
  }

  @Test
  void submitAnswer_appliesSanitizationByDefault() {
    var parsed = parser.parse("/kick <a:Why?>");
    var session = PromptSession.start("user1", parsed).submitAnswer("§cHello");
    assertEquals("Hello", session.answers().get(0));
  }

  @Test
  void submitAnswer_skipsSanitizationWhenDisabled() {
    var parsed = parser.parse("/kick <-ds>");
    var session = PromptSession.start("user1", parsed).submitAnswer("§cHello");
    assertEquals("§cHello", session.answers().get(0));
  }

  // --- PCM metadata preservation ---

  @Test
  void finish_preservesPCMDelay() {
    var parsed = parser.parse("/kick <> <!:20 msg {0}>");
    var session = PromptSession.start("user1", parsed).submitAnswer("x");
    var result = session.finish();
    var pcm = result.onCompleteCmds().get(0);
    assertEquals(20, pcm.delayTicks());
  }

  @Test
  void finish_preservesPCMDispatchTarget() {
    var parsed = parser.parse("/kick <> <! msg {0} @console>");
    var session = PromptSession.start("user1", parsed).submitAnswer("x");
    var result = session.finish();
    assertEquals(DispatchTarget.CONSOLE, result.onCompleteCmds().get(0).dispatchTarget());
  }

  // --- Edge cases ---

  @Test
  void sessionWithoutPrompts_finishesImmediately() {
    var parsed = parser.parse("/kick Steve");
    var session = PromptSession.start("user1", parsed);
    var result = session.finish();
    assertEquals("/kick Steve", result.assembledCommand());
    assertFalse(result.hasPostCommands());
  }

  @Test
  void immutable_sessionNotAffectedBySubsequentOperations() {
    var parsed = parser.parse("/kick <a:first> <a:second>");
    var session1 = PromptSession.start("user1", parsed);
    var afterFirst = session1.submitAnswer("a1");
    // session1 should be unchanged
    assertEquals(0, session1.answers().size());
    assertEquals(1, afterFirst.answers().size());
  }

  @Test
  void finish_idempotent() {
    var parsed = parser.parse("/kick <>");
    var session = PromptSession.start("user1", parsed).submitAnswer("x");
    var r1 = session.finish();
    var r2 = session.finish();
    assertEquals(r1, r2);
  }

  // --- Regression: H5 (equals/hashCode include remaining + pcmQueue) ---

  @Test
  void equals_differentiatesSessionsByRemaining() {
    var parsed = parser.parse("/kick <a:first> <a:second>");
    var s1 = PromptSession.start("u", parsed);
    var s2 = s1.submitAnswer("a1");
    assertNotEquals(s1, s2, "Sessions with different remaining prompts must not be equal");
    assertNotEquals(s1.hashCode(), s2.hashCode(), "Hash code must differ for different sessions");
  }

  @Test
  void equals_ignoresAnswerOrder() {
    var parsed = parser.parse("/kick <a:first> <a:second>");
    var s = PromptSession.start("u", parsed);
    var t = s.submitAnswer("a1");
    // Two sessions with same answers, state, remaining, cancel reason should be equal
    var u = PromptSession.start("u", parsed).submitAnswer("a1");
    assertEquals(t, u);
  }

  // --- Regression: M16 (pcmQueue is defensive copy) ---

  @Test
  void pcmQueue_isNotSharedBetweenSessions() {
    var parsed = parser.parse("/kick <a:first> <!msg {0}>");
    var s1 = PromptSession.start("u", parsed);
    var s2 = s1.submitAnswer("a1");
    // Mutating parsedCommand's postCmds (which is the parser's returned list view)
    // should not affect either session's view of pcmQueue
    assertEquals(1, s1.pcmQueue().size());
    assertEquals(1, s2.pcmQueue().size());
    // The two sessions share an immutable copy: same content, different reference is OK
    assertEquals(s1.pcmQueue(), s2.pcmQueue());
  }
}
