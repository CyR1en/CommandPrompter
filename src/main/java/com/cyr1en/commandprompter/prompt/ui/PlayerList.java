package com.cyr1en.commandprompter.prompt.ui;


import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PlayerList {
    // All player list inventory in using stored here.
    private static List<PlayerList> playerlists;
    private static List<ItemStack> skulls;
    int page = 0;

    private Inventory inventory;

    private Player player;

    public Consumer<Player> getCloseListener() {
        return closeListener;
    }

    private Consumer<Player> closeListener;
    private BiConsumer<Player, String> completeListener;
    CommandPrompter plugin;

    static {
        skulls = new ArrayList<>();
        playerlists = new ArrayList<>();

    }

    public PlayerList(CommandPrompter plugin, Player player, String title) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(player, 54, Util.color(title));
        PlayerList.setFormat(plugin.getPromptConfig().skullNameFormat());
    }

    public static void cachePlayer(Player player) {
        uncachePlayer(player.getName());
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        skullMeta.setOwningPlayer(player);
        var name = String.format(format, player.getName());
        skullMeta.setDisplayName(Util.color(name));
        skull.setItemMeta(skullMeta);
        skulls.add(skull);
    }

    public static void setFormat(String format) {
        PlayerList.format = format;
    }

    public static void uncachePlayer(String player) {
        for (ItemStack item : skulls) {
            String name = item.getItemMeta().getDisplayName();
            if (name.equals(player)) {
                skulls.remove(item);
                return;
            }

        }
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public PlayerList onClose(Consumer<Player> closeListener) {
        this.closeListener = closeListener;
        return this;
    }

    public PlayerList onComplete(BiConsumer<Player, String> completeListener) {
        this.completeListener = completeListener;
        return this;
    }

    public void freshPage(int aPage) {
        inventory.clear();

        page = aPage;
        //validate page
        if (page < 0)
            page = 0;
        int max_page;
        max_page = skulls.size() / 45 - 1;
        if (skulls.size() % 45 != 0)
            max_page++;

        if (page > max_page)
            page = max_page;

        //calc begin index and end index of skulls
        int begin = page * 45;
        int end = begin + 45;
        if (end > skulls.size())
            end = skulls.size();
        //calc rows

        if (plugin.getPromptConfig().sorted())
            skulls.sort((s1, s2) -> {
                var n1 = Util.stripColor(s1.getItemMeta().getDisplayName());
                var n2 = Util.stripColor(s2.getItemMeta().getDisplayName());
                return n1.compareTo(n2);
            });

        for (int i = 0; i < end; i++, begin++) {
            inventory.setItem(i, skulls.get(begin));
        }

        //prev page button
        var prevString = plugin.getPromptConfig().previousItem();
        ItemStack prev = new ItemStack(Util.getCheckedMaterial(prevString, Material.FEATHER));
        ItemMeta prevMeta = prev.getItemMeta();
        prevMeta.setDisplayName(Util.color(plugin.getPromptConfig().previousText()));
        prev.setItemMeta(prevMeta);
        inventory.setItem(9 * 5 + 2, prev);

        //next page button
        var nextString = plugin.getPromptConfig().nextItem();
        ItemStack next = new ItemStack(Util.getCheckedMaterial(nextString, Material.FEATHER));
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.setDisplayName(Util.color(plugin.getPromptConfig().nextText()));
        next.setItemMeta(nextMeta);
        inventory.setItem(9 * 5 + 6, next);
    }


    public void open() {
        freshPage(0);
        player.openInventory(inventory);
        playerlists.add(this);
    }

    public void nextPage() {
        freshPage(page + 1);
    }

    public void prevPage() {
        freshPage(page - 1);
    }

    public static List<PlayerList> getPlayerlists() {
        return playerlists;
    }

    public void drop() {
        playerlists.remove(this);
    }

    public void close() {
        closeListener.accept(player);
    }

    public void complete(String str) {
        completeListener.accept(player, str);
    }


}