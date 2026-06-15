package dev.cyr1en.promptpaper.screen.playerui;

import dev.cyr1en.promptpaper.config.PromptConfig;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Base class for player-list filters used by {@link HeadCache} to narrow
 * which player heads appear in a {@link PlayerUIScreen}.
 */
public abstract class CacheFilter {

    private final Pattern regexKey;
    private final String configKey;
    private final int capGroupOffset;

    public CacheFilter(Pattern regexKey, String configKey) { this(regexKey, configKey, 0); }

    public CacheFilter(Pattern regexKey, String configKey, int capGroupOffset) {
        this.regexKey = regexKey;
        this.configKey = configKey;
        this.capGroupOffset = capGroupOffset;
    }

    public Pattern getRegexKey() { return regexKey; }

    public String getConfigKey() { return configKey; }

    public int getCapGroupOffset() { return capGroupOffset; }

    public String getFormat(PromptConfig config) {
        return config.getFilterFormat(configKey);
    }

    /**
     * Reconstructs this filter from a raw prompt-key string, extracting any embedded parameters.
     */
    public abstract CacheFilter reConstruct(String promptKey);

    /**
     * Returns online players matching this filter relative to the given player.
     */
    public abstract List<Player> filter(Player relativePlayer);

    @Override
    public String toString() { return getClass().getSimpleName(); }

    public static class WorldFilter extends CacheFilter {
        public WorldFilter() { super(Pattern.compile("w"), "World"); }
        @Override public List<Player> filter(Player p) { return p.getWorld().getPlayers(); }
        public CacheFilter reConstruct(String s) { return new WorldFilter(); }
    }

    public static class RadialFilter extends CacheFilter {
        private final int radius;
        public RadialFilter(int radius) { super(Pattern.compile("r(\\d+)"), "Radial", 1); this.radius = radius; }
        public RadialFilter() { this(0); }
        public CacheFilter reConstruct(String s) {
            var m = getRegexKey().matcher(s);
            return new RadialFilter(m.find() ? Integer.parseInt(m.group(1)) : 0);
        }
        @Override public List<Player> filter(Player p) {
            return p.getWorld().getPlayers().stream()
                    .filter(o -> o.getLocation().distance(p.getLocation()) <= radius).toList();
        }
    }

    public static class SelfFilter extends CacheFilter {
        public SelfFilter() { super(Pattern.compile("s"), "Self"); }
        @Override public List<Player> filter(Player p) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(OfflinePlayer::getPlayer).filter(Objects::nonNull)
                    .filter(o -> !o.equals(p)).toList();
        }
        public CacheFilter reConstruct(String s) { return new SelfFilter(); }
    }
}
