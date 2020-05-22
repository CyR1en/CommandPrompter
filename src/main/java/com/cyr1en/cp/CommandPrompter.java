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

package com.cyr1en.cp;

import com.cyr1en.cp.command.CommodoreRegistry;
import com.cyr1en.cp.commands.Reload;
import com.cyr1en.cp.config.SimpleConfig;
import com.cyr1en.cp.config.SimpleConfigManager;
import com.cyr1en.cp.listener.CommandListener;
import com.cyr1en.cp.util.SRegex;
import com.cyr1en.kiso.mc.I18N;
import com.cyr1en.kiso.mc.UpdateChecker;
import com.cyr1en.kiso.mc.command.CommandManager;
import com.cyr1en.kiso.mc.command.CommandMessenger;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

public class CommandPrompter extends JavaPlugin {

  private final String[] CONFIG_HEADER = new String[]{"CPCommand Prompter", "Configuration"};

  private SimpleConfigManager manager;
  private SimpleConfig config;
  private Logger logger;
  private CommandManager commandManager;
  private I18N i18n;
  private UpdateChecker updateChecker;

  @Override
  public void onEnable() {
    Bukkit.getServer().getScheduler().runTaskLater(this, this::start, 1L);
  }

  @Override
  public void onDisable() {
    PromptRegistry.clean();
    if(Objects.nonNull(updateChecker) && !updateChecker.isDisabled())
      HandlerList.unregisterAll(updateChecker);
  }

  private void start() {
    new Metrics(this);
    logger = getLogger();
    this.manager = new SimpleConfigManager(this);
    Bukkit.getPluginManager().registerEvents(new CommandListener(this), this);
    i18n = new I18N(this, "CommandPrompter");
    setupConfig();
    setupUpdater();
    setupCommands();
  }

  private void setupConfig() {
    config = manager.getNewConfig("config.yml", CONFIG_HEADER);
    if (config.get("Prompt-Prefix") == null) {
      config.set("Prompt-Prefix", "[&3Prompter&r] ", "Set the prompter's prefix");
      config.saveConfig();
    }
    if (config.get("Prompt-Timeout") == null) {
      config.set("Prompt-Timeout", 300, new String[]{"After how many seconds", "until CommandPrompter cancels", "a prompt"});
      config.saveConfig();
    }
    if (config.get("Cancel-Keyword") == null) {
      config.set("Cancel-Keyword", "cancel", new String[]{"Word that cancels command", "prompting."});
      config.saveConfig();
    }
    if (config.get("Enable-Permission") == null) {
      config.set("Enable-Permission", false, new String[]{"Enable permission check", "before a player can use", "the prompting feature",
              "", "Checking for commandprompter.use"});
      config.saveConfig();
    }
    if (config.get("Update-Checker") == null) {
      config.set("Update-Checker", true, new String[]{"Allow CommandPrompter to", "check if it's up to date."});
      config.saveConfig();
    }
    if (config.get("Argument-Regex") == null) {
      config.set("Argument-Regex", " <.*?> ",
              new String[]{"This will determine if",
                      "a part of a command is",
                      "a prompt.",
                      "",
                      "ONLY CHANGE THE FIRST AND LAST",
                      "I.E (.*?), {.*?}, or [.*?]"});
      config.saveConfig();
    }
  }

  private void setupCommands() {
    setupCommandManager();
    commandManager.registerCommand(Reload.class);
    PluginCommand command = getCommand("commandprompter");
    Objects.requireNonNull(command).setExecutor(commandManager);
    commandManager.registerTabCompleter(command);
    CommodoreRegistry.register(this, command);
  }

  private void setupCommandManager() {
    CommandManager.Builder cmgBuilder = new CommandManager.Builder();
    cmgBuilder.plugin(this);
    cmgBuilder.setPrefix(getConfig().getString("Prompt-Prefix"));
    cmgBuilder.setPlayerOnlyMessage(getI18N().getProperty("CommandPlayerOnly"));
    cmgBuilder.setCommandInvalidMessage(getI18N().getProperty("CommandInvalid"));
    cmgBuilder.setNoPermMessage(getI18N().getFormattedProperty("CommandNoPerm"));
    cmgBuilder.setFallBack(context -> {
      getCommandManager().getMessenger().sendMessage(context.getSender(),
              getI18N().getFormattedProperty("PluginVersion", getDescription().getVersion()));
      UpdateChecker uC = getUpdateChecker();
      if (!uC.isDisabled() && uC.newVersionAvailable())
        uC.sendUpdateAvailableMessage(context.getSender());
      return false;
    });
    commandManager = cmgBuilder.build();
  }

  private void setupUpdater() {
    updateChecker = new UpdateChecker(this, 47772);
    if(updateChecker.isDisabled()) return;
    Bukkit.getServer().getScheduler().runTaskAsynchronously(this, () -> {
      if (updateChecker.newVersionAvailable())
        logger.info(SRegex.ANSI_GREEN + "A new update is available! (" + updateChecker.getCurrVersion().asString() + ")" + SRegex.ANSI_RESET);
      else
        logger.info("No update was found.");
    });
    Bukkit.getPluginManager().registerEvents(updateChecker, this);
  }

  public I18N getI18N() {
    return i18n;
  }

  public CommandManager getCommandManager() {
    return commandManager;
  }

  public void reload(boolean clean) {
    config.reloadConfig();
    i18n = new I18N(this, "CommandPrompter");
    commandManager.getMessenger().setPrefix(config.getString("Prompt-Prefix"));
    setupUpdater();
    if (clean)
      PromptRegistry.clean();
  }

  public SimpleConfig getConfiguration() {
    return config;
  }


  public UpdateChecker getUpdateChecker() {
    return updateChecker;
  }
}

