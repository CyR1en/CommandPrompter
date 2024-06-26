package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import com.cyr1en.commandprompter.prompt.ui.CacheFilter;
import com.cyr1en.commandprompter.prompt.ui.HeadCache;
import net.william278.husktowns.api.HuskTownsAPI;
import net.william278.husktowns.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Pattern;

@TargetPlugin(pluginName = "HuskTowns")
public class HuskTownsHook extends BaseHook implements FilterHook {
    private static final HuskTownsAPI huskTownsAPI = HuskTownsAPI.getInstance();

    public HuskTownsHook(CommandPrompter plugin) {
        super(plugin);
    }

    @Override
    public void registerFilters(HeadCache headCache) {
        headCache.registerFilter(new TownFilter());
    }

    /*
     * A PlayerUI filter that would filter all players that are in the same town as the relative player.
     */
    private static class TownFilter extends CacheFilter {

        public TownFilter() {
            super(Pattern.compile("ht"), "PlayerUI.Filter-Format.HuskTowns");
        }

        @Override
        public CacheFilter reConstruct(String promptKey) {
            return this;
        }

        @Override
        public List<Player> filter(Player relativePlayer) {
            var town = huskTownsAPI.getUserTown(User.of(relativePlayer.getUniqueId(), relativePlayer.getName()));
            if (town.isEmpty()) return List.of();
            if (town.get().town().getMembers().isEmpty()) return List.of();
            return town.get().town().getMembers().keySet().stream()
                    .map(Bukkit::getPlayer)
                    .toList();
        }
    }
}
