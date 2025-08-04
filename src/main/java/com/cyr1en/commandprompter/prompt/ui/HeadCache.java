package com.cyr1en.commandprompter.prompt.ui;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.util.ModelDataComponent;
import com.cyr1en.commandprompter.util.PluginLogger;
import com.cyr1en.commandprompter.hook.hooks.PapiHook;
import com.cyr1en.commandprompter.util.Util;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;

import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.cyr1en.commandprompter.util.AdventureUtil.*;

public class HeadCache implements Listener {

    private final LoadingCache<Player, Optional<ItemStack>> HEAD_CACHE;
    private final List<CacheFilter> filters;

    private final CommandPrompter plugin;
    private final PluginLogger logger;

    public HeadCache(CommandPrompter plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.filters = new ArrayList<>();
        HEAD_CACHE = CacheBuilder.newBuilder().maximumSize(plugin.getPromptConfig().cacheSize())
                .build(new CacheLoader<>() {
                    @Override
                    public @NotNull Optional<ItemStack> load(@NotNull Player key) {
                        logger.debug("Loading head for {0}", key.getName());
                        if (!Bukkit.getOnlinePlayers().contains(key)) {
                            logger.debug("Player is not in online players");
                            return Optional.empty();
                        }
                        logger.debug("Constructing ItemStack ...");
                        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                        var skullMeta = makeSkullMeta(key, plugin.getPluginLogger());
                        skull.setItemMeta(skullMeta);
                        return Optional.of(skull);
                    }
                });
    }

    public void registerFilters() {
        registerFilter(new CacheFilter.WorldFilter());
        registerFilter(new CacheFilter.RadialFilter());
        registerFilter(new CacheFilter.SelfFilter());
        plugin.getHookContainer().getFilterHooks()
                .forEach(hook -> hook.ifHooked(filterHook -> filterHook.registerFilters(this))
                        .complete());
    }

    public void registerFilter(CacheFilter filter) {
        if (Objects.isNull(filter))
            return;
        if (!filters.contains(filter)) {
            filters.add(filter);
            logger.debug("Registered filter: " + filter.getClass().getSimpleName());
        }
    }

    public List<CacheFilter> getFilters() {
        return filters;
    }

    public String makeFilteredPattern() {
        var filterKeys = filters.stream()
                .map(filter -> {
                    var stringKey = filter.getRegexKey().toString();
                    return "(" + stringKey + ")";
                }).toList();
        return "p(?::(%s?)+)?".replace("%s", String.join("?", filterKeys));
    }

    public Optional<ItemStack> getHeadFor(Player player) {
        return HEAD_CACHE.getUnchecked(player);
    }

    public void invalidate(Player player) {
        if (Objects.isNull(player))
            return;

        if (getHeadFor(player).isPresent())
            HEAD_CACHE.invalidate(player);
    }

    public ImmutableMap<Player, Optional<ItemStack>> getHeadFor(Iterable<? extends Player> key) {
        try {
            return HEAD_CACHE.getAll(key);
        } catch (ExecutionException e) {
            return ImmutableMap.of();
        }
    }

    private List<ItemStack> sortHeads(ArrayList<ItemStack> headList) {
        @SuppressWarnings("unchecked")
        var copy = (ArrayList<ItemStack>) headList.clone();
        copy.sort((s1, s2) -> {
            var n1 = plain(Objects.requireNonNull(s1.getItemMeta()).displayName());
            var n2 = plain(Objects.requireNonNull(s2.getItemMeta()).displayName());
            return n1.compareToIgnoreCase(n2);
        });
        return copy;
    }

    public List<ItemStack> getHeadsFor(List<Player> players) {
        var result = new ArrayList<ItemStack>();
        for (Player player : players) {
            logger.debug("Player: {0}", player.getName());
            getHeadFor(player).ifPresent(result::add);
        }
        return result;
    }

    public List<ItemStack> getHeadsSortedFor(List<Player> players) {
        return sortHeads((ArrayList<ItemStack>) getHeadsFor(players));
    }

