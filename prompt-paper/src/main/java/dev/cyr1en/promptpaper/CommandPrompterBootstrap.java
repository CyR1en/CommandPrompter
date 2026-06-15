package dev.cyr1en.promptpaper;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper plugin bootstrap executed before {@link CommandPrompter} is loaded.
 * <p>
 * Disables the bStats relocation check. This property must be set in
 * {@code bootstrap()} — before the shaded bStats library inspects its own
 * class locations — and cannot be moved to {@link CommandPrompter#onEnable()}.
 * <p>
 * Command registration is handled in {@link CommandPrompter#onEnable()} via
 * {@code LifecycleEvents.COMMANDS}. Registering the handler here was tried
 * first but proven unreliable: Paper can fire the COMMANDS event before
 * {@link #createPlugin(PluginProviderContext)} populates the bootstrap's
 * plugin reference, leaving a null at registration time and producing an
 * NPE on the first command invocation.
 */
public class CommandPrompterBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        System.setProperty("bstats.relocatecheck", "false");
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        return new CommandPrompter();
    }
}
