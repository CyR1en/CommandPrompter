package dev.cyr1en.promptpaper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import dev.cyr1en.promptpaper.approval.ApprovalCoordinator;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationRateLimiter;
import dev.cyr1en.promptpaper.screen.confirmation.NonceResponseRegistry;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResponseCommandTest extends MockBukkitTest {

  private ResponseCommand responseCommand;
  private ApprovalCoordinator mockApprovalCoordinator;
  private PromptEngine mockEngine;
  private NonceResponseRegistry mockNonceRegistry;

  @BeforeEach
  void setUpCommand() {
    mockApprovalCoordinator = mock(ApprovalCoordinator.class);
    mockEngine = mock(PromptEngine.class);
    mockNonceRegistry = mock(NonceResponseRegistry.class);

    lenient().when(plugin.getApprovalCoordinator()).thenReturn(mockApprovalCoordinator);
    lenient().when(plugin.getEngine()).thenReturn(mockEngine);
    lenient().when(plugin.getNonceRegistry()).thenReturn(mockNonceRegistry);
    lenient().when(plugin.getRateLimiter()).thenReturn(new ConfirmationRateLimiter());

    responseCommand = new ResponseCommand(plugin);
  }

  @Test
  @DisplayName(
      "Approval nonces (a_ prefix) route directly to ApprovalCoordinator and never local confirmation")
  void testApprovalNonceRouting() {
    Player player = createPlayer("Approver");
    String approvalNonce = "a_cryptoNonceToken12345";

    responseCommand.executeResponse(player, approvalNonce, "confirm");

    verify(mockApprovalCoordinator, times(1)).handleResponse(player, approvalNonce, "confirm");
    verify(mockEngine, never()).getSession(player);
    verify(mockNonceRegistry, never())
        .consume(anyString(), any(), anyLong(), anyLong(), anyInt(), anyString());
  }

  @Test
  @DisplayName(
      "Replay/unknown approval nonces route to ApprovalCoordinator and never fall back to local confirmation")
  void testUnknownApprovalNonceNeverFallsBack() {
    Player player = createPlayer("Approver");
    String unknownApprovalNonce = "a_unknownOrExpired";

    responseCommand.executeResponse(player, unknownApprovalNonce, "decline");

    verify(mockApprovalCoordinator, times(1))
        .handleResponse(player, unknownApprovalNonce, "decline");
    verify(mockEngine, never()).getSession(player);
    verify(mockNonceRegistry, never())
        .consume(anyString(), any(), anyLong(), anyLong(), anyInt(), anyString());
  }

  @Test
  @DisplayName(
      "Local confirmation nonces (no a_ prefix) route to PromptEngine / NonceResponseRegistry")
  void testLocalConfirmationNonceRouting() {
    Player player = createPlayer("Responder");
    String localNonce = "local_token_abc";

    responseCommand.executeResponse(player, localNonce, "confirm");

    // Approval coordinator must not be invoked for local nonces
    verify(mockApprovalCoordinator, never()).handleResponse(any(), any(), any());
  }

  @Test
  @DisplayName("Rate limited responses are aborted early")
  void testRateLimiterAborts() {
    Player player = createPlayer("Spammer");
    ConfirmationRateLimiter limiter =
        new ConfirmationRateLimiter(
            java.time.Clock.systemUTC(), 2, java.time.Duration.ofSeconds(10));
    when(plugin.getRateLimiter()).thenReturn(limiter);

    // 2 allowed
    responseCommand.executeResponse(player, "a_nonce1", "confirm");
    responseCommand.executeResponse(player, "a_nonce2", "confirm");
    // 3rd rejected by rate limiter
    responseCommand.executeResponse(player, "a_nonce3", "confirm");

    verify(mockApprovalCoordinator, times(2)).handleResponse(any(), any(), any());
  }

  @Test
  @DisplayName(
      "Local confirmation rejection with no active session logs production warn with truncated nonce")
  void testLocalConfirmationNoSessionLogsWarn() {
    Player player = createPlayer("NoSessionPlayer");
    dev.cyr1en.promptpaper.util.PluginLogger mockLogger =
        mock(dev.cyr1en.promptpaper.util.PluginLogger.class);
    when(plugin.getPluginLogger()).thenReturn(mockLogger);
    when(mockEngine.getSession(player)).thenReturn(java.util.Optional.empty());

    String rawNonce = "longSecretNonceToken123456789";
    responseCommand.executeResponse(player, rawNonce, "confirm");

    org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(mockLogger, times(1)).warn(captor.capture());
    String logMsg = captor.getValue();
    assertTrue(logMsg.contains("no active session"), "Must contain safe reason: " + logMsg);
    assertTrue(
        logMsg.contains(NonceResponseRegistry.truncateNonce(rawNonce)),
        "Must contain truncated nonce: " + logMsg);
    assertFalse(logMsg.contains(rawNonce), "Must not leak full raw nonce: " + logMsg);
    assertTrue(logMsg.contains("attempts="), "Must contain attempt count: " + logMsg);
  }

  @Test
  @DisplayName(
      "Local confirmation rejection on consume failure logs safe sanitized reason and bounds logs")
  void testLocalConfirmationConsumeFailureLogsWarnBounded() {
    Player player = createPlayer("ConsumeFailPlayer");
    dev.cyr1en.promptpaper.util.PluginLogger mockLogger =
        mock(dev.cyr1en.promptpaper.util.PluginLogger.class);
    when(plugin.getPluginLogger()).thenReturn(mockLogger);

    var mockSession = mock(dev.cyr1en.promptcore.session.PromptSession.class);
    when(mockSession.isActive()).thenReturn(true);
    when(mockSession.incarnation()).thenReturn(1L);
    when(mockSession.generation()).thenReturn(0L);
    when(mockSession.currentIndex()).thenReturn(0);
    when(mockEngine.getSession(player)).thenReturn(java.util.Optional.of(mockSession));

    when(mockNonceRegistry.consume(anyString(), any(), anyLong(), anyLong(), anyInt(), anyString()))
        .thenReturn(
            dev.cyr1en.promptpaper.screen.confirmation.ConsumeResult.rejected(
                dev.cyr1en.promptpaper.screen.confirmation.RejectionReason.EXPIRED));

    String rawNonce = "superSecretNonce9876543210";
    String maliciousDecision = "confirm\u0000\u001BevilPayload";

    responseCommand.executeResponse(player, rawNonce, maliciousDecision);

    org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(mockLogger, times(1)).warn(captor.capture());
    String logMsg = captor.getValue();
    assertTrue(logMsg.contains("EXPIRED"), "Must contain enum reason: " + logMsg);
    assertTrue(
        logMsg.contains(NonceResponseRegistry.truncateNonce(rawNonce)),
        "Must contain truncated nonce: " + logMsg);
    assertFalse(logMsg.contains(rawNonce), "Must not leak full raw nonce: " + logMsg);
    assertFalse(logMsg.contains("evilPayload"), "Must not leak raw decision: " + logMsg);
    assertFalse(logMsg.contains("\u0000"), "Must not contain C0 controls: " + logMsg);
  }

  @Test
  @DisplayName(
      "Rate-limit warning emits exactly once even when prior rejected responses logged on rejection channel")
  void testRateLimitWarningEmitsAfterPriorRejectionLogs() {
    Player player = createPlayer("BurstPlayer");
    ConfirmationRateLimiter limiter =
        new ConfirmationRateLimiter(
            java.time.Clock.systemUTC(), 2, java.time.Duration.ofSeconds(10));
    when(plugin.getRateLimiter()).thenReturn(limiter);
    when(mockEngine.getSession(player)).thenReturn(java.util.Optional.empty());

    dev.cyr1en.promptpaper.util.PluginLogger mockLogger =
        mock(dev.cyr1en.promptpaper.util.PluginLogger.class);
    when(plugin.getPluginLogger()).thenReturn(mockLogger);

    // 1st request: permitted by limiter, rejected by no session -> logs rejection warning
    responseCommand.executeResponse(player, "nonce1", "confirm");

    // 2nd request: permitted by limiter, rejected by no session -> rejection warning suppressed by
    // channel
    responseCommand.executeResponse(player, "nonce2", "confirm");

    // 3rd request: denied by limiter -> rate limit warning emitted!
    responseCommand.executeResponse(player, "nonce3", "confirm");

    // 4th request: denied by limiter -> rate limit warning suppressed
    responseCommand.executeResponse(player, "nonce4", "confirm");

    org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(mockLogger, times(2)).warn(captor.capture());

    List<String> logs = captor.getAllValues();
    assertEquals(2, logs.size());
    assertTrue(
        logs.get(0).contains("no active session"), "First log must be rejection: " + logs.get(0));
    assertTrue(
        logs.get(1).contains("Rate limited"), "Second log must be rate limit: " + logs.get(1));
  }
}
