package dev.cyr1en.promptpaper.screen.item;

import dev.cyr1en.promptcore.ItemOutputFormat;
import dev.cyr1en.promptcore.ItemTokenFormatter;
import dev.cyr1en.promptpaper.item.catalog.CatalogEntry;
import dev.cyr1en.promptpaper.item.snapshot.ItemSnapshot;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/**
 * Fail-closed token generation for item prompt answers. Every token passes through
 * {@link ItemTokenFormatter}; invalid, oversized, or otherwise unformattable values throw
 * {@link IllegalArgumentException} so the screen can cancel with
 * {@link dev.cyr1en.promptcore.CancelReason#ERROR} instead of emitting a bad answer.
 */
public final class ItemAnswerTokens {

    private ItemAnswerTokens() {}

    /**
     * Generates the answer token for a verified physical inventory selection.
     *
     * @param format the requested output format
     * @param snapshot the captured snapshot the selection was verified against (slot source)
     * @param verifiedItem the revalidated live item (key/material/amount source)
     * @return the validated token
     * @throws IllegalArgumentException if the token violates the format whitelist or bounds
     */
    public static String fromSelection(ItemOutputFormat format, ItemSnapshot snapshot, ItemStack verifiedItem) {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(verifiedItem, "verifiedItem must not be null");
        if (snapshot.isEmpty() || verifiedItem.isEmpty() || verifiedItem.getType().isAir() || verifiedItem.getAmount() <= 0) {
            throw new IllegalArgumentException("Cannot generate answer token from empty item selection");
        }
        return switch (format) {
            case KEY -> ItemTokenFormatter.formatKey(verifiedItem.getType().getKey().toString());
            case MATERIAL -> ItemTokenFormatter.formatMaterial(verifiedItem.getType().name());
            case SLOT -> ItemTokenFormatter.formatSlot(snapshot.playerSlot());
            case AMOUNT -> ItemTokenFormatter.formatAmount(verifiedItem.getAmount());
        };
    }

    /**
     * Generates the answer token for a catalog selection. Catalog entries are pre-validated
     * by the catalog parser, so failures here indicate a registry contract violation.
     *
     * @param format the requested output format ({@link ItemOutputFormat#SLOT} is rejected)
     * @param entry the selected catalog entry
     * @return the validated token
     * @throws IllegalArgumentException if the token violates the format whitelist or bounds
     */
    public static String fromCatalogEntry(ItemOutputFormat format, CatalogEntry entry) {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(entry, "entry must not be null");
        return switch (format) {
            case KEY -> ItemTokenFormatter.formatKey(entry.canonicalKey());
            case MATERIAL -> ItemTokenFormatter.formatMaterial(entry.material().name());
            case AMOUNT -> ItemTokenFormatter.formatAmount(1);
            case SLOT -> throw new IllegalArgumentException(
                    "Slot output is not supported for catalog selections");
        };
    }
}
