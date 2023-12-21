package com.cyr1en.commandprompter.prompt.ui;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.config.PromptConfig;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Class that would filter players based on a regex key.
 * <p>
 * For example, if the regex key is 'w', then it would filter all players
 * that are in the same world as the relative player.
 */
public abstract class CacheFilter {


    /**
     * The regex key that would be used for parsing
     */
    private final Pattern regexKey;

    /**
     * The config key for this cache filter's format
     */
    private final String configKey;

    /**
     * Construct a new cache filter.
     *
     * @param regexKey the regex key
     */
    public CacheFilter(Pattern regexKey, String configKey) {
        this.regexKey = regexKey;
        this.configKey = configKey;
    }

    /**
     * Get the regex key.
     *
     * @return the regex key
     */
    public Pattern getRegexKey() {
        return regexKey;
    }

    /**
     * Get the config key.
     *
     * @return the config key
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Get the format for this cache filter.
     *
     * @param config the prompt config
     * @return the format
     */
    public String getFormat(PromptConfig config) {
        return config.getFilterFormat(this);

    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    /**
     * Clone this cache filter with a new prompt key.
     *
     * @param promptKey the prompt key
     * @return the cloned cache filter
     */
    public abstract CacheFilter reConstruct(String promptKey);

    /**
     * Abstract method that would filter player based on subclass implementation.
     *
     * <p>
     * This method would be called by {@link HeadCache} when it needs to filter
     * players.
     *
     * @param relativePlayer the players to filter
     * @return the filtered players
     */
    public abstract List<Player> filter(Player relativePlayer);


    /**
     * Filter players based on the world of the relative player.
     * <p>
     * This is a special case of {@link CacheFilter} where the regex key is 'w'.
     * This would filter all players that are in the same world as the relative player.
     */
    public static class WorldFilter extends CacheFilter {

        /**
         * Construct a new world filter.
         */
        public WorldFilter() {
            super(Pattern.compile("w"), "PlayerUI.Filter-Format.World");
        }

        @Override
        public List<Player> filter(Player relativePlayer) {
            return relativePlayer.getWorld().getPlayers();
        }

        public CacheFilter reConstruct(String promptKey) {
            return new WorldFilter();
        }
    }

    /**
     * Filter players based on the radius of the relative player.
     * <p>
     * This is a special case of {@link CacheFilter} where the regex key is 'r'.
     * This would filter all players that are within the radius of the relative player.
     */
    public static class RadialFilter extends CacheFilter {

        /**
         * The radius to check relative to the relative player
         */
        private final int radius;

        /**
         * Construct a new radius filter.
         *
         * @param radius the radius
         */
        public RadialFilter(int radius) {
            super(Pattern.compile("r(\\d+)"), "PlayerUI.Filter-Format.Radial");
            this.radius = radius;
        }

        /**
         * Construct a new radius filter with a radius of 0.
         */
        public RadialFilter() {
            this(0);
        }

        public CacheFilter reConstruct(String promptKey) {
            var matcher = this.getRegexKey().matcher(promptKey);
            var found = matcher.find();
            //print all groups
            for (int i = 0; i <= matcher.groupCount(); i++) {
                CommandPrompter.getInstance().getPluginLogger().debug("Group %d: %s", i, matcher.group(i));
            }
            var radius = found ? Integer.parseInt(matcher.group(1)) : 0;
            return new RadialFilter(radius);
        }

        @Override
        public List<Player> filter(Player relativePlayer) {
            return relativePlayer.getWorld().getPlayers().stream()
                    .filter(p -> p.getLocation().distance(relativePlayer.getLocation()) <= radius)
                    .toList();
        }
    }
}
