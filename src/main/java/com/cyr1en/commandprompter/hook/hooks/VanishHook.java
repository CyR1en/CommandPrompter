package com.cyr1en.commandprompter.hook.hooks;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.prompt.ui.HeadCache;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Supplier;

public abstract class VanishHook extends BaseHook {

    private final HeadCache headCache;

    public VanishHook(CommandPrompter plugin) {
        super(plugin);
        this.headCache = plugin.getHeadCache();
    }

    public abstract boolean isInvisible(Player p);

    public void onStateChange(Player player, Supplier<Boolean> isGoingInvisible) {
        getPlugin().getPluginLogger().debug("Pre Vanish State Change: " + headCache.getHeads().stream().map(i ->
                Objects.requireNonNull(i.getItemMeta()).getDisplayName()).toList());
        if (isGoingInvisible.get())
            headCache.invalidate(player);
        else
            headCache.getHeadFor(Objects.requireNonNull(player));
        getPlugin().getPluginLogger().debug("Post Vanish State Change: " + headCache.getHeads().stream().map(i ->
                Objects.requireNonNull(i.getItemMeta()).getDisplayName()).toList());
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
