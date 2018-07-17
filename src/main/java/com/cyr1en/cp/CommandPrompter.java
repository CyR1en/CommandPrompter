package com.cyr1en.cp;

import com.cyr1en.cp.config.SimpleConfig;
import com.cyr1en.cp.config.SimpleConfigManager;
import com.cyr1en.cp.listener.CommandListener;
import com.cyr1en.cp.util.PluginUpdater;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class CommandPrompter extends JavaPlugin {

    private final String[] CONFIG_HEADER = new String[]{"Command Prompter", "Configuration"};

    private SimpleConfigManager manager;
    private SimpleConfig config;
    private Logger logger;

    @Override
    public void onEnable() {
        Bukkit.getServer().getScheduler().runTaskLater(this, this::start, 1L);
    }

    @Override
    public void onDisable() {
    }

    private void start() {
        logger = getLogger();
        if (ProxySelector.getDefault() == null) {
            ProxySelector.setDefault(new ProxySelector() {
                private final List<Proxy> DIRECT_CONNECTION = Collections.unmodifiableList(Collections.singletonList(Proxy.NO_PROXY));
                public void connectFailed(URI arg0, SocketAddress arg1, IOException arg2) {}
                public List<Proxy> select(URI uri) { return DIRECT_CONNECTION; }
            });
        }
        PluginUpdater spu = new PluginUpdater(this, "https://contents.cyr1en.com/command-prompter/plinfo/");
        Bukkit.getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (spu.needsUpdate())
                logger.warning("A new update is available!");
            else
                logger.info("No update was found.");
        });
        this.manager = new SimpleConfigManager(this);
        Bukkit.getPluginManager().registerEvents(new CommandListener(this), this);
        Bukkit.getPluginManager().registerEvents(spu, this);
        setupConfig();
    }

    private void setupConfig() {
        config = manager.getNewConfig("config.yml", CONFIG_HEADER);
        if (config.get("Prompt-Prefix") == null) {
            config.set("Prompt-Prefix", "[&3&lPrompter&r] ", "Set the prompter's prefix");
            config.saveConfig();
        }
        if (config.get("Prompt-Timeout") == null) {
            config.set("Prompt-Timeout", 300, new String[]{"After how many seconds", "until CommandPrompter cancels", "a prompt"});
            config.saveConfig();
        }
        if (config.get("Timeout-Message") == null) {
            config.set("Timeout-Message", "Command execution has been cancelled!",
                    new String[]{"Message that will be sent", "to players when prompts", "automatically cancels"});
            config.saveConfig();
        }
    }

    public SimpleConfig getConfiguration() {
        return config;
    }
}
