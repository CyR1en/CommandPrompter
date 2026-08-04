package dev.cyr1en.promptpaper;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper plugin bootstrap executed before {@link CommandPrompter} is loaded.
 * <p>
 * The bootstrap is intentionally limited to creating the plugin instance.
 * Command registration is handled in {@link CommandPrompter#onEnable()} via
 * {@code LifecycleEvents.COMMANDS}; registering the handler here can happen
 * before the plugin instance is available to command constructors. bStats is
 * relocated in the shadow jar, so no global relocation-check override is
 * required here.
 */
public class CommandPrompterBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        // Keep bootstrap side-effect free. Shaded libraries are privately relocated.
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        return new CommandPrompter();
    }
}
