package com.cyr1en.commandprompter.prompt.ui.anvil;

import org.bukkit.inventory.Inventory;

interface AnvilContainerWrapper {

    /**
     * Retrieves the raw text that has been entered into the Anvil at the moment
     * <br><br>
     * This field is marked as public in the Minecraft AnvilContainer only from Minecraft 1.11 and upwards
     *
     * @return The raw text in the rename field
     */
    default String getRenameText() {
        return null;
    }

    /**
     * Sets the provided text as the literal hovername of the item in the left input slot
     *
     * @param text The text to set
     */
    default void setRenameText(String text) {}

    /**
     * Gets the {@link Inventory} wrapper of the NMS container
     *
     * @return The inventory of the NMS container
     */
    Inventory getBukkitInventory();
}
