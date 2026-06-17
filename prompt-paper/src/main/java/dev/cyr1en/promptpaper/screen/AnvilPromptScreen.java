package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptui.AnvilInputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptui.ComponentUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Prompt screen that presents an anvil GUI for text input,
 * falling back to chat if no NMS provider is available.
 */
public class AnvilPromptScreen extends AbstractWrapperPromptScreen {

    private final dev.cyr1en.promptpaper.preset.AnvilPrompt anvilPrompt;

    public AnvilPromptScreen(CommandPrompter plugin, Player player, dev.cyr1en.promptpaper.preset.AnvilPrompt anvilPrompt, List<ScreenProvider> providers) {
        super(plugin, player, anvilPrompt.promptText(), providers);
        this.anvilPrompt = anvilPrompt;
    }

    /**
     * Tries each {@link ScreenProvider} to create an anvil screen,
     * falls back to chat if none succeed.
     */
    @Override
    public void open() {
        var promptConfig = plugin.getConfigLoader().getPromptConfig();
        var config = buildConfig(promptConfig);

        for (var provider : providers) {
            try {
                plugin.getPluginLogger().debug("Attempting anvil provider: "
                        + provider.getClass().getSimpleName());
                var nms = provider.createAnvil(plugin, player, displayText);
                if (nms instanceof AnvilInputScreen anvilScreen) {
                    anvilScreen.configure(config);
                    anvilScreen.onResult(this::handleResult);
                    anvilScreen.open();
                    this.wrapped = anvilScreen;
                    this.open = true;
                    plugin.getPluginLogger().debug("Anvil provider succeeded: "
                            + provider.getClass().getSimpleName());
                    return;
                }
            } catch (Exception e) {
                plugin.getPluginLogger().debug("Anvil provider "
                        + provider.getClass().getSimpleName() + " failed: " + e.getMessage());
            }
        }
        plugin.getPluginLogger().debug("All anvil providers failed, falling back to chat");
        wrapped = fallbackToChat();
        wrapped.open();
        open = true;
    }

    private Map<String, String> buildConfig(PromptConfig cfg) {
        var config = new HashMap<String, String>();
        
        boolean isPreset = anvilPrompt != null && !anvilPrompt.id().startsWith("inline-");

        if (isPreset) {
            config.put("enableTitle", "true");
            config.put("customTitle", anvilPrompt.title());
        } else {
            config.put("enableTitle", String.valueOf(cfg.enableTitle()));
            config.put("customTitle", cfg.customTitle());
        }

        if (isPreset) {
            config.put("promptMessage", anvilPrompt.leftButton().buttonText());
        } else {
            config.put("promptMessage", cfg.promptMessage());
        }

        if (isPreset) {
            config.put("enableCancelItem", String.valueOf(anvilPrompt.rightButton().show()));
        } else {
            config.put("enableCancelItem", String.valueOf(cfg.enableCancelItem()));
        }

        if (isPreset) {
            config.put("anvilItem", anvilPrompt.leftButton().buttonIcon());
        } else {
            config.put("anvilItem", cfg.anvilItem());
        }
        config.put("itemHideTooltips", String.valueOf(cfg.itemHideTooltips()));
        
        if (isPreset) {
            config.put("itemCustomModelData", String.valueOf(anvilPrompt.leftButton().customModelData()));
        } else {
            config.put("itemCustomModelData", String.valueOf(cfg.itemCustomModelData()));
        }
        config.put("itemAnvilEnchanted", String.valueOf(cfg.itemAnvilEnchanted()));

        config.put("anvilResultItem", cfg.anvilResultItem());
        config.put("resultItemHideTooltips", String.valueOf(cfg.resultItemHideTooltips()));
        config.put("resultItemCustomModelData", String.valueOf(cfg.resultItemCustomModelData()));
        config.put("resultItemAnvilEnchanted", String.valueOf(cfg.resultItemAnvilEnchanted()));

        if (isPreset) {
            config.put("anvilCancelItem", anvilPrompt.rightButton().buttonIcon());
        } else {
            config.put("anvilCancelItem", cfg.anvilCancelItem());
        }
        config.put("cancelItemHideTooltips", String.valueOf(cfg.cancelItemHideTooltips()));
        
        if (isPreset) {
            config.put("cancelItemCustomModelData", String.valueOf(anvilPrompt.rightButton().customModelData()));
        } else {
            config.put("cancelItemCustomModelData", String.valueOf(cfg.cancelItemCustomModelData()));
        }
        config.put("cancelItemAnvilEnchanted", String.valueOf(cfg.cancelItemAnvilEnchanted()));
        
        if (isPreset) {
            config.put("cancelItemHoverText", anvilPrompt.rightButton().buttonHoverText());
        } else {
            config.put("cancelItemHoverText", cfg.cancelItemHoverText());
        }

        config.put("displayText", displayText);
        return config;
    }

    /**
     * Strips color codes from the answer, checks the cancel keyword,
     * and forwards the result to the callback.
     */
    @Override
    protected void handleResult(ScreenResult result) {
        if (!open) return;
        open = false;
        plugin.getPluginLogger().debug("Anvil result for " + player.getName()
                + " cancelled=" + result.cancelled() + " answer=" + result.answer());
        if (callback == null) return;
        if (result.cancelled()) {
            callback.accept(result);
            return;
        }
        var stripped = ComponentUtil.stripColor(result.answer()).trim();
        if (stripped.equalsIgnoreCase(plugin.getConfigLoader().getConfig().cancelKeyword())) {
            plugin.getPluginLogger().debug("Anvil result matched cancel keyword for " + player.getName());
            callback.accept(ScreenResult.cancel());
            return;
        }
        callback.accept(ScreenResult.answer(stripped));
    }
}
