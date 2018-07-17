package com.cyr1en.cp.listener;

import com.cyr1en.cp.CommandPrompter;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Prompt implements Listener {

    private CommandPrompter plugin;
    private Player sender;
    private List<String> prompts;
    private String message;
    private ScheduledExecutorService scheduler;

    public Prompt(CommandPrompter plugin, Player sender, List<String> prompts, String message) {
        this.plugin = plugin;
        this.sender = sender;
        this.prompts = prompts;
        this.message = message;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        sendPrompt();
    }

    private void sendPrompt() {
        int timeout = plugin.getConfiguration().getInt("Prompt-Timeout");
        scheduler.schedule(() -> {
            cancel();
            String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
            String cMsg = plugin.getConfiguration().getString("Timeout-Message");
            sender.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + cMsg));
        }, timeout, TimeUnit.SECONDS);
        String prompt = prompts.get(0).replaceAll("<", "");
        prompt = prompt.replaceAll(">", "");
        List<String> parts = Arrays.asList(prompt.split("\\{br}"));
        String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
        parts.forEach(part -> sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + part)));
    }

    @EventHandler (priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(sender))
            return;
        if (event.getMessage().equalsIgnoreCase("cancel")) {
            cancel();
            String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
            String cMsg = plugin.getConfiguration().getString("Timeout-Message");
            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + cMsg));
            event.setCancelled(true);
        } else if (prompts.size() > 1) {
            prompts.set(0, prompts.get(0).replaceAll("[^\\w\\s<>]", "\\\\$0"));
            message = message.replaceAll(prompts.get(0), event.getMessage());
            prompts.remove(0);
            sendPrompt();
            event.setCancelled(true);
        } else if (prompts.size() == 1) {
            prompts.set(0, prompts.get(0).replaceAll("[^\\w\\s<>]", "\\\\$0"));
            message = message.replaceAll(prompts.get(0), event.getMessage());
            prompts.remove(0);
            dispatch(sender, message);
            event.setCancelled(true);
        }
    }

    private void cancel() {
        prompts.clear();
        HandlerList.unregisterAll(this);
    }

    private void dispatch(Player sender, String command) {
        new BukkitRunnable() {
            public void run() {
                sender.chat(command);
            }
        }.runTask(plugin);
        HandlerList.unregisterAll(this);
    }

}