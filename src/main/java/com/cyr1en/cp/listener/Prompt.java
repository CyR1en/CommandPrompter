/*
 * MIT License
 *
 * Copyright (c) 2020 Ethan Bacurio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cyr1en.cp.listener;

import com.cyr1en.cp.CommandPrompter;
import com.cyr1en.cp.PromptRegistry;
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

    boolean op = false;
    if (prompt.contains("-op ")) {
      prompt = prompt.replaceAll("-op ", "").trim();
      op = true;
    }
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
    if (!prompts.isEmpty() && PromptRegistry.inCommandProcess(sender)) {
      prompts.clear();
      PromptRegistry.deregisterPrompt(this);
      String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
      String cMsg = plugin.getI18N().getProperty("PromptCancel");
      sender.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + cMsg));
    }
  }

  public CommandPrompter getPlugin() {
    return plugin;
  }

  private void dispatch(Player sender, String command) {
    Dispatcher.dispatchCommand(plugin, sender, command);
    PromptRegistry.deregisterPrompt(this);
  }

}