package dev.cyr1en.promptpaper.screen.confirmation;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ConfirmationGuiConfigTest extends MockBukkitTest {

    @Test
    void defaultValues() {
        var config = ConfirmationGuiConfig.builder().build();

        assertEquals(27, config.size());
        assertEquals(11, config.confirmSlot());
        assertEquals(13, config.infoSlot());
        assertEquals(15, config.declineSlot());

        assertEquals(Material.LIME_STAINED_GLASS_PANE, config.confirmItem().getType());
        assertEquals(Material.PAPER, config.infoItem().getType());
        assertEquals(Material.RED_STAINED_GLASS_PANE, config.declineItem().getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, config.fillerItem().getType());
    }

    @Test
    void customBuilderValues() {
        var title = Component.text("Custom Title");
        var confirmItem = new ItemStack(Material.EMERALD_BLOCK);
        var declineItem = new ItemStack(Material.REDSTONE_BLOCK);
        var infoItem = new ItemStack(Material.BOOK);
        var fillerItem = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);

        var config = ConfirmationGuiConfig.builder()
                .title(title)
                .size(54)
                .confirmSlot(20)
                .confirmItem(confirmItem)
                .infoSlot(22)
                .infoItem(infoItem)
                .declineSlot(24)
                .declineItem(declineItem)
                .fillerItem(fillerItem)
                .build();

        assertEquals(title, config.title());
        assertEquals(54, config.size());
        assertEquals(20, config.confirmSlot());
        assertEquals(22, config.infoSlot());
        assertEquals(24, config.declineSlot());
        assertEquals(Material.EMERALD_BLOCK, config.confirmItem().getType());
        assertEquals(Material.BOOK, config.infoItem().getType());
        assertEquals(Material.REDSTONE_BLOCK, config.declineItem().getType());
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, config.fillerItem().getType());
    }

    @Test
    void createDefaultFactoryMethod() {
        var title = Component.text("Delete File?");
        var message = Component.text("This action cannot be undone.");

        var config = ConfirmationGuiConfig.createDefault(title, message);

        assertEquals(title, config.title());
        assertEquals(27, config.size());
        assertEquals(11, config.confirmSlot());
        assertEquals(13, config.infoSlot());
        assertEquals(15, config.declineSlot());
        assertNotNull(config.infoItem().getItemMeta());
        assertEquals(List.of(message), config.infoItem().getItemMeta().lore());
    }

    @Test
    void invalidSizeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ConfirmationGuiConfig.builder().size(0).build());
        assertThrows(IllegalArgumentException.class, () ->
                ConfirmationGuiConfig.builder().size(25).build());
        assertThrows(IllegalArgumentException.class, () ->
                ConfirmationGuiConfig.builder().size(63).build());
    }

    @Test
    void outOfBoundsSlotsThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                ConfirmationGuiConfig.builder().size(27).confirmSlot(-1).build());
        assertThrows(IllegalArgumentException.class, () ->
                ConfirmationGuiConfig.builder().size(27).confirmSlot(27).build());
        assertThrows(IllegalArgumentException.class, () ->
                ConfirmationGuiConfig.builder().size(27).infoSlot(30).build());
        assertThrows(IllegalArgumentException.class, () ->
                ConfirmationGuiConfig.builder().size(27).declineSlot(-5).build());
    }

    @Test
    void overlappingSlotsThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                ConfirmationGuiConfig.builder().confirmSlot(11).infoSlot(11).build());
        assertThrows(IllegalArgumentException.class, () ->
                ConfirmationGuiConfig.builder().confirmSlot(15).declineSlot(15).build());
        assertThrows(IllegalArgumentException.class, () ->
                ConfirmationGuiConfig.builder().infoSlot(13).declineSlot(13).build());
    }
}
