/*
 * MIT License
 *
 * Copyright (c) 2020 Ethan Bacurio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cyr1en.commandprompter;

import com.cyr1en.commandprompter.commands.CommandAPIWrapper;
import com.cyr1en.commandprompter.config.CommandPrompterConfig;
import com.cyr1en.commandprompter.config.ConfigurationManager;
import com.cyr1en.commandprompter.config.PromptConfig;
import com.cyr1en.commandprompter.hook.HookContainer;
import com.cyr1en.commandprompter.listener.CommandListener;
import com.cyr1en.commandprompter.listener.CommandSendListener;
import com.cyr1en.commandprompter.listener.VanillaListener;
import com.cyr1en.commandprompter.prompt.PromptManager;
import com.cyr1en.commandprompter.prompt.prompts.ChatPrompt;
import com.cyr1en.commandprompter.prompt.ui.HeadCache;
import com.cyr1en.commandprompter.util.PluginLogger;
import com.cyr1en.commandprompter.util.PluginMessenger;
import com.cyr1en.commandprompter.util.ServerUtil;
import com.cyr1en.kiso.mc.I18N;
import com.cyr1en.kiso.mc.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

import static com.cyr1en.commandprompter.util.AdventureUtil.mm;

public class CommandPrompter extends JavaPlugin {

    private static CommandPrompter instance;

    private ConfigurationManager configManager;
    private CommandPrompterConfig config;
    private PromptConfig promptConfig;
    private HookContainer hookContainer;

    private PluginLogger logger;
    private CommandListener commandListener;
    private I18N i18n;
    private UpdateChecker updateChecker;
    private PromptManager promptManager;
    private PluginMessenger messenger;
    private HeadCache headCache;
    private CommandAPIWrapper commandAPIWrapper;

    public CommandPrompter() {
        new Metrics(this, 5359);
        setupConfig();
        logger = new PluginLogger(this, "CommandPrompter");
        var serverType = ServerUtil.resolve();
        logger.debug("Server Name: " + serverType.name());
        logger.debug("Server Version: " + ServerUtil.version());
        i18n = new I18N(this, "CommandPrompter");
        messenger = new PluginMessenger(config.promptPrefix());
        commandAPIWrapper = new CommandAPIWrapper(this);
        instance = this;
    }

    @Override
    public void onLoad() {
        commandAPIWrapper.load();
    }

    @Override
    public void onEnable() {
        commandAPIWrapper.onEnable();
        setupUpdater();
        initPromptSystem();
        setupCommands();
        Bukkit.getPluginManager().registerEvents(new CommandSendListener(this), this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            hookContainer = new HookContainer(this);
            hookContainer.initHooks();
            headCache.registerFilters();
            promptManager.registerPrompts();
            ChatPrompt.resolveListener(this);
        }, 1L);
    }

    @Override
    public void onDisable() {
        commandAPIWrapper.onDisable();
        if (promptManager != null)
            promptManager.clearPromptRegistry();

        if (Objects.nonNull(updateChecker) && !updateChecker.isDisabled())
            HandlerList.unregisterAll(updateChecker);
    }

    private void initPromptSystem() {
        Bukkit.getPluginManager().registerEvents(headCache = new HeadCache(this), this);
        promptManager = new PromptManager(this);
        initCommandListener();
    }

    /**
     * Function to initialize the command listener that this plugin will use
     * <p>
     * If unsafe is enabled in the config, this plugin will use the modified
     * command map. Otherwise, it will just use the vanilla listener.
     */
    private void initCommandListener() {
        commandListener = new VanillaListener(promptManager);
        Bukkit.getPluginManager().registerEvents(commandListener, this);
    }

    private void setupConfig() {
        configManager = new ConfigurationManager(this);
        config = configManager.getConfig(CommandPrompterConfig.class);
        promptConfig = configManager.getConfig(PromptConfig.class);
    }

    private void setupCommands() {
        commandAPIWrapper.registerCommands();
    }

    private void setupUpdater() {
        updateChecker = new UpdateChecker(this, 47772);
        if (updateChecker.isDisabled())
            return;
        Bukkit.getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (updateChecker.newVersionAvailable())
                logger.info(mm("<green>A new update is available! ({0})</green>", updateChecker.getCurrVersion().asString()));
            else
                logger.info("No update was found.");
        });
        Bukkit.getPluginManager().registerEvents(updateChecker, this);
    }

    public I18N getI18N() {
        return i18n;
    }

    public HookContainer getHookContainer() {
        return this.hookContainer;
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }

    public PromptManager getPromptManager() {
        return promptManager;
    }

    public PluginLogger getPluginLogger() {
        return logger;
    }

    public HeadCache getHeadCache() {
        return headCache;
    }

    public void reload(boolean clean) {
        config = configManager.reload(CommandPrompterConfig.class);
        promptConfig = configManager.reload(PromptConfig.class);
        messenger.setPrefix(config.promptPrefix());
        logger = new PluginLogger(this, "CommandPrompter");
        i18n = new I18N(this, "CommandPrompter");
        headCache.reBuildCache();
        promptManager.getParser().initRegex();
        ChatPrompt.DefaultListener.setPriority(this);
        setupUpdater();
        if (clean)
            promptManager.clearPromptRegistry();

        // Update commands just in case tab completer is changed
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }

    public static CommandPrompter getInstance() {
        return instance;
    }

    public CommandPrompterConfig getConfiguration() {
        return config;
    }

    public PromptConfig getPromptConfig() {
        return promptConfig;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
