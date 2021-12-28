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

package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.api.Dispatcher;
import com.cyr1en.commandprompter.api.prompt.Prompt;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Class that would manage all prompts.
 * <p>
 * We need to register a new prompt into this map. And we simply do that by appending a new
 * Prompt class with its optional argument key.
 * <p>
 * i.e: For chat prompt, the key would just be an empty string, and for an anvil prompt the key
 * would be 'a'.
 */
public class PromptManager extends HashMap<String, Class<? extends Prompt>> {

    private final CommandPrompter plugin;
    private final PromptRegistry promptRegistry;
    private final PromptParser promptParser;
    private final ScheduledExecutorService scheduler;

    public PromptManager(CommandPrompter commandPrompter) {
        this.plugin = commandPrompter;
        this.promptRegistry = new PromptRegistry(plugin);
        this.promptParser = new PromptParser(this);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void parse(PromptContext context) {
        promptParser.parsePrompts(context);
        var timeout = plugin.getConfiguration().promptTimeout();
        scheduler.schedule(() -> cancel(context.getSender()), timeout, TimeUnit.SECONDS);
    }

    public void sendPrompt(CommandSender sender) {
        if (!promptRegistry.containsKey(sender)) return;
        if (promptRegistry.get(sender).isEmpty()) return;
        Objects.requireNonNull(promptRegistry.get(sender).peek()).sendPrompt();
    }

    public void processPrompt(PromptContext context) {
        var sender = context.getSender();

        if (!getPromptRegistry().containsKey(sender)) return;
        if (promptRegistry.get(sender).isEmpty()) return;

        getPromptRegistry().get(sender).poll();
        getPromptRegistry().get(sender).addCompleted(context.getContent());
        if (promptRegistry.get(sender).isEmpty()) {
            var queue = promptRegistry.get(sender);

            var isCurrentOp = sender.isOp();
            if (queue.isOp()) sender.setOp(true);
            Dispatcher.dispatchCommand(plugin, (Player) sender, queue.getCompleteCommand());
            if (!isCurrentOp) {
                sender.setOp(false);
                // Redundancy for de-op
                Bukkit.getScheduler().runTaskLater(plugin, () -> sender.setOp(false), 1);
            }
            promptRegistry.unregister(sender);
        } else if (sender instanceof Player player)
            sendPrompt(player);
    }

    public PromptRegistry getPromptRegistry() {
        return promptRegistry;
    }

    public PromptParser getParser() {
        return promptParser;
    }

    public void cancel(CommandSender sender) {
        if (!promptRegistry.containsKey(sender)) return;
        promptRegistry.unregister(sender);
        plugin.getMessenger().sendMessage(sender, plugin.getI18N().getProperty("PromptCancel"));
    }

    public Pattern getArgumentPattern() {
        var pattern = "-(%s) ";
        var arguments = String.join("|", this.keySet());
        return Pattern.compile(pattern.formatted(arguments));
    }

    public void clearPromptRegistry() {
        promptRegistry.clear();
    }

    public CommandPrompter getPlugin() {
        return plugin;
    }
}
