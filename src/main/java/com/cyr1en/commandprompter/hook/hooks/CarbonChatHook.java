package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptManager;
import net.draycia.carbon.api.CarbonChatProvider;
import net.draycia.carbon.api.event.events.CarbonChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.Objects;

@TargetPlugin(pluginName = "CarbonChat")
public class CarbonChatHook extends BaseHook implements Listener {
    private final PromptManager promptManager;

    public CarbonChatHook(CommandPrompter plugin) {
        super(plugin);
        this.promptManager = plugin.getPromptManager();
    }

    public boolean subscribe() {
        var cc = CarbonChatProvider.carbonChat();
        if (cc == null) {
            getPlugin().getPluginLogger().warn("No CarbonChat was provided, using default chat listener...");
            return false;
        }
        cc.eventHandler().subscribe(CarbonChatEvent.class, -100, false, this::handle);
        return true;
    }

    public void handle(CarbonChatEvent event) {
        var player = Bukkit.getPlayer(event.sender().uuid());
        if (Objects.isNull(player) || !promptManager.getPromptRegistry().inCommandProcess(player))
            return;
        event.cancelled(true);
        event.recipients().clear();
        Arrays.stream(event.getClass().getDeclaredMethods()).forEach(method -> getPlugin().getPluginLogger().debug(method.toGenericString()));
        var msg = event.message();
        var serializedMsg = PlainTextComponentSerializer.plainText().serialize(msg);
        var cancel = getPlugin().getConfiguration().cancelKeyword();

        if (cancel.equalsIgnoreCase(serializedMsg)) {
            promptManager.cancel(player, PromptManager.CancelReason.Manual);
            event.message(Component.empty());
            return;
        }

        var queue = promptManager.getPromptRegistry().get(player);
        if (Objects.isNull(queue))
            return;

        var prompt = queue.peek();
        if (Objects.nonNull(prompt)) {
            var ds = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());
            serializedMsg = prompt.sanitizeInput() ? ds : serializedMsg;
        }
        var ctx = new PromptContext.Builder()
                .setCommandSender(player)
                .setPromptedPlayer(player)
                .setContent(serializedMsg).build();
        Bukkit.getScheduler().runTask(getPlugin(), () -> promptManager.processPrompt(ctx));
    }
}
