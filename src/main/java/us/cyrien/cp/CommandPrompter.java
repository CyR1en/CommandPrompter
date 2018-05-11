package us.cyrien.cp;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import us.cyrien.cp.config.SimpleConfig;
import us.cyrien.cp.config.SimpleConfigManager;
import us.cyrien.cp.listener.CommandListener;
import us.cyrien.cp.util.SpigotPluginUpdater;

import java.util.logging.Logger;

public class CommandPrompter extends JavaPlugin {

    private final String[] CONFIG_HEADER = new String[]{"Command Prompter", "Configuration"};

    private SimpleConfigManager manager;
    private SimpleConfig config;
    private Logger logger;

    @Override
    public void onEnable() {
        logger = getLogger();
        SpigotPluginUpdater spu = new SpigotPluginUpdater(this, "https://contents.cyr1en.com/command-prompter/plugin.html");
        spu.enableOut();
        if(spu.needsUpdate()) {
            logger.warning("A new update is available!");
            spu.externalUpdate();
        } else {
            logger.info("No update was found.");
        }
        this.manager = new SimpleConfigManager(this);
        Bukkit.getPluginManager().registerEvents(new CommandListener(this), this);
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
