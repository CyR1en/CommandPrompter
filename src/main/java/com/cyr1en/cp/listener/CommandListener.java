package com.cyr1en.cp.listener;

import com.cyr1en.cp.CommandPrompter;
import com.cyr1en.cp.PromptRegistry;
import com.cyr1en.cp.util.SRegex;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class CommandListener implements Listener {

    private CommandPrompter plugin;

    public CommandListener(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        process(event.getPlayer(), event, event.getMessage());
    }

    private void process(Player player, Cancellable cancellable, String command) {
        if (plugin.getConfiguration().getBoolean("Enable-Permission") && !player.hasPermission("commandprompter.use")) {
            return;
        }
        if (plugin.inCommandProcess(player.getPlayer())) {
            String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + plugin.getI18N().getProperty("PromptInProgress")));
            cancellable.setCancelled(true);
        } else {
            SRegex simpleRegex = new SRegex(command);
            String regex = plugin.getConfiguration().getString("Argument-Regex").trim();
            String parsedEscapedRegex = (String.valueOf(regex.charAt(0))).replaceAll("[^\\w\\s]", "\\\\$0") +
                    (regex.substring(1, regex.length() - 1)) +
                    (String.valueOf(regex.charAt(regex.length() - 1))).replaceAll("[^\\w\\s]", "\\\\$0");
            simpleRegex.find(Pattern.compile(parsedEscapedRegex));
            List<String> prompts = simpleRegex.getResultsList();
            if (prompts.size() > 0) {
                cancellable.setCancelled(true);
                PromptRegistry.registerPrompt(new Prompt(plugin, player, new LinkedList<>(prompts), command));
            }
        }
    }


}