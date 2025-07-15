package com.cyr1en.commandprompter.util;

import java.util.List;
import java.util.Map;

import org.bukkit.Color;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Non-referential implementation of {@link CustomModelDataComponent}.
 * 
 * <p>
 * This class is used to provide a custom implementation of the
 * {@link CustomModelDataComponent} interface
 * that does not rely on the Bukkit API's built-in implementation.
 * 
 */
public class ModelDataComponent implements CustomModelDataComponent {

    private List<Float> floats;
    private List<Boolean> flags;
    private List<String> strings;
    private List<Color> colors;

    private ModelDataComponent() {
        this.floats = List.of();
        this.flags = List.of();
        this.strings = List.of();
        this.colors = List.of();
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        return Map.of(
                "floats", floats,
                "flags", flags,
                "strings", strings,
                "colors", colors);
    }

    @Override
    public @NotNull List<Float> getFloats() {
        return List.copyOf(floats);
    }

    @Override
    public void setFloats(@NotNull List<Float> floats) {
        this.floats = List.copyOf(floats);
    }

    @Override
    public @NotNull List<Boolean> getFlags() {
        return List.copyOf(flags);
    }

    @Override
    public void setFlags(@NotNull List<Boolean> flags) {
        this.flags = List.copyOf(flags);
    }

    @Override
    public @NotNull List<String> getStrings() {
        return List.copyOf(strings);
    }

    @Override
    public void setStrings(@NotNull List<String> strings) {
        this.strings = List.copyOf(strings);
    }

    @Override
    public @NotNull List<Color> getColors() {
        return List.copyOf(colors);
    }

    @Override
    public void setColors(@NotNull List<Color> colors) {
        this.colors = List.copyOf(colors);
    }

    public static @NotNull ModelDataComponent.Builder builder() {
        return new Builder();
    }

    public static @NotNull ModelDataComponent legacy(int data) {
        var builder = builder();
        builder.floatsFromInt(data);
        return builder.build();
    }

    public static class Builder {
        private ModelDataComponent component;

        public Builder() {
            this.component = new ModelDataComponent();
        }

        public Builder floats(Float... floats) {
            var list = List.of(floats);
            component.setFloats(list);
            return this;
        }

        public Builder floatsFromInt(Integer... ints) {
            var list = List.of(ints);
            component.setFloats(list.stream().map(Integer::floatValue).toList());
            return this;
        }

        public Builder flags(List<Boolean> flags) {
            component.setFlags(flags);
            return this;
        }

        public Builder strings(List<String> strings) {
            component.setStrings(strings);
            return this;
        }

        public Builder colors(List<Color> colors) {
            component.setColors(colors);
            return this;
        }

        public ModelDataComponent build() {
            return component;
        }
    }

}
