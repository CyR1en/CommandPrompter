package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.hook.annotations.TargetPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@TargetPlugin(pluginName = "PlaceholderAPI")
public class PapiHook extends BaseHook {
    public PapiHook(CommandPrompter plugin) {
        super(plugin);
    }

    public String setPlaceholder(@NotNull Player player, @NotNull String txt) {
        if (!papiPlaceholders(txt)) return txt;
        return PlaceholderAPI.setPlaceholders(player, txt);
    }

    public boolean papiPlaceholders(String str) {
        return PlaceholderAPI.containsPlaceholders(str);
    }

}
