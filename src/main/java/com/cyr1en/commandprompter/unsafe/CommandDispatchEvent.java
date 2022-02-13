package com.cyr1en.commandprompter.unsafe;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;


public class CommandDispatchEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private final CommandSender sender;
    private final String commandLine;

    public CommandDispatchEvent(CommandSender sender, String commandLine) {
        this.sender = sender;
        this.commandLine = commandLine;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public CommandSender getSender() {
        return sender;
    }

    public String getCommandLine() {
        return commandLine;
    }

}
