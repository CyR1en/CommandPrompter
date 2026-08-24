package dev.cyr1en.promptpaper.config;

import dev.cyr1en.promptcore.config.RecordConfigLoader;
import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.i18n.PaperI18n;
import java.util.Objects;

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

    /** Fully parsed configuration generation that has not yet been published. */
    public record PreparedReload(
            CommandPrompterConfig config, PromptConfig promptConfig, PaperI18n i18n) {
        public PreparedReload {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(promptConfig, "promptConfig");
            Objects.requireNonNull(i18n, "i18n");
        }
    }

    /** Re-reads and validates all configuration files without changing the active state. */
    public PreparedReload prepareReload() {
        var newConfig = configManager.getConfig(CommandPrompterConfig.class);
        plugin.getLogger().fine("config.yml loaded: timeout=" + newConfig.promptTimeout()
                + " debug=" + newConfig.debugMode() + " fancy=" + newConfig.fancyLogger()
                + " locale=" + newConfig.locale());
        if (newConfig.isUsingDeprecatedArgumentRegex()) {
            plugin.getLogger().warning("Argument-Regex is deprecated; using derived delimiters '"
                    + newConfig.parserConfig().opening() + "' and '" + newConfig.parserConfig().closing()
                    + "'. Please migrate to Syntax.Prompt.Open and Syntax.Prompt.Close.");
        } else if (newConfig.hasArgumentRegexDisagreement()) {
            plugin.getLogger().warning("Argument-Regex is deprecated and conflicts with Syntax.Prompt; using Syntax.Prompt.");
        }
        var newPromptConfig = configManager.getConfig(PromptConfig.class);
        plugin.getLogger().fine("prompt-config.yml loaded: mappings="
                + newPromptConfig.getScreenMappings().size());

        var newI18n = new PaperI18n(
                newConfig.locale(),
                plugin.getDataFolder(),
                plugin.getClass().getClassLoader(),
                plugin.getLogger());
        plugin.getLogger().fine("i18n reloaded for locale=" + newConfig.locale());
        return new PreparedReload(newConfig, newPromptConfig, newI18n);
    }

    /** Publishes a prepared generation after validating custom-screen mapping collisions. */
    public void publishReload(PreparedReload prepared) {
        publishReload(prepared, () -> {});
    }

    /**
     * Publishes a prepared generation and the remaining runtime snapshots under the same custom
     * screen mutation lock. The callback must contain only non-throwing snapshot assignments.
     */
    public void publishReload(PreparedReload prepared, Runnable publishRuntimeSnapshots) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(publishRuntimeSnapshots, "publishRuntimeSnapshots");

        var customRegistry = plugin != null
                ? (plugin.getCustomScreenRegistry() != null
                        ? plugin.getCustomScreenRegistry()
                        : (plugin.getScreenKeyResolver() != null ? plugin.getScreenKeyResolver().customRegistry() : null))
                : null;
        if (customRegistry != null) {
            customRegistry.validateMappingsAndPublish(prepared.promptConfig().getScreenMappings(), () -> {
                publishRuntimeSnapshots.run();
                state = new ConfigState(prepared.config(), prepared.promptConfig(), prepared.i18n());
            });
        } else {
            publishRuntimeSnapshots.run();
            state = new ConfigState(prepared.config(), prepared.promptConfig(), prepared.i18n());
        }
        plugin.getLogger().fine("Configuration reloaded successfully");
    }

    /** Re-reads, validates, and atomically publishes this loader's configuration state. */
    public void reload() {
        publishReload(prepareReload());
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
