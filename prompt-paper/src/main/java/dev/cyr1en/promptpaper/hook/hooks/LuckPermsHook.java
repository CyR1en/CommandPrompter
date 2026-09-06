package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.hook.annotations.TargetPlugin;
import dev.cyr1en.promptpaper.screen.playerui.CacheFilter;
import dev.cyr1en.promptpaper.screen.playerui.HeadCache;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.regex.Pattern;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Hook for the LuckPerms plugin. Registers two player-list filters: {@code lpo} (players in the
 * executing player's own primary group) and {@code lpg<group>;} (players in a specific LuckPerms
 * group).
 */
@TargetPlugin(pluginName = "LuckPerms")
public class LuckPermsHook extends BaseHook implements FilterHook {

  private LuckPerms api;

  public LuckPermsHook(CommandPrompter plugin) {
    super(plugin);
    try {
      var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
      if (provider != null) this.api = provider.getProvider();
    } catch (ServiceConfigurationError e) {
      plugin.getPluginLogger().debug("LuckPerms API service is unavailable: " + e.getMessage());
    } catch (LinkageError e) {
      plugin.getPluginLogger().debug("LuckPerms API linkage is unavailable: " + e.getMessage());
    } catch (RuntimeException e) {
      plugin.getPluginLogger().debug("LuckPerms API service lookup failed: " + e.getMessage());
    }
  }

  @Override
  public void registerFilters(HeadCache cache) {
    if (api == null) {
      getPlugin().getPluginLogger().debug("LuckPerms unavailable; no LuckPerms filters registered");
      return;
    }
    cache.registerFilter(new OwnGroupFilter());
    cache.registerFilter(new GroupFilter());
  }

  /** Returns all online players whose primary LuckPerms group matches the given name. */
  private List<Player> getPlayersWithGroup(String groupName) {
    if (api == null || groupName.isBlank()) return List.of();
    try {
      if (api.getUserManager() == null) return List.of();
      return Bukkit.getOnlinePlayers().stream()
          .<Player>map(p -> p)
          .filter(p -> groupName.equals(getPrimaryGroup(p)))
          .toList();
    } catch (ServiceConfigurationError | LinkageError | RuntimeException _) {
      return List.of();
    }
  }

  private String getPrimaryGroup(Player player) {
    if (api == null || player == null) return null;
    try {
      var userManager = api.getUserManager();
      if (userManager == null) return null;
      var user = userManager.getUser(player.getUniqueId());
      return user == null ? null : user.getPrimaryGroup();
    } catch (ServiceConfigurationError | LinkageError | RuntimeException _) {
      return null;
    }
  }

  private class OwnGroupFilter extends CacheFilter {
    OwnGroupFilter() {
      super(Pattern.compile("lpo"), "LuckPermsOwnGroup");
    }

    @Override
    public CacheFilter reConstruct(String promptKey) {
      return this;
    }

    @Override
    public List<Player> filter(Player relative) {
      var group = getPrimaryGroup(relative);
      return group == null ? List.of() : getPlayersWithGroup(group);
    }
  }

  private class GroupFilter extends CacheFilter {
    private final String groupName;

    GroupFilter() {
      this("");
    }

    GroupFilter(String groupName) {
      super(Pattern.compile("lpg(\\S+);"), "LuckPermsGroup", 1);
      this.groupName = groupName;
    }

    @Override
    public CacheFilter reConstruct(String promptKey) {
      var m = getRegexKey().matcher(promptKey);
      return new GroupFilter(m.find() ? m.group(1) : "");
    }

    @Override
    public List<Player> filter(Player relative) {
      return getPlayersWithGroup(groupName);
    }
  }
}
