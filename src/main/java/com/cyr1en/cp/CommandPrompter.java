package com.cyr1en.cp;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.cyr1en.cp.config.SimpleConfig;
import com.cyr1en.cp.config.SimpleConfigManager;
import com.cyr1en.cp.listener.CommandListener;
import com.cyr1en.cp.util.PluginUpdater;

import java.util.logging.Logger;

public class CommandPrompter extends JavaPlugin {

    private final String[] CONFIG_HEADER = new String[]{"Command Prompter", "Configuration"};

    private SimpleConfigManager manager;
    private SimpleConfig config;
    private Logger logger;

    @Override
    public void onEnable() {
        logger = getLogger();
        PluginUpdater spu = new PluginUpdater(this, "https://contents.cyr1en.com/command-prompter/plinfo/");
        if(spu.needsUpdate()) {
            logger.warning("A new update is available!");
        } else {
            logger.info("No update was found.");
        }
        this.manager = new SimpleConfigManager(this);
        Bukkit.getPluginManager().registerEvents(new CommandListener(this), this);
        Bukkit.getPluginManager().registerEvents(spu, this);
        setupConfig();
    }

    @Override
    public void onDisable() {
    }

    private void setupConfig() {
        config = manager.getNewConfig("config.yml", CONFIG_HEADER);
        if (config.get("Prompt-Prefix") == null) {
            config.set("Prompt-Prefix", "[&3&lPrompter&r] ", "Set the prompter's prefix");
            config.saveConfig();
        }
    }

    public SimpleConfig getConfiguration() {
        return config;
    }
}
