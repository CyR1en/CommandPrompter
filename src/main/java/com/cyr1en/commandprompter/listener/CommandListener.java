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
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class CommandListener implements Listener {

    protected CommandPrompter plugin;
    protected PromptManager promptManager;

    public CommandListener(PromptManager promptManager) {
        this.promptManager = promptManager;
        this.plugin = promptManager.getPlugin();
    }

    protected void process(PromptContext context) {
        // Sanity Checks
        if (!context.getSender().hasPermission("commandprompter.use") &&
                plugin.getConfiguration().enablePermission()) {
            plugin.getMessenger().sendMessage(context.getSender(),
                    plugin.getI18N().getProperty("PromptNoPerm"));
            return;
        }
        if (promptManager.getPromptRegistry().inCommandProcess(context.getSender())) {
            plugin.getMessenger().sendMessage(context.getSender(),
                    plugin.getI18N().getProperty("PromptInProgress"));
            return;
        }
        if (!promptManager.getParser().isParsable(context)) return;
        if (!(context.getSender() instanceof Player)) {
            plugin.getMessenger().sendMessage(context.getSender(),
                    plugin.getI18N().getProperty("PromptPlayerOnly"));
            return;
        }
        context.getCancellable().setCancelled(true);
        promptManager.parse(context);
        promptManager.sendPrompt(context.getSender());
    }
}