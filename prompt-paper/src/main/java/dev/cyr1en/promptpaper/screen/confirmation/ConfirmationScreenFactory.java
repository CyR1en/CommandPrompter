package dev.cyr1en.promptpaper.screen.confirmation;

import dev.cyr1en.promptcore.ConfirmationMode;
import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptcore.session.PromptSession;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.factory.MaterialMapper;
import dev.cyr1en.promptpaper.preset.ConfirmationPrompt;
import dev.cyr1en.promptpaper.screen.dialog.DialogLifecycle;
import dev.cyr1en.promptui.ComponentUtil;
import java.util.List;
import org.bukkit.entity.Player;

/** Builds confirmation screens and their ordered dialog/GUI/chat fallback views. */
public final class ConfirmationScreenFactory {

  private ConfirmationScreenFactory() {}

  public static ConfirmationPromptScreen create(
      CommandPrompter plugin,
      MaterialMapper materialMapper,
      Player player,
      ConfirmationPrompt confirmation) {
    var promptConfig = plugin.getConfigLoader().getPromptConfig();
    var screenConfig = promptConfig != null ? promptConfig.confirmationConfig() : null;
    var defaultMode =
        screenConfig != null && screenConfig.defaultMode() != null
            ? screenConfig.defaultMode()
            : ConfirmationMode.GUI;
    var mode = confirmation.mode() != null ? confirmation.mode() : defaultMode;
    var sessionOpt =
        plugin.getEngine() != null
            ? plugin.getEngine().getSession(player)
            : java.util.Optional.<PromptSession>empty();
    var config = plugin.getConfigLoader().getConfig();
    int timeoutSeconds =
        sessionOpt
            .flatMap(PromptSession::currentPrompt)
            .map(PromptTag::timeout)
            .orElseGet(
                () -> {
                  if (confirmation.behavior() != null
                      && confirmation.behavior().timeout() != null) {
                    return confirmation.behavior().timeout();
                  }
                  if (confirmation.timeout() != null) return confirmation.timeout();
                  return config != null ? config.promptTimeout() : 60;
                });
    var responseLifetime = DialogLifecycle.responseLifetime(timeoutSeconds);

    var dialogTitle =
        confirmation.title() != null
            ? ComponentUtil.mini(confirmation.title())
            : (screenConfig != null && screenConfig.guiTitle() != null
                ? ComponentUtil.mini(screenConfig.guiTitle())
                : org.bukkit.Bukkit.getServer() != null
                    ? ComponentUtil.mini("Confirmation")
                    : null);
    var dialogBody = ComponentUtil.mini(confirmation.promptText());
    var dialogConfirm =
        confirmation.confirmText() != null
            ? ComponentUtil.mini(confirmation.confirmText())
            : (screenConfig != null && screenConfig.defaultConfirmLabel() != null
                ? ComponentUtil.mini(screenConfig.defaultConfirmLabel())
                : ComponentUtil.mini("Confirm"));
    var dialogCancel =
        confirmation.cancelText() != null
            ? ComponentUtil.mini(confirmation.cancelText())
            : (screenConfig != null && screenConfig.defaultCancelLabel() != null
                ? ComponentUtil.mini(screenConfig.defaultCancelLabel())
                : ComponentUtil.mini("Cancel"));
    var dialogView =
        new ConfirmationDialogView(
            plugin,
            player,
            dialogTitle,
            dialogBody,
            dialogConfirm,
            null,
            dialogCancel,
            null,
            null,
            responseLifetime);

    ConfirmationGuiConfig guiConfig;
    if (screenConfig != null) {
      var guiTitle =
          confirmation.title() != null
              ? ComponentUtil.mini(confirmation.title())
              : (screenConfig.guiTitle() != null
                  ? ComponentUtil.mini(screenConfig.guiTitle())
                  : ComponentUtil.mini("<dark_gray>Confirmation</dark_gray>"));
      var confirmMat =
          materialMapper.resolveOrDefault(
              screenConfig.confirmItem().material(), "confirmation gui confirm_item");
      var confirmName =
          ComponentUtil.mini(
              confirmation.confirmText() != null
                  ? confirmation.confirmText()
                  : (screenConfig.confirmItem().name() != null
                      ? screenConfig.confirmItem().name()
                      : screenConfig.defaultConfirmLabel()));
      var cancelMat =
          materialMapper.resolveOrDefault(
              screenConfig.cancelItem().material(), "confirmation gui cancel_item");
      var cancelName =
          ComponentUtil.mini(
              confirmation.cancelText() != null
                  ? confirmation.cancelText()
                  : (screenConfig.cancelItem().name() != null
                      ? screenConfig.cancelItem().name()
                      : screenConfig.defaultCancelLabel()));
      var infoMat =
          materialMapper.resolveOrDefault(
              screenConfig.infoItem().material(), "confirmation gui info_item");
      var infoName =
          ComponentUtil.mini(
              screenConfig.infoItem().name() != null
                  ? screenConfig.infoItem().name()
                  : "<yellow><bold>Information</bold></yellow>");
      var infoLore =
          confirmation.promptText() != null
              ? List.of(ComponentUtil.mini(confirmation.promptText()))
              : List.<net.kyori.adventure.text.Component>of();

      guiConfig =
          ConfirmationGuiConfig.builder()
              .title(guiTitle)
              .confirmSlot(screenConfig.confirmItem().slot())
              .confirmItem(confirmMat, confirmName)
              .declineSlot(screenConfig.cancelItem().slot())
              .declineItem(cancelMat, cancelName)
              .infoSlot(screenConfig.infoItem().slot())
              .infoItem(infoMat, infoName, infoLore)
              .build();
    } else {
      guiConfig =
          ConfirmationGuiConfig.builder()
              .title(
                  confirmation.title() != null
                      ? ComponentUtil.mini(confirmation.title())
                      : ComponentUtil.mini("<dark_gray>Confirmation</dark_gray>"))
              .infoMessage(ComponentUtil.mini(confirmation.promptText()))
              .build();
    }
    var guiView = new ConfirmationGuiView(plugin, player, guiConfig);

    var chatPrompt = ComponentUtil.mini(confirmation.promptText());
    var chatConfirm =
        confirmation.confirmText() != null
            ? ComponentUtil.mini(confirmation.confirmText())
            : (screenConfig != null && screenConfig.defaultConfirmLabel() != null
                ? ComponentUtil.mini(screenConfig.defaultConfirmLabel())
                : ConfirmationChatView.DEFAULT_CONFIRM_LABEL);
    var chatCancel =
        confirmation.cancelText() != null
            ? ComponentUtil.mini(confirmation.cancelText())
            : (screenConfig != null && screenConfig.defaultCancelLabel() != null
                ? ComponentUtil.mini(screenConfig.defaultCancelLabel())
                : ConfirmationChatView.DEFAULT_CANCEL_LABEL);

    long incarnation = sessionOpt.map(PromptSession::incarnation).orElse(0L);
    long generation = sessionOpt.map(PromptSession::generation).orElse(0L);
    int promptIndex = sessionOpt.map(PromptSession::currentIndex).orElse(0);

    var registry =
        plugin.getNonceRegistry() != null ? plugin.getNonceRegistry() : new NonceResponseRegistry();
    var chatView =
        new ConfirmationChatView(
            plugin,
            player,
            chatPrompt,
            chatConfirm,
            chatCancel,
            registry,
            incarnation,
            generation,
            promptIndex,
            responseLifetime);

    List<ConfirmationView> fallbackChain =
        switch (mode) {
          case DIALOG -> List.of(dialogView, guiView, chatView);
          case GUI -> List.of(guiView, chatView);
          case CHAT -> List.of(chatView);
        };

    var soundKey =
        confirmation.sound() != null
            ? confirmation.sound()
            : screenConfig != null ? screenConfig.sound() : null;
    Runnable soundAction =
        soundKey != null && !soundKey.isBlank()
            ? () ->
                player
                    .getScheduler()
                    .run(
                        plugin,
                        task -> {
                          try {
                            var sound =
                                net.kyori.adventure.sound.Sound.sound(
                                    net.kyori.adventure.key.Key.key(soundKey),
                                    net.kyori.adventure.sound.Sound.Source.MASTER,
                                    1.0f,
                                    1.0f);
                            player.playSound(sound);
                          } catch (Throwable t) {
                            plugin
                                .getPluginLogger()
                                .warn("Invalid confirmation sound key: " + soundKey);
                          }
                        },
                        null)
            : null;

    boolean valueMode = confirmation.valueMode();
    String confirmValue = valueMode ? "true" : ConfirmationPromptScreen.DEFAULT_CONFIRM_VALUE;
    String declineValue = valueMode ? "false" : ConfirmationPromptScreen.DEFAULT_DECLINE_VALUE;
    return new ConfirmationPromptScreen(
        fallbackChain,
        confirmValue,
        declineValue,
        !valueMode,
        soundAction,
        valueMode,
        timeoutSeconds);
  }
}
