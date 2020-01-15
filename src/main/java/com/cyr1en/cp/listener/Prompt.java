package com.cyr1en.cp.listener;

import com.cyr1en.cp.CommandPrompter;
import com.cyr1en.cp.api.Dispatcher;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Prompt implements Listener {

  private CommandPrompter plugin;
  private Player sender;
  private LinkedList<String> prompts;
  private String message;
  private ScheduledExecutorService scheduler;
  private String cancelKey;

  public Prompt(CommandPrompter plugin, Player sender, LinkedList<String> prompts, String message) {
    this.plugin = plugin;
    this.sender = sender;
    this.prompts = prompts;
    this.message = message;
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    this.cancelKey = plugin.getConfiguration().getString("Cancel-Keyword");
    int timeout = plugin.getConfiguration().getInt("Prompt-Timeout");

    scheduler.schedule(this::cancel, timeout, TimeUnit.SECONDS);
    sendPrompt();
  }

  private void sendPrompt() {
    String regex = plugin.getConfiguration().getString("Argument-Regex").trim();

    String parsedEscapedRegex = (String.valueOf(regex.charAt(0)) + regex.charAt(regex.length() - 1))
            .replaceAll("[^\\w\\s]", "\\\\$0");
    String prompt = prompts.get(0).replaceAll("[" + parsedEscapedRegex + "]", "");

    if (prompt.contains("-a ")) {
      prompt = prompt.replaceAll("-a ", "").trim();
      List<String> parts = Arrays.stream(prompt.split("\\{br}")).map(s ->
              ChatColor.translateAlternateColorCodes('&', s)).collect(Collectors.toList());

      ItemStack item = new ItemStack(Material.PAPER);
      ItemMeta meta = item.getItemMeta();
      meta.addEnchant(Enchantment.LURE, 1, true);
      meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
      meta.setDisplayName(parts.get(0));
      if (parts.size() > 1)
        meta.setLore(parts.subList(1, parts.size()));
      item.setItemMeta(meta);

      new AnvilGUI.Builder().plugin(plugin)
              .onCloseOnEscape(p -> cancel())
              .onClose(p -> {
                if (prompts.size() > 0)
                  sendPrompt();
              })
              .onComplete(this::onAnvilComplete)
              .itemStack(item).open(this.sender);
    } else {
      regularPrompt(prompt);
    }
  }

  private AnvilGUI.Response onAnvilComplete(Player player, String res) {
    if (res.equalsIgnoreCase(cancelKey)) {
      cancel();
    } else if (prompts.size() > 1) {
      String currPrompt = prompts.poll();
      message = message.replace(currPrompt, res);
    } else if (prompts.size() == 1) {
      String currPrompt = prompts.poll();
      message = message.replace(currPrompt, res);
      dispatch(sender, message);
      scheduler.shutdownNow();
    }
    return AnvilGUI.Response.close();
  }

  private void regularPrompt(String prompt) {
    List<String> parts = Arrays.asList(prompt.split("\\{br}"));
    String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
    parts.forEach(part -> sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + part)));
  }

  public Player getSender() {
    return sender;
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChat(AsyncPlayerChatEvent event) {
    if (!event.getPlayer().equals(sender))
      return;
    Bukkit.getScheduler().runTask(plugin, () -> process(event.getPlayer(), event.getMessage()));
    event.setCancelled(true);
  }

  private void process(Player sender, String response) {
    if (response.equalsIgnoreCase(cancelKey)) {
      cancel();
    } else if (prompts.size() > 1) {
      String currPrompt = prompts.poll();
      message = message.replace(currPrompt, response);
      sendPrompt();
    } else if (prompts.size() == 1) {
      String currPrompt = prompts.poll();
      message = message.replace(currPrompt, response);
      dispatch(sender, message);
      scheduler.shutdownNow();
    }
  }

  private void cancel() {
    if (!prompts.isEmpty() && plugin.inCommandProcess(sender)) {
      prompts.clear();
      plugin.deregisterPrompt(this);
      String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
      String cMsg = plugin.getI18N().getProperty("PromptCancel");
      sender.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + cMsg));
    }
  }

  private void dispatch(Player sender, String command) {
    Dispatcher.dispatchCommand(plugin, sender, command);
    plugin.deregisterPrompt(this);
  }

}