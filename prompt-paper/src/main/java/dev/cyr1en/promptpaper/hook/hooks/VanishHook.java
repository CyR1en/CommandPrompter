package dev.cyr1en.promptpaper.hook.hooks;

import dev.cyr1en.promptpaper.CommandPrompter;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/**
 * Abstract base for vanish-detection hooks. Provides a default
 * {@link #isInvisible} check via Bukkit metadata and handles
 * head-cache invalidation when a player's visibility state changes.
 */
public abstract class VanishHook extends BaseHook {

    protected VanishHook(CommandPrompter plugin) {
        super(plugin);
    }

    /**
     * Checks whether a player is vanished via the {@code vanished} metadata value.
     * Subclasses (e.g. SuperVanish, VanishNoPacket) override this with their own API.
     */
    public boolean isInvisible(Player player) {
        return player.getMetadata("vanished").stream()
                .anyMatch(MetadataValue::asBoolean);
    }

    /**
     * Invalidates the player's head cache when going invisible, or refreshes
     * it when becoming visible again.
     */
    public void onStateChange(Player player, Supplier<Boolean> isGoingInvisible) {
        var cache = getPlugin().getHeadCache();
        if (isGoingInvisible.get())
            cache.invalidate(player);
        else
            cache.getHeadFor(player);
    }
}
