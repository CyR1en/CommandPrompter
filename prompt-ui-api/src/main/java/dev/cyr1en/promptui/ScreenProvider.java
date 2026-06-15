package dev.cyr1en.promptui;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Service Provider Interface for NMS-backed screen implementations.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}. The {@code prompt-ui-<ver>}
 * modules each register a concrete implementation under
 * {@code META-INF/services/dev.cyr1en.promptui.ScreenProvider}. At runtime,
 * the consumer in {@code prompt-paper} calls
 * {@code ServiceLoader.load(ScreenProvider.class, ...)} to obtain the matching
 * implementation for the current Minecraft version.
 *
 * <p>Implementations are not required to be thread-safe. The Bukkit/Paper API
 * bound here means that the SPI is currently usable only on Bukkit-based servers.
 */
public interface ScreenProvider {

    /**
     * Create an anvil input screen for the given player.
     *
     * @param plugin the owning plugin (used for event registration & scheduler calls)
     * @param player the player that will interact with the screen
     * @param text   the display text shown above the anvil
     * @return the new screen; the caller will configure and open it
     */
    InputScreen createAnvil(JavaPlugin plugin, Player player, String text);

    /**
     * Create a sign input screen for the given player.
     *
     * @param plugin the owning plugin (used for event registration & scheduler calls)
     * @param player the player that will interact with the screen
     * @param lines  the default lines to display on the virtual sign
     * @return the new screen; the caller will configure and open it
     */
    InputScreen createSign(JavaPlugin plugin, Player player, String[] lines);
}
