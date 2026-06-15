package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptcore.PromptTag;
import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.ScreenType;
import dev.cyr1en.promptpaper.screen.dialog.DialogCompletionContext;
import dev.cyr1en.promptpaper.screen.playerui.PlayerUIScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.bukkit.entity.Player;

/**
 * Routes a {@link PromptTag} to the appropriate {@link InputScreen}
 * implementation based on the configured screen-mapping for the tag's key.
 */
public class ScreenRouter {

    private final CommandPrompter plugin;
    private final List<ScreenProvider> providers;

    public ScreenRouter(CommandPrompter plugin) {
        this.plugin = plugin;
        this.providers = new ArrayList<>();
        try {
            var loader = ServiceLoader.load(ScreenProvider.class, plugin.getClass().getClassLoader());
            for (var provider : loader) {
                providers.add(provider);
                plugin.getPluginLogger().info("Loaded screen provider: " + provider.getClass().getName());
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warn("Failed to load screen providers: " + e.getMessage());
        }
        if (providers.isEmpty()) {
            plugin.getLogger().info("No GUI screen providers found — GUI prompts will fall back to chat.");
        }
    }

    public InputScreen create(Player player, PromptTag tag) {
        return create(player, tag, null);
    }

    public InputScreen create(Player player, PromptTag tag, DialogCompletionContext context) {
        var promptConfig = plugin.getConfigLoader().getPromptConfig();
        var mapping = promptConfig.getScreenMappings().getOrDefault(tag.key(), ScreenType.CHAT);
        plugin.getPluginLogger().debug("Screen route for " + player.getName()
                + " key=" + tag.key() + " mapping=" + mapping);
        return switch (mapping) {
            case CHAT -> new ChatPromptScreen(plugin, player, tag.displayText());
            case ANVIL -> new AnvilPromptScreen(plugin, player, tag.displayText(), providers);
            case SIGN -> new SignPromptScreen(plugin, player, tag.displayText(), providers);
            case DIALOG -> new DialogPromptScreen(plugin, player, tag, promptConfig, context);
            case PLAYER -> new PlayerUIScreen(plugin, player, tag);
        };
    }
}
