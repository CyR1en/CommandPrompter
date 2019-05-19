package com.cyr1en.cp.commands;

import co.aikar.commands.annotation.*;
import com.cyr1en.cp.CommandPrompter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@CommandAlias("cmdprompter|cmdprmptr|cmdp|cp")
public class CommandPrompterCmd extends CPBaseCommand {

  public CommandPrompterCmd(CommandPrompter plugin) {
    super(plugin);
  }

  @Default
  @Subcommand("testDispatch")
  public void testDispatch(Player player) {
    Bukkit.getServer().dispatchCommand(player, "gamemode creative");
  }

  @Default
  @Subcommand("reload")
  @CommandCompletion("@reload")
  @CommandPermission("commandprompter.reload")
  public void onReload(Player player) {
    reloadFalse(player);
  }

  @Subcommand("reload true")
  @CommandPermission("commandprompter.reload")
  public void onReloadTrue(Player player) {
    plugin.reload(false);
    sendMessage(player, "Successfully reloaded configuration and cleared prompt queues.");
  }

  @Subcommand("reload false")
  @CommandPermission("commandprompter.reload")
  public void onReloadFalse(Player player) {
    reloadFalse(player);
  }

  private void reloadFalse(Player p) {
    plugin.reload(false);
    sendMessage(p, "Successfully reloaded configuration");
  }
}
