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
package com.cyr1en.cp.prompt;

import com.cyr1en.cp.api.prompt.Prompt;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class PromptRegistry {

  private static Map<PromptQueue> registeredPrompts;

  static {
    registeredPrompts = new ArrayList<>();
  }

  public static void forEach(Consumer<? super PromptQueue> consumer) {
    registeredPrompts.forEach(consumer);
  }

  public static void registerPrompt(Prompt prompt) {
    PromptRegistry.registerPrompt(prompt, false);
  }

  public static void deregisterPrompt(Prompt prompt) {
    PromptRegistry.deregisterPrompt(prompt, false);
  }

  public static void registerPrompt(Prompt prompt, boolean eventBased) {
    registeredPrompts.add(prompt);
    if (eventBased)
      Bukkit.getPluginManager().registerEvents(prompt, prompt.getPlugin());
  }

  public static void deregisterPrompt(Prompt prompt, boolean eventBased) {
    registeredPrompts.remove(prompt);
    if (eventBased)
      HandlerList.unregisterAll(prompt);
  }

  public static void clean() {
    if (Objects.nonNull(registeredPrompts))
      PromptRegistry.forEach(PromptRegistry::deregisterPrompt);
  }

  public static boolean inCommandProcess(CommandSender sender) {
    return registeredPrompts.stream().anyMatch(prompt -> prompt.getContext().getSender() == sender);
  }

}
