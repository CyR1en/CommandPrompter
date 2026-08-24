package dev.cyr1en.promptpaper.screen.confirmation;

import dev.cyr1en.promptui.ComponentUtil;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Immutable configuration for the 27-slot chest GUI confirmation view.
 */
public record ConfirmationGuiConfig(
        Component title,
        int size,
        int confirmSlot,
        ItemStack confirmItem,
        int infoSlot,
        ItemStack infoItem,
        int declineSlot,
        ItemStack declineItem,
        ItemStack fillerItem) {

    public static final int DEFAULT_SIZE = 27;
    public static final int DEFAULT_CONFIRM_SLOT = 11;
    public static final int DEFAULT_INFO_SLOT = 13;
    public static final int DEFAULT_DECLINE_SLOT = 15;

    public ConfirmationGuiConfig {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(confirmItem, "confirmItem must not be null");
        Objects.requireNonNull(infoItem, "infoItem must not be null");
        Objects.requireNonNull(declineItem, "declineItem must not be null");
        Objects.requireNonNull(fillerItem, "fillerItem must not be null");

        if (size <= 0 || size % 9 != 0 || size > 54) {
            throw new IllegalArgumentException("GUI size must be a multiple of 9 between 9 and 54, got: " + size);
        }
        if (confirmSlot < 0 || confirmSlot >= size) {
            throw new IllegalArgumentException(
                    "Confirm slot must be between 0 and " + (size - 1) + ", got: " + confirmSlot);
        }
        if (infoSlot < 0 || infoSlot >= size) {
            throw new IllegalArgumentException(
                    "Info slot must be between 0 and " + (size - 1) + ", got: " + infoSlot);
        }
        if (declineSlot < 0 || declineSlot >= size) {
            throw new IllegalArgumentException(
                    "Decline slot must be between 0 and " + (size - 1) + ", got: " + declineSlot);
        }
        if (confirmSlot == infoSlot || confirmSlot == declineSlot || infoSlot == declineSlot) {
            throw new IllegalArgumentException("Slots must be unique (confirm=" + confirmSlot
                    + ", info=" + infoSlot + ", decline=" + declineSlot + ")");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ConfirmationGuiConfig createDefault(Component title, Component infoMessage) {
        return builder()
                .title(title)
                .infoMessage(infoMessage)
                .build();
    }

    public static class Builder {
        private Component title = ComponentUtil.mini("<dark_gray>Confirmation</dark_gray>");
        private int size = DEFAULT_SIZE;
        private int confirmSlot = DEFAULT_CONFIRM_SLOT;
        private ItemStack confirmItem;
        private int infoSlot = DEFAULT_INFO_SLOT;
        private ItemStack infoItem;
        private int declineSlot = DEFAULT_DECLINE_SLOT;
        private ItemStack declineItem;
        private ItemStack fillerItem;

        public Builder title(Component title) {
            this.title = title != null ? title : Component.empty();
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder confirmSlot(int slot) {
            this.confirmSlot = slot;
            return this;
        }

        public Builder confirmItem(ItemStack item) {
            this.confirmItem = item != null ? item.clone() : null;
            return this;
        }

        public Builder confirmItem(Material material, Component name) {
            this.confirmItem = buildItem(material, name, List.of());
            return this;
        }

        public Builder infoSlot(int slot) {
            this.infoSlot = slot;
            return this;
        }

        public Builder infoItem(ItemStack item) {
            this.infoItem = item != null ? item.clone() : null;
            return this;
        }

        public Builder infoMessage(Component message) {
            this.infoItem = buildItem(
                    Material.PAPER,
                    ComponentUtil.mini("<!italic><yellow><bold>Information</bold></yellow>"),
                    message != null ? List.of(message) : List.of());
            return this;
        }

        public Builder infoItem(Material material, Component name, List<Component> lore) {
            this.infoItem = buildItem(material, name, lore);
            return this;
        }

        public Builder declineSlot(int slot) {
            this.declineSlot = slot;
            return this;
        }

        public Builder declineItem(ItemStack item) {
            this.declineItem = item != null ? item.clone() : null;
            return this;
        }

        public Builder declineItem(Material material, Component name) {
            this.declineItem = buildItem(material, name, List.of());
            return this;
        }

        public Builder fillerItem(ItemStack item) {
            this.fillerItem = item != null ? item.clone() : null;
            return this;
        }

        public Builder fillerMaterial(Material material) {
            this.fillerItem = buildItem(material, ComponentUtil.mini("<!italic> "), List.of());
            return this;
        }

        public ConfirmationGuiConfig build() {
            var finalConfirm = confirmItem != null
                    ? confirmItem
                    : buildItem(
                            Material.LIME_STAINED_GLASS_PANE,
                            ComponentUtil.mini("<!italic><green><bold>Confirm</bold></green>"),
                            List.of());
            var finalInfo = infoItem != null
                    ? infoItem
                    : buildItem(
                            Material.PAPER,
                            ComponentUtil.mini("<!italic><yellow><bold>Information</bold></yellow>"),
                            List.of());
            var finalDecline = declineItem != null
                    ? declineItem
                    : buildItem(
                            Material.RED_STAINED_GLASS_PANE,
                            ComponentUtil.mini("<!italic><red><bold>Decline</bold></red>"),
                            List.of());
            var finalFiller = fillerItem != null
                    ? fillerItem
                    : buildItem(Material.GRAY_STAINED_GLASS_PANE, ComponentUtil.mini("<!italic> "), List.of());

            return new ConfirmationGuiConfig(
                    title,
                    size,
                    confirmSlot,
                    finalConfirm,
                    infoSlot,
                    finalInfo,
                    declineSlot,
                    finalDecline,
                    finalFiller);
        }

        private static ItemStack buildItem(Material material, Component displayName, List<Component> lore) {
            var item = new ItemStack(material);
            var meta = item.getItemMeta();
            if (meta != null) {
                if (displayName != null) meta.displayName(displayName);
                if (lore != null && !lore.isEmpty()) meta.lore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }
    }
}
