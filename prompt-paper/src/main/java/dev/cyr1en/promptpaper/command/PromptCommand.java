package dev.cyr1en.promptpaper.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.cyr1en.promptpaper.CommandPrompter;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Base class for every CommandPrompter Brigadier command. Each concrete
 * subclass represents a single top-level literal (e.g. {@code /commandprompter}
 * or {@code /consoledelegate}) and contributes its own description and
 * aliases for the registrar. The {@link #plugin} reference is constructor
 * injected at event-fire time, so command bodies can dereference
 * {@code engine} / {@code screenManager} / {@code configLoader} directly
 * without a deferred-supplier or {@code attachPlugin} step.
 *
 * <p>The {@link #allowed(CommandSender)} helper consolidates the permission
 * check and the optional sender-type filter into a single predicate, which
 * the {@link #build()} default wires into {@code .requires(...)}. Composite
 * commands override {@code build()} to attach their subcommand branches
 * before calling {@code .build()}. Subclasses that want to pass {@code this}
 * to {@code .executes(...)} implement {@code Command<CommandSourceStack>}
 * themselves.
 */
public abstract class PromptCommand {

    private final String name;
    private final String description;
    private final List<String> aliases;
    private final String permission;
    private final Class<? extends CommandSender> senderFilter;
    protected final CommandPrompter plugin;

    protected PromptCommand(CommandPrompter plugin,
                            String name,
                            String permission,
                            Class<? extends CommandSender> senderFilter,
                            String description,
                            List<String> aliases) {
        this.plugin = plugin;
        this.name = name;
        this.description = description;
        this.aliases = List.copyOf(aliases);
        this.permission = permission;
        this.senderFilter = senderFilter;
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final List<String> aliases() {
        return aliases;
    }

    /**
     * Returns true if the given sender may use this command. Combines the
     * optional {@code permission} and {@code senderFilter} into a single
     * predicate. Either check being absent is treated as a pass.
     */
    public final boolean allowed(CommandSender sender) {
        if (permission != null && !sender.hasPermission(permission)) {
            return false;
        }
        if (senderFilter != null && !senderFilter.isInstance(sender)) {
            return false;
        }
        return true;
    }

    /**
     * Builds the literal command node. Composite commands override this to
     * attach their subcommand branches before calling {@code .build()}.
     */
    public abstract LiteralCommandNode<CommandSourceStack> build();
}