    public List<ItemStack> getHeadsSorted() {
        var keys = HEAD_CACHE.asMap().entrySet().stream()
                .filter(entry -> entry.getValue().isPresent())
                .map(Map.Entry::getKey).toList();
        return sortHeads((ArrayList<ItemStack>) getHeadsFor(keys));
    }

    public List<ItemStack> getHeads() {
        return HEAD_CACHE.asMap().values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get).toList();
    }

    private SkullMeta makeSkullMeta(Player owningPlayer, PluginLogger logger) {
        var skullMeta = (SkullMeta) Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD);
        Objects.requireNonNull(skullMeta).setOwningPlayer(owningPlayer);

        var skullFormat = plugin.getPromptConfig().skullNameFormat();
        var customModelData = plugin.getPromptConfig().skullCustomModelData();
        if (customModelData != 0) {
            logger.debug("Setting custom model data: {0}", customModelData);
            var data = ModelDataComponent.legacy(customModelData);
            skullMeta.setCustomModelDataComponent(data);
        }
        var skullName = skullFormat.replaceAll("%s", owningPlayer.getName());
        setDisplayName(skullMeta, skullName);
        logger.debug("Skull Meta: [{0}. {1}]", toLegacyColor(skullMeta.displayName()), skullMeta.getOwningPlayer());
        return skullMeta;
    }

    public void setDisplayName(SkullMeta skullMeta, String name) {
        var owner = skullMeta.getOwningPlayer();
        if (Objects.isNull(owner))
            return;
        var player = owner.getPlayer();
        if (Objects.isNull(player))
            return;

        logger.debug("Setting display name for {0}: {1}", player.getName(), name);
        name = name.replace("%s", player.getName());
        var nameComponent = color(name);

        var papi = plugin.getHookContainer().getHook(PapiHook.class);

        if (!papi.isHooked()) {
            logger.debug("PAPI is not hooked");
            skullMeta.displayName(nameComponent);
            logger.debug("Skull Meta: [%s. %s]", toLegacyColor(skullMeta.displayName()), skullMeta.getOwningPlayer());
            return;
        }

        var hook = papi.get();
        if (!hook.papiPlaceholders(name)) {
            logger.debug("No PAPI placeholders found");
            skullMeta.displayName(nameComponent);
            logger.debug("Skull Meta: [%s. %s]", toLegacyColor(skullMeta.displayName()), skullMeta.getOwningPlayer());
            return;
        }

        name = hook.setPlaceholder(player, name);
        logger.debug("PAPI placeholders found: %s", name);
        skullMeta.displayName(nameComponent);
        logger.debug("Skull Meta: [%s. %s]", toLegacyColor(skullMeta.displayName()), skullMeta.getOwningPlayer());
    }

    public CompletableFuture<LoadingCache<Player, Optional<ItemStack>>> reBuildCache() {
        HEAD_CACHE.invalidateAll();
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Building cache ...");
            var players = Bukkit.getOnlinePlayers().stream().filter(p -> !isVanished(p)).toList();
            players.forEach(HEAD_CACHE::getUnchecked);
            return HEAD_CACHE;
        });
    }

    public boolean isEmpty() {
        return HEAD_CACHE.size() == 0;
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onPlayerLogin(PlayerJoinEvent e) {
        logger.debug("Caching {0}", e.getPlayer());
        var cacheDelay = plugin.getPromptConfig().cacheDelay();
        logger.debug("Caching Delay: {0}", cacheDelay);

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (isVanished(e.getPlayer())) {
                logger.debug("Player is vanished");
                return;
            }

            HEAD_CACHE.getUnchecked(e.getPlayer());
            logger.debug("Cache status for {0}: {1}", e.getPlayer(), getHeadFor(e.getPlayer()).isPresent());
        }, cacheDelay);
    }

    private boolean isVanished(Player player) {
        var vanishHook = plugin.getHookContainer().getVanishHook();
        logger.debug("Acquired VanishHook: {0}", vanishHook);
        if (!vanishHook.isHooked())
            return false;
        return vanishHook.get().isInvisible(player);
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onPlayerQuit(PlayerQuitEvent e) {
        HEAD_CACHE.invalidate(e.getPlayer());
    }

}
