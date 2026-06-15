package dev.cyr1en.promptpaper.screen.dialog;

import org.bukkit.entity.Player;

/**
 * Snapshot of the player and partial-command needed by the {@code d:tab}
 * dialog to look up completions at build time. Null for non-TAB dialogs.
 */
public record DialogCompletionContext(Player player, String partialCommand) {

    /** True if the context carries everything the TAB branch needs. */
    public boolean hasCompletions() {
        return player != null && partialCommand != null && !partialCommand.isEmpty();
    }
}
