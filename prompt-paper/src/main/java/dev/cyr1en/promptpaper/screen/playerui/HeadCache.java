package dev.cyr1en.promptpaper.screen.playerui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.hooks.FilterHook;
import dev.cyr1en.promptpaper.hook.hooks.VanishHook;
import dev.cyr1en.promptpaper.util.Scheduler;
import dev.cyr1en.promptui.ComponentUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
 * Maintains a bounded LRU cache of player-head {@link ItemStack}s used by {@link PlayerUIScreen}
 * for tab-completion buttons.
 *
 * <p>The cache is a memoization layer only, never the display source: the player list shown by
 * {@link PlayerUIScreen} is derived fresh from Bukkit, so the cache bound ({@code
 * PlayerUI.Cache-Size}, {@code <= 0} meaning unbounded) only limits how many heads are kept in
 * memory (2.x parity). The most-recently-used entries survive eviction, and an evicted entry is
 * simply recomputed on the next access.
 */
public class HeadCache implements Listener {

  private final CommandPrompter plugin;
  private final Scheduler scheduler;
  private final int maxCacheSize;
  private final Map<UUID, Optional<ItemStack>> cache;
  // UUIDs whose cached head was built without skin textures; always a subset of the cache keys.
  private final Set<UUID> untextured = ConcurrentHashMap.newKeySet();
  private final List<CacheFilter> filters;

  public HeadCache(CommandPrompter plugin, Scheduler scheduler) {
    this.plugin = plugin;
    this.scheduler = scheduler;
    this.maxCacheSize = plugin.getConfigLoader().getPromptConfig().cacheSize();
    this.cache =
        Collections.synchronizedMap(
            new LinkedHashMap<UUID, Optional<ItemStack>>(16, 0.75f, true) {
              @Override
              protected boolean removeEldestEntry(Map.Entry<UUID, Optional<ItemStack>> eldest) {
                int max = maxCacheSize;
                boolean evict = max > 0 && size() > max;
                if (evict) untextured.remove(eldest.getKey());
                return evict;
              }
            });
    this.filters = new ArrayList<>();
  }

  /**
   * Registers the built-in filters (world, radial, self) and any filters contributed by {@link
   * FilterHook} hooks.
   */
  public void registerFilters(HookContainer hooks) {
    registerFilter(new CacheFilter.WorldFilter());
    registerFilter(new CacheFilter.RadialFilter());
    registerFilter(new CacheFilter.SelfFilter());
    hooks.getHooksImplementing(FilterHook.class).forEach(hook -> hook.registerFilters(this));
  }

  public void registerFilter(CacheFilter filter) {
    if (filter != null && !filters.contains(filter)) filters.add(filter);
  }

  public List<CacheFilter> getFilters() {
    return List.copyOf(filters);
  }

  /**
   * Parses a combined filter key (e.g. {@code r10s}) into the ordered list of {@link CacheFilter}
   * instances it encodes.
   *
   * <p>The input is consumed deterministically from cursor 0. At each cursor position every
   * registered filter is tried with its regex anchored at the cursor ({@code matcher.region(cursor,
   * key.length())} + {@code matcher.lookingAt()}); the longest match wins, with ties going to the
   * earliest registered filter — this prevents short keys (e.g. {@code w}) from shadowing longer
   * integration keys (e.g. {@code wgrm...;}). Each matched token is reconstructed via {@link
   * CacheFilter#reConstruct(String)} on the exact matched substring so hooks can extract their
   * parameters.
   *
   * <p>If no filter matches at a cursor position, the entire unrecognized span is skipped
   * (advancing until a position matches or the input ends), one debug line is logged with the
   * skipped substring, and parsing continues. Returns {@link List#of()} for null/blank input and
   * never returns null.
   */
  public List<CacheFilter> extractFilters(String filterKey) {
    if (filterKey == null || filterKey.isBlank()) return List.of();
    var result = new ArrayList<CacheFilter>();
    int cursor = 0;
    while (cursor < filterKey.length()) {
      var match = longestMatchAt(filterKey, cursor);
      if (match == null) {
        int skipStart = cursor;
        do {
          cursor++;
        } while (cursor < filterKey.length() && longestMatchAt(filterKey, cursor) == null);
        plugin
            .getPluginLogger()
            .debug(
                "PlayerUI skipping unrecognized filter token: '"
                    + filterKey.substring(skipStart, cursor)
                    + "'");
        continue;
      }
      var token = filterKey.substring(cursor, match.end());
      result.add(match.filter().reConstruct(token));
      cursor = match.end();
    }
    return result;
  }

