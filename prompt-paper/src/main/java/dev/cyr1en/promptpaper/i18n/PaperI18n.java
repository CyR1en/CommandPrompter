package dev.cyr1en.promptpaper.i18n;

import dev.cyr1en.promptcore.i18n.AbstractI18n;
import java.io.File;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Paper-specific i18n implementation.
 *
 * <p>Produces {@link Component} instances from translated message strings using
 * the following formatting pipeline:
 *
 * <ol>
 *   <li>{@link #preFormat} — expands PlaceholderAPI {@code %papi_placeholders%} via
 *       {@link PapiExpander} (safe: no-op when PAPI is not installed)
 *   <li>{@link AbstractI18n#applyPlaceholders} — replaces {@code %key%} tokens with
 *       the supplied {@link Placeholder} values
 *   <li>{@link #postFormat} — parses the resulting string with MiniMessage to produce
 *       the final {@link Component}
 * </ol>
 *
 * <p>Call sites use the inherited {@link #get(String, Placeholder...)} and
 * {@link #get(String, Player, Placeholder...)} overloads.
 */
public class PaperI18n extends AbstractI18n<Component, Player> {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /**
     * Constructs and immediately loads the merged property set for the given locale.
     *
     * @param locale            the locale string (e.g. {@code "en_US"})
     * @param baseDir           the plugin data folder (used to locate the user-override
     *                          {@code locales/} directory)
     * @param pluginClassLoader the class loader to use when reading bundled properties files
     * @param logger            a logger for diagnostic/warning output
     */
    public PaperI18n(String locale, File baseDir, ClassLoader pluginClassLoader, Logger logger) {
        super(locale, baseDir, pluginClassLoader, logger);
    }

    /**
     * Expands PlaceholderAPI placeholders in {@code text} for the given player.
     *
     * <p>This runs <em>before</em> the custom {@code %key%} substitution so that
     * PAPI results can themselves contain {@code %key%} tokens if needed. The call
     * is guarded by {@link PapiExpander} — it is safe even when PAPI is absent.
     *
     * @param text    the raw message string
     * @param context the context player; may be {@code null}
     * @return the text with PAPI placeholders expanded (or unchanged if PAPI is absent)
     */
    @Override
    protected String preFormat(String text, Player context) {
        return PapiExpander.expand(context, text);
    }

    /**
     * Parses the fully substituted string with MiniMessage and returns a {@link Component}.
     *
     * @param text    the substituted message string (MiniMessage-formatted)
     * @param context unused for this implementation
     * @return the parsed {@link Component}
     */
    @Override
    protected Component postFormat(String text, Player context) {
        return MINI.deserialize(text);
    }
}
