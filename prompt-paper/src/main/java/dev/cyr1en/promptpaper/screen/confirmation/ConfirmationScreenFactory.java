package dev.cyr1en.promptpaper.screen.confirmation;

import dev.cyr1en.promptcore.ConfirmationMode;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.factory.MaterialMapper;
import dev.cyr1en.promptpaper.preset.ConfirmationPrompt;
import dev.cyr1en.promptui.ComponentUtil;
import java.time.Duration;
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
            plugin, player, dialogTitle, dialogBody, dialogConfirm, dialogCancel);

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

    var sessionOpt =
        plugin.getEngine() != null
            ? plugin.getEngine().getSession(player)
            : java.util.Optional.<dev.cyr1en.promptcore.session.PromptSession>empty();
    long incarnation =
        sessionOpt.map(dev.cyr1en.promptcore.session.PromptSession::incarnation).orElse(0L);
    long generation =
        sessionOpt.map(dev.cyr1en.promptcore.session.PromptSession::generation).orElse(0L);
    int promptIndex =
        sessionOpt.map(dev.cyr1en.promptcore.session.PromptSession::currentIndex).orElse(0);
    int timeoutSecs;
    if (confirmation.timeout() != null) {
      timeoutSecs = confirmation.timeout();
    } else if (plugin.getConfigLoader() != null && plugin.getConfigLoader().getConfig() != null) {
      timeoutSecs = plugin.getConfigLoader().getConfig().promptTimeout();
    } else {
      timeoutSecs = 60;
    }
    long effectiveTtl = timeoutSecs > 0 ? Math.min(timeoutSecs, 3600) : 3600;

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
            Duration.ofSeconds(effectiveTtl));

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
        fallbackChain, confirmValue, declineValue, !valueMode, soundAction, valueMode);
  }
}
