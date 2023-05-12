package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import com.cyr1en.commandprompter.prompt.prompts.ChatPrompt;
import es.capitanpuerka.puerkaschat.events.PuerkasChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@TargetPlugin(pluginName = "PuerkasChat")
public class PuerkasChatHook implements Listener {
    private final CommandPrompter plugin;

    private PuerkasChatHook(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(PuerkasChatEvent event) {
        plugin.getPluginLogger().debug("PuerkasChatEvent Listener Invoked");
        var responseHandler = new ChatPrompt.ResponseHandler(this.plugin);
        responseHandler.onResponse(event.getPlayer(), event.getChatMessage(), event);
    }
}
