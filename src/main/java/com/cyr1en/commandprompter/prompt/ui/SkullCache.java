package com.cyr1en.commandprompter.prompt.ui;


import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.hook.hooks.SuperVanishHook;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static List<ItemStack> getSortedSkulls(ArrayList<ItemStack> skullList) {
        @SuppressWarnings("unchecked")
        var copy = (ArrayList<ItemStack>) skullList.clone();
        copy.sort((s1, s2) -> {
            var n1 = Util.stripColor(Objects.requireNonNull(s1.getItemMeta()).getDisplayName());
            var n2 = Util.stripColor(Objects.requireNonNull(s2.getItemMeta()).getDisplayName());
            return n1.compareToIgnoreCase(n2);
        });
        return copy;
    }

    public static List<ItemStack> getSkullsSorted() {
        return getSortedSkulls(skulls);
    }

    public static List<ItemStack> getSkullsFor(List<Player> players) {
        var result = new ArrayList<ItemStack>();
        for (Player player : players) {
            CommandPrompter.getInstance().getPluginLogger().debug("Player: " + player);
            var is = skulls.stream()
                    .filter(i -> Util.stripColor(i.getItemMeta().getDisplayName()).equals(player.getName()))
                    .findFirst();
            is.ifPresent(result::add);
        }
        return result;
    }

    public static List<ItemStack> getSkullsSortedFor(List<Player> players) {
        return getSortedSkulls((ArrayList<ItemStack>) getSkullsFor(players));
    }

    public static List<ItemStack> getSkulls() {
        return skulls;
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onPlayerLogin(PlayerLoginEvent e) {
        var isInv = new AtomicBoolean(false);
        var svHook = plugin.getHookContainer().getHook(SuperVanishHook.class);
        plugin.getPluginLogger().debug("SV Hooked: " + svHook.isHooked());
        svHook.ifHooked(hook -> {
            if(hook.isInvisible(e.getPlayer()))
                isInv.set(true);
        });
        if(isInv.get()) {
            plugin.getPluginLogger().debug("Player is vanished (SuperVanish) skipping skull cache");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> SkullCache.cachePlayer(e.getPlayer()));
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onPlayerQuit(PlayerQuitEvent e) {
        SkullCache.unCachePlayer(e.getPlayer().getName());
    }
}