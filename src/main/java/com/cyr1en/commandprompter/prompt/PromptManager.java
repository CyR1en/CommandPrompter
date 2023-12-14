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
import com.cyr1en.commandprompter.api.prompt.Prompt;
import com.cyr1en.commandprompter.prompt.prompts.AnvilPrompt;
import com.cyr1en.commandprompter.prompt.prompts.ChatPrompt;
import com.cyr1en.commandprompter.prompt.prompts.PlayerUIPrompt;
import com.cyr1en.commandprompter.prompt.prompts.SignPrompt;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.fusesource.jansi.Ansi;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Class that would manage all prompts.
 * <p>
 * We need to register a new prompt into this map. And we simply do that by
 * appending a new
 * Prompt class with its optional argument key.
 * <p>
 * i.e: For chat prompt, the key would just be an empty string, and for an anvil
 * prompt the key
 * would be 'a'.
 */
public class PromptManager extends HashMap<String, Class<? extends Prompt>> {

    private final CommandPrompter plugin;
    private final PromptRegistry promptRegistry;
    private final PromptParser promptParser;
    private final BukkitScheduler scheduler;

    public PromptManager(CommandPrompter commandPrompter) {
        this.plugin = commandPrompter;
        this.promptRegistry = new PromptRegistry(plugin);
        this.promptParser = new PromptParser(this);
        this.scheduler = Bukkit.getScheduler();
        registerPrompts();
    }

    private void registerPrompts() {
        this.put("", ChatPrompt.class);
        this.put("a", AnvilPrompt.class);
        this.put(plugin.getHeadCache().makeFilteredPattern(), PlayerUIPrompt.class);
        this.put("s", SignPrompt.class);
    }

    @Override
    public Class<? extends Prompt> put(String key, Class<? extends Prompt> value) {
        var ret = super.put(key, value);
        plugin.getPluginLogger().info("Registered " +
                new Ansi().fgRgb(153, 214, 90).a(value.getSimpleName()).reset());
        return ret;
    }

    public void parse(PromptContext context) {
        var queueHash = promptParser.parsePrompts(context);
        var timeout = plugin.getConfiguration().promptTimeout();
        scheduler.runTaskLater(plugin, () -> cancel(context.getSender(), queueHash), 20L * timeout);
    }

    public void sendPrompt(CommandSender sender) {
        if (!promptRegistry.containsKey(sender))
            return;

        var queue = promptRegistry.get(sender);
        if (queue.isEmpty() && !queue.containsPCM())
            return;
        plugin.getPluginLogger().debug("PromptQueue for %s: %s", sender.getName(), promptRegistry.get(sender));

        if (!queue.isEmpty()) {
            var prompt = Objects.requireNonNull(queue.peek());
            Bukkit.getScheduler().runTaskLater(plugin, prompt::sendPrompt, 2L);
            plugin.getPluginLogger().debug("Sent %s to %s", prompt.getClass().getSimpleName(), sender.getName());
        } else if (queue.containsPCM()) {
            // This means queue is empty but contains PCM. If it does, we just dispatch it.
            dispatchQueue(sender, queue);
        }

    }

    public void processPrompt(PromptContext context) {
        var sender = context.getSender();

        if (!getPromptRegistry().containsKey(sender))
            return;

        var queue = promptRegistry.get(sender);
        if (queue.isEmpty() && !queue.containsPCM())
            return;

        if (!checkInput(queue, context))
            return;

        getPromptRegistry().get(sender).poll();
        getPromptRegistry().get(sender).addCompleted(context.getContent());
        plugin.getPluginLogger().debug("PromptQueue for %s: %s", sender.getName(), promptRegistry.get(sender));
        if (promptRegistry.get(sender).isEmpty()) {
            dispatchQueue(sender, queue);
        } else if (sender instanceof Player player)
            sendPrompt(player);

    }

