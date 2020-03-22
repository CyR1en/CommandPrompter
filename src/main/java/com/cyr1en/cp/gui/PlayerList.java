package com.cyr1en.cp.gui;


import com.cyr1en.cp.CommandPrompter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PlayerList {
    // All player list inventory in using stored here.
    private static List<PlayerList> playerlists;
    private static List<ItemStack> skulls;


    private Inventory inventory;

    private Player player;
    private Consumer<Player> closeListener;
    private BiConsumer<Player, String> completeListener;
    private String title;

    static {
        skulls = new ArrayList<>();
        playerlists = new ArrayList<>();
    }
    public static void cachePlayer(Player player)
    {
        uncachePlayer(player);
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta= (SkullMeta) skull.getItemMeta();
        skullMeta.setOwningPlayer(player);
        skullMeta.setDisplayName(player.getName());
        skull.setItemMeta(skullMeta);
        skulls.add(skull);

    }

    public static void uncachePlayer(Player player)
    {
        for(ItemStack item:skulls)
        {
            if (item.getItemMeta().getDisplayName().equals(player.getName()))
                skulls.remove(item);
        }
    }

    public PlayerList(CommandPrompter plugin, Player player, String title) {
        this.title = title;
        this.player = player;
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
    public void open()
    {
        open(0);
    }


    public void open(int page) {

        //validate page
        if(page<0)
            page=0;
        int max_page;
        max_page=skulls.size()/45;
        if(skulls.size()%45!=0)
            max_page++;
        if(page>max_page)
            page=max_page;

        //calc begin index and end index of skulls
        int begin = page * 45 ;
        int end = begin + 45;
        if(end > skulls.size())
            end = skulls.size();
        //calc rows
        int rows = (begin - end )/9;
        if((begin-end)%9!=0)
            rows++;

        this.inventory = Bukkit.createInventory(player,rows*9 + 9,title);
        for(int i=0;i<end;i++,begin++)
        {
            inventory.setItem(i,skulls.get(begin));
        }

        player.openInventory(inventory);
        playerlists.add(this);
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
        drop();
    }


}
