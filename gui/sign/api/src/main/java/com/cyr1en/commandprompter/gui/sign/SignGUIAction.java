package com.cyr1en.commandprompter.gui.sign;

import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * An interface used for handling the action after the player finished editing the sign.
 */
public interface SignGUIAction {

    /**
     * @return The {@link SignGUIActionInfo} instance containing information about this action
     */
    SignGUIActionInfo getInfo();

    /**
     * Called to execute the actions after the player finished editing the sign.
     *
     * @param gui    The {@link SignGUI} instance
     * @param signEditor The {@link SignGUI} instance containing information relevant to the {@link com.cyr1en.commandprompter.gui.sign.version.VersionWrapper}
     * @param player The player who edited the sign
     */
    void execute(SignGUI gui, SignEditor signEditor, Player player);

    /**
     * Creates a new SignGUIAction that opens the sign gui again with the new lines.
     *
     * @param lines The new lines, may be less than 4
     * @return The new {@link SignGUIAction} instance
     * @throws IllegalArgumentException If lines is null.
     */
    static SignGUIAction displayNewLines(String... lines) {
        Validate.notNull(lines, "The lines cannot be null");

        return new SignGUIAction() {

            private SignGUIActionInfo info = new SignGUIActionInfo("displayNewLines", true, 1);

            @Override
            public SignGUIActionInfo getInfo() {
                return info;
            }

            @Override
            public void execute(SignGUI gui, SignEditor signEditor, Player player) {
                gui.displayNewLines(player, signEditor, Arrays.copyOf(lines, 4), null);
            }
        };
    }

    /**
     * Creates a new SignGUIAction that opens the sign gui again with the new lines using the Adventure component (1.20.5+).
     * Lines set using this method are only shown when using a mojang-mapped Paper plugin.
     * If you want to set fallback lines to use when Adventure components cannot be used, use {@link #displayNewAdventureLines(Object[], String[])}.
     * Please note that if you use this method and the Adventure components cannot be used, the sign will be empty.
     *
     * @param adventureLines The new adventure lines, may be less than 4
     * @return The new {@link SignGUIAction} instance
     * @throws IllegalArgumentException If adventure lines is null.
     * @see #displayNewAdventureLines(Object[], String[])
     */
    static SignGUIAction displayNewAdventureLines(Object... adventureLines) {
        return displayNewAdventureLines(adventureLines, null);
    }

    /**
     * Creates a new SignGUIAction that opens the sign gui again with the new lines using the Adventure component (1.20.5+).
     * Lines set using this method are only shown when using a mojang-mapped Paper plugin.
     * Please note that if you use don't submit fallback lines'and the Adventure components cannot be used, the sign will be empty.
     *
     * @param adventureLines The new lines, may be less than 4
     * @param fallbackLines  The fallback lines, may be less than 4. These are used when the Adventure components cannot be used. May be null.
     * @return The new {@link SignGUIAction} instance
     * @throws IllegalArgumentException If adventure lines is null.
     * @see #displayNewLines(String...)
     */
    static SignGUIAction displayNewAdventureLines(Object[] adventureLines, String[] fallbackLines) {
        Validate.notNull(adventureLines, "The lines cannot be null");

        return new SignGUIAction() {

            private SignGUIActionInfo info = new SignGUIActionInfo("displayNewLines", true, 1);

            @Override
            public SignGUIActionInfo getInfo() {
                return info;
            }

            @Override
            public void execute(SignGUI gui, SignEditor signEditor, Player player) {
                gui.displayNewLines(player, signEditor, fallbackLines != null ? Arrays.copyOf(fallbackLines, 4) : new String[4],
                        Arrays.copyOf(adventureLines, 4));
            }
        };
    }

    /**
     * Creates a new SignGUIAction that opens an inventory.
     * The inventory is opened synchronously by calling the method {@link org.bukkit.scheduler.BukkitScheduler#runTask(org.bukkit.plugin.Plugin, Runnable)}
     *
     * @param plugin    Your {@link org.bukkit.plugin.java.JavaPlugin} instance
     * @param inventory The inventory to open
     * @return The new {@link SignGUIAction} instance
     */
    static SignGUIAction openInventory(JavaPlugin plugin, Inventory inventory) {
        Validate.notNull(plugin, "The plugin cannot be null");
        Validate.notNull(inventory, "The inventory cannot be null");

        return new SignGUIAction() {

            private SignGUIActionInfo info = new SignGUIActionInfo("openInventory", false, 1);

            @Override
            public SignGUIActionInfo getInfo() {
                return info;
            }

            @Override
            public void execute(SignGUI gui, SignEditor signEditor, Player player) {
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inventory));
            }
        };
    }

    /**
     * Creates a new SignGUIAction that runs a runnable.
     * The runnable will be run asynchronously.
     *
     * @param runnable The runnable to run
     * @return The new {@link SignGUIAction} instance
     */
    static SignGUIAction run(Runnable runnable) {
        Validate.notNull(runnable, "The runnable cannot be null");

        return new SignGUIAction() {

            private SignGUIActionInfo info = new SignGUIActionInfo("run", false, 0);

            @Override
            public SignGUIActionInfo getInfo() {
                return info;
            }

            @Override
            public void execute(SignGUI gui, SignEditor signEditor, Player player) {
                runnable.run();
            }
        };
    }

    /**
     * Creates a new SignGUIAction that runs a runnable synchronously.
     *
     * @param plugin   Your {@link org.bukkit.plugin.java.JavaPlugin} instance
     * @param runnable The runnable to run
     * @return The new {@link SignGUIAction} instance
     */
    static SignGUIAction runSync(JavaPlugin plugin, Runnable runnable) {
        Validate.notNull(plugin, "The plugin cannot be null");
        Validate.notNull(runnable, "The runnable cannot be null");

        return new SignGUIAction() {

            private SignGUIActionInfo info = new SignGUIActionInfo("runSync", false, 0);

            @Override
            public SignGUIActionInfo getInfo() {
                return info;
            }

            @Override
            public void execute(SignGUI gui, SignEditor signEditor, Player player) {
                Bukkit.getScheduler().runTask(plugin, runnable);
            }
        };
    }

    /**
     * Describes a {@link SignGUIAction}
     */
    class SignGUIActionInfo {

        private final String name;
        private final boolean keepOpen;
        private final int conflicting;

        /**
         * Creates a new SignGUIActionInfo.
         *
         * @param name        The name of the action
         * @param keepOpen    Whether the sign gui should be kept open
         * @param conflicting The conflicting int
         */
        public SignGUIActionInfo(String name, boolean keepOpen, int conflicting) {
            this.name = name;
            this.keepOpen = keepOpen;
            this.conflicting = conflicting;
        }

        /**
         * @return The name of the action
         */
        public String getName() {
            return name;
        }

        /**
         * @return Whether the sign gui should be kept open.
         */
        public boolean isKeepOpen() {
            return keepOpen;
        }

        /**
         * Checks whether the result is conflicting with another result.
         *
         * @param other The conflicting int of the other result
         * @return Whether the result is conflicting with the other result
         */
        public boolean isConflicting(SignGUIActionInfo other) {
            return (conflicting & other.conflicting) != 0;
        }
    }
}
