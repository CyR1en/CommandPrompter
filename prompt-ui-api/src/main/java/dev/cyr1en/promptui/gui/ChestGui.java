package dev.cyr1en.promptui.gui;

import dev.cyr1en.promptui.pane.Pane;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A chest-style GUI with 1-6 rows.
 *
 * <p>Uses a single merged {@link GuiComponent} that spans both the chest rows
 * and the player inventory rows below. The component is split at render time
 * via row exclusion.</p>
 */
public final class ChestGui extends NamedGui implements MergedGui, InventoryBased, InventoryHolder {

    private static final int ROW_WIDTH = 9;
    private static final int PLAYER_INV_ROWS = 4; // 36 slots = hotbar (1 row) + storage (3 rows)

    private final int rows;
    private final GuiComponent guiComponent;

    /**
     * Creates a chest GUI with the given row count.
     *
     * @param plugin the owning plugin
     * @param rows   number of chest rows (1-6)
     */
    public ChestGui(@NotNull JavaPlugin plugin, int rows) {
        super(plugin, null);
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Chest rows must be 1-6, got " + rows);
        }
        this.rows = rows;
        this.guiComponent = new GuiComponent(ROW_WIDTH, rows + PLAYER_INV_ROWS);
    }

    // -- InventoryBased --

    /**
     * Creates a Bukkit chest inventory and registers it in the GUI map.
     */
    @NotNull
    @Override
    public Inventory createInventory() {
        Inventory inv = Bukkit.createInventory(this, rows * ROW_WIDTH, getTitle());
        Gui.addInventory(inv, this);
        this.inventory = inv;
        return inv;
    }

    // -- GuiComponent (MergedGui) --

    @NotNull
    @Override
    public GuiComponent getGuiComponent() {
        return guiComponent;
    }

    @Override
    public void addPane(@NotNull Slot offset, @NotNull Pane pane) {
        guiComponent.addPane(offset, pane);
    }

    @NotNull
    @Override
    public List<Pane> getPanes() {
        return guiComponent.getPanes();
    }

    // -- Lifecycle --

    /**
     * Clears the inventory and re-renders the merged gui component into the chest slots.
     */
    @Override
    public void update() {
        if (inventory == null) {
            createInventory();
        }
        inventory.clear();
        guiComponent.display();

        // Place top (chest) items: rows 0..rows-1
        guiComponent.placeItems(inventory, 0, 0);

        // Place bottom (player) items
        if (isPlayerInventoryUsed()) {
            // The component reserves rows rows..(rows+4) for player inventory
            // We need to get those rows rendered into the actual player inventory shown below
            // But the chest inventory only has `rows * 9` slots — player inventory is separate
        }
    }

    /**
     * Shows the chest GUI for the given human entity.
     */
    @Override
    public void show(@NotNull HumanEntity humanEntity) {
        if (inventory == null) {
            createInventory();
        }
        update();
        super.show(humanEntity);
    }

    /**
     * Dispatches the click to the merged gui component.
     *
     * @return true if the click was consumed
     */
    @Override
    public boolean click(@NotNull InventoryClickEvent event) {
        return guiComponent.click(this, event, event.getRawSlot());
    }

    // -- InventoryHolder --

    /**
     * Returns the chest inventory, creating it if necessary.
     */
    @NotNull
    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            createInventory();
        }
        return inventory;
    }

    // -- Getters --

    public int getRows() {
        return rows;
    }
}
