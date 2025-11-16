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
import com.cyr1en.commandprompter.util.ServerUtil;
import com.cyr1en.commandprompter.util.Util;
import com.cyr1en.kiso.mc.Version;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    private static final HashMap<Class<? extends Prompt>, Version> supportTable;

    private static final Version LATEST = Version.parse("1.21.10");
    // Arbitrary 10 version, that means this should work until minecraft v10 lol "any".
    private static final Version ANY = Version.parse("10");

    static {
        supportTable = new HashMap<>();
        supportTable.put(ChatPrompt.class, ANY);
        supportTable.put(AnvilPrompt.class, LATEST);
        supportTable.put(PlayerUIPrompt.class, LATEST);
        supportTable.put(SignPrompt.class, LATEST);
    }


    public PromptManager(CommandPrompter commandPrompter) {
        this.plugin = commandPrompter;
        this.promptRegistry = new PromptRegistry(plugin);
        this.promptParser = new PromptParser(this);
        this.scheduler = Bukkit.getScheduler();
    }

    public void registerPrompts() {
        this.put("", ChatPrompt.class);
        this.put("a", AnvilPrompt.class);
        this.put(plugin.getHeadCache().makeFilteredPattern(), PlayerUIPrompt.class);
        this.put("s", SignPrompt.class);
    }

    @Override
    public Class<? extends Prompt> put(String key, Class<? extends Prompt> value) {
        if (!supportTable.containsKey(value)) return null;
        var version = supportTable.get(value);
        var serverVersion = ServerUtil.parsedVersion();
        plugin.getPluginLogger().debug("Server Version: " + serverVersion);
        plugin.getPluginLogger().debug("Prompt Version: " + version);

        if (serverVersion.isNewerThan(version)) {
            plugin.getPluginLogger().warn("Prompt %s is not supported on this server version", value.getSimpleName());
            return null;
        }

        var ret = super.put(key, value);
        plugin.getPluginLogger().info("Registered " +
                new Ansi().fgRgb(166, 218, 149).a(value.getSimpleName()).reset());
        return ret;
    }

    public void parse(PromptContext context) {
        var queueHash = promptParser.parsePrompts(context);
        var timeout = plugin.getConfiguration().promptTimeout();
        scheduler.runTaskLater(plugin, () ->
                cancel(context.getPromptedPlayer(), queueHash, CancelReason.Timeout), 20L * timeout);
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
        var sender = context.getPromptedPlayer();

        if (!getPromptRegistry().containsKey(sender))
            return;

        var queue = promptRegistry.get(sender);
        if (queue.isEmpty() && !queue.containsPCM()) {
            dispatchQueue(sender, queue);
            return;
        }

        if (!checkInput(queue, context))
            return;

        var p = getPromptRegistry().get(sender).poll();
        var content = context.getContent();
        if (Objects.nonNull(p) && p.sanitizeInput())
            content = sanitize(content);

        getPromptRegistry().get(sender).addCompleted(content);
        plugin.getPluginLogger().debug("PromptQueue for %s: %s", sender.getName(), promptRegistry.get(sender));
        if (promptRegistry.get(sender).isEmpty()) {
            dispatchQueue(sender, queue);
        } else
            sendPrompt(sender);

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
        if (!promptRegistry.containsKey(sender)) {
            plugin.getPluginLogger().err("No prompt queue found for %s", sender.getName());
            return;
        }

        plugin.getPluginLogger().debug("Dispatching for %s: %s", sender.getName(), queue.getCompleteCommand());
        if (plugin.getConfiguration().showCompleted())
            plugin.getMessenger().sendMessage(sender, plugin.getI18N()
                    .getFormattedProperty("CompletedCommand", queue.getCompleteCommand()));

        queue.dispatch(plugin, (Player) sender);
        promptRegistry.unregister(sender);
    }

    private boolean checkInput(PromptQueue promptQueue, PromptContext context) {
        if (promptQueue.peek() == null)
            return true;

        var prompt = promptQueue.peek();
        var validator = prompt.getInputValidator();
        if (validator.validate(context.getContent()))
            return true;

        plugin.getMessenger().sendMessage(context.getPromptedPlayer(), validator.messageOnFail());
        sendPrompt(context.getPromptedPlayer());
        return false;
    }

    private static final Pattern symbols = Pattern.compile("[{}\\[\\]<>()$§&]+");

    private String sanitize(String input) {
        plugin.getPluginLogger().debug("Sanitizing input: " + input);
        input = Util.stripColor(ChatColor.translateAlternateColorCodes('&', input));
        input = symbols.matcher(input).replaceAll("");
        plugin.getPluginLogger().debug("Sanitized input: " + input);
        return input;
    }

    public PromptRegistry getPromptRegistry() {
        return promptRegistry;
    }

    public PromptParser getParser() {
        return promptParser;
    }

    public void cancel(CommandSender sender, int queueHash, CancelReason reason) {
        if (!promptRegistry.containsKey(sender))
            return;
        plugin.getPluginLogger().debug("Canceling prompt queue for %s. (Reason: %s)", sender.getName(), reason.name());
        plugin.getPluginLogger().debug("queueHash: " + queueHash);
        plugin.getPluginLogger().debug("registryQueueHash: " + promptRegistry.get(sender).hashCode());

        if (queueHash != -1 && queueHash != promptRegistry.get(sender).hashCode())
            return;

        var queue = promptRegistry.get(sender);
        if (reason != CancelReason.Timeout && queue.containsPCM()) {
            var filtered = queue.getPostCommandMetas().stream().filter(PromptQueue.PostCommandMeta::isOnCancel);
            filtered.forEach(pcm -> {
                plugin.getPluginLogger().debug("Dispatching PCM: %s", pcm);
                Bukkit.getScheduler().runTaskLater(plugin, () -> queue.execPCM(pcm, (Player) sender), pcm.delayTicks());
            });
        }

        promptRegistry.unregister(sender);
        if (plugin.getConfiguration().showCancelled())
            plugin.getMessenger().sendMessage(sender, plugin.getI18N().getProperty("PromptCancel"));
        plugin.getPluginLogger().debug("Command completion called for: %s", sender.getName());
    }

    public void cancel(CommandSender sender, CancelReason reason) {
        cancel(sender, -1, reason);
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

    public static enum CancelReason {
        GUIExit,
        GUIErr,
        Manual,
        Timeout,
        BlankInput
    }
}
