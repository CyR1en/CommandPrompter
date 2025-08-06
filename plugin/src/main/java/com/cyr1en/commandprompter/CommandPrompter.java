package com.cyr1en.commandprompter;

import com.cyr1en.commandprompter.common.util.ServerUtil;
import com.cyr1en.commandprompter.config.CommandPrompterConfig;
import com.cyr1en.commandprompter.config.ConfigurationManager;
import com.cyr1en.commandprompter.config.PromptConfig;

import com.cyr1en.commandprompter.util.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandPrompter extends JavaPlugin {


    private ConfigurationManager configManager;
    private CommandPrompterConfig config;
    private PromptConfig promptConfig;

    private PluginLogger logger;

    public CommandPrompter() {
        setupConfig();
        logger = new PluginLogger(this, "CommandPrompter");
    }

    private void setupConfig() {
        configManager = new ConfigurationManager(this);
        config = configManager.getConfig(CommandPrompterConfig.class);
        promptConfig = configManager.getConfig(PromptConfig.class);
    }

    public CommandPrompterConfig getConfiguration() {
        return this.config;
    }

    @Override
    public void onLoad() {
        var serverType = ServerUtil.resolve();
        logger.debug("Server Name: " + serverType.name());
        logger.debug("Server Version: " + ServerUtil.version());
    }

    @Override
    public void onEnable() {
        // Plugin startup logic

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
