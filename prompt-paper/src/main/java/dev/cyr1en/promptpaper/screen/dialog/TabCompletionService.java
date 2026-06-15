package dev.cyr1en.promptpaper.screen.dialog;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandException;
import org.bukkit.entity.Player;

/**
 * Looks up tab-completion suggestions for a {@code d:tab} prompt by
 * delegating to the server's Bukkit {@link org.bukkit.command.CommandMap}.
 * Returns an empty list on any failure (null input, no match, no permission).
 */
public final class TabCompletionService {

    /**
     * Returns completions for the last token of the given command line,
     * or an empty list if the input is invalid or the command is unknown.
     */
    public List<String> complete(Player player, String cmdLine) {
        if (player == null || cmdLine == null || cmdLine.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            var map = Bukkit.getServer().getCommandMap();
            var raw = map.tabComplete(player, cmdLine);
            return raw == null ? new ArrayList<>() : new ArrayList<>(raw);
        } catch (CommandException | IllegalArgumentException e) {
            return new ArrayList<>();
        }
    }
}