  private record TokenMatch(CacheFilter filter, int end) {}

  /**
   * Returns the registered filter whose regex matches anchored at {@code pos} with the largest
   * match end, or null if none match. Ties keep the earliest registered filter because iteration
   * order is registration order and a candidate only replaces the best when it is strictly longer.
   */
  private TokenMatch longestMatchAt(String key, int pos) {
    TokenMatch best = null;
    for (CacheFilter filter : filters) {
      var matcher = filter.getRegexKey().matcher(key);
      matcher.region(pos, key.length());
      if (matcher.lookingAt() && (best == null || matcher.end() > best.end())) {
        best = new TokenMatch(filter, matcher.end());
      }
    }
    return best;
  }

  /**
   * Returns a cached {@link Material#PLAYER_HEAD} for the given player, creating and styling it on
   * cache miss.
   *
   * <p>Vanished players always return {@link Optional#empty()} without caching, so no path
   * (filtered or unfiltered) can reveal them and no stale empty entry survives after the player
   * unvanishes.
   */
  public Optional<ItemStack> getHeadFor(Player player) {
    if (isVanished(player)) return Optional.empty();
    var uuid = player.getUniqueId();
    // A head cached before the skin was applied (e.g. by a skin plugin shortly after join) is
    // rebuilt once the live profile carries textures. Every other cached head is reused as-is.
    if (untextured.contains(uuid) && hasTextures(player.getPlayerProfile())) invalidate(player);
    var head = cache.computeIfAbsent(uuid, ignored -> createHead(player));
    // Not cached: the player is not online, so there is nothing to show and no entry to clean up.
    return head != null ? head : Optional.empty();
  }

