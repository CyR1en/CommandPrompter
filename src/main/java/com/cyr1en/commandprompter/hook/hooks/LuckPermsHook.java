package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.Hook;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import com.cyr1en.commandprompter.prompt.ui.CacheFilter;
import com.cyr1en.commandprompter.prompt.ui.HeadCache;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Hook for LuckPerms plugin.
 *
 * <p>
 * Main component of this hook are the cache filters for LuckPerms groups.
 */
@TargetPlugin(pluginName = "LuckPerms")
public class LuckPermsHook extends BaseHook implements FilterHook {

    private LuckPerms api;

    /**
     * Construct a new LuckPermsHook.
     *
     * @param plugin the plugin
     */
    public LuckPermsHook(CommandPrompter plugin) {
        super(plugin);
        RegisteredServiceProvider<LuckPerms> provider = plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.api = provider.getProvider();
        } else {
            // If LuckPerms was loaded but the service provider was not found then we make sure that the container
            // is empty.
            plugin.getHookContainer().replace(LuckPermsHook.class, Hook.empty());
        }
    }

    /**
     * Register the cache filters for LuckPerms groups.
     *
     * @param cache the head cache.
     */
    public void registerFilters(HeadCache cache) {
        cache.registerFilter(new OwnGroupFilter(this));
        cache.registerFilter(new GroupFilter(this));
    }

    /**
     * Get all players that are in the same group as the relative player.
     *
     * @param groupName the group name
     * @return a list of players with the same group
     */
    @SuppressWarnings("unchecked")
    private List<Player> getPlayersWithGroup(String groupName) {
        if (api == null || groupName.isBlank()) return List.of();
        return (List<Player>) Bukkit.getOnlinePlayers().stream()
                .filter(p -> {
                    var user = api.getUserManager().getUser(p.getName());
                    if (user == null) return false;
                    var group = user.getPrimaryGroup();
                    return group.equals(groupName);
                }).toList();
    }

    /**
     * Get the LuckPerms API.
     *
     * @return the LuckPerms API
     */
    private LuckPerms getApi() {
        return api;
    }


    /**
     * A PlayerUI filter that would filter all players that are in the same group as the relative player.
     *
     * <p>
     * This is a special case of {@link CacheFilter} where the regex key is 'og'.
     * This would filter all players that are in the same group as the relative player.
     */
    private static class OwnGroupFilter extends CacheFilter {

        private final LuckPermsHook hook;

        /**
         * Construct a new own group filter.
         *
         * @param hook the LuckPerms hook
         */
        public OwnGroupFilter(LuckPermsHook hook) {
            super(Pattern.compile("lpo"), "PlayerUI.Filter-Format.LuckPermsOwnGroup");
            this.hook = hook;
        }

        @Override
        public CacheFilter reConstruct(String promptKey) {
            // No need to re-construct
            return this;
        }

        /**
         * Filter all players that are in the same group as the relative player.
         *
         * @param relativePlayer the players to filter
         * @return a list of players with the same group
         */
        @Override
        public List<Player> filter(Player relativePlayer) {
            var user = hook.getApi().getUserManager().getUser(relativePlayer.getUniqueId());
            if (user == null) return List.of();
            var group = user.getPrimaryGroup();
            return hook.getPlayersWithGroup(group);
        }
    }

    /**
     * A PlayerUI filter that would filter all players based on the group name.
     *
     * <p>
     * The regex for this filter is 'g(\S+)'. In regex capturing group 1 is the group
     * name that would be used to filter.
     */
    private static class GroupFilter extends CacheFilter {

        private final String groupName;
        private final LuckPermsHook hook;

        /**
         * Default construct a new group filter.
         *
         * @param hook the LuckPerms hook
         */
        public GroupFilter(LuckPermsHook hook) {
            this("", hook);
        }

        /**
         * Constructor to construct a new group filter with a group name.
         *
         * <p>
         * This constructor will be used by the {@link #reConstruct(String)} function.
         *
         * @param groupName the group name that would be used to filter
         * @param hook      the LuckPerms hook
         */
        public GroupFilter(String groupName, LuckPermsHook hook) {
            super(Pattern.compile("lpg(\\S+);"), "PlayerUI.Filter-Format.LuckPermsGroup", 1);
            this.groupName = groupName;
            this.hook = hook;
        }

        @Override
        public CacheFilter reConstruct(String promptKey) {
            var matcher = this.getRegexKey().matcher(promptKey);
            var found = matcher.find();
            var groupName = found ? matcher.group(1) : "";
            return new GroupFilter(groupName, hook);
        }

        @Override
        public List<Player> filter(Player relativePlayer) {
            var players = hook.getPlayersWithGroup(groupName);
            hook.getPlugin().getPluginLogger().debug("Players: %s", players);
            return players;
        }
    }

}
