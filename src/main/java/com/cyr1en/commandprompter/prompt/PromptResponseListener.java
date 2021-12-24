package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.CommandPrompter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;


public record PromptResponseListener(PromptManager manager, CommandPrompter plugin) implements Listener {

    public PromptManager getManager() {
        return this.manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!manager.getPromptRegistry().inCommandProcess(event.getPlayer()))
            return;

        var message = ChatColor.stripColor(
                ChatColor.translateAlternateColorCodes('&', event.getMessage()));
        var cancelKeyword = plugin.getConfiguration().cancelKeyword();

        if (cancelKeyword.equalsIgnoreCase(message))
            manager.cancel(event.getPlayer());
        var ctx = new PromptContext(event, event.getPlayer(), message);
        Bukkit.getScheduler().runTask(plugin, () -> manager.processPrompt(ctx));
        event.setCancelled(true);
    }
}
