package dev.cyr1en.promptui.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * An anvil GUI with three input slots (first item, second item, result)
 * and a rename text field.
 *
 * <p>The actual anvil inventory is created via a version-specific
 * {@link AnvilInventory} implementation provided at construction time.</p>
 */
public final class AnvilGui extends NamedGui implements InventoryBased {

    private static final int SLOT_FIRST = 0;
    private static final int SLOT_SECOND = 1;
    private static final int SLOT_RESULT = 2;

    private final AnvilInventory anvilInventory;
    private final GuiComponent firstItemComponent;
    private final GuiComponent secondItemComponent;
    private final GuiComponent resultComponent;
    private final GuiComponent playerGuiComponent;

    private Consumer<String> onNameInputChanged;
    @Nullable
    private Consumer<InventoryClickEvent> onResultClick;
    private boolean nameInputSubscribed;

    /**
     * Creates an anvil GUI.
     *
     * @param plugin         the owning plugin
     * @param anvilInventory the version-specific anvil inventory implementation
     */
    public AnvilGui(@NotNull JavaPlugin plugin, @NotNull AnvilInventory anvilInventory) {
        super(plugin, null);
        this.anvilInventory = anvilInventory;
        this.firstItemComponent = new GuiComponent(1, 1);
        this.secondItemComponent = new GuiComponent(1, 1);
        this.resultComponent = new GuiComponent(1, 1);
        this.playerGuiComponent = new GuiComponent(9, 4);
    }

    // -- InventoryBased --

    /**
     * Creates the anvil inventory via the NMS implementation and registers it in the GUI map.
     */
    @NotNull
    @Override
    public Inventory createInventory() {
        Inventory inv = anvilInventory.createInventory(getTitleHolder());
        Gui.addInventory(inv, this);
        this.inventory = inv;
        return inv;
    }

    // -- Lifecycle --

    /**
     * Renders all item components into the anvil inventory's three slots (first, second, result).
     */
    @Override
    public void update() {
        if (inventory == null) {
            createInventory();
        }
        // Place item components into the 3 anvil slots
        GuiItemContainer first = firstItemComponent.display();
        GuiItem item = first.getItem(0, 0);
        if (item != null) {
            item.applyUUID();
            inventory.setItem(SLOT_FIRST, item.getItem());
        }

        GuiItemContainer second = secondItemComponent.display();
        item = second.getItem(0, 0);
        if (item != null) {
            item.applyUUID();
            inventory.setItem(SLOT_SECOND, item.getItem());
        }

        GuiItemContainer result = resultComponent.display();
        item = result.getItem(0, 0);
        if (item != null) {
            item.applyUUID();
            inventory.setItem(SLOT_RESULT, item.getItem());
        }

        // Player inventory component placed in the bottom area
        if (isPlayerInventoryUsed()) {
            playerGuiComponent.display();
            // Player inventory items are managed by HumanEntityCache
        }
    }

    /**
     * Shows the anvil GUI, subscribing to rename text changes on first open.
     */
    @Override
    public void show(@NotNull HumanEntity humanEntity) {
        if (inventory == null) {
            createInventory();
        }
        update();

        // Subscribe to name input changes (idempotent — show() can be retried
        // by the listener on re-open; re-subscribing would replace the
        // callback and lose any prior subscriber's reference).
        if (!nameInputSubscribed) {
            anvilInventory.subscribeToNameInputChanges(text -> {
                this.anvilInventory.renameText = text;
                if (onNameInputChanged != null) {
                    onNameInputChanged.accept(text);
                }
            });
            nameInputSubscribed = true;
        }

        super.show(humanEntity);
    }

    /**
     * Dispatches a click to the appropriate anvil slot (first, second, result, or player inventory).
     *
     * @return true if the click was consumed
     */
    @Override
    public boolean click(@NotNull InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        return switch (rawSlot) {
            case SLOT_FIRST  -> firstItemComponent.click(this, event, 0);
            case SLOT_SECOND -> secondItemComponent.click(this, event, 0);
            case SLOT_RESULT -> {
                if (onResultClick != null) {
                    onResultClick.accept(event);
                    yield true;
                }
                if (resultComponent.click(this, event, 0)) {
                    yield true;
                }
                yield false;
            }
            default -> {
                // Player inventory slot
                int slot = rawSlot - 3; // offset past anvil slots
                yield playerGuiComponent.click(this, event, slot);
            }
        };
    }

    // -- Item components --

    @NotNull
    public GuiComponent getFirstItemComponent() {
        return firstItemComponent;
    }

    @NotNull
    public GuiComponent getSecondItemComponent() {
        return secondItemComponent;
    }

    @NotNull
    public GuiComponent getResultComponent() {
        return resultComponent;
    }

    @NotNull
    public GuiComponent getPlayerGuiComponent() {
        return playerGuiComponent;
    }

    // -- Result click --

    /**
     * Sets a direct callback for result-slot clicks, bypassing UUID matching
     * (necessary because NMS overwrites the result ItemStack, stripping the
     * UUID tag that the pane system relies on).
     */
    public void setOnResultClick(@Nullable Consumer<InventoryClickEvent> onResultClick) {
        this.onResultClick = onResultClick;
    }

    // -- Name input --

    public void setOnNameInputChanged(@Nullable Consumer<String> onNameInputChanged) {
        this.onNameInputChanged = onNameInputChanged;
    }

    @NotNull
    public String getRenameText() {
        return anvilInventory.getRenameText();
    }

    /**
     * Returns the underlying NMS anvil inventory abstraction.
     */
    @NotNull
    public AnvilInventory getAnvilInventory() {
        return anvilInventory;
    }
}
