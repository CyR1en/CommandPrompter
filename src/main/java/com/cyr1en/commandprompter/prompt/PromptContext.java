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

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;


public class PromptContext {
  private final Cancellable cancellable;
  private final CommandSender sender;
  private final String content;

  public PromptContext(PlayerCommandPreprocessEvent e) {
    this(e, e.getPlayer(), e.getMessage());
  }

  public PromptContext(Cancellable callable, Player sender, String content) {
    this.cancellable = callable;
    this.sender = sender;
    this.content = content;
  }

  public CommandSender getSender() {
    return sender;
  }

  public Cancellable getCancellable() {
    return cancellable;
  }

  public String getContent() {
    return content;
  }

  @Override
  public String toString() {
    return "PromptContext{" +
            "callable=" + cancellable +
            ", sender=" + sender +
            ", content='" + content + '\'' +
            '}';
  }
}
