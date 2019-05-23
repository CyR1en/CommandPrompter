package com.cyr1en.cp.command;

import com.cyr1en.cp.CommandPrompter;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.lucko.commodore.Commodore;
import me.lucko.commodore.CommodoreProvider;
import org.bukkit.command.Command;

public class BrigadierRegistry {
  public static void register(CommandPrompter plugin, Command cmd) {
    Commodore commodore = CommodoreProvider.getCommodore(plugin);
    commodore.register(cmd, LiteralArgumentBuilder.literal("commandprompter")
            .then(LiteralArgumentBuilder.literal("reload"))
    );
  }
}
