package com.cyr1en.cp.listener;

import com.cyr1en.cp.CommandPrompter;
import com.cyr1en.cp.util.SRegex;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.server.ServerCommandEvent;

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
    System.out.println(event.getClass().getSimpleName() + " caught");
    process(event.getPlayer(), event, event.getMessage());
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onServerCommand(ServerCommandEvent event) {
    System.out.println(event.getClass().getSimpleName() + " caught");
    CommandSender sender = event.getSender();
    if (sender instanceof Player)
      process((Player) sender, event, event.getCommand());
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onCommand(ServerCommandEvent event) {
    System.out.println(event.getClass().getSimpleName() + " caught");
    CommandSender sender = event.getSender();
    if (sender instanceof Player)
      process((Player) sender, event, event.getCommand());
  }


  @EventHandler(priority = EventPriority.LOWEST)
  public void onEvent(PlayerEggThrowEvent event) {
    System.out.println("Egg thrown");
    Bukkit.getServer().dispatchCommand(event.getPlayer(), "gamemode creative");
  }

  private void process(Player player, Cancellable cancellable, String command) {
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
        plugin.registerPrompt(new Prompt(plugin, player, new LinkedList<>(prompts), command));
      }
    }
  }


}