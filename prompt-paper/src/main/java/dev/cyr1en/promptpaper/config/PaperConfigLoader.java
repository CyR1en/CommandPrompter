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
    private CommandPrompterConfig config;
    private PromptConfig promptConfig;
    private PaperI18n i18n;

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
        config = configManager.getConfig(CommandPrompterConfig.class);
        plugin.getLogger().fine("config.yml loaded: timeout=" + config.promptTimeout()
                + " debug=" + config.debugMode() + " fancy=" + config.fancyLogger()
                + " locale=" + config.locale());
        promptConfig = configManager.getConfig(PromptConfig.class);
        plugin.getLogger().fine("prompt-config.yml loaded: mappings="
                + promptConfig.getScreenMappings().size());
        if (i18n == null || !i18n.getLocale().equals(config.locale())) {
            i18n = new PaperI18n(
                    config.locale(),
                    plugin.getDataFolder(),
                    plugin.getClass().getClassLoader(),
                    plugin.getLogger());
        } else {
            i18n.reload();
        }
        plugin.getLogger().fine("i18n reloaded for locale=" + config.locale());
        plugin.getLogger().fine("Configuration reloaded successfully");
    }

    public CommandPrompterConfig getConfig() {
        return config;
    }

    public PromptConfig getPromptConfig() {
        return promptConfig;
    }

    public PaperI18n getI18n() {
        return i18n;
    }
}
