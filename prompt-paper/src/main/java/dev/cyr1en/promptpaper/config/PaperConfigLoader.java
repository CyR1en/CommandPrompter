package dev.cyr1en.promptpaper.config;

import dev.cyr1en.promptcore.config.RecordConfigLoader;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.i18n.PaperI18n;

/**
 * Convenience facade that loads and caches all plugin config records and the i18n service.
 *
 * <p>Constructed once at plugin enable; call {@link #reload()} to re-read from disk.
 */
public class PaperConfigLoader {

    private final CommandPrompter plugin;
    private final RecordConfigLoader configManager;
    private volatile ConfigState state;

    /**
     * Creates the loader and immediately loads all config files.
     *
     * @param plugin the owning plugin
     */
    public PaperConfigLoader(CommandPrompter plugin) {
        this.plugin = plugin;
        this.configManager = new RecordConfigLoader(plugin.getDataFolder());
        reload();
    }

    /** Re-reads {@code config.yml} and {@code prompt-config.yml} from disk, replacing all cached records. */
    public void reload() {
        // Load and validate the complete replacement off to the side. A single volatile state
        // publication prevents player scheduler threads from observing a mixed old/new set.
        var newConfig = configManager.getConfig(CommandPrompterConfig.class);
        plugin.getLogger().fine("config.yml loaded: timeout=" + newConfig.promptTimeout()
                + " debug=" + newConfig.debugMode() + " fancy=" + newConfig.fancyLogger()
                + " locale=" + newConfig.locale());
        var newPromptConfig = configManager.getConfig(PromptConfig.class);
        plugin.getLogger().fine("prompt-config.yml loaded: mappings="
                + newPromptConfig.getScreenMappings().size());
        var newI18n = new PaperI18n(
                newConfig.locale(),
                plugin.getDataFolder(),
                plugin.getClass().getClassLoader(),
                plugin.getLogger());
        plugin.getLogger().fine("i18n reloaded for locale=" + newConfig.locale());
        var newState = new ConfigState(newConfig, newPromptConfig, newI18n);
        state = newState;
        plugin.getLogger().fine("Configuration reloaded successfully");
    }

    public CommandPrompterConfig getConfig() {
        return state.config();
    }

    public PromptConfig getPromptConfig() {
        return state.promptConfig();
    }

    public PaperI18n getI18n() {
        return state.i18n();
    }

    private record ConfigState(
            CommandPrompterConfig config, PromptConfig promptConfig, PaperI18n i18n) {}
}
