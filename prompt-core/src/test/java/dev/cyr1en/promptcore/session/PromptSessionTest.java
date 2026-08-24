package dev.cyr1en.promptcore.session;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptcore.*;
import dev.cyr1en.promptcore.parser.CommandLineParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptSessionTest {

  private final CommandLineParser parser = new CommandLineParser();

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

  @Test
  void finish_assemblesCommand() {
    var parsed = parser.parse("/kick <a:Why?>");
    var session = PromptSession.start("user1", parsed).submitAnswer("griefing");
    var result = session.finish();
    assertEquals("/kick \"griefing\"", result.assembledCommand());
  }

  @Test
  void finish_withMultipleAnswers() {
    var parsed = parser.parse("/cmd <a:first> <a:second>");
    var session = PromptSession.start("user1", parsed).submitAnswer("a1").submitAnswer("a2");
    var result = session.finish();
    assertEquals("/cmd \"a1\" \"a2\"", result.assembledCommand());
  }

  @Test
  void finish_withPCM_preservesOriginalPCMUnchanged() {
    var parsed = parser.parse("/ban <a:Why?> <! tempban {0} 7d>");
    var session = PromptSession.start("user1", parsed).submitAnswer("griefing");
    var result = session.finish();
    assertEquals("/ban \"griefing\"", result.assembledCommand());
    assertEquals(1, result.onCompleteCmds().size());
    assertEquals("tempban {0} 7d", result.onCompleteCmds().get(0).command());
    assertArrayEquals(new int[] {0}, result.onCompleteCmds().get(0).answerIndices());
    assertEquals(List.of("griefing"), result.answers());
  }

  @Test
  void compoundAnswersAppendToPriorHistoryAndPreservePCMOriginals() {
    var parsed =
        parser.parse("/ban <a:Reason> <d:text:Name && d:num[0,24]:Days> <!audit {0} {1} {2}>");
    var session = PromptSession.start("user1", parsed).submitAnswer("griefing");
    var completed = session.submitAnswers(List.of("Steve", "7"));

    assertEquals(List.of("griefing", "Steve", "7"), completed.answers());
    assertEquals("/ban \"griefing\" \"Steve\" \"7\"", completed.finish().assembledCommand());
    assertEquals("audit {0} {1} {2}", completed.finish().onCompleteCmds().get(0).command());
    assertArrayEquals(
        new int[] {0, 1, 2}, completed.finish().onCompleteCmds().get(0).answerIndices());
  }

  @Test
  void pcmOriginalTemplatePreservedWhenAnswersContainPlaceholders() {
    var parsed = parser.parse("/cmd <a:first -ds> <a:second> <!log {0} {1}>");
    var completed =
        PromptSession.start("user1", parsed).submitAnswer("{1}").submitAnswer("literal");

    var result = completed.finish();
    assertEquals("log {0} {1}", result.onCompleteCmds().get(0).command());
    assertArrayEquals(new int[] {0, 1}, result.onCompleteCmds().get(0).answerIndices());
    assertEquals(List.of("{1}", "literal"), result.answers());
  }

  @Test
  void compoundAnswersRejectNullElementsBeforeSanitization() {
    var parsed = parser.parse("/cmd <d:text:One && d:text:Two>");
    var session = PromptSession.start("user1", parsed);
    var answers = new java.util.ArrayList<String>();
    answers.add("ok");
    answers.add(null);

    assertThrows(NullPointerException.class, () -> session.submitAnswers(answers));
  }

  @Test
  void escapedDelimiterInsidePromptRemainsReplaceable() {
    var parsed = parser.parse("/ask <a:Why? \\>>");
    var completed = PromptSession.start("user1", parsed).submitAnswer("because");

    assertEquals("/ask \"because\"", completed.finish().assembledCommand());
  }

  @Test
  void finish_cancelled_returnsOnCancelPCMs() {
    var parsed = parser.parse("/kick <a:Why?> <!! msg {0}>");
    var session = PromptSession.start("user1", parsed).cancel(CancelReason.MANUAL);
    var result = session.finish();
    assertTrue(result.onCompleteCmds().isEmpty());
    assertEquals(1, result.onCancelCmds().size());
    // Inception PCM template is preserved byte-for-byte; answers list is empty.
    assertEquals("msg {0}", result.onCancelCmds().get(0).command());
    assertArrayEquals(new int[] {0}, result.onCancelCmds().get(0).answerIndices());
    assertTrue(result.answers().isEmpty());
  }

  @Test
  void finish_noPCMs() {
    var parsed = parser.parse("/kick <>");
    var session = PromptSession.start("user1", parsed).submitAnswer("Steve");
    var result = session.finish();
    assertEquals("/kick \"Steve\"", result.assembledCommand());
    assertFalse(result.hasPostCommands());
  }

  @Test
  void finish_withoutCompleting_throws() {
    var parsed = parser.parse("/kick <a:Why?>");
    var session = PromptSession.start("user1", parsed);
    assertThrows(IllegalStateException.class, session::finish);
  }

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
    // session1 remains unchanged.
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
    // Identical sessions must be equal.
    var u = PromptSession.start("u", parsed).submitAnswer("a1");
    assertEquals(t, u);
  }

  @Test
  void pcmQueue_isNotSharedBetweenSessions() {
    var parsed = parser.parse("/kick <a:first> <!msg {0}>");
    var s1 = PromptSession.start("u", parsed);
    var s2 = s1.submitAnswer("a1");
    // Mutating postCmds does not affect pcmQueue.
    assertEquals(1, s1.pcmQueue().size());
    assertEquals(1, s2.pcmQueue().size());
    // Sessions share an immutable copy.
    assertEquals(s1.pcmQueue(), s2.pcmQueue());
  }

  // ====================================================================
  // Issue #78 / #92: per-prompt answer arity (dialog presets with 0/N inputs)
  // ====================================================================

  /**
   * A zero-answer preset (batch [] with expected 0) consumes one prompt, advances the prompt
   * ordinal, and adds nothing to the flat answer list. The final command removes the preset and
   * inserts the next prompt's answer.
   */
  @Test
  void zeroAnswerPresetAdvancesIndexAndRemovesTagFromAssembly() {
    var parsed = parser.parse("/cmd <@my_id><a:next>");
    var afterPreset = PromptSession.start("u", parsed).submitAnswers(List.of(), 0);

    assertEquals(1, afterPreset.currentIndex());
    assertEquals(List.of(), afterPreset.answers());
    assertEquals(List.of(0), afterPreset.submittedAnswerCounts());
    assertEquals("next", afterPreset.currentPrompt().get().displayText());

    var completed = afterPreset.submitAnswer("c");
    assertEquals(List.of("c"), completed.answers());
    assertEquals(List.of(0, 1), completed.submittedAnswerCounts());
    assertEquals("/cmd \"c\"", completed.finish().assembledCommand());
  }

  /** A zero-answer preset must not shift {0} / post-command answer indexes. */
  @Test
  void zeroAnswerPresetDoesNotShiftPcmIndexes() {
    var parsed = parser.parse("/cmd <@my_id><a:next> <!audit {0}>");
    var completed = PromptSession.start("u", parsed).submitAnswers(List.of(), 0).submitAnswer("c");
    assertEquals("/cmd \"c\"", completed.finish().assembledCommand());
    assertEquals(List.of("c"), completed.answers());
    assertEquals("audit {0}", completed.finish().onCompleteCmds().get(0).command());
    assertArrayEquals(new int[] {0}, completed.finish().onCompleteCmds().get(0).answerIndices());
  }

  /**
   * A two-answer preset joins its answers with the existing compound behavior, then the following
   * prompt's answer is appended; flat answers and PCM indexes are real-answer based.
   */
  @Test
  void twoAnswerPresetJoinsThenNormalPromptAppends() {
    var parsed = parser.parse("/cmd <@my_id> <a:next> <!audit {0} {1} {2}>");
    var completed =
        PromptSession.start("u", parsed).submitAnswers(List.of("a", "b"), 2).submitAnswer("c");

    assertEquals(List.of("a", "b", "c"), completed.answers());
    assertEquals(List.of(2, 1), completed.submittedAnswerCounts());
    assertEquals("/cmd \"a\" \"b\" \"c\"", completed.finish().assembledCommand());
    assertEquals("audit {0} {1} {2}", completed.finish().onCompleteCmds().get(0).command());
    assertArrayEquals(
        new int[] {0, 1, 2}, completed.finish().onCompleteCmds().get(0).answerIndices());
  }

  /** Multi-answer presets occupy their flat positions; the following answer is the next index. */
  @Test
  void multiAnswerPresetKeepsFollowingAnswerAtNextIndex() {
    var parsed = parser.parse("/cmd <@my_id> <a:next> <!log {0} {1} {2} {3}>");
    var completed =
        PromptSession.start("u", parsed).submitAnswers(List.of("a", "b", "c"), 3).submitAnswer("d");
    assertEquals(List.of("a", "b", "c", "d"), completed.answers());
    assertEquals("/cmd \"a\" \"b\" \"c\" \"d\"", completed.finish().assembledCommand());
    assertEquals("log {0} {1} {2} {3}", completed.finish().onCompleteCmds().get(0).command());
    assertArrayEquals(
        new int[] {0, 1, 2, 3}, completed.finish().onCompleteCmds().get(0).answerIndices());
  }

  /** The legacy tag-shape inference still works for compound tags (arity = sub-tags). */
  @Test
  void legacyCompoundSubmitRecordsSubTagArity() {
    var parsed = parser.parse("/cmd <d:text:One && d:text:Two> <a:next>");
    var afterCompound = PromptSession.start("u", parsed).submitAnswers(List.of("x", "y"));
    assertEquals(1, afterCompound.currentIndex());
    assertEquals(List.of(2), afterCompound.submittedAnswerCounts());
    assertEquals(List.of("x", "y"), afterCompound.answers());
    assertEquals("next", afterCompound.currentPrompt().get().displayText());
  }

  /** currentIndex is the consumed-prompt ordinal, not the flat answer count. */
  @Test
  void currentIndexIsPromptOrdinalNotAnswerCount() {
    var parsed = parser.parse("/cmd <d:text:A && d:text:B> <a:next>");
    var afterCompound = PromptSession.start("u", parsed).submitAnswers(List.of("x", "y"), 2);
    assertEquals(1, afterCompound.currentIndex());
    assertEquals(1, afterCompound.remainingCount());
  }

  @Test
  void batchZeroAnswersIsValidAndCompletes() {
    var parsed = parser.parse("/cmd <@my_id>");
    var completed = PromptSession.start("u", parsed).submitAnswers(List.of(), 0);
    assertTrue(completed.isComplete());
    assertEquals(List.of(), completed.answers());
    assertEquals(List.of(0), completed.submittedAnswerCounts());
    assertEquals("/cmd", completed.finish().assembledCommand());
  }

  @Test
  void batchRejectsNegativeExpectedCount() {
    var parsed = parser.parse("/cmd <@my_id>");
    var session = PromptSession.start("u", parsed);
    assertThrows(IllegalArgumentException.class, () -> session.submitAnswers(List.of("a"), -1));
  }

  @Test
  void batchRejectsSizeMismatch() {
    var parsed = parser.parse("/cmd <@my_id>");
    var session = PromptSession.start("u", parsed);
    assertThrows(IllegalArgumentException.class, () -> session.submitAnswers(List.of("a"), 2));
    assertThrows(IllegalArgumentException.class, () -> session.submitAnswers(List.of(), 1));
    assertThrows(IllegalArgumentException.class, () -> session.submitAnswers(List.of("a", "b"), 1));
  }

  @Test
  void batchRejectsNullElements() {
    var parsed = parser.parse("/cmd <@my_id>");
    var session = PromptSession.start("u", parsed);
    var withNull = new java.util.ArrayList<String>();
    withNull.add("a");
    withNull.add(null);
    assertThrows(NullPointerException.class, () -> session.submitAnswers(withNull, 2));
  }

  @Test
  void batchSanitizesEveryAnswerExactlyOnce() {
    var parsed = parser.parse("/cmd <@my_id>");
    var completed = PromptSession.start("u", parsed).submitAnswers(List.of("§cA", "&bB"), 2);
    assertEquals(List.of("A", "B"), completed.answers());
  }

  @Test
  void batchAppliesCurrentTagSanitizePolicy() {
    // Non-compound preset tags sanitize by default; -ds inline tags do not.
    var parsed = parser.parse("/cmd <@my_id>");
    var sanitized = PromptSession.start("u", parsed).submitAnswers(List.of("§cA"), 1);
    assertEquals("A", sanitized.answers().get(0));

    var parsedDs = parser.parse("/cmd <-ds>");
    var unsanitized = PromptSession.start("u", parsedDs).submitAnswers(List.of("§cA"), 1);
    assertEquals("§cA", unsanitized.answers().get(0));
  }

  @Test
  void buildPartialCommandUsesRecordedArities() {
    var parsed = parser.parse("/cmd <@my_id> <a:next> tail");
    var afterPreset = PromptSession.start("u", parsed).submitAnswers(List.of(), 0);
    // The preset is dropped; the current (unanswered) prompt truncates the rest.
    assertEquals("/cmd ", afterPreset.buildPartialCommand());

    var afterTwo = PromptSession.start("u", parsed).submitAnswers(List.of("a", "b"), 2);
    assertEquals("/cmd \"a\" \"b\" ", afterTwo.buildPartialCommand());
  }

  // ====================================================================
  // Monotonic Generation Tracking
  // ====================================================================

  @Test
  void initialSessionHasGenerationZero() {
    var parsed = parser.parse("/test <a:p1> <a:p2>");
    var session = PromptSession.start("u1", parsed);
    assertEquals(0L, session.generation());
  }

  @Test
  void submitAnswerIncrementsGenerationMonotonically() {
    var parsed = parser.parse("/test <a:p1> <a:p2>");
    var s0 = PromptSession.start("u1", parsed);
    assertEquals(0L, s0.generation());

    var s1 = s0.submitAnswer("ans1");
    assertEquals(1L, s1.generation());

    var s2 = s1.submitAnswer("ans2");
    assertEquals(2L, s2.generation());
  }

  @Test
  void submitAnswersIncrementsGenerationMonotonically() {
    var parsed = parser.parse("/test <d:text:p1 && d:text:p2>");
    var s0 = PromptSession.start("u1", parsed);
    assertEquals(0L, s0.generation());

    var s1 = s0.submitAnswers(List.of("a", "b"));
    assertEquals(1L, s1.generation());
  }

  @Test
  void cancelIncrementsGenerationMonotonically() {
    var parsed = parser.parse("/test <a:p1>");
    var s0 = PromptSession.start("u1", parsed);
    assertEquals(0L, s0.generation());

    var s1 = s0.cancel(CancelReason.MANUAL);
    assertEquals(1L, s1.generation());
  }

  @Test
  void cancelReasonErrorSupported() {
    var parsed = parser.parse("/test <a:p1>");
    var session = PromptSession.start("u1", parsed).cancel(CancelReason.ERROR);
    assertEquals(CancelReason.ERROR, session.cancelReason().orElseThrow());
  }

  // ====================================================================
  // SEC-09: C0 Controls & Answer Length Hard Cap
  // ====================================================================

  @Test
  void sec09_c0ControlsAreStrippedAtIngestion() {
    var parsed = parser.parse("/say <a:Msg -ds>");
    var session =
        PromptSession.start("u1", parsed).submitAnswer("Hello\nWorld\r\n\u0000\u0007\u001B");
    assertEquals("HelloWorld", session.answers().get(0));
  }

  @Test
  void sec09_c0ControlsStrippedInBatchSubmission() {
    var parsed = parser.parse("/say <d:text:Msg1 && d:text:Msg2 -ds>");
    var session =
        PromptSession.start("u1", parsed).submitAnswers(List.of("Line1\nLine2", "Foo\u0000Bar"));
    assertEquals("Line1Line2", session.answers().get(0));
    assertEquals("FooBar", session.answers().get(1));
  }

  @Test
  void answerMaxLengthEnforcedAtHardCap() {
    var parsed = parser.parse("/say <a:Msg -ds>");
    var session = PromptSession.start("u1", parsed);

    var exactly1024 = "a".repeat(1024);
    var accepted = session.submitAnswer(exactly1024);
    assertEquals(1024, accepted.answers().get(0).length());

    var tooLong1025 = "a".repeat(1025);
    assertThrows(IllegalArgumentException.class, () -> session.submitAnswer(tooLong1025));
  }

  @Test
  void answerMaxLengthEnforcedInBatchSubmission() {
    var parsed = parser.parse("/say <d:text:Msg1 && d:text:Msg2 -ds>");
    var session = PromptSession.start("u1", parsed);

    var tooLong = "a".repeat(1025);
    assertThrows(
        IllegalArgumentException.class, () -> session.submitAnswers(List.of("valid", tooLong)));
  }

  @Test
  void pcmMetadataPreserved_whenAnswerContainsPlayerPlaceholder() {
    var parsed = parser.parse("/give <a:target -ds> <!give {0} diamond 1>");
    var session = PromptSession.start("u1", parsed).submitAnswer("{player}");
    var result = session.finish();

    assertEquals("/give \"{player}\"", result.assembledCommand());
    assertEquals(List.of("{player}"), result.answers());
    assertEquals(1, result.onCompleteCmds().size());
    assertEquals("give {0} diamond 1", result.onCompleteCmds().get(0).command());
    assertArrayEquals(new int[] {0}, result.onCompleteCmds().get(0).answerIndices());
    assertTrue(result.onCancelCmds().isEmpty());
  }

  @Test
  void pcmMetadataPreserved_whenAnswerContainsSecondaryTemplateSyntax() {
    var parsed = parser.parse("/cmd <a:arg -ds> <!audit {0}>");
    var session = PromptSession.start("u1", parsed).submitAnswer("{1:upper}");
    var result = session.finish();

    assertEquals("/cmd \"{1:upper}\"", result.assembledCommand());
    assertEquals(List.of("{1:upper}"), result.answers());
    assertEquals(1, result.onCompleteCmds().size());
    assertEquals("audit {0}", result.onCompleteCmds().get(0).command());
    assertArrayEquals(new int[] {0}, result.onCompleteCmds().get(0).answerIndices());
    assertTrue(result.onCancelCmds().isEmpty());
  }

  @Test
  void pcmMetadataPreserved_whenAnswerContainsPapiPlaceholder() {
    var parsed =
        parser.parse("/eco <a:target -ds> <!msg {0} balance: %vault_eco_balance% @console>");
    var session = PromptSession.start("u1", parsed).submitAnswer("%player_name%");
    var result = session.finish();

    assertEquals("/eco \"%player_name%\"", result.assembledCommand());
    assertEquals(List.of("%player_name%"), result.answers());
    assertEquals(1, result.onCompleteCmds().size());
    var pcm = result.onCompleteCmds().get(0);
    assertEquals("msg {0} balance: %vault_eco_balance%", pcm.command());
    assertEquals(DispatchTarget.CONSOLE, pcm.dispatchTarget());
    assertArrayEquals(new int[] {0}, pcm.answerIndices());
    assertTrue(result.onCancelCmds().isEmpty());
  }

  @Test
  void pcmMetadataPreserved_whenAnswerContainsC0Controls() {
    var parsed = parser.parse("/say <a:msg -ds> <!log {0}>");
    var session = PromptSession.start("u1", parsed).submitAnswer("Hello\u0000\u0007\u001BWorld");
    var result = session.finish();

    assertEquals("/say \"HelloWorld\"", result.assembledCommand());
    assertEquals(List.of("HelloWorld"), result.answers());
    assertEquals(1, result.onCompleteCmds().size());
    assertEquals("log {0}", result.onCompleteCmds().get(0).command());
    assertArrayEquals(new int[] {0}, result.onCompleteCmds().get(0).answerIndices());
    assertTrue(result.onCancelCmds().isEmpty());
  }

  @Test
  void pcmMetadataPreserved_whenAnswerContainsTagsAndSemicolons() {
    var parsed = parser.parse("/execute <a:payload -ds> <!log {0}; echo complete>");
    var session =
        PromptSession.start("u1", parsed).submitAnswer("<a:injected>; /op hacker; <d:test>");
    var result = session.finish();

    assertEquals("/execute \"<a:injected>; /op hacker; <d:test>\"", result.assembledCommand());
    assertEquals(List.of("<a:injected>; /op hacker; <d:test>"), result.answers());
    assertEquals(1, result.onCompleteCmds().size());
    assertEquals("log {0}; echo complete", result.onCompleteCmds().get(0).command());
    assertArrayEquals(new int[] {0}, result.onCompleteCmds().get(0).answerIndices());
    assertTrue(result.onCancelCmds().isEmpty());
  }

  @Test
  void pcmMetadataPreserved_lifecycleFilteringSeparatesCompleteAndCancelPCMs() {
    var parsed =
        parser.parse(
            "/action <a:target> <!/say complete {0} @player> <!:5 /eco reward {0} @console> <!!/msg {0} cancelled>");

    var completedSession = PromptSession.start("u1", parsed).submitAnswer("Steve");
    var completeResult = completedSession.finish();
    assertEquals("/action \"Steve\"", completeResult.assembledCommand());
    assertEquals(List.of("Steve"), completeResult.answers());
    assertEquals(2, completeResult.onCompleteCmds().size());
    assertTrue(completeResult.onCancelCmds().isEmpty());

    var pcm0 = completeResult.onCompleteCmds().get(0);
    assertEquals("/say complete {0}", pcm0.command());
    assertEquals(0, pcm0.delayTicks());
    assertEquals(DispatchTarget.PLAYER, pcm0.dispatchTarget());
    assertArrayEquals(new int[] {0}, pcm0.answerIndices());

    var pcm1 = completeResult.onCompleteCmds().get(1);
    assertEquals("/eco reward {0}", pcm1.command());
    assertEquals(5, pcm1.delayTicks());
    assertEquals(DispatchTarget.CONSOLE, pcm1.dispatchTarget());
    assertArrayEquals(new int[] {0}, pcm1.answerIndices());

    var cancelledSession = PromptSession.start("u1", parsed).cancel(CancelReason.MANUAL);
    var cancelResult = cancelledSession.finish();
    assertTrue(cancelResult.onCompleteCmds().isEmpty());
    assertEquals(1, cancelResult.onCancelCmds().size());
    var cancelPcm = cancelResult.onCancelCmds().get(0);
    assertEquals("/msg {0} cancelled", cancelPcm.command());
    assertEquals(DispatchTarget.PASSTHROUGH, cancelPcm.dispatchTarget());
    assertArrayEquals(new int[] {0}, cancelPcm.answerIndices());
  }

  // ====================================================================
  // FINE Logs Security / No Payload Leaks
  // ====================================================================

  @Test
  void promptSessionFineLogs_doNotLeakAnswersOrAssembledCommands() {
    var logger = java.util.logging.Logger.getLogger(PromptSession.class.getName());
    var originalLevel = logger.getLevel();
    logger.setLevel(java.util.logging.Level.FINEST);
    var captured = new java.util.ArrayList<String>();
    var handler =
        new java.util.logging.Handler() {
          @Override
          public void publish(java.util.logging.LogRecord record) {
            captured.add(record.getMessage());
          }

          @Override
          public void flush() {}

          @Override
          public void close() throws SecurityException {}
        };
    handler.setLevel(java.util.logging.Level.FINEST);
    logger.addHandler(handler);
    try {
      var parsed = parser.parse("/secret_cmd <a:prompt1> <d:text:prompt2> <! secret_pcm>");
      var session = PromptSession.start("user_uuid_123", parsed);
      var s1 = session.submitAnswer("secret_answer_one");
      var s2 = s1.submitAnswers(List.of("secret_answer_two"));
      var result = s2.finish();
      assertNotNull(result);

      for (var msg : captured) {
        assertFalse(
            msg.contains("secret_answer_one"), "FINE log must not contain answer 1: " + msg);
        assertFalse(
            msg.contains("secret_answer_two"), "FINE log must not contain answer 2: " + msg);
        assertFalse(msg.contains("secret_pcm"), "FINE log must not contain PCM command: " + msg);
        assertFalse(
            msg.contains("/secret_cmd"), "FINE log must not contain assembled command: " + msg);
      }
    } finally {
      logger.removeHandler(handler);
      logger.setLevel(originalLevel);
    }
  }

  @Test
  void toString_reportsAnswerCountOnlyAndDoesNotLeakAnswers() {
    var parsed = parser.parse("/secret <a:p1> <a:p2>");
    var session = PromptSession.start("user1", parsed);
    var str0 = session.toString();
    assertTrue(str0.contains("answers=0"), "Initial toString must report answers=0: " + str0);
    assertFalse(str0.contains("answers=[]"), "toString must not contain raw answers list: " + str0);

    var s1 = session.submitAnswer("superSecretValue123");
    var str1 = s1.toString();
    assertTrue(
        str1.contains("answers=1"), "After 1 answer, toString must report answers=1: " + str1);
    assertFalse(
        str1.contains("superSecretValue123"), "toString must not contain answer value: " + str1);

    var s2 = s1.submitAnswer("anotherSecretValue456");
    var str2 = s2.toString();
    assertTrue(
        str2.contains("answers=2"), "After 2 answers, toString must report answers=2: " + str2);
    assertFalse(
        str2.contains("superSecretValue123"), "toString must not contain answer 1: " + str2);
    assertFalse(
        str2.contains("anotherSecretValue456"), "toString must not contain answer 2: " + str2);
  }
}
