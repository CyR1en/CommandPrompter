package dev.cyr1en.promptpaper.i18n;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Guards PlaceholderAPI calls so the {@link PapiDelegate} class is only referenced
 * (and therefore loaded) when the {@code PlaceholderAPI} plugin is enabled at runtime.
 *
 * <p>This indirection prevents a {@link NoClassDefFoundError} on servers that do not
 * have PlaceholderAPI installed.
 */
final class PapiExpander {

    private PapiExpander() {}

    /**
     * Expands PAPI placeholders in {@code text} when PAPI is available and
     * {@code player} is non-null; otherwise returns {@code text} unchanged.
     *
     * @param player the context player for placeholder resolution; may be {@code null}
     * @param text   the raw message string
     * @return the text with PAPI placeholders expanded, or the original text
     */
    static String expand(Player player, String text) {
        if (player == null) return text;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
        return PapiDelegate.expand(player, text);
    }
}
