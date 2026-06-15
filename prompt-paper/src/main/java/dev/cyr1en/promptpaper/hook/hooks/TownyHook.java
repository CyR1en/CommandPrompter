package dev.cyr1en.promptpaper.hook.hooks;

import com.palmergames.bukkit.towny.TownyAPI;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import dev.cyr1en.promptpaper.screen.playerui.CacheFilter;
import dev.cyr1en.promptpaper.screen.playerui.HeadCache;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Hook for the Towny plugin. Registers two player-list filters:
 * {@code tt} (players in the same town) and {@code tn} (players in the same nation).
 */
@TargetPlugin(pluginName = "Towny")
public class TownyHook extends BaseHook implements FilterHook {

    public TownyHook(CommandPrompter plugin) { super(plugin); }

    @Override
    public void registerFilters(HeadCache cache) {
        cache.registerFilter(new TownFilter());
        cache.registerFilter(new NationFilter());
    }

    private static class TownFilter extends CacheFilter {
        TownFilter() { super(Pattern.compile("tt"), "TownyTown"); }
        @Override public CacheFilter reConstruct(String promptKey) { return this; }
        @Override public List<Player> filter(Player relative) {
            var town = TownyAPI.getInstance().getTown(relative);
            if (town == null) return List.of();
            return town.getResidents().stream()
                    .map(r -> Bukkit.getPlayer(r.getName()))
                    .filter(Objects::nonNull).distinct().toList();
        }
    }

    private static class NationFilter extends CacheFilter {
        NationFilter() { super(Pattern.compile("tn"), "TownyNation"); }
        @Override public CacheFilter reConstruct(String promptKey) { return this; }
        @Override public List<Player> filter(Player relative) {
            var nation = TownyAPI.getInstance().getNation(relative);
            if (nation == null) return List.of();
            return nation.getResidents().stream()
                    .map(r -> Bukkit.getPlayer(r.getName()))
                    .filter(Objects::nonNull).distinct().toList();
        }
    }
}
