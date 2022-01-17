package com.cyr1en.commandprompter.prompt.ui;


import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.util.Util;
import com.cyr1en.kiso.utils.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PlayerList {
    // All player list inventory in using stored here.
    private static final List<PlayerList> playerList;
    private static final List<ItemStack> skulls;
    private static String format;

    private static final int DEFAULT_SIZE = 54;
    private static final int DEFAULT_PREV_COL = 2;
    private static final int DEFAULT_NEXT_COL = 6;
    private static final Pair<Integer, Integer> DEFAULT_BUTTON_POS = new Pair<>(DEFAULT_PREV_COL, DEFAULT_NEXT_COL);

    static {
        skulls = new ArrayList<>();
        playerList = new ArrayList<>();
        format = "%s";
    }

    public static void cachePlayer(Player player) {
        unCachePlayer(player.getName());
        var logger = CommandPrompter.getInstance().getPluginLogger();
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        var skullMeta = PlayerList.makeSkullMeta(player, logger);
        skull.setItemMeta(skullMeta);
        skulls.add(skull);
    }

    private static SkullMeta makeSkullMeta(Player owningPlayer, PluginLogger logger) {
        var skullMeta = (SkullMeta) Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD);
        Objects.requireNonNull(skullMeta).setOwningPlayer(owningPlayer);
        PlayerList.setFormat(CommandPrompter.getInstance().getPromptConfig().skullNameFormat());
        var name = String.format(format, owningPlayer.getName());
        skullMeta.setDisplayName(Util.color(name));
        logger.debug("Skull Meta: {%s. %s}", skullMeta.getDisplayName(), skullMeta.getOwningPlayer());
        return skullMeta;
    }

    public static void setFormat(String format) {
        PlayerList.format = format;
    }

    public static void unCachePlayer(String player) {
        skulls.removeAll(skulls.stream().filter(i ->
                Util.stripColor(Objects.requireNonNull(i.getItemMeta()).getDisplayName()).equals(player)).toList());
    }


    private int page = 0;
    private final Inventory inventory;
    private final Player player;
    private final int size;

    private Consumer<Player> closeListener;
    private BiConsumer<Player, String> completeListener;
    private final CommandPrompter plugin;

    public PlayerList(CommandPrompter plugin, Player player, String title) {
        this(plugin, player, title, DEFAULT_SIZE);
    }

    public PlayerList(CommandPrompter plugin, Player player, String title, int size) {
        this.plugin = plugin;
        this.player = player;
        this.size = size > 17 && size < 55 ? size - (size % 9) : DEFAULT_SIZE;
        this.inventory = Bukkit.createInventory(player, this.size, Util.color(title));
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public Consumer<Player> getCloseListener() {
        return closeListener;
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
        max_page = skulls.size() / size - 1;
        if (skulls.size() % size != 0)
            max_page++;

        if (page > max_page)
            page = max_page;

        //calc begin index and end index of skulls
        int begin = page * size;
        int end = begin + size;
        if (end > skulls.size())
            end = skulls.size();
        //calc rows

        plugin.getPluginLogger().debug("Skulls List: " + skulls.stream()
                .map(i -> Objects.requireNonNull(i.getItemMeta()).getDisplayName()).toList());
        if (plugin.getPromptConfig().sorted()) {
            plugin.getPluginLogger().debug("Skull list sorted.");
            skulls.sort((s1, s2) -> {
                var n1 = Util.stripColor(Objects.requireNonNull(s1.getItemMeta()).getDisplayName());
                var n2 = Util.stripColor(Objects.requireNonNull(s2.getItemMeta()).getDisplayName());
                return n1.compareToIgnoreCase(n2);
            });
            plugin.getPluginLogger().debug("Sorted Skulls List: " + skulls.stream()
                    .map(i -> Objects.requireNonNull(i.getItemMeta()).getDisplayName()).toList());
        }

        for (int i = 0; i < end; i++, begin++) {
            inventory.setItem(i, skulls.get(begin));
        }

        var buttonPos = getCheckedColumns();
        //prev page button
        var prevString = plugin.getPromptConfig().previousItem();
        ItemStack prev = new ItemStack(Util.getCheckedMaterial(prevString, Material.FEATHER));
        ItemMeta prevMeta = prev.getItemMeta();
        Objects.requireNonNull(prevMeta).setDisplayName(Util.color(plugin.getPromptConfig().previousText()));
        prev.setItemMeta(prevMeta);
        var prevCol = buttonPos.getX();
        inventory.setItem((size - 9) + prevCol, prev);

        //next page button
        var nextString = plugin.getPromptConfig().nextItem();
        ItemStack next = new ItemStack(Util.getCheckedMaterial(nextString, Material.FEATHER));
        ItemMeta nextMeta = next.getItemMeta();
        Objects.requireNonNull(nextMeta).setDisplayName(Util.color(plugin.getPromptConfig().nextText()));
        next.setItemMeta(nextMeta);
        var nextCol = buttonPos.getY();
        inventory.setItem((size - 9) + nextCol, next);
    }

    private Pair<Integer, Integer> getCheckedColumns() {
        var prevCol = plugin.getPromptConfig().previousColumn();
        var nextCol = plugin.getPromptConfig().nextColumn();
        if (nextCol == prevCol)
            return DEFAULT_BUTTON_POS;
        if (prevCol > 9 || nextCol > 9 || prevCol < 1 || nextCol < 1)
            return DEFAULT_BUTTON_POS;
        return new Pair<>(prevCol - 1, nextCol - 1);
    }

    public void open() {
        freshPage(0);
        player.openInventory(inventory);
        playerList.add(this);
    }

    public void nextPage() {
        freshPage(page + 1);
    }

    public void prevPage() {
        freshPage(page - 1);
    }

    public static List<PlayerList> getPlayerList() {
        return playerList;
    }

    public void drop() {
        playerList.remove(this);
    }

    public void close() {
        closeListener.accept(player);
    }

    public void complete(String str) {
        completeListener.accept(player, str);
    }


}