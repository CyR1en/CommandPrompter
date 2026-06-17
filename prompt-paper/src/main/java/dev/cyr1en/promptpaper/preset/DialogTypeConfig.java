package dev.cyr1en.promptpaper.preset;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Objects;

/**
 * Represents the {@code dialog_type} block of a {@link DialogPrompt}. Maps to the
 * {@code dialogTypeConfig} definition in the JSON schema.
 *
 * <p>The structure mirrors Paper's native {@code DialogType} hierarchy:
 *
 * <ul>
 *   <li>{@link DialogType#MULTI_ACTION multi_action}: an action grid, optionally populated
 *       from tab-completion. Exactly one of {@link #actions()} or {@link #actionsSource()}
 *       must be provided (enforced by {@link PromptDefinitionDeserializer}).
 *   <li>{@link DialogType#CONFIRMATION confirmation}: a two-button confirm/cancel dialog.
 * </ul>
 *
 * @param type the kind of dialog layout
 * @param columns number of columns in a multi-action grid; required for multi_action
 * @param actions explicit action buttons for a multi-action grid (mutually exclusive with
 *     {@code actionsSource})
 * @param actionsSource alternative dynamic source for buttons (e.g. tab-completion)
 * @param exitAction optional exit / cancel button for a multi-action grid
 * @param confirmAction the confirm button (required for confirmation)
 * @param cancelAction the optional cancel button for a confirmation
 */
public record DialogTypeConfig(
    DialogType type,
    Integer columns,
    List<ActionButtonConfig> actions,
    @SerializedName("actions_source") ActionsSource actionsSource,
    @SerializedName("exit_action") ActionButtonConfig exitAction,
    @SerializedName("confirm_action") ActionButtonConfig confirmAction,
    @SerializedName("cancel_action") ActionButtonConfig cancelAction) {

  /** Canonical constructor. Coerces {@code null} {@link #actions()} to an empty list. */
  public DialogTypeConfig {
    Objects.requireNonNull(type, "type must not be null");
    actions = actions == null ? List.of() : List.copyOf(actions);
  }
}
