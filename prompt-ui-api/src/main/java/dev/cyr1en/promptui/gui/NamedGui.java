package dev.cyr1en.promptui.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A GUI that has a title, exposed via {@link TextHolder}.
 *
 * <p>Subclasses should call {@link #markChanges()} when the title is modified
 * to ensure it is reflected in the inventory on the next {@link #update()}.</p>
 */
public abstract class NamedGui extends Gui {

    private TextHolder title;

    protected NamedGui(@NotNull JavaPlugin plugin, @Nullable Inventory inventory) {
        super(plugin, inventory);
    }

    /**
     * Returns the title as a {@link TextHolder}.
     */
    @Nullable
    public TextHolder getTitleHolder() {
        return title;
    }

    /**
     * Returns the title component or null.
     */
    @Nullable
    public net.kyori.adventure.text.Component getTitle() {
        return title != null ? title.getComponent() : null;
    }

    /**
     * Sets the title from a plain text string (supports legacy color codes).
     */
    public void setTitle(@NotNull String title) {
        this.title = TextHolder.of(title);
        markChanges();
    }

    /**
     * Sets the title from an existing {@link TextHolder}.
     */
    public void setTitle(@NotNull TextHolder title) {
        this.title = title;
        markChanges();
    }

    /**
     * Sets the title from an existing Component.
     */
    public void setTitle(@NotNull net.kyori.adventure.text.Component title) {
        this.title = TextHolder.of(title);
        markChanges();
    }
}
