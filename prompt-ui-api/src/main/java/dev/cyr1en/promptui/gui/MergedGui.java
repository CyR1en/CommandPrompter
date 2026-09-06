package dev.cyr1en.promptui.gui;

import dev.cyr1en.promptui.pane.Pane;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A GUI whose rendered inventory region is represented by a single {@link GuiComponent}, typically
 * used by chest-style GUIs. Implementations that do not render player-inventory rows expose only
 * their top region rather than advertising an absent bottom component.
 */
public interface MergedGui {

  /** Adds a pane at the given position within the merged component. */
  void addPane(@NotNull Slot offset, @NotNull Pane pane);

  /** Returns the panes registered on this merged GUI. */
  @NotNull
  List<Pane> getPanes();

  /** Returns the flattened list of all GuiItems across all panes. */
  @NotNull
  default List<GuiItem> getItems() {
    return getGuiComponent().getItems();
  }

  /** Returns the underlying {@link GuiComponent} for this merged GUI. */
  @NotNull
  GuiComponent getGuiComponent();
}
