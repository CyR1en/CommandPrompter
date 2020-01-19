package com.cyr1en.cp;

import com.cyr1en.cp.listener.Prompt;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PromptRegistry {

  private static List<Prompt> registeredPrompts;

  static {
    registeredPrompts = new ArrayList<>();
  }

  public static List<Prompt> getRegistry() {
    return registeredPrompts;
  }

  public static void registerPrompt(Prompt prompt) {
    registeredPrompts.add(prompt);
    Bukkit.getPluginManager().registerEvents(prompt, prompt.getPlugin());
  }

  public static void deregisterPrompt(Prompt prompt) {
    registeredPrompts.remove(prompt);
    HandlerList.unregisterAll(prompt);
  }

  public static void clean() {
    if(Objects.nonNull(registeredPrompts))
      for (Prompt registeredPrompt : registeredPrompts) {
        deregisterPrompt(registeredPrompt);
      }
  }

}
