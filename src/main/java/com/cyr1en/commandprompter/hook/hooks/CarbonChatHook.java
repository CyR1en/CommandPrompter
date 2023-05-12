package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptManager;
import com.cyr1en.commandprompter.prompt.PromptParser;
import com.cyr1en.commandprompter.prompt.prompts.ChatPrompt;
import net.draycia.carbon.api.CarbonChatProvider;
import net.draycia.carbon.api.events.CarbonChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

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
        if (Objects.isNull(player) || !promptManager.getPromptRegistry().inCommandProcess(player)) return;
        event.result(new CarbonChatEvent.Result(true, Component.empty()));
        var msg = PlainTextComponentSerializer.plainText().serialize(event.message());
        var cancel = getPlugin().getConfiguration().cancelKeyword();

        if (cancel.equalsIgnoreCase(msg)) {
            promptManager.cancel(player);
            return;
        }

        var queue = promptManager.getPromptRegistry().get(player);
        if (Objects.isNull(queue)) return;

        var prompt = queue.peek();
        if (Objects.nonNull(prompt)) {
            var ds = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());
            msg = prompt.getArgs().contains(PromptParser.PromptArgument.DISABLE_SANITATION) ?
                    ds : msg;
        }
        var ctx = new PromptContext(null, player, msg);
        Bukkit.getScheduler().runTask(getPlugin(), () -> promptManager.processPrompt(ctx));
    }
}
