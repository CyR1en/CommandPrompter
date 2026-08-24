package dev.cyr1en.promptpaper;

import dev.cyr1en.promptpaper.command.CommandRegistrar;
import dev.cyr1en.promptpaper.config.PaperConfigLoader;
import dev.cyr1en.promptpaper.config.PromptConfig;
import dev.cyr1en.promptpaper.custom.CommandPrompterAPIFacade;
import dev.cyr1en.promptpaper.custom.CustomScreenAuditLogger;
import dev.cyr1en.promptpaper.custom.CustomScreenRegistry;
import dev.cyr1en.promptpaper.custom.ScreenKeyResolver;
import dev.cyr1en.promptpaper.engine.PromptEngine;
import dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator;
import dev.cyr1en.promptpaper.execution.dispatch.PaperImmediateActionDispatcher;
import dev.cyr1en.promptpaper.execution.dispatch.PaperPrimaryCommandDispatcher;
import dev.cyr1en.promptpaper.execution.runtime.ExecutionRegistry;
import dev.cyr1en.promptpaper.factory.PromptFactory;
import dev.cyr1en.promptpaper.hook.HookContainer;
import dev.cyr1en.promptpaper.hook.PluginHook;
import dev.cyr1en.promptpaper.hook.hooks.ChatListenerHook;
import dev.cyr1en.promptpaper.i18n.PaperI18n;
import dev.cyr1en.promptpaper.item.catalog.ItemCatalogRegistry;
import dev.cyr1en.promptpaper.listener.ChatPromptListener;
import dev.cyr1en.promptpaper.listener.CommandSendListener;
import dev.cyr1en.promptpaper.listener.PlayerCommandListener;
import dev.cyr1en.promptpaper.listener.PluginDisableListener;
import dev.cyr1en.promptpaper.preset.PresetRegistry;
import dev.cyr1en.promptpaper.screen.ScreenManager;
import dev.cyr1en.promptpaper.screen.confirmation.ConfirmationRateLimiter;
import dev.cyr1en.promptpaper.screen.confirmation.NonceResponseRegistry;
import dev.cyr1en.promptpaper.screen.playerui.HeadCache;
import dev.cyr1en.promptpaper.util.PaperScheduler;
import dev.cyr1en.promptpaper.util.PluginLogger;
import dev.cyr1en.promptpaper.util.Scheduler;
import dev.cyr1en.promptui.api.CommandPrompterAPI;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
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
    private PresetRegistry presetRegistry;
    private ItemCatalogRegistry itemCatalogRegistry;
    private PromptFactory promptFactory;
    private Scheduler scheduler;
    private NonceResponseRegistry nonceResponseRegistry;
    private ConfirmationRateLimiter confirmationRateLimiter;
    private CustomScreenAuditLogger customScreenAuditLogger;
    private CustomScreenRegistry customScreenRegistry;
    private ScreenKeyResolver screenKeyResolver;
    private ExecutionRegistry executionRegistry;
    private dev.cyr1en.promptpaper.execution.dispatch.PaperPrimaryCommandDispatcher primaryCommandDispatcher;
    private dev.cyr1en.promptpaper.execution.dispatch.PaperImmediateActionDispatcher immediateActionDispatcher;
    private dev.cyr1en.promptpaper.approval.ApprovalCoordinator approvalCoordinator;
    private dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator executionCoordinator;
    private CommandPrompterAPIFacade apiFacade;
    private final AtomicBoolean activeLifecycle = new AtomicBoolean(false);

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

            initLoggerAndConfig();
            initPresets();
            initItemCatalogs();
            initCustomScreenRegistryAndResolver();
            initCoreSubsystems();
            initListeners();
            initHooks();
            initCommands();

            // Set internal lifecycle active immediately BEFORE ServicesManager registration
            this.activeLifecycle.set(true);
            registerServices();

            pluginLogger.info("CommandPrompterPaper v" + getPluginMeta().getVersion() + " enabled.");
        } catch (Exception e) {
            getLogger().severe("Failed to enable CommandPrompterPaper: " + e.getMessage());
            cleanupEnableFailure();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initLoggerAndConfig() {
        this.pluginLogger = new PluginLogger(this);
        this.configLoader = new PaperConfigLoader(this);
        pluginLogger.reload(configLoader.getConfig());
        pluginLogger.debug("Config loaded, debugMode=" + configLoader.getConfig().debugMode()
                + " locale=" + configLoader.getConfig().locale());
        pluginLogger.debug("I18n initialized for locale=" + configLoader.getConfig().locale());
    }

    private void initPresets() {
        this.presetRegistry = new PresetRegistry(this);
        this.presetRegistry.reload();
        var presetMsg = "Loaded presets: <green>" + presetRegistry.promptCount() + " prompts</green>, <gold>" +
                presetRegistry.postCommandCount() + " post commands</gold>";
        pluginLogger.info(presetMsg);
        pluginLogger.debug("Loaded prompt IDs: " + String.join(", ", presetRegistry.getPromptIds()));
        pluginLogger.debug("Loaded post-command IDs: " + String.join(", ", presetRegistry.getPostCommandIds()));
    }

    private void initItemCatalogs() {
        this.itemCatalogRegistry = new ItemCatalogRegistry(this);
        this.itemCatalogRegistry.reload();
        var snapshot = itemCatalogRegistry.getSnapshot();
        var catalogMsg = "Loaded item catalogs: <green>" + snapshot.categoryCount() + " categories</green>, <gold>" +
                snapshot.totalEntryCount() + " items</gold>";
        pluginLogger.info(catalogMsg);
        pluginLogger.debug("Loaded catalog categories: " + String.join(", ", snapshot.categories()));
    }

    private void initCustomScreenRegistryAndResolver() {
        this.customScreenAuditLogger = event -> {
            if (pluginLogger != null) {
                pluginLogger.info("[CustomScreenAudit] " + event.type()
                        + ": key='" + event.key()
                        + "', owner='" + event.ownerName()
                        + "', providerId=" + event.providerId()
                        + " (" + event.detail() + ")");
            }
        };

        this.customScreenRegistry = new CustomScreenRegistry(
                this::isPluginActive,
                () -> configLoader != null && configLoader.getPromptConfig() != null
                        ? configLoader.getPromptConfig().getScreenMappings()
                        : Map.of(),
                PromptConfig.RESERVED_SCREEN_KEYS,
                this.customScreenAuditLogger
        );
        this.screenKeyResolver = new ScreenKeyResolver(
                customScreenRegistry,
                () -> configLoader != null && configLoader.getPromptConfig() != null
                        ? configLoader.getPromptConfig().getScreenMappings()
                        : Map.of()
        );
        pluginLogger.debug("CustomScreenRegistry and ScreenKeyResolver initialized");
    }

    private void initCoreSubsystems() {
        this.scheduler = new PaperScheduler(this);
        pluginLogger.debug("Scheduler: PaperScheduler (Folia-safe)");
        var leaseRegistry = new dev.cyr1en.promptpaper.approval.PlayerInteractionLeaseRegistry();
        this.engine = new PromptEngine(this, scheduler, screenKeyResolver, null, leaseRegistry);
        pluginLogger.debug("PromptEngine initialized");

        this.executionRegistry = new ExecutionRegistry();
        this.engine.setExecutionRegistry(executionRegistry);
        this.primaryCommandDispatcher = new dev.cyr1en.promptpaper.execution.dispatch.PaperPrimaryCommandDispatcher(this, scheduler);
        this.immediateActionDispatcher = new dev.cyr1en.promptpaper.execution.dispatch.PaperImmediateActionDispatcher(this, scheduler);

        this.nonceResponseRegistry = new NonceResponseRegistry();
        this.confirmationRateLimiter = new ConfirmationRateLimiter();
        pluginLogger.debug("Confirmation services initialized (nonce registry & rate limiter)");

        this.promptFactory = new PromptFactory(this);
        this.screenManager = new ScreenManager(
                this, engine, promptFactory, scheduler, null, null, null, null);
        this.approvalCoordinator = new dev.cyr1en.promptpaper.approval.ApprovalCoordinator(
                this, scheduler, executionRegistry, screenManager, engine, null, leaseRegistry, null, null, null, null);
        this.executionCoordinator = new dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator(
                this, engine, executionRegistry, primaryCommandDispatcher, immediateActionDispatcher, approvalCoordinator);
        this.screenManager.setExecutionCoordinator(executionCoordinator);
        this.engine.setExecutionCoordinator(executionCoordinator);
        pluginLogger.debug("ApprovalCoordinator and ExecutionCoordinator initialized");
        pluginLogger.debug("ScreenManager initialized (factory: providers=" + promptFactory.providerCount() + ")");
    }

    private void initListeners() {
        this.headCache = new HeadCache(this, scheduler);
        registerEvents(headCache);
        pluginLogger.debug("HeadCache registered");

        registerEvents(new PlayerCommandListener(this, screenManager));
        registerEvents(new CommandSendListener(this));
        registerEvents(new PluginDisableListener(this, screenManager));
        registerEvents(this);
        pluginLogger.debug("Listeners registered");
    }

    private void registerServices() {
        this.apiFacade = new CommandPrompterAPIFacade(
                customScreenRegistry,
                screenManager.getProviderLifecycleCoordinator()
        );
        getServer().getServicesManager().register(
                CommandPrompterAPI.class,
                apiFacade,
                this,
                ServicePriority.Normal);
        pluginLogger.debug("CommandPrompterAPI registered with Bukkit ServicesManager");
    }

    private void cleanupEnableFailure() {
        if (activeLifecycle != null) {
            this.activeLifecycle.set(false);
        }
        if (customScreenRegistry != null) {
            customScreenRegistry.freeze();
        }
        try {
            if (getServer() != null && getServer().getServicesManager() != null) {
                getServer().getServicesManager().unregisterAll(this);
            }
        } catch (Throwable ignored) {}
        if (customScreenRegistry != null) {
            customScreenRegistry.unregisterAll();
        }
    }

    private void initHooks() {
        this.hookContainer = new HookContainer(this);
        hookContainer.initHooks();
        headCache.registerFilters(hookContainer);
        var hookedCount = hookContainer.getHooksImplementing(PluginHook.class).size();
        pluginLogger.debug("Hooks initialized: " + hookedCount + " active");

        resolveChatListener();
    }

    private void initCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> new CommandRegistrar(this).registerAll(event.registrar()));
        pluginLogger.debug("Command registrar registered");
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
        getServer().getPluginManager().registerEvent(
                AsyncChatEvent.class,
                listener,
                listener.resolvePriority(),
                (registeredListener, event) -> listener.onPlayerChat((AsyncChatEvent) event),
                this,
                false);
        pluginLogger.info("Using default Bukkit chat listener");
    }

    /** Cancels all active screens and sessions for a disconnecting player. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();
        var eng = getEngine();
        var hasSession = eng != null && eng.hasActiveSession(player);
        var logger = getPluginLogger();
        if (logger != null) {
            logger.debug("Player quit: name=" + player.getName()
                    + " hasSession=" + hasSession);
        }
        var appCoord = getApprovalCoordinator();
        if (appCoord != null) {
            appCoord.onInitiatorQuit(uuid);
            appCoord.onTargetQuit(uuid);
        }
        var screens = getScreenManager();
        if (screens != null) {
            screens.cancelAll(player, dev.cyr1en.promptpaper.engine.CancellationMode.DISCARD_ONLY, false);
        } else if (eng != null) {
            eng.discard(uuid);
        }
        var execCoord = getExecutionCoordinator();
        if (execCoord != null) {
            execCoord.cancel(uuid);
        }
    }

    /** Tears down hooks, cancels all active sessions, and logs shutdown. */
    @Override
    public void onDisable() {
        // 1. Mark inactive & freeze custom registry FIRST, and unregister ServicesManager
        if (activeLifecycle != null) {
            this.activeLifecycle.set(false);
        }
        var registry = getCustomScreenRegistry();
        if (registry != null) {
            registry.freeze();
        }
        try {
            if (getServer() != null && getServer().getServicesManager() != null) {
                getServer().getServicesManager().unregisterAll(this);
            }
        } catch (Throwable ignored) {}

        var logger = getPluginLogger();
        if (logger != null) {
            logger.debug("Disabling plugin");
        }

        // 2. Perform bulk custom screen teardown before general subsystem shutdown
        var screens = getScreenManager();
        if (screens != null) {
            screens.bulkTeardownCustomScreens();
        }

        // 3. Disable hooks
        var hooks = getHookContainer();
        if (hooks != null) hooks.disableAll();

        // 4. Cancel engine sessions and executions
        var eng = getEngine();
        if (eng != null) eng.cancelAll(dev.cyr1en.promptpaper.engine.CancellationMode.DISCARD_ONLY);
        if (approvalCoordinator != null) {
            approvalCoordinator.shutdown();
        }
        var execCoord = getExecutionCoordinator();
        if (execCoord != null) execCoord.cancelAll();

        // 5. Clear nonces and rate limiter
        var nonces = getNonceRegistry();
        if (nonces != null) nonces.clear();
        var rateLimiter = getRateLimiter();
        if (rateLimiter != null) rateLimiter.clear();

        // 6. Tear down any remaining built-in active screens across online players
        if (screens != null) {
            screens.teardownBuiltInScreens();
        }

        if (logger != null) {
            logger.info("CommandPrompterPaper disabled.");
        }
    }

    /** Registers an event listener with the server. */
    private void registerEvents(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    public boolean isPluginActive() {
        return activeLifecycle.get() && isEnabled();
    }

    public CustomScreenAuditLogger getCustomScreenAuditLogger() { return customScreenAuditLogger; }
    public CustomScreenRegistry getCustomScreenRegistry() { return customScreenRegistry; }
    public ScreenKeyResolver getScreenKeyResolver() { return screenKeyResolver; }
    public PaperConfigLoader getConfigLoader() { return configLoader; }
    public PluginLogger getPluginLogger() { return pluginLogger; }
    public PromptEngine getEngine() { return engine; }
    public ScreenManager getScreenManager() { return screenManager; }
    public ExecutionRegistry getExecutionRegistry() { return executionRegistry; }
    public dev.cyr1en.promptpaper.execution.dispatch.PrimaryCommandDispatcher getPrimaryCommandDispatcher() { return primaryCommandDispatcher; }
    public dev.cyr1en.promptpaper.execution.dispatch.ImmediateActionDispatcher getImmediateActionDispatcher() { return immediateActionDispatcher; }
    public dev.cyr1en.promptpaper.approval.ApprovalCoordinator getApprovalCoordinator() { return approvalCoordinator; }
    public dev.cyr1en.promptpaper.execution.coordinator.ExecutionCoordinator getExecutionCoordinator() { return executionCoordinator; }
    public HeadCache getHeadCache() { return headCache; }
    public HookContainer getHookContainer() { return hookContainer; }
    public PresetRegistry getPresetRegistry() { return presetRegistry; }
    public ItemCatalogRegistry getItemCatalogRegistry() { return itemCatalogRegistry; }
    public ItemCatalogRegistry getCatalogRegistry() { return itemCatalogRegistry; }
    public PromptFactory getPromptFactory() { return promptFactory; }
    public Scheduler getScheduler() { return scheduler; }
    public NonceResponseRegistry getNonceRegistry() { return nonceResponseRegistry; }
    public ConfirmationRateLimiter getRateLimiter() { return confirmationRateLimiter; }
    public PaperI18n getI18n() { return configLoader.getI18n(); }
}
