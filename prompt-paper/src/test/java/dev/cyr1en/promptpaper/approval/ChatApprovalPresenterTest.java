package dev.cyr1en.promptpaper.approval;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.execution.runtime.ExecutionId;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatApprovalPresenterTest {

  private ChatApprovalPresenter presenter;
  private ApprovalCapability capability;
  private String nonce;

  @BeforeEach
  void setUp() {
    presenter = new ChatApprovalPresenter();
    nonce = "a_test-nonce-1234567890";
    capability =
        new ApprovalCapability(
            nonce,
            ExecutionId.create(),
            "admin_gate",
            UUID.randomUUID(),
            1L,
            UUID.randomUUID(),
            Instant.now().plusSeconds(30));
  }

  @Test
  @DisplayName("buildConfirmCommand and buildDeclineCommand format exact response commands")
  void testCommandFormatting() {
    String confirmCmd = ChatApprovalPresenter.buildConfirmCommand(capability);
    String declineCmd = ChatApprovalPresenter.buildDeclineCommand(capability);

    assertEquals("/commandprompter:response " + nonce + " confirm", confirmCmd);
    assertEquals("/commandprompter:response " + nonce + " decline", declineCmd);
  }

  @Test
  @DisplayName("render attaches ClickEvent with exact commands to confirm and decline buttons")
  void testRenderClickEvents() {
    Component rendered = presenter.render(capability, "Please approve execution of /op test");
    assertNotNull(rendered);

    String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
    assertTrue(plain.contains("Please approve execution of /op test"));
    assertTrue(plain.contains("[Approve]"));
    assertTrue(plain.contains("[Deny]"));

    // Verify click events exist in the component tree
    boolean foundConfirm = false;
    boolean foundDecline = false;

    for (Component child : rendered.children()) {
      ClickEvent click = child.clickEvent();
      if (click != null) {
        if (click.value().equals("/commandprompter:response " + nonce + " confirm")) {
          foundConfirm = true;
        }
        if (click.value().equals("/commandprompter:response " + nonce + " decline")) {
          foundDecline = true;
        }
      }
      for (Component subChild : child.children()) {
        ClickEvent subClick = subChild.clickEvent();
        if (subClick != null) {
          if (subClick.value().equals("/commandprompter:response " + nonce + " confirm")) {
            foundConfirm = true;
          }
          if (subClick.value().equals("/commandprompter:response " + nonce + " decline")) {
            foundDecline = true;
          }
        }
      }
    }

    assertTrue(foundConfirm, "Must contain clickable confirm action");
    assertTrue(foundDecline, "Must contain clickable decline action");
  }

  @Test
  @DisplayName("sanitize strips raw C0 control characters and truncates excessive length")
  void testSanitizeC0AndLength() {
    String withC0 = "Hello\u0000World\u0007\u001F!\nLine2";
    String sanitized = ChatApprovalPresenter.sanitize(withC0);
    assertFalse(sanitized.contains("\u0000"));
    assertFalse(sanitized.contains("\u0007"));
    assertFalse(sanitized.contains("\u001F"));
    assertFalse(sanitized.contains("\n"));
    assertTrue(sanitized.contains("HelloWorld!Line2"));

    String huge = "a".repeat(5000);
    String capped = ChatApprovalPresenter.sanitize(huge);
    assertEquals(ChatApprovalPresenter.MAX_INPUT_LENGTH, capped.length());
  }

  @Test
  @DisplayName("Injected MiniMessage tags, newlines, and legacy color markers render literal and non-clickable")
  void testInjectedTagsAndColorsRenderLiteral() {
    String malicious = "<click:run_command:/op attacker><hover:show_text:'bad'>Click to win!</click></hover><newline><green>Green Text</green> §cRed &aGreen [Approve]";
    Component rendered = presenter.render(capability, malicious);
    assertNotNull(rendered);

    String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
    assertTrue(plain.contains("<click:run_command:/op attacker>"));
    assertTrue(plain.contains("<newline>"));
    assertTrue(plain.contains("<green>Green Text</green>"));
    assertTrue(plain.contains("§cRed"));
    assertTrue(plain.contains("&aGreen"));
    assertTrue(plain.contains("[Approve]"));
    assertTrue(plain.contains("[Deny]"));

    // Collect all click events across the component tree
    java.util.List<ClickEvent> clickEvents = new java.util.ArrayList<>();
    collectClickEvents(rendered, clickEvents);

    // Exactly 2 click events: one confirm button, one decline button
    assertEquals(2, clickEvents.size(), "Only the two code-built buttons must have ClickEvents");
    assertEquals(
        "/commandprompter:response " + nonce + " confirm",
        clickEvents.get(0).value());
    assertEquals(
        "/commandprompter:response " + nonce + " decline",
        clickEvents.get(1).value());
  }

  private void collectClickEvents(Component component, java.util.List<ClickEvent> out) {
    if (component.clickEvent() != null) {
      out.add(component.clickEvent());
    }
    for (Component child : component.children()) {
      collectClickEvents(child, out);
    }
  }

  @Test
  @DisplayName("present dispatches rendered Adventure component to Audience")
  void testPresent() {
    AtomicReference<Component> sent = new AtomicReference<>();
    Audience audience = new Audience() {
      @Override
      public void sendMessage(Component message) {
        sent.set(message);
      }
    };

    presenter.present(audience, capability, "Authorize action?");

    assertNotNull(sent.get());
    String plain = PlainTextComponentSerializer.plainText().serialize(sent.get());
    assertTrue(plain.contains("Authorize action?"));
    assertTrue(plain.contains("[Approve]"));
    assertTrue(plain.contains("[Deny]"));
  }

  @Test
  @DisplayName("presenter and capability toString do not expose raw nonces in logs")
  void testNoNonceLeakInToString() {
    assertFalse(presenter.toString().contains(nonce));
    assertFalse(capability.toString().contains(nonce));
  }
}