  /**
   * Builds the head for an online player, or returns null (not cached) if the player is offline.
   *
   * <p>The skull is given the player's own live {@link PlayerProfile}, which already carries the
   * {@code textures} property (from Mojang in online mode, or from a skin plugin in offline mode),
   * so no profile lookup is needed. A skull profile without textures makes the server call {@link
   * PlayerProfile#update()}, a Mojang profile request that can never succeed for offline-mode
   * (version 3) UUIDs. Such players get a plain head instead; only version 4 (Mojang) UUIDs keep
   * the old lookup-based {@link SkullMeta#setOwningPlayer} fallback.
   */
  private Optional<ItemStack> createHead(Player player) {
    if (!Bukkit.getOnlinePlayers().contains(player)) return null;
    var skull = new ItemStack(Material.PLAYER_HEAD);
    var meta = (SkullMeta) Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD);
    if (meta != null) {
      var profile = player.getPlayerProfile();
      if (hasTextures(profile)) {
        meta.setPlayerProfile(profile);
      } else {
        untextured.add(player.getUniqueId());
        if (player.getUniqueId().version() == 4) meta.setOwningPlayer(player);
      }
      var promptConfig = plugin.getConfigLoader().getPromptConfig();
      var format = promptConfig.skullNameFormat();
      var cmData = promptConfig.skullCustomModelData();
      meta.displayName(ComponentUtil.mini("<!italic>" + format.formatted(player.getName())));
      if (cmData != 0) {
        applyCustomModelData(meta, cmData);
      }
      skull.setItemMeta(meta);
    }
    return Optional.of(skull);
  }

  private static boolean hasTextures(PlayerProfile profile) {
    if (profile == null) return false;
    for (ProfileProperty property : profile.getProperties()) {
      if ("textures".equals(property.getName()) && !property.getValue().isEmpty()) return true;
    }
    return false;
  }

  @SuppressWarnings("deprecation")
  private void applyCustomModelData(SkullMeta meta, int cmData) {
    try {
      var cmd = meta.getCustomModelDataComponent();
      cmd.setFloats(List.of((float) cmData));
      meta.setCustomModelDataComponent(cmd);
    } catch (NoSuchMethodError e) {
      meta.setCustomModelData(cmData);
    }
  }

  public void invalidate(Player player) {
    cache.remove(player.getUniqueId());
    untextured.remove(player.getUniqueId());
  }

  /**
   * Returns all cached heads, excluding entries whose owner is offline or has vanished after being
   * cached.
   */
  public List<ItemStack> getHeads() {
    return cache.entrySet().stream()
        .filter(entry -> entry.getValue().isPresent())
        .filter(
            entry -> {
              var cachedPlayer = Bukkit.getPlayer(entry.getKey());
              return cachedPlayer != null && !isVanished(cachedPlayer);
            })
        .map(entry -> entry.getValue().get())
        .toList();
  }

  /** Returns all cached heads sorted alphabetically by display name. */
  public List<ItemStack> getHeadsSorted() {
    var list = new ArrayList<>(getHeads());
    var serializer = PlainTextComponentSerializer.plainText();
    list.sort(
        (s1, s2) -> {
          var d1 = s1.getItemMeta() != null ? s1.getItemMeta().displayName() : null;
          var d2 = s2.getItemMeta() != null ? s2.getItemMeta().displayName() : null;
          var n1 = d1 != null ? serializer.serialize(d1) : "";
          var n2 = d2 != null ? serializer.serialize(d2) : "";
          return n1.compareToIgnoreCase(n2);
        });
    return list;
  }

  public int size() {
    // Count only populated entries so empty/unloaded heads are detected as stale.
    return (int) cache.values().stream().filter(Optional::isPresent).count();
  }

  /**
   * Rebuilds the cache from currently online non-vanished players, then executes the callback.
   * Processing is batched across ticks to avoid blocking the server thread.
   */
  public void buildCache(Runnable callback) {
    cache.clear();
    untextured.clear();
    var players = new ArrayList<Player>(Bukkit.getOnlinePlayers());
    players.removeIf(this::isVanished);
    processBatch(players, 0, callback, new AtomicBoolean());
  }

  private static final int BATCH_SIZE = 25;

  private void processBatch(
      List<Player> players, int start, Runnable callback, AtomicBoolean callbackCompleted) {
    int end = Math.min(start + BATCH_SIZE, players.size());
    if (start >= end) {
      if (callbackCompleted.compareAndSet(false, true)) callback.run();
      return;
    }
    var remaining = new AtomicInteger(end - start);
    for (int i = start; i < end; i++) {
      Player p = players.get(i);
      var playerCompleted = new AtomicBoolean();
      Runnable completePlayer =
          () -> {
            if (playerCompleted.compareAndSet(false, true) && remaining.decrementAndGet() == 0) {
              continueBatch(players, end, callback, callbackCompleted);
            }
          };
      try {
        var task =
            p.getScheduler()
                .run(
                    plugin,
                    scheduledTask -> {
                      try {
                        getHeadFor(p);
                      } finally {
                        completePlayer.run();
                      }
                    },
                    completePlayer);
        if (task == null) completePlayer.run();
      } catch (Throwable t) {
        completePlayer.run();
      }
    }
  }

  private void continueBatch(
      List<Player> players, int end, Runnable callback, AtomicBoolean callbackCompleted) {
    if (end < players.size()) {
      try {
        scheduler.runLater(() -> processBatch(players, end, callback, callbackCompleted), 1);
      } catch (Throwable t) {
        if (callbackCompleted.compareAndSet(false, true)) callback.run();
      }
    } else {
      if (callbackCompleted.compareAndSet(false, true)) callback.run();
    }
  }

  public void reBuildCache() {
    cache.clear();
    untextured.clear();
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    // Use PlayerJoinEvent so player is in online players list when cache updates.
    var player = event.getPlayer();
    var vanished = isVanished(player);
    plugin
        .getPluginLogger()
        .debug(
            "Player join: name="
                + player.getName()
                + " vanished="
                + vanished
                + " cacheDelay="
                + plugin.getConfigLoader().getPromptConfig().cacheDelay());
    if (vanished) return;
    var delay = plugin.getConfigLoader().getPromptConfig().cacheDelay();
    if (delay > 0) {
      try {
        var task =
            player
                .getScheduler()
                .runDelayed(plugin, scheduledTask -> getHeadFor(player), null, delay);
        if (task == null) {
          plugin
              .getPluginLogger()
              .debug("Head-cache join task retired for " + player.getUniqueId());
        }
      } catch (Exception e) {
        plugin
            .getPluginLogger()
            .debug(
                "Unable to schedule head-cache join task for "
                    + player.getUniqueId()
                    + ": "
                    + e.getMessage());
      }
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

  /** Returns true if the given player is hidden by a vanish plugin. */
  public boolean isVanished(Player player) {
    return plugin
        .getHookContainer()
        .getFirstHooked(VanishHook.class)
        .map(h -> h.isInvisible(player))
        .orElse(false);
  }
}
