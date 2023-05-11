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
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Objects;

/**
 * Class that will hold all ongoing prompts.
 * <p>
 * The data structure of this class is a hash table. The key of this data struct is the command
 * sender itself and the value is a {@link PromptQueue}
 */
public class PromptRegistry extends HashMap<CommandSender, PromptQueue> {

    private final CommandPrompter pluginInstance;

    public PromptRegistry(CommandPrompter pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    public void initRegistryFor(PromptContext context, String command, String escapedRegex) {
        if (containsKey(context.getSender())) return;
        var queue = new PromptQueue(command, context.getSender().isOp(), context.isSetPermissionAttachment(), escapedRegex);
        put(context.getSender(), queue);
    }

    public void addPrompt(CommandSender sender, Prompt p) {
        if (!containsKey(sender)) return;
        get(sender).add(p);
        pluginInstance.getPluginLogger().debug("Registered: (%s : %s)",
                sender.getName(), p.getClass().getSimpleName());
    }

    public void unregister(CommandSender sender) {
        if (!containsKey(sender)) return;
        remove(sender);
        pluginInstance.getPluginLogger().debug("Un-Registered: %s", sender.getName());
    }

    public boolean inCommandProcess(CommandSender sender) {
        if (!containsKey(sender)) return false;
        if (Objects.isNull(get(sender))) {
            remove(sender);
            return false;
        }
        return true;
    }

}
