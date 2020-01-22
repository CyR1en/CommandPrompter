package com.cyr1en.cp.util;


import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

public class UpdateChecker implements Listener {

  private static final String RESOURCE_URL = "https://www.spigotmc.org/resources/%s.%s/";
  private static final String API_URL = "https://api.spigotmc.org/legacy/update.php?resource=%s";

  private JavaPlugin plugin;

  private String resourceName;
  private int resourceID;
  private boolean isDisabled;

  public UpdateChecker(JavaPlugin plugin, int resourceID) {
    this.plugin = plugin;
    resourceName = plugin.getDescription().getName().toLowerCase();
    this.resourceID = resourceID;
    isDisabled = !plugin.getConfig().getBoolean("Update-Checker");
  }

  public boolean isDisabled() {
    return isDisabled;
  }

  public String getCurrVersion() {
    String version = "0.0.0";
    try{
      InputStreamReader ir = new InputStreamReader(buildConnection(Objects.requireNonNull(stringAsUrl())).getInputStream());
      BufferedReader br = new BufferedReader(ir);
      version = br.readLine();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return version;
  }

  public boolean newVersionAvailable() {
    String curr = plugin.getDescription().getVersion().replaceAll("[a-zA-z ]|:", "").replaceAll("\\.", "");
    String arg = getCurrVersion().replaceAll("[a-zA-z ]|:", "").replaceAll("\\.", "");
    int iCurr = Integer.parseInt(curr);
    int iArg = Integer.parseInt(arg);
    return iArg > iCurr;
  }

  private URL stringAsUrl() {
    try {
      return new URL(String.format(API_URL, resourceID));
    } catch (MalformedURLException e) {
      System.out.println(UpdateChecker.API_URL);
      return null;
    }
  }

  private HttpURLConnection buildConnection(URL url) {
    try {
      HttpURLConnection.setFollowRedirects(true);
      HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
      httpURLConnection.setRequestMethod("GET");

      httpURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
              "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Safari/537.36");
      return httpURLConnection;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onJoin(PlayerJoinEvent event) {
    Bukkit.getServer().getScheduler().runTaskAsynchronously(plugin, () -> sendUpdateAvailableMessage(event.getPlayer()));
  }

  public void sendUpdateAvailableMessage(CommandSender sender) {
    if (!newVersionAvailable())
      return;
    String v = getCurrVersion().replaceAll("[a-zA-z: ]", "");
    if (sender instanceof Player && sender.isOp()) {
      try {
        if (Class.forName("org.spigotmc.SpigotConfig") != null) {
          BaseComponent[] textComponent = new ComponentBuilder("[")
                  .color(ChatColor.GOLD)
                  .append("CommandPrompter")
                  .color(ChatColor.GREEN)
                  .append("]")
                  .color(ChatColor.GOLD)
                  .append(" A new update is available: ")
                  .color(ChatColor.AQUA)
                  .append(v)
                  .color(ChatColor.YELLOW)
                  .event(new ClickEvent(ClickEvent.Action.OPEN_URL, String.format(RESOURCE_URL, resourceName, resourceID)))
                  .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click here download the new version.").create()))
                  .create();
          sender.spigot().sendMessage(textComponent);
        }
      } catch (ClassNotFoundException e) {
        String msg = org.bukkit.ChatColor.translateAlternateColorCodes('&', "&6[&aCommandPrompter&6] &bA new update is available: &e" + v);
        Objects.requireNonNull(sender).sendMessage(msg);
      }
    }
  }
}