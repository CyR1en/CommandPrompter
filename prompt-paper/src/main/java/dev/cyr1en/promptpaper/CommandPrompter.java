package dev.cyr1en.promptpaper;

import dev.cyr1en.promptpaper.command.CommandRegistrar;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.PluginHook;
import dev.cyr1en.promptpaper.hook.hooks.ChatListenerHook;
import dev.cyr1en.promptpaper.listener.ChatPromptListener;
import dev.cyr1en.promptpaper.listener.CommandSendListener;
import dev.cyr1en.promptpaper.listener.PlayerCommandListener;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.screen.ScreenRouter;
import dev.cyr1en.promptpaper.screen.playerui.HeadCache;
import dev.cyr1en.promptpaper.util.PaperScheduler;
import dev.cyr1en.promptpaper.util.PluginLogger;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main Paper plugin entry point for CommandPrompter v3.
 * Wires together the prompt engine, screen manager, hooks, and listeners.
 * Command registration is handled at the end of {@link #onEnable()} via
 * {@link CommandRegistrar} and {@code LifecycleEvents.COMMANDS} — this
 * class is intentionally unaware of the concrete command classes.
 * <p>
 * Registration deliberately happens in {@code onEnable()} rather than in
 * {@link CommandPrompterBootstrap#bootstrap} because Paper can fire the
 * COMMANDS event before {@code createPlugin()} populates the bootstrap's
 * plugin reference, producing a null at registration time and an NPE on
 * the first command invocation.
 */
public class CommandPrompter extends JavaPlugin implements Listener {

    private PaperConfigLoader configLoader;
    private PluginLogger pluginLogger;
    private PromptEngine engine;
    private ScreenManager screenManager;
    private HeadCache headCache;
    private HookContainer hookContainer;

    /**
     * Initializes all plugin subsystems: config, scheduler, engine, screen
     * manager, listeners, hooks, and the head cache. Finally registers a
     * {@code LifecycleEvents.COMMANDS} handler that builds and registers all
     * top-level commands via {@link CommandRegistrar}. Disables the plugin
     * on failure.
     */
    @Override
    public void onEnable() {
        try {
            new Metrics(this, 5359);

            this.pluginLogger = new PluginLogger(this);
            this.configLoader = new PaperConfigLoader(this);
            pluginLogger.reload(configLoader.getConfig());
            pluginLogger.debug("Config loaded, debugMode=" + configLoader.getConfig().debugMode());

            var scheduler = new PaperScheduler(this);
            pluginLogger.debug("Scheduler: PaperScheduler (Folia-safe)");
            this.engine = new PromptEngine(this, scheduler);
            pluginLogger.debug("PromptEngine initialized");

            var router = new ScreenRouter(this);
            this.screenManager = new ScreenManager(this, engine, router, scheduler);
            pluginLogger.debug("ScreenManager initialized");

            this.headCache = new HeadCache(this, scheduler);
            getServer().getPluginManager().registerEvents(headCache, this);
            pluginLogger.debug("HeadCache registered");

            getServer().getPluginManager().registerEvents(
                    new PlayerCommandListener(this, screenManager), this);
            getServer().getPluginManager().registerEvents(
                    new CommandSendListener(this), this);
            getServer().getPluginManager().registerEvents(this, this);
            pluginLogger.debug("Listeners registered");

            this.hookContainer = new HookContainer(this);
            hookContainer.initHooks();
            headCache.registerFilters(hookContainer);
            var hookedCount = hookContainer.getHooksImplementing(PluginHook.class).size();
            pluginLogger.debug("Hooks initialized: " + hookedCount + " active");

            resolveChatListener();

            getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                    event -> new CommandRegistrar(this).registerAll(event.registrar()));
            pluginLogger.debug("Command registrar registered");

            pluginLogger.info("CommandPrompterPaper v" + getPluginMeta().getVersion() + " enabled.");
        } catch (Exception e) {
            getLogger().severe("Failed to enable CommandPrompterPaper: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Selects the best available chat input listener. Iterates registered
     * {@link ChatListenerHook} implementations (e.g. CarbonChat) and
     * subscribes the first one that succeeds; otherwise falls back to the
     * default Bukkit listener.
     */
    private void resolveChatListener() {
        var chatHooks = hookContainer.getHooksImplementing(ChatListenerHook.class);
        for (var hook : chatHooks) {
            if (hook.subscribe(screenManager)) {
                pluginLogger.info("Using " + hook.getClass().getSimpleName() + " for chat input");
                return;
            }
        }
        var listener = new ChatPromptListener(this, screenManager);
        getServer().getPluginManager().registerEvents(listener, this);
        pluginLogger.info("Using default Bukkit chat listener");
    }

    /** Cancels all active screens and sessions for a disconnecting player. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var hasSession = engine != null && engine.hasActiveSession(player);
        pluginLogger.debug("Player quit: name=" + player.getName()
                + " hasSession=" + hasSession);
        if (screenManager != null) screenManager.cancelAll(player);
    }

    /** Tears down hooks, cancels all active sessions, and logs shutdown. */
    @Override
    public void onDisable() {
        if (pluginLogger != null) {
            pluginLogger.debug("Disabling plugin: hooks=" + (hookContainer != null)
                    + " engine=" + (engine != null) + " screenManager=" + (screenManager != null));
        }
        if (hookContainer != null) hookContainer.disableAll();
        if (engine != null) engine.cancelAll();
        if (pluginLogger != null) {
            pluginLogger.info("CommandPrompterPaper disabled.");
        }
    }

    public PaperConfigLoader getConfigLoader() { return configLoader; }
    public PluginLogger getPluginLogger() { return pluginLogger; }
    public PromptEngine getEngine() { return engine; }
    public ScreenManager getScreenManager() { return screenManager; }
    public HeadCache getHeadCache() { return headCache; }
    public HookContainer getHookContainer() { return hookContainer; }
}
