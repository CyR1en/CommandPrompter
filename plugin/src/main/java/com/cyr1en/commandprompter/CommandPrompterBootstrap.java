package com.cyr1en.commandprompter;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("UnstableApiUsage")
public class CommandPrompterBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {

    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        return new CommandPrompter();
    }
}
