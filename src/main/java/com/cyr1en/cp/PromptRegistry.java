package com.cyr1en.cp;

import com.cyr1en.cp.listener.Prompt;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class PromptRegistry  {

  private static List<Prompt> registeredPrompts;

  static {
    registeredPrompts = new ArrayList<>();
  }

  public static void forEach(Consumer<? super Prompt> consumer) {
    registeredPrompts.forEach(consumer);
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
      PromptRegistry.forEach(PromptRegistry::deregisterPrompt);
  }

  public static boolean inCommandProcess(CommandSender sender) {
    return registeredPrompts.stream().anyMatch(prompt -> prompt.getSender() == sender);
  }

}
