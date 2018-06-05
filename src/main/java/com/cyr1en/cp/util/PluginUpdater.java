package com.cyr1en.cp.util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;

/**
 *
 * @author Benjamin | Bentipa
 * @version 1.0 Created on 03.04.2015
 *
 */
public class PluginUpdater implements Listener {

    private URL url;
    private final JavaPlugin plugin;

    private boolean canceled;

    private String version;
    private String downloadURL;
    private String changeLog;
    private boolean out;

    public PluginUpdater(JavaPlugin plugin, String pluginUrl) {
        this.plugin = plugin;
        try {
            url = new URL(pluginUrl);
        } catch (MalformedURLException e) {
            canceled = true;
            plugin.getLogger().log(Level.WARNING, "Error: Bad URL while checking {0} !", plugin.getName());
        }
        out = false;
        canceled = false;
    }

    private void queryUpdateData() {
        try {
            Document doc = Jsoup.connect(url.toString()).get();
            version = doc.getElementById("pl-version").text();
            downloadURL = doc.getElementById("links-direct").text();
            changeLog = doc.getElementById("pl-changelog").text();
            changeLog = checkChangeLog();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Error querying update data for ''{0}''!", plugin.getName());
            plugin.getLogger().log(Level.WARNING, "Error: ", e);
        }
    }

    public boolean needsUpdate() {
        queryUpdateData();
        if (canceled)
            return false;
        if (newVersionAvailable()) {
            if (out) {
                plugin.getLogger().log(Level.INFO, "- New Version found: {0}", version);
                plugin.getLogger().log(Level.INFO, "- Download it here: {0}", downloadURL);
                plugin.getLogger().log(Level.INFO, "- Changelog: {0}", changeLog);
            }
            return true;
        }
        return false;
    }


    private boolean newVersionAvailable() {
        queryUpdateData();
        String curr = plugin.getDescription().getVersion().replaceAll("\\.", "");
        String arg = version.replaceAll("[a-zA-z ]|:", "").replaceAll("\\.", "");
        int iCurr = Integer.valueOf(curr);
        int iArg = Integer.valueOf(arg);
        return iArg > iCurr;
    }

    public void update() {
        try {
            URL download = new URL(downloadURL);
            if (out)
                plugin.getLogger().log(Level.INFO, "Trying to download {0} ..", downloadURL);
            try {
                File file = new File("plugins/" + plugin.getName() + "-" + version.replaceAll("[a-zA-z ]|:", "") + ".jar");
                moveOldPluginJar();
                FileUtils.copyURLToFile(download, file);
                if (out) {
                    Bukkit.getLogger().info("Successfully downloaded update");
                    Bukkit.getLogger().info("Restart server to apply changes");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void moveOldPluginJar() {
        Bukkit.getServer().getPluginManager().disablePlugin(plugin);
        File pluginsFolder = new File("plugins");
        File[] files = pluginsFolder.listFiles();
        ArrayList<File> fList = new ArrayList<>(Arrays.asList(files == null ? new File[]{} : files));
        for (File f : fList) {
            try {
                if (com.cyr1en.cp.util.FileUtil.isJarFile(f)) {
                    InputStream inputStream = plugin.getClass().getResourceAsStream("/plugin.yml");
                    Yaml yaml = new Yaml();
                    HashMap<String, String> manifest = yaml.load(inputStream);
                    if (manifest.get("name").equals(plugin.getDescription().getName())) {
                        File oldFolder = new File("plugins/" + plugin.getName() + "/old");
                        if (!oldFolder.exists())
                            oldFolder.mkdir();
                        if (f.renameTo(new File(oldFolder.getPath() + "/" + f.getName()))) {
                            f.delete();
                            return;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String checkChangeLog() {
        if (changeLog == null)
            return "";
        return changeLog.length() > 32 ? "Changelog too long, visit " + url.toString() + " to read it." : changeLog;
    }

    public void setOut(boolean out) {
        this.out = out;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        if(!newVersionAvailable())
            return;
        String v = version.replaceAll("[a-zA-z: ]", "");
        if(event.getPlayer().isOp()) {
            try {
                if(Class.forName("org.spigotmc.SpigotConfig") != null) {
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
                            .event(new ClickEvent(ClickEvent.Action.OPEN_URL, downloadURL))
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click here to update").create()))
                            .create();
                    event.getPlayer().spigot().sendMessage(textComponent);
                }
            } catch (ClassNotFoundException e) {
                String msg = org.bukkit.ChatColor.translateAlternateColorCodes('&', "&6[&aCommandPrmpter&6] &bA new update is available: &e" + v);
                event.getPlayer().sendRawMessage(msg);
            }
        }
    }
}