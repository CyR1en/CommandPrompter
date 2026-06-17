package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenProvider;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptpaper.CommandPrompter;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * Base class for prompt screens that delegate to a {@link ScreenProvider}
 * NMS implementation, falling back to a chat prompt if no provider is available.
 */
public abstract class AbstractWrapperPromptScreen implements InputScreen {

    protected final CommandPrompter plugin;
    protected final Player player;
    protected final String displayText;
    protected final List<ScreenProvider> providers;
    protected InputScreen wrapped;
    protected Consumer<ScreenResult> callback;
    protected boolean open;

    protected AbstractWrapperPromptScreen(CommandPrompter plugin, Player player, String displayText, List<ScreenProvider> providers) {
        this.plugin = plugin;
        this.player = player;
        this.displayText = displayText;
        this.providers = providers;
    }

    /**
     * Creates a {@link ChatPromptScreen} fallback wired to this screen's result callback.
     */
    protected InputScreen fallbackToChat() {
        plugin.getPluginLogger().debug("Falling back to chat prompt for " + player.getName());
        var fallback = new ChatPromptScreen(plugin, player, new dev.cyr1en.promptpaper.preset.ChatPrompt("chat", "inline-fallback", displayText, new dev.cyr1en.promptpaper.preset.CancelBehavior(false, "", false, ""), true));
        fallback.onResult(this::handleResult);
        return fallback;
    }

    /**
     * Subclasses implement this to transform the wrapped screen's result before forwarding.
     */
    protected abstract void handleResult(ScreenResult result);

    @Override
    public void close() {
        open = false;
        if (wrapped != null) wrapped.close();
    }

    @Override
    public boolean isOpen() { return open; }

    @Override
    public void onResult(Consumer<ScreenResult> callback) { this.callback = callback; }
}
