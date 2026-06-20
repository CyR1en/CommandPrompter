package dev.cyr1en.promptpaper.screen.playerui;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.FilterHook;
import dev.cyr1en.promptpaper.hook.hooks.VanishHook;
import dev.cyr1en.promptpaper.util.Scheduler;
import dev.cyr1en.promptui.ComponentUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Maintains a cache of player-head {@link ItemStack}s used by
 * {@link PlayerUIScreen} for tab-completion buttons.
 */
public class HeadCache implements Listener {

    private final CommandPrompter plugin;
    private final Scheduler scheduler;
    private final Map<UUID, Optional<ItemStack>> cache;
    private final List<CacheFilter> filters;

    public HeadCache(CommandPrompter plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.cache = new ConcurrentHashMap<>();
        this.filters = new ArrayList<>();
    }

    /**
     * Registers the built-in filters (world, radial, self) and any
     * filters contributed by {@link FilterHook} hooks.
     */
    public void registerFilters(HookContainer hooks) {
        registerFilter(new CacheFilter.WorldFilter());
        registerFilter(new CacheFilter.RadialFilter());
        registerFilter(new CacheFilter.SelfFilter());
        hooks.getHooksImplementing(FilterHook.class)
                .forEach(hook -> hook.registerFilters(this));
    }

    public void registerFilter(CacheFilter filter) {
        if (filter != null && !filters.contains(filter))
            filters.add(filter);
    }

    public List<CacheFilter> getFilters() { return List.copyOf(filters); }

    public String makeFilteredPattern() {
        var parts = filters.stream()
                .map(f -> "(" + f.getRegexKey() + ")")
                .toList();
        return "p(?::(%s?)+)?".replace("%s", String.join("?", parts));
    }

    /**
     * Returns a cached {@link Material#PLAYER_HEAD} for the given player,
     * creating and styling it on cache miss.
     */
    public Optional<ItemStack> getHeadFor(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), uuid -> {
            if (!Bukkit.getOnlinePlayers().contains(player)) return Optional.empty();
            var skull = new ItemStack(Material.PLAYER_HEAD);
            var meta = (SkullMeta) Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD);
            if (meta != null) {
                meta.setOwningPlayer(player);
                var promptConfig = plugin.getConfigLoader().getPromptConfig();
                var format = promptConfig.skullNameFormat();
                var cmData = promptConfig.skullCustomModelData();
                meta.displayName(ComponentUtil.mini("<!italic>" + format.formatted(player.getName())));
                if (cmData != 0) meta.setCustomModelData(cmData);
                skull.setItemMeta(meta);
            }
            return Optional.of(skull);
        });
    }

    public void invalidate(Player player) {
        cache.remove(player.getUniqueId());
    }

    public List<ItemStack> getHeads() {
        return cache.values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    /**
     * Returns all cached heads sorted alphabetically by display name.
     */
    public List<ItemStack> getHeadsSorted() {
        var list = new ArrayList<>(getHeads());
        list.sort((s1, s2) -> {
            var n1 = s1.getItemMeta() != null ? s1.getItemMeta().getDisplayName() : "";
            var n2 = s2.getItemMeta() != null ? s2.getItemMeta().getDisplayName() : "";
            return n1.compareToIgnoreCase(n2);
        });
        return list;
    }

    public int size() {
        // Count only populated entries so the staleness check in
        // PlayerUIScreen.open() detects a cache where every entry is
        // Optional.empty() (which happens when getHeadFor is invoked
        // before the player is in Bukkit.getOnlinePlayers()).
        return (int) cache.values().stream()
                .filter(Optional::isPresent)
                .count();
    }

    /**
     * Rebuilds the cache from currently online non-vanished players,
     * then executes the callback. Processing is batched across ticks
     * to avoid blocking the server thread.
     */
    public void buildCache(Runnable callback) {
        cache.clear();
        var players = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        players.removeIf(this::isVanished);
        processBatch(players, 0, callback);
    }

    private static final int BATCH_SIZE = 25;

    private void processBatch(List<Player> players, int start, Runnable callback) {
        int end = Math.min(start + BATCH_SIZE, players.size());
        for (int i = start; i < end; i++) {
            getHeadFor(players.get(i));
        }
        if (end < players.size()) {
            scheduler.runLater(() -> processBatch(players, end, callback), 1);
        } else {
            callback.run();
        }
    }

    public void reBuildCache() { cache.clear(); }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Use PlayerJoinEvent (not PlayerLoginEvent) so the player is
        // already in Bukkit.getOnlinePlayers() when getHeadFor runs.
        // PlayerLoginEvent fires during authentication, often seconds
        // before the player actually joins, which causes
        // getHeadFor to cache Optional.empty() and leaves the cache
        // permanently stale.
        var player = event.getPlayer();
        var vanished = isVanished(player);
        plugin.getPluginLogger().debug("Player join: name=" + player.getName()
                + " vanished=" + vanished + " cacheDelay="
                + plugin.getConfigLoader().getPromptConfig().cacheDelay());
        if (vanished) return;
        var delay = plugin.getConfigLoader().getPromptConfig().cacheDelay();
        if (delay > 0) {
            scheduler.runLater(() -> getHeadFor(player), delay);
        } else {
            getHeadFor(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        plugin.getPluginLogger().debug("Player quit, removing from head cache: " + player.getName());
        invalidate(player);
    }

    /**
     * Returns true if the given player is hidden by a vanish plugin.
     */
    public boolean isVanished(Player player) {
        return plugin.getHookContainer().getFirstHooked(VanishHook.class)
                .map(h -> h.isInvisible(player))
                .orElse(false);
    }
}
