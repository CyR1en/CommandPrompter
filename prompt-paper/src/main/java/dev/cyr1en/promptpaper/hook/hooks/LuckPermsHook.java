package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import dev.cyr1en.promptpaper.screen.playerui.CacheFilter;
import dev.cyr1en.promptpaper.screen.playerui.HeadCache;
import java.util.List;
import java.util.regex.Pattern;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Hook for the LuckPerms plugin. Registers two player-list filters:
 * {@code lpo} (players in the executing player's own primary group) and
 * {@code lpg<group>;} (players in a specific LuckPerms group).
 */
@TargetPlugin(pluginName = "LuckPerms")
public class LuckPermsHook extends BaseHook implements FilterHook {

    private LuckPerms api;

    public LuckPermsHook(CommandPrompter plugin) {
        super(plugin);
        var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) this.api = provider.getProvider();
    }

    @Override
    public void registerFilters(HeadCache cache) {
        cache.registerFilter(new OwnGroupFilter());
        cache.registerFilter(new GroupFilter());
    }

    /**
     * Returns all online players whose primary LuckPerms group matches the given name.
     */
    private List<Player> getPlayersWithGroup(String groupName) {
        if (api == null || groupName.isBlank()) return List.of();
        return Bukkit.getOnlinePlayers().stream()
                .<Player>map(p -> p)
                .filter(p -> {
                    var user = api.getUserManager().getUser(p.getUniqueId());
                    if (user == null) return false;
                    return user.getPrimaryGroup().equals(groupName);
                }).toList();
    }

    private class OwnGroupFilter extends CacheFilter {
        OwnGroupFilter() {
            super(Pattern.compile("lpo"), "LuckPermsOwnGroup");
        }
        @Override public CacheFilter reConstruct(String promptKey) { return this; }
        @Override public List<Player> filter(Player relative) {
            var user = api.getUserManager().getUser(relative.getUniqueId());
            if (user == null) return List.of();
            return getPlayersWithGroup(user.getPrimaryGroup());
        }
    }

    private class GroupFilter extends CacheFilter {
        private final String groupName;
        GroupFilter() { this(""); }
        GroupFilter(String groupName) {
            super(Pattern.compile("lpg(\\S+);"), "LuckPermsGroup", 1);
            this.groupName = groupName;
        }
        @Override public CacheFilter reConstruct(String promptKey) {
            var m = getRegexKey().matcher(promptKey);
            return new GroupFilter(m.find() ? m.group(1) : "");
        }
        @Override public List<Player> filter(Player relative) {
            return getPlayersWithGroup(groupName);
        }
    }
}
