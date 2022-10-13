package com.cyr1en.commandprompter.prompt.ui;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.PluginLogger;
import com.cyr1en.commandprompter.hook.hooks.SuperVanishHook;
import com.cyr1en.commandprompter.util.Util;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.html.Option;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class HeadCache implements Listener {

    private final LoadingCache<Player, ItemStack> HEAD_CACHE;

    private final CommandPrompter plugin;
    private final String format;

    public HeadCache(CommandPrompter plugin) {
        this.plugin = plugin;
        this.format = plugin.getPromptConfig().skullNameFormat();
        HEAD_CACHE = CacheBuilder.newBuilder().maximumSize(plugin.getPromptConfig().cacheSize())
                .build(new CacheLoader<Player, ItemStack>() {
                    @Override
                    public @NotNull ItemStack load(@NotNull Player key) throws Exception {
                        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                        var skullMeta = makeSkullMeta(key, plugin.getPluginLogger());
                        skull.setItemMeta(skullMeta);
                        return skull;
                    }
                });
    }

    public Optional<ItemStack> getHeadFor(Player player) {
        try {
            return Optional.of(HEAD_CACHE.get(player));
        } catch (Exception ignore) {
            return Optional.empty();
        }
    }

    public Optional<ImmutableMap<Player, ItemStack>> getHeadFor(Iterable<? extends Player> key) {
        try {
            return Optional.of(HEAD_CACHE.getAll(key));
        } catch (ExecutionException e) {
            return Optional.empty();
        }
    }


    private SkullMeta makeSkullMeta(Player owningPlayer, PluginLogger logger) {
        var skullMeta = (SkullMeta) Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD);
        Objects.requireNonNull(skullMeta).setOwningPlayer(owningPlayer);
        SkullCache.setFormat(CommandPrompter.getInstance().getPromptConfig().skullNameFormat());
        var name = String.format(format, owningPlayer.getName());
        skullMeta.setDisplayName(Util.color(name));
        logger.debug("Skull Meta: {%s. %s}", skullMeta.getDisplayName(), skullMeta.getOwningPlayer());
        return skullMeta;
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onPlayerLogin(PlayerLoginEvent e) {
        var isInv = new AtomicBoolean(false);
        var svHook = plugin.getHookContainer().getHook(SuperVanishHook.class);
        plugin.getPluginLogger().debug("SV Hooked: " + svHook.isHooked());
        svHook.ifHooked(hook -> {
            if (hook.isInvisible(e.getPlayer()))
                isInv.set(true);
        });
        if (isInv.get()) {
            plugin.getPluginLogger().debug("Player is vanished (SuperVanish) skipping skull cache");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> SkullCache.cachePlayer(e.getPlayer()));
    }
}
