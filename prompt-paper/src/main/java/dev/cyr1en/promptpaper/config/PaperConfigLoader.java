package dev.cyr1en.promptpaper.config;

import dev.cyr1en.promptpaper.CommandPrompter;

/**
 * Convenience facade that loads and caches all three plugin config records.
 *
 * <p>Constructed once at plugin enable; call {@link #reload()} to re-read from disk.
 */
public class PaperConfigLoader {

    private final CommandPrompter plugin;
    private final ConfigurationManager configManager;
    private CommandPrompterConfig config;
    private PromptConfig promptConfig;
    private MessageConfig messageConfig;

    /**
     * Creates the loader and immediately loads all config files.
     *
     * @param plugin the owning plugin
     */
    public PaperConfigLoader(CommandPrompter plugin) {
        this.plugin = plugin;
        this.configManager = new ConfigurationManager(plugin);
        reload();
    }

    /** Re-reads {@code config.yml} and {@code prompt-config.yml} from disk, replacing all cached records. */
    public void reload() {
        config = configManager.getConfig(CommandPrompterConfig.class);
        plugin.getLogger().fine("config.yml loaded: timeout=" + config.promptTimeout()
                + " debug=" + config.debugMode() + " fancy=" + config.fancyLogger());
        promptConfig = configManager.getConfig(PromptConfig.class);
        plugin.getLogger().fine("prompt-config.yml loaded: mappings="
                + promptConfig.getScreenMappings().size());
        messageConfig = configManager.getConfig(MessageConfig.class);
        plugin.getLogger().fine("messages loaded: cancel=" + messageConfig.promptCancelled()
                + " timeout=" + messageConfig.promptTimedOut());
        plugin.getLogger().fine("Configuration reloaded successfully");
    }

    public CommandPrompterConfig getConfig() {
        return config;
    }

    public PromptConfig getPromptConfig() {
        return promptConfig;
    }

    public MessageConfig getMessageConfig() {
        return messageConfig;
    }
}
