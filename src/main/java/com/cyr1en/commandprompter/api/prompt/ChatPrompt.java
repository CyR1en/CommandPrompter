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

package com.cyr1en.commandprompter.api.prompt;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.prompt.PromptContext;
import com.cyr1en.commandprompter.prompt.PromptQueue;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Arrays;
import java.util.List;

public abstract class ChatPrompt implements EventBasedPrompt<AsyncPlayerChatEvent> {

  private final CommandPrompter plugin;
  private final PromptContext context;
  private final PromptQueue queue;
  private final String trigger;

  public ChatPrompt(CommandPrompter plugin, String trigger, PromptContext context, PromptQueue queue) {
    this.plugin = plugin;
    this.context = context;
    this.queue = queue;
    this.trigger = trigger;
  }

  @Override
  public void sendPrompt() {
    List<String> parts = Arrays.asList(context.getContent().split("\\{br}"));
    String prefix = getPlugin().getConfiguration().promptPrefix();
    parts.forEach(part -> context.getSender().sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + part)));
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChat(AsyncPlayerChatEvent event) {
    if (!event.getPlayer().equals(context.getSender()))
      return;
    Bukkit.getScheduler().runTask(getPlugin(), () -> process(event.getPlayer(), event.getMessage()));
    event.setCancelled(true);
  }

  public abstract void process(Player player, String message);

  @Override
  public CommandPrompter getPlugin() {
    return this.plugin;
  }

  @Override
  public PromptQueue getPromptQueue(){
    return this.queue;
  }

  @Override
  public String getTrigger() {
    return this.trigger;
  }

  @Override
  public PromptContext getContext() {
    return this.context;
  }
}
