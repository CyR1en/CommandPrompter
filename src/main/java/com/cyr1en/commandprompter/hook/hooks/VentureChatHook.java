package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Objects;

@TargetPlugin(pluginName = "VentureChat")
public class VentureChatHook {

    private final CommandPrompter p;
    private final Plugin mvPl;
    private Collection<String> channelAliases;

    private VentureChatHook(CommandPrompter plugin) {
        this.p = plugin;
        mvPl = p.getServer().getPluginManager().getPlugin("VentureChat");
        extractChannels();
    }

    private void extractChannels() {
        var cs = mvPl.getConfig().getConfigurationSection("channels");
        channelAliases = Objects.requireNonNull(cs).getKeys(false).stream()
                .map(k -> cs.getString(k + ".alias")).toList();
        p.getPluginLogger().debug("Channels detected " + channelAliases);
    }

    public boolean isChatChannel(String alias) {
        return channelAliases.contains(alias);
    }

}
