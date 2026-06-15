package dev.cyr1en.promptpaper.command;

import dev.cyr1en.promptpaper.CommandPrompter;
import io.papermc.paper.command.brigadier.Commands;

import java.util.List;

/**
 * Holds the list of top-level CommandPrompter commands and registers them
 * with Paper's Brigadier dispatcher. Constructed at lifecycle event-fire
 * time with a fully-initialized {@link CommandPrompter} reference, so every
 * top-level command (and any subcommand they hold) gets a real plugin
 * pointer via constructor injection.
 *
 * <p>The lifecycle handler that drives this is registered in
 * {@link CommandPrompterBootstrap#bootstrap}. When Paper fires
 * {@code LifecycleEvents.COMMANDS}, the bootstrap constructs a new
 * {@code CommandRegistrar(plugin)} and calls
 * {@link #registerAll(Commands)} on the event's registrar.
 */
public class CommandRegistrar {

    private final List<PromptCommand> topLevel;

    public CommandRegistrar(CommandPrompter plugin) {
        this.topLevel = List.of(
                new PromptRootCommand(plugin),
                new ConsoleDelegateCommand(plugin),
                new PlayerDelegateCommand(plugin));
    }

    /**
     * Builds each top-level command's literal node and registers it with
     * the given Brigadier {@link Commands} registrar. Description and
     * aliases come from the {@link PromptCommand} metadata.
     */
    public void registerAll(Commands registrar) {
        for (var cmd : topLevel) {
            registrar.register(cmd.build(), cmd.description(), cmd.aliases());
        }
    }
}