    @Nullable
    public Class<? extends Prompt> get(String key) {
        var prompt = super.get(key);
        if (!Objects.isNull(prompt)) return prompt;

        var entrySet = new HashSet<>(Set.copyOf(this.entrySet()));
        entrySet.removeIf(entry -> entry.getKey().isEmpty());

        for (var entry : entrySet) {
            var pattern = Pattern.compile(entry.getKey());
            plugin.getPluginLogger().debug("Pattern: " + pattern);
            plugin.getPluginLogger().debug("Key: " + key);
            if (pattern.matcher(key).matches())
                return entry.getValue();
        }
        return null;
    }

    private void dispatchQueue(CommandSender sender, PromptQueue queue) {
        var isCurrentOp = sender.isOp();
        plugin.getPluginLogger().debug("Is Currently OP?: " + isCurrentOp);
        plugin.getPluginLogger().debug("PromptQueue OP: " + queue.isOp());
        if (queue.isOp() && !isCurrentOp) {
            sender.setOp(true);
            plugin.getPluginLogger().debug("Gave OP status temporarily");
        }
        plugin.getPluginLogger().debug("Dispatching for %s: %s", sender.getName(), queue.getCompleteCommand());
        if (plugin.getConfiguration().showCompleted())
            plugin.getMessenger().sendMessage(sender, plugin.getI18N()
                    .getFormattedProperty("CompletedCommand", queue.getCompleteCommand()));

        queue.dispatch(plugin, (Player) sender);

        if (!isCurrentOp) {
            sender.setOp(false);
            plugin.getPluginLogger().debug("Remove OP status");
            // Redundancy for de-op
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getPluginLogger().debug("Remove OP status (redundancy)");
                sender.setOp(false);
            }, 2L);
        }
        promptRegistry.unregister(sender);
    }

    private boolean checkInput(PromptQueue promptQueue, PromptContext context) {
        if (promptQueue.peek() == null)
            return true;

        var prompt = promptQueue.peek();
        if (prompt.isValidInput(context.getContent()))
            return true;

        var errMsg = plugin.getPromptConfig().getIVErrMessageWithRegex(prompt.getRegexCheck().pattern());
        plugin.getMessenger().sendMessage(context.getSender(), errMsg);
        sendPrompt(context.getSender());
        return false;
    }

    public PromptRegistry getPromptRegistry() {
        return promptRegistry;
    }

    public PromptParser getParser() {
        return promptParser;
    }

    public void cancel(CommandSender sender, int queueHash) {
        if (!promptRegistry.containsKey(sender))
            return;
        plugin.getPluginLogger().debug("queueHash: " + queueHash);
        plugin.getPluginLogger().debug("registryQueueHash: " + promptRegistry.get(sender).hashCode());
        if (queueHash != -1 && queueHash != promptRegistry.get(sender).hashCode())
            return;
        var queue = promptRegistry.get(sender);
        if (queue.containsPCM()) {
            queue.getPostCommandMetas().forEach(pcm -> {
                if (!pcm.isOnCancel())
                    return;

                if (pcm.delayTicks() > 0)
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> queue.execPCM(pcm, (Player) sender),
                            pcm.delayTicks());
                else
                    queue.execPCM(pcm, (Player) sender);
            });
        }
        promptRegistry.unregister(sender);
        plugin.getMessenger().sendMessage(sender, plugin.getI18N().getProperty("PromptCancel"));
        plugin.getPluginLogger().debug("Command completion called for: %s", sender.getName());
    }

    public void cancel(CommandSender sender) {
        cancel(sender, -1);
    }

    public Pattern getArgumentPattern(String... additionalKeys) {
        var pattern = "-(%s) ";
        var keySet = new HashSet<>(Set.copyOf(this.keySet()));
        keySet.remove("");

        var arguments = String.join("|", keySet);
        arguments += "|" + String.join("|", additionalKeys);

        var compiled = Pattern.compile(pattern.formatted(arguments));
        plugin.getPluginLogger().debug("ArgumentPattern: " + compiled);
        return compiled;
    }

    public void clearPromptRegistry() {
        promptRegistry.clear();
    }

    public CommandPrompter getPlugin() {
        return plugin;
    }
}
