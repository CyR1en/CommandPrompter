package dev.cyr1en.promptpaper.screen;

import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptui.ComponentUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;

/**
 * Prompt screen that sends a chat message and captures the player's
 * next chat input as the answer.
 */
public class ChatPromptScreen implements InputScreen {

    private final CommandPrompter plugin;
    private final Player player;
    private final dev.cyr1en.promptpaper.preset.ChatPrompt chatPrompt;
    private final String displayText;
    private Consumer<ScreenResult> callback;
    private boolean open;

    public ChatPromptScreen(CommandPrompter plugin, Player player, dev.cyr1en.promptpaper.preset.ChatPrompt chatPrompt) {
        this.plugin = plugin;
        this.player = player;
        this.chatPrompt = chatPrompt;
        this.displayText = chatPrompt.promptText();
    }

    /**
     * Sends the prompt text (with an optional clickable cancel link) to the player.
     */
    @Override
    public void open() {
        var prefix = plugin.getConfigLoader().getConfig().promptPrefix();
        plugin.getPluginLogger().debug("Opening chat prompt for " + player.getName()
                + " text=" + displayText);

        var promptConfig = plugin.getConfigLoader().getPromptConfig();
        Component cancelComponent = null;
        
        boolean sendCancel = promptConfig.sendCancelText();
        boolean isClickable = promptConfig.sendCancelText();
        String cancelMsg = promptConfig.textCancelMessage();
        String hoverMsg = promptConfig.textCancelHoverMessage();
        
        if (chatPrompt.cancel() != null && !chatPrompt.id().startsWith("inline-")) {
            sendCancel = chatPrompt.cancel().send();
            isClickable = chatPrompt.cancel().clickable();
            if (chatPrompt.cancel().message() != null && !chatPrompt.cancel().message().isBlank()) {
                cancelMsg = chatPrompt.cancel().message();
            }
            if (chatPrompt.cancel().hoverMessage() != null && !chatPrompt.cancel().hoverMessage().isBlank()) {
                hoverMsg = chatPrompt.cancel().hoverMessage();
            }
        }

        if (sendCancel) {
            var builder = ComponentUtil.mini(cancelMsg);
            if (isClickable) {
                builder = builder.clickEvent(ClickEvent.runCommand("/cmdp " + plugin.getConfigLoader().getConfig().cancelKeyword()))
                        .hoverEvent(HoverEvent.showText(ComponentUtil.mini(hoverMsg)));
            }
            cancelComponent = builder;
        }

        var lines = displayText.split("\\{br\\}");
        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];
            var linePrefix = (i == 0) ? prefix : "";
            var lineCancel = (i == lines.length - 1) ? cancelComponent : null;
            var lineComponent = Objects.isNull(lineCancel) ? ComponentUtil.mini(linePrefix + line)
                    : ComponentUtil.mini(linePrefix + line).append(lineCancel);
            player.sendMessage(lineComponent);
        }
        open = true;
    }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        plugin.getPluginLogger().debug("Chat prompt closed for " + player.getName());
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void onResult(Consumer<ScreenResult> callback) {
        this.callback = callback;
    }

    /**
     * Captures the player's next chat message and delivers it as the screen result.
     */
    void handleInput(String input) {
        if (!open) {
            plugin.getPluginLogger().debug("Chat input for " + player.getName() + " but screen not open");
            return;
        }
        plugin.getPluginLogger().debug("Chat input from " + player.getName() + ": " + input);
        open = false;
        if (callback != null) {
            callback.accept(ScreenResult.answer(input));
        }
    }
}
