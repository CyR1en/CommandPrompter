package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import com.cyr1en.commandprompter.prompt.ui.CacheFilter;
import com.cyr1en.commandprompter.prompt.ui.HeadCache;
import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@TargetPlugin(pluginName = "Towny")
public class TownyHook extends BaseHook implements FilterHook {
    public TownyHook(CommandPrompter plugin) {
        super(plugin);
    }

    public void registerFilters(HeadCache cache) {
        cache.registerFilter(new TownFilter());
        cache.registerFilter(new NationFilter());
    }

    /**
     * A PlayerUI filter that would filter all players that are in the same town as the relative player.
     */
    private static class TownFilter extends CacheFilter {


        public TownFilter() {
            super(Pattern.compile("t"), "PlayerUI.Filter-Format.TownyTown");
        }

        @Override
        public CacheFilter reConstruct(String promptKey) {
            return this;
        }

        @Override
        public List<Player> filter(Player relativePlayer) {
            var town = TownyAPI.getInstance().getTown(relativePlayer);
            if (town == null) return List.of();
            return town.getResidents().stream()
                    .map(r -> Bukkit.getPlayer(r.getName()))
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    /**
     * A PlayerUI filter that would filter all players that are in the same nation as the relative player.
     */
    private static class NationFilter extends CacheFilter {

        public NationFilter() {
            super(Pattern.compile("n"), "PlayerUI.Filter-Format.TownyNation");
        }

        @Override
        public CacheFilter reConstruct(String promptKey) {
            return this;
        }

        @Override
        public List<Player> filter(Player relativePlayer) {
            var nation = TownyAPI.getInstance().getNation(relativePlayer);
            if (nation == null) return List.of();
            return nation.getResidents().stream()
                    .map(r -> Bukkit.getPlayer(r.getName()))
                    .filter(Objects::nonNull)
                    .toList();
        }
    }
}
