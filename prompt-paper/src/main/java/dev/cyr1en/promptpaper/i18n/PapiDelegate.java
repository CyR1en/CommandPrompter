package dev.cyr1en.promptpaper.i18n;

import org.bukkit.entity.Player;

/**
 * Isolated helper for PlaceholderAPI calls.
 *
 * <p>This class is loaded only when PAPI is actually present, preventing a
 * {@link NoClassDefFoundError} from crashing the plugin if PAPI is absent.
 * Always access this through {@link PapiExpander} rather than calling it directly.
 */
final class PapiDelegate {

    private PapiDelegate() {}

    /**
     * Expands PlaceholderAPI placeholders in {@code text} for the given player.
     * Returns the text unchanged when {@code player} is {@code null}.
     */
    static String expand(Player player, String text) {
        if (player == null) return text;
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
    }
}
