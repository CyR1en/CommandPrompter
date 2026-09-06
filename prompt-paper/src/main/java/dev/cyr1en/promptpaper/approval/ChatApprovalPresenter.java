package dev.cyr1en.promptpaper.approval;

import java.util.Objects;
import java.util.regex.Pattern;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Presenter for chat-based approval requests.
 *
 * <p>Constructs and dispatches grounded Adventure components containing clickable confirm and
 * decline buttons bound to a single-use cryptographic capability nonce.
 *
 * <p>This presenter does <b>not</b> own any PromptSession or InputScreen and performs no scheduling
 * itself; callers are responsible for invoking it on the appropriate entity scheduler.
 */
public final class ChatApprovalPresenter {

  public static final String COMMAND_PREFIX = "/commandprompter:response";
  public static final String ACTION_CONFIRM = "confirm";
  public static final String ACTION_DECLINE = "decline";

  public static final int MAX_INPUT_LENGTH = 2048;
  private static final Pattern C0_CONTROLS = Pattern.compile("[\\u0000-\\u001F\\u007F]");

  public static final Component DEFAULT_CONFIRM_LABEL =
      Component.text("[Approve]").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
  public static final Component DEFAULT_DECLINE_LABEL =
      Component.text("[Deny]").color(NamedTextColor.RED).decorate(TextDecoration.BOLD);

  public static final Component DEFAULT_CONFIRM_HOVER =
      Component.text("Click to approve this command execution").color(NamedTextColor.GREEN);
  public static final Component DEFAULT_DECLINE_HOVER =
      Component.text("Click to deny this command execution").color(NamedTextColor.RED);

  private final Component confirmLabel;
  private final Component declineLabel;
  private final Component confirmHover;
  private final Component declineHover;

  public ChatApprovalPresenter() {
    this(
        DEFAULT_CONFIRM_LABEL, DEFAULT_DECLINE_LABEL, DEFAULT_CONFIRM_HOVER, DEFAULT_DECLINE_HOVER);
  }

  public ChatApprovalPresenter(
      Component confirmLabel,
      Component declineLabel,
      Component confirmHover,
      Component declineHover) {
    this.confirmLabel = confirmLabel != null ? confirmLabel : DEFAULT_CONFIRM_LABEL;
    this.declineLabel = declineLabel != null ? declineLabel : DEFAULT_DECLINE_LABEL;
    this.confirmHover = confirmHover != null ? confirmHover : DEFAULT_CONFIRM_HOVER;
    this.declineHover = declineHover != null ? declineHover : DEFAULT_DECLINE_HOVER;
  }

  /**
   * Sanitizes a raw input string to ensure it is bounded and C0-control free.
   *
   * @param raw the raw input text
   * @return sanitized string
   */
  public static String sanitize(String raw) {
    if (raw == null) {
      return "";
    }
    String cleaned = C0_CONTROLS.matcher(raw).replaceAll("").strip();
    if (cleaned.length() > MAX_INPUT_LENGTH) {
      return cleaned.substring(0, MAX_INPUT_LENGTH);
    }
    return cleaned;
  }

  /**
   * Builds the confirm command string for a capability.
   *
   * @param capability the approval capability
   * @return command string
   */
  public static String buildConfirmCommand(ApprovalCapability capability) {
    Objects.requireNonNull(capability, "capability must not be null");
    return COMMAND_PREFIX + " " + capability.nonce() + " " + ACTION_CONFIRM;
  }

  /**
   * Builds the decline command string for a capability.
   *
   * @param capability the approval capability
   * @return command string
   */
  public static String buildDeclineCommand(ApprovalCapability capability) {
    Objects.requireNonNull(capability, "capability must not be null");
    return COMMAND_PREFIX + " " + capability.nonce() + " " + ACTION_DECLINE;
  }

  /**
   * Renders the interactive approval prompt component from a raw prompt string.
   *
   * <p>The prompt text is sanitized and rendered as literal plain text to prevent answer-derived
   * MiniMessage or legacy formatting injection, while approve and deny buttons remain code-built
   * interactive Adventure components.
   *
   * @param capability the approval capability
   * @param rawPrompt the prompt message string (will be sanitized and rendered as literal text)
   * @return the complete Adventure component
   */
  public Component render(ApprovalCapability capability, String rawPrompt) {
    String sanitized = sanitize(rawPrompt);
    Component promptComponent = sanitized.isEmpty() ? Component.empty() : Component.text(sanitized);
    return render(capability, promptComponent);
  }

  /**
   * Renders the interactive approval prompt component from an existing Component.
   *
   * @param capability the approval capability
   * @param promptComponent the base prompt component
   * @return the complete Adventure component
   */
  public Component render(ApprovalCapability capability, Component promptComponent) {
    Objects.requireNonNull(capability, "capability must not be null");
    Component base = promptComponent != null ? promptComponent : Component.empty();

    Component confirmButton =
        confirmLabel
            .hoverEvent(HoverEvent.showText(confirmHover))
            .clickEvent(ClickEvent.runCommand(buildConfirmCommand(capability)));

    Component declineButton =
        declineLabel
            .hoverEvent(HoverEvent.showText(declineHover))
            .clickEvent(ClickEvent.runCommand(buildDeclineCommand(capability)));

    Component actions =
        Component.empty().append(confirmButton).append(Component.space()).append(declineButton);

    if (base.equals(Component.empty())) {
      return actions;
    }

    return base.append(Component.newline()).append(actions);
  }

  /**
   * Sends the rendered approval prompt directly to an Adventure Audience.
   *
   * @param audience recipient audience (e.g. Player)
   * @param capability the approval capability
   * @param promptComponent the base prompt component
   */
  public void present(Audience audience, ApprovalCapability capability, Component promptComponent) {
    Objects.requireNonNull(audience, "audience must not be null");
    audience.sendMessage(render(capability, promptComponent));
  }

  /**
   * Sends the rendered approval prompt directly to an Adventure Audience from a raw prompt string.
   *
   * @param audience recipient audience (e.g. Player)
   * @param capability the approval capability
   * @param rawPrompt the raw prompt text
   */
  public void present(Audience audience, ApprovalCapability capability, String rawPrompt) {
    Objects.requireNonNull(audience, "audience must not be null");
    audience.sendMessage(render(capability, rawPrompt));
  }

  @Override
  public String toString() {
    return "ChatApprovalPresenter[]";
  }
}
