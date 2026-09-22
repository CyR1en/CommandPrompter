package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.hook.hooks.GeyserHook;
import dev.cyr1en.promptui.AnvilInputScreen;
import dev.cyr1en.promptui.ComponentUtil;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.util.BedrockUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Prompt screen that presents an anvil GUI for text input, falling back to chat if no NMS provider
 * is available.
 */
public class AnvilPromptScreen extends AbstractWrapperPromptScreen {

  private final dev.cyr1en.promptpaper.preset.AnvilPrompt anvilPrompt;

  public AnvilPromptScreen(
      CommandPrompter plugin,
      Player player,
      dev.cyr1en.promptpaper.preset.AnvilPrompt anvilPrompt,
      List<ScreenProvider> providers) {
    super(plugin, player, anvilPrompt.promptText(), providers);
    this.anvilPrompt = anvilPrompt;
  }

  /**
   * Tries each {@link ScreenProvider} to create an anvil screen, falls back to chat if none
   * succeed.
   */
  @Override
  public void open() {
    var promptConfig = plugin.getConfigLoader().getPromptConfig();
    var config = buildConfig(promptConfig);

    for (var provider : providers) {
      InputScreen candidate = null;
      try {
        plugin
            .getPluginLogger()
            .debug("Attempting anvil provider: " + provider.getClass().getSimpleName());
        var nms = provider.createAnvil(plugin, player, displayText);
        candidate = nms;
        if (nms instanceof AnvilInputScreen anvilScreen) {
          anvilScreen.configure(config);
          if (BedrockUtil.isBedrockPlayer(player) && plugin.getHookContainer() != null) {
            plugin
                .getHookContainer()
                .getHook(GeyserHook.class)
                .filter(GeyserHook::isAnvilPatchEnabled)
                .ifPresent(hook -> anvilScreen.setItemPresentation(hook.itemPresentation()));
          }
          anvilScreen.onResult(this::handleResult);
          this.wrapped = anvilScreen;
          this.open = true;
          anvilScreen.onOpenFailure(failure -> handleAsyncProviderFailure(anvilScreen, failure));
          anvilScreen.open();
          plugin
              .getPluginLogger()
              .debug("Anvil provider succeeded: " + provider.getClass().getSimpleName());
          return;
        }
      } catch (Throwable t) {
        open = false;
        if (candidate != null) {
          try {
            candidate.close();
          } catch (Throwable closeFailure) {
            plugin
                .getPluginLogger()
                .debug("Anvil provider cleanup failed: " + closeFailure.getMessage());
          }
        }
        plugin
            .getPluginLogger()
            .debug(
                "Anvil provider "
                    + provider.getClass().getSimpleName()
                    + " failed: "
                    + t.getMessage());
      }
    }
    plugin.getPluginLogger().debug("All anvil providers failed, falling back to chat");
    wrapped = fallbackToChat();
    open = true;
    try {
      wrapped.open();
    } catch (Throwable failure) {
      open = false;
      plugin
          .getPluginLogger()
          .warn("Chat fallback for anvil prompt failed: " + failure.getMessage());
    }
  }

  private void handleAsyncProviderFailure(AnvilInputScreen failed, Throwable failure) {
    if (!open || wrapped != failed) return;
    plugin.getPluginLogger().debug("Asynchronous anvil provider failed: " + failure.getMessage());
    open = false;
    try {
      failed.close();
    } catch (Throwable closeFailure) {
      plugin.getPluginLogger().debug("Anvil provider cleanup failed: " + closeFailure.getMessage());
    }
    try {
      var fallback = fallbackToChat();
      wrapped = fallback;
      open = true;
      fallback.open();
    } catch (Throwable fallbackFailure) {
      open = false;
      plugin
          .getPluginLogger()
          .warn("Chat fallback for anvil prompt failed: " + fallbackFailure.getMessage());
    }
  }

