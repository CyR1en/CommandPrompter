package com.cyr1en.commandprompter.prompt.ui;


import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public record SkullCache(CommandPrompter plugin) implements Listener {
    // All player list inventory in using stored here.
    private static final ArrayList<ItemStack> skulls;
    private static String format;

    static {
        skulls = new ArrayList<>();
        format = "%s";
    }

    public static void cachePlayer(Player player) {
        unCachePlayer(player.getName());
        var logger = CommandPrompter.getInstance().getPluginLogger();
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        var skullMeta = SkullCache.makeSkullMeta(player, logger);
        skull.setItemMeta(skullMeta);
        skulls.add(skull);
    }

    private static SkullMeta makeSkullMeta(Player owningPlayer, PluginLogger logger) {
        var skullMeta = (SkullMeta) Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD);
        Objects.requireNonNull(skullMeta).setOwningPlayer(owningPlayer);
        SkullCache.setFormat(CommandPrompter.getInstance().getPromptConfig().skullNameFormat());
        var name = String.format(format, owningPlayer.getName());
        skullMeta.setDisplayName(Util.color(name));
        logger.debug("Skull Meta: {%s. %s}", skullMeta.getDisplayName(), skullMeta.getOwningPlayer());
        return skullMeta;
    }

    public static void setFormat(String format) {
        SkullCache.format = format;
    }

    public static void unCachePlayer(String player) {
        skulls.removeAll(skulls.stream().filter(i ->
                Util.stripColor(Objects.requireNonNull(i.getItemMeta()).getDisplayName()).equals(player)).toList());
    }

    public static List<ItemStack> getSkullsSorted() {
        @SuppressWarnings("unchecked")
        var copy = (ArrayList<ItemStack>) skulls.clone();
        copy.sort((s1, s2) -> {
            var n1 = Util.stripColor(Objects.requireNonNull(s1.getItemMeta()).getDisplayName());
            var n2 = Util.stripColor(Objects.requireNonNull(s2.getItemMeta()).getDisplayName());
            return n1.compareToIgnoreCase(n2);
        });
        return copy;
    }

    public static List<ItemStack> getSkulls() {
        return skulls;
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onPlayerLogin(PlayerLoginEvent e) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> SkullCache.cachePlayer(e.getPlayer()));
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onPlayerQuit(PlayerQuitEvent e) {
        SkullCache.unCachePlayer(e.getPlayer().getName());
    }
}