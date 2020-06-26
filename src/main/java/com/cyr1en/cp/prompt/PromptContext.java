package com.cyr1en.cp.prompt;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;


public class PromptContext {
  private final Cancellable callable;
  private final Player player;
  private final String content;

  public PromptContext(PlayerCommandPreprocessEvent e) {
    this.callable = e;
    this.player = e.getPlayer();
    this.content = e.getMessage();
  }

  public Player getPlayer() {
    return player;
  }

  public Cancellable getCallable() {
    return callable;
  }

  public String getContent() {
    return content;
  }

  @Override
  public String toString() {
    return "PromptContext{" +
            "callable=" + callable +
            ", player=" + player +
            ", content='" + content + '\'' +
            '}';
  }
}
