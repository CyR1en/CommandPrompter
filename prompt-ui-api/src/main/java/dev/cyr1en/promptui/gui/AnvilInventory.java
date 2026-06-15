package dev.cyr1en.promptui.gui;

import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * NMS abstraction for the anvil inventory type.
 *
 * <p>Defined in {@code prompt-ui-api} so that {@link AnvilGui} can reference it.
 * The concrete implementation lives in {@code prompt-ui-26.1}.</p>
 */
public abstract class AnvilInventory {

    protected String renameText = "";
    protected short cost;

    /**
     * Creates the anvil inventory with the given title.
     */
    @NotNull
    public abstract Inventory createInventory(@NotNull TextHolder title);

    /**
     * Returns the current text in the rename field.
     */
    @NotNull
    public String getRenameText() {
        return renameText;
    }

    /**
     * Sets the anvil repair cost experience level requirement.
     */
    public void setCost(short cost) {
        this.cost = cost;
    }

    /**
     * Returns the anvil repair cost.
     */
    public short getCost() {
        return cost;
    }

    /**
     * Registers a callback invoked whenever the player changes the anvil rename text.
     */
    public abstract void subscribeToNameInputChanges(@NotNull Consumer<String> callback);
}
