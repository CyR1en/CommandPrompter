package us.cyrien.cp;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import us.cyrien.cp.config.SimpleConfig;
import us.cyrien.cp.config.SimpleConfigManager;
import us.cyrien.cp.listener.CommandListener;

public class CommandPrompter extends JavaPlugin {

    private final String[] CONFIG_HEADER = new String[]{"Command Prompter", "Configuration"};

    private SimpleConfigManager manager;
    private SimpleConfig config;

    @Override
    public void onEnable() {
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
            config.set("Prompt-Prefix", "[&3&lPrompter&r]", "Set the prompter's prefix");
            config.saveConfig();
        }
    }

    public SimpleConfig getConfiguration() {
        return config;
    }
}
