package dev.cyr1en.promptui.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Thin wrapper around an Adventure {@link Component} for inventory titles.
 *
 * <p>MC 26.1+ is Adventure-native, so this is a single-class replacement for
 * the multi-layered TextHolder hierarchy used by older inventory frameworks.</p>
 */
public final class TextHolder {

    private final Component component;

    private TextHolder(Component component) {
        this.component = component;
    }

    /**
     * Creates a TextHolder from a plain text string.
     *
     * @param text plain text (supports legacy color codes via '&amp;')
     * @return a new TextHolder
     */
    public static TextHolder of(String text) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        return new TextHolder(component);
    }

    /**
     * Creates a TextHolder from an existing Component.
     *
     * @param component the Adventure component
     * @return a new TextHolder
     */
    public static TextHolder of(Component component) {
        return new TextHolder(component);
    }

    /**
     * Returns the underlying Adventure Component.
     *
     * @return the component
     */
    public Component getComponent() {
        return component;
    }
}