  Map<String, String> buildConfig(PromptConfig cfg) {
    var config = new HashMap<String, String>();

    boolean isPreset = !anvilPrompt.id().startsWith("inline-");
    boolean bedrockPlayer = BedrockUtil.isBedrockPlayer(player);
    boolean patchedAnvil =
        plugin.getHookContainer() != null
            && plugin
                .getHookContainer()
                .getHook(GeyserHook.class)
                .map(GeyserHook::isAnvilPatchEnabled)
                .orElse(false);
    config.put("geyserAnvilPatch", String.valueOf(patchedAnvil));
    String initialText = cfg.promptMessage();
    if ("BLANK".equals(initialText)) initialText = "";
    else if (initialText == null || initialText.isEmpty()) initialText = anvilPrompt.promptText();

    config.put("enableTitle", isPreset ? "true" : String.valueOf(cfg.enableTitle()));
    config.put("customTitle", isPreset ? anvilPrompt.title() : cfg.customTitle());
    config.put(
        "enableFirstItem", isPreset ? String.valueOf(anvilPrompt.leftButton().show()) : "true");
    config.put("promptMessage", isPreset ? anvilPrompt.promptText() : initialText);
    config.put(
        "itemHoverText",
        isPreset
            ? anvilPrompt.leftButton().buttonHoverText()
            : java.util.Objects.requireNonNullElse(cfg.itemHoverText(), ""));
    config.put(
        "itemDamage",
        String.valueOf(isPreset ? anvilPrompt.leftButton().damage() : cfg.itemDamage()));
    config.put(
        "cancelItemDamage", String.valueOf(isPreset ? anvilPrompt.rightButton().damage() : 0));
    config.put(
        "enableCancelItem",
        isPreset
            ? String.valueOf(anvilPrompt.rightButton().show())
            : String.valueOf(cfg.enableCancelItem()));
    config.put("anvilItem", isPreset ? anvilPrompt.leftButton().buttonIcon() : cfg.anvilItem());
    config.put("itemHideTooltips", String.valueOf(cfg.itemHideTooltips()));
    config.put(
        "itemCustomModelData",
        String.valueOf(
            isPreset ? anvilPrompt.leftButton().customModelData() : cfg.itemCustomModelData()));
    config.put("itemAnvilEnchanted", String.valueOf(cfg.itemAnvilEnchanted()));

    config.put("anvilResultItem", cfg.anvilResultItem());
    config.put("resultItemHideTooltips", String.valueOf(cfg.resultItemHideTooltips()));
    config.put("resultItemCustomModelData", String.valueOf(cfg.resultItemCustomModelData()));
    config.put("resultItemAnvilEnchanted", String.valueOf(cfg.resultItemAnvilEnchanted()));

    config.put(
        "anvilCancelItem",
        isPreset ? anvilPrompt.rightButton().buttonIcon() : cfg.anvilCancelItem());
    config.put("cancelItemHideTooltips", String.valueOf(cfg.cancelItemHideTooltips()));
    config.put(
        "cancelItemCustomModelData",
        String.valueOf(
            isPreset
                ? anvilPrompt.rightButton().customModelData()
                : cfg.cancelItemCustomModelData()));
    config.put("cancelItemAnvilEnchanted", String.valueOf(cfg.cancelItemAnvilEnchanted()));
    config.put(
        "cancelItemMessage",
        isPreset
            ? anvilPrompt.rightButton().buttonText()
            : java.util.Objects.requireNonNullElse(cfg.cancelItemName(), ""));
    config.put(
        "cancelItemHoverText",
        isPreset ? anvilPrompt.rightButton().buttonHoverText() : cfg.cancelItemHoverText());

    if (bedrockPlayer) {
      // A native Bedrock anvil needs an input to expose its text field and result operation.
      config.put("enableFirstItem", "true");
    }
    if (bedrockPlayer && !patchedAnvil) {
      // Bedrock predicts a real rename recipe. A cancel item in the material slot invalidates
      // that recipe, and its output must be the same item as the input. Closing cancels instead.
      config.put("enableCancelItem", "false");
      config.put("anvilItem", config.get("anvilResultItem"));
      config.put("itemHideTooltips", config.get("resultItemHideTooltips"));
      config.put("itemCustomModelData", config.get("resultItemCustomModelData"));
      config.put("itemAnvilEnchanted", config.get("resultItemAnvilEnchanted"));
      config.put("itemHoverText", "");
      config.put("itemDamage", "0");
    }

    config.put("displayText", displayText);
    return config;
  }

  /**
   * Strips color codes from the answer, checks the cancel keyword, and forwards the result to the
   * callback.
   */
  @Override
  protected void handleResult(ScreenResult result) {
    if (!open) return;
    open = false;
    plugin
        .getPluginLogger()
        .debug("Anvil result for " + player.getName() + " cancelled=" + result.cancelled());
    if (callback == null) return;
    if (result.cancelled()) {
      callback.accept(result);
      return;
    }
    var raw = result.answer();
    var stripped = ComponentUtil.stripColor(raw).trim();
    if (stripped.equalsIgnoreCase(plugin.getConfigLoader().getConfig().cancelKeyword())) {
      plugin.getPluginLogger().debug("Anvil result matched cancel keyword for " + player.getName());
      callback.accept(ScreenResult.cancel());
      return;
    }
    // Cancel-keyword comparison stays color-insensitive; the forwarded answer only
    // strips color when the preset asks for sanitization.
    callback.accept(ScreenResult.answer(anvilPrompt.sanitize() ? stripped : raw.trim()));
  }
}
