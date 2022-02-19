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

package com.cyr1en.commandprompter.listener;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.commands.Cancel;
import com.cyr1en.commandprompter.hook.hooks.VentureChatHook;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.concurrent.atomic.AtomicBoolean;

public class CommandListener implements Listener {

    protected CommandPrompter plugin;
    protected PromptManager promptManager;

    public CommandListener(PromptManager promptManager) {
        this.promptManager = promptManager;
        this.plugin = promptManager.getPlugin();
    }

    protected void process(PromptContext context) {
        // Sanity Checks
        plugin.getPluginLogger().debug("Command: " + context.getContent());
        plugin.getPluginLogger().debug("Command Caught using: %s", this.getClass().getSimpleName());

        if (isIgnored(context)) {
            plugin.getPluginLogger().debug("Caught command is ignored.");
            return;
        }

        // Check if the command is CommandPrompter's cancel command
        if (context.getContent().matches(Cancel.commandPattern.toString()))
            return;

        if (!context.getSender().hasPermission("commandprompter.use") &&
                plugin.getConfiguration().enablePermission()) {
            plugin.getMessenger().sendMessage(context.getSender(),
                    plugin.getI18N().getProperty("PromptNoPerm"));
            return;
        }
        if (promptManager.getPromptRegistry().inCommandProcess(context.getSender())) {
            plugin.getMessenger().sendMessage(context.getSender(),
                    plugin.getI18N().getFormattedProperty("PromptInProgress",
                            plugin.getConfiguration().cancelKeyword()));
            context.getCancellable().setCancelled(true);
            return;
        }
        if (!promptManager.getParser().isParsable(context)) return;
        if (!(context.getSender() instanceof Player)) {
            plugin.getMessenger().sendMessage(context.getSender(),
                    plugin.getI18N().getProperty("PromptPlayerOnly"));
            return;
        }
        context.getCancellable().setCancelled(true);
        plugin.getPluginLogger().debug("Ctx Before Parse: " + context);
        promptManager.parse(context);
        promptManager.sendPrompt(context.getSender());
    }

    private boolean isIgnored(PromptContext context) {
        var end = context.getContent().indexOf(" ");
        end = end == -1 ? context.getContent().length() : end;
        var cmd = context.getContent().substring(0, end);
        return plugin.getConfiguration().ignoredCommands().contains(cmd) || isCmdChatChannel(cmd);
    }

    private boolean isCmdChatChannel(String cmd) {
        var out = new AtomicBoolean(false);
        var vcHook = plugin.getHookContainer().getHook(VentureChatHook.class);
        plugin.getPluginLogger().debug("VentureChat hooked: " + vcHook.isHooked());
        vcHook.ifHooked(hook -> out.set(hook.isChatChannel(cmd)));
        plugin.getPluginLogger().debug("is VentureChat channel: " + out.get());
        return out.get();
    }
}