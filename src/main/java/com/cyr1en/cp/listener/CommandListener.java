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

package com.cyr1en.cp.listener;

import com.cyr1en.cp.CommandPrompter;
import com.cyr1en.cp.PromptRegistry;
import com.cyr1en.cp.util.SRegex;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class CommandListener implements Listener {

    private CommandPrompter plugin;

    public CommandListener(CommandPrompter plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        process(event.getPlayer(), event, event.getMessage());
    }

    private void process(Player player, Cancellable cancellable, String command) {
        if (plugin.getConfiguration().getBoolean("Enable-Permission") && !player.hasPermission("commandprompter.use")) {
            return;
        }
        if (PromptRegistry.inCommandProcess(player.getPlayer())) {
            String prefix = plugin.getConfiguration().getString("Prompt-Prefix");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + plugin.getI18N().getProperty("PromptInProgress")));
            cancellable.setCancelled(true);
        } else {
            SRegex simpleRegex = new SRegex(command);
            String regex = plugin.getConfiguration().getString("Argument-Regex").trim();
            String parsedEscapedRegex = (String.valueOf(regex.charAt(0))).replaceAll("[^\\w\\s]", "\\\\$0") +
                    (regex.substring(1, regex.length() - 1)) +
                    (String.valueOf(regex.charAt(regex.length() - 1))).replaceAll("[^\\w\\s]", "\\\\$0");
            simpleRegex.find(Pattern.compile(parsedEscapedRegex));
            List<String> prompts = simpleRegex.getResultsList();
            if (prompts.size() > 0) {
                cancellable.setCancelled(true);
                PromptRegistry.registerPrompt(new Prompt(plugin, player, new LinkedList<>(prompts), command));
            }
        }
    }


}