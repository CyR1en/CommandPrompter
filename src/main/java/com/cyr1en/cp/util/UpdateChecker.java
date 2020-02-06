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
import java.util.logging.Level;

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
      HttpURLConnection connection = buildConnection(Objects.requireNonNull(stringAsUrl()));
      InputStreamReader ir = new InputStreamReader(connection.getInputStream());
      BufferedReader br = new BufferedReader(ir);
      version = br.readLine();
    } catch (IOException e) {
      plugin.getLogger().log(Level.WARNING, "An error occurred while getting update data: ''{0}''!", e.getCause());
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
    String formatted = String.format(API_URL, resourceID);
    try {
      return new URL(formatted);
    } catch (MalformedURLException e) {
      plugin.getLogger().log(Level.WARNING, "''{0}'' is a malformed URL!", formatted);
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