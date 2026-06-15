package dev.cyr1en.promptpaper.hook.hooks;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import dev.cyr1en.promptpaper.screen.playerui.CacheFilter;
import dev.cyr1en.promptpaper.screen.playerui.HeadCache;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Hook for the WorldGuard plugin. Registers three player-list filters:
 * {@code wgr} (players in the same region), {@code wgrm<id>;} (region members),
 * and {@code wgro<id>;} (region owners).
 */
@TargetPlugin(pluginName = "WorldGuard")
public class WorldGuardHook extends BaseHook implements FilterHook {

    public WorldGuardHook(CommandPrompter plugin) { super(plugin); }

    @Override
    public void registerFilters(HeadCache cache) {
        getPlugin().getPluginLogger().debug("WorldGuard registering filters: Region, Members, Owners");
        cache.registerFilter(new RegionFilter());
        cache.registerFilter(new RegionMembersFilter());
        cache.registerFilter(new RegionOwnersFilter());
    }

    /**
     * Extracts online players whose UUIDs appear in a WorldGuard region's
     * domain (members or owners), using the provided domain-extraction function.
     */
    private static List<Player> filterDomain(Player relative, String regionId, Function<ProtectedRegion, DefaultDomain> extract) {
        var container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        var regions = container.get(BukkitAdapter.adapt(relative.getWorld()));
        if (regions == null) return List.of();
        var region = regions.getRegion(regionId);
        if (region == null) return List.of();
        var ids = extract.apply(region).getUniqueIds();
        return Bukkit.getOnlinePlayers().stream()
                .<Player>map(p -> p)
                .filter(p -> ids.contains(p.getUniqueId()))
                .toList();
    }

    private static class RegionFilter extends CacheFilter {
        RegionFilter() { super(Pattern.compile("wgr"), "WorldGuardRegion"); }
        @Override public CacheFilter reConstruct(String promptKey) { return this; }
        @Override public List<Player> filter(Player relative) {
            var world = BukkitAdapter.adapt(relative.getWorld());
            var container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            var regions = container.get(world);
            if (regions == null) return List.of();
            var loc = BukkitAdapter.adapt(relative.getLocation());
            var relativeSet = regions.getApplicableRegions(loc.toVector().toBlockPoint());
            return relative.getWorld().getPlayers().stream()
                    .filter(p -> {
                        var pLoc = BukkitAdapter.adapt(p.getLocation());
                        var pSet = regions.getApplicableRegions(pLoc.toVector().toBlockPoint());
                        return pSet.getRegions().stream().anyMatch(relativeSet.getRegions()::contains);
                    }).toList();
        }
    }

    private static class RegionMembersFilter extends CacheFilter {
        private final String regionId;
        RegionMembersFilter() { this(""); }
        RegionMembersFilter(String regionId) {
            super(Pattern.compile("wgrm(\\S+);"), "WorldGuardRegionMembers", 1);
            this.regionId = regionId;
        }
        @Override public CacheFilter reConstruct(String promptKey) {
            var m = getRegexKey().matcher(promptKey);
            return new RegionMembersFilter(m.find() ? m.group(1) : "");
        }
        @Override public List<Player> filter(Player relative) {
            return filterDomain(relative, regionId, ProtectedRegion::getMembers);
        }
    }

    private static class RegionOwnersFilter extends CacheFilter {
        private final String regionId;
        RegionOwnersFilter() { this(""); }
        RegionOwnersFilter(String regionId) {
            super(Pattern.compile("wgro(\\S+);"), "WorldGuardRegionOwners", 1);
            this.regionId = regionId;
        }
        @Override public CacheFilter reConstruct(String promptKey) {
            var m = getRegexKey().matcher(promptKey);
            return new RegionOwnersFilter(m.find() ? m.group(1) : "");
        }
        @Override public List<Player> filter(Player relative) {
            return filterDomain(relative, regionId, ProtectedRegion::getOwners);
        }
    }
}
