package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import dev.cyr1en.promptpaper.screen.playerui.CacheFilter;
import dev.cyr1en.promptpaper.screen.playerui.HeadCache;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import net.william278.husktowns.api.HuskTownsAPI;
import net.william278.husktowns.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Hook for the HuskTowns plugin. Registers a {@code ht} player-list filter
 * that returns all members of the executing player's current town.
 */
@TargetPlugin(pluginName = "HuskTowns")
public class HuskTownsHook extends BaseHook implements FilterHook {

    public HuskTownsHook(CommandPrompter plugin) { super(plugin); }

    @Override
    public void registerFilters(HeadCache cache) {
        cache.registerFilter(new TownFilter());
    }

    private static class TownFilter extends CacheFilter {
        TownFilter() { super(Pattern.compile("ht"), "HuskTowns"); }
        @Override public CacheFilter reConstruct(String promptKey) { return this; }
        @Override public List<Player> filter(Player relative) {
            var opt = HuskTownsAPI.getInstance().getUserTown(User.of(relative.getUniqueId(), relative.getName()));
            if (opt.isEmpty()) return List.of();
            return opt.get().town().getMembers().keySet().stream()
                    .map(Bukkit::getPlayer).filter(Objects::nonNull).distinct().toList();
        }
    }
}
