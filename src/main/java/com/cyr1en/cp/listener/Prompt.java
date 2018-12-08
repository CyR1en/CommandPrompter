package com.cyr1en.cp.listener;

import com.cyr1en.cp.CommandPrompter;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
    scheduler.schedule(this::cancel, timeout, TimeUnit.SECONDS);
    String prompt = prompts.get(0).replaceAll("[<>]", "");
    if (prompt.contains("-a ")) {
      System.out.print("Prompt-type: Anvil");
      prompt = prompt.replaceAll("-a ", "").trim();
      new AnvilGUI(plugin, sender, prompt, (player, response) -> {
        process(player, response);
        return null;
      });
    } else {
      System.out.print("Prompt-type: Anvil");
      List<String> parts = Arrays.asList(prompt.split("\\{br}"));
      String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
      parts.forEach(part -> sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + part)));
    }
  }

  public Player getSender() {
    return sender;
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChat(AsyncPlayerChatEvent event) {
    if (!event.getPlayer().equals(sender))
      return;
    process(event.getPlayer(), event.getMessage());
    event.setCancelled(true);
  }

  private void process(Player sender, String response) {
    if (response.equalsIgnoreCase("cancel")) {
      cancel();
    } else if (prompts.size() > 1) {
      prompts.set(0, prompts.get(0).replaceAll("[^\\w\\s<>]", "\\\\$0")); //escape characters
      message = message.replaceAll(prompts.get(0), response);
      prompts.remove(0);
      sendPrompt();
    } else if (prompts.size() == 1) {
      prompts.set(0, prompts.get(0).replaceAll("[^\\w\\s<>]", "\\\\$0")); //escape characters
      message = message.replaceAll(prompts.get(0), response);
      prompts.remove(0);
      dispatch(sender, message);
      scheduler.shutdownNow();
    }
  }

  private void cancel() {
    if(!prompts.isEmpty() && plugin.inCommandProcess(sender)) {
      prompts.clear();
      plugin.deregisterPrompt(this);
      String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
      String cMsg = plugin.getConfiguration().getString("Timeout-Message");
      sender.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + cMsg));
    }
  }

  private void dispatch(Player sender, String command) {
    new BukkitRunnable() {
      public void run() {
        sender.chat(command);
      }
    }.runTask(plugin);
    plugin.deregisterPrompt(this);
  }

}