package dev.cyr1en.promptpaper.item.snapshot;

import dev.cyr1en.promptpaper.item.snapshot.ItemSlotMapping.SlotCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ItemSlotMapping Tests")
class ItemSlotMappingTest {

    @Test
    @DisplayName("GUI 0..26 maps to logical player slots 9..35 (main inventory rows 1-3)")
    void testMainStorageMapping() {
        for (int guiSlot = 0; guiSlot <= 26; guiSlot++) {
            int expectedPlayerSlot = guiSlot + 9;
            OptionalInt playerSlot = ItemSlotMapping.toPlayerSlot(guiSlot);
            assertTrue(playerSlot.isPresent(), "GUI slot " + guiSlot + " should be mapped");
            assertEquals(expectedPlayerSlot, playerSlot.getAsInt(), "GUI slot " + guiSlot + " must map to " + expectedPlayerSlot);

            OptionalInt reverseGui = ItemSlotMapping.toGuiSlot(expectedPlayerSlot);
            assertTrue(reverseGui.isPresent());
            assertEquals(guiSlot, reverseGui.getAsInt());

            assertEquals(Optional.of(SlotCategory.MAIN_STORAGE), ItemSlotMapping.getCategoryForGuiSlot(guiSlot));
            assertEquals(Optional.of(SlotCategory.MAIN_STORAGE), ItemSlotMapping.getCategoryForPlayerSlot(expectedPlayerSlot));
            assertTrue(ItemSlotMapping.isMainStorageSlot(expectedPlayerSlot));
            assertFalse(ItemSlotMapping.isHotbarSlot(expectedPlayerSlot));
            assertFalse(ItemSlotMapping.isArmorSlot(expectedPlayerSlot));
            assertFalse(ItemSlotMapping.isOffhandSlot(expectedPlayerSlot));
        }
    }

    @Test
    @DisplayName("GUI 27..35 maps to logical player slots 0..8 (hotbar)")
    void testHotbarMapping() {
        for (int guiSlot = 27; guiSlot <= 35; guiSlot++) {
            int expectedPlayerSlot = guiSlot - 27;
            OptionalInt playerSlot = ItemSlotMapping.toPlayerSlot(guiSlot);
            assertTrue(playerSlot.isPresent(), "GUI slot " + guiSlot + " should be mapped");
            assertEquals(expectedPlayerSlot, playerSlot.getAsInt(), "GUI slot " + guiSlot + " must map to " + expectedPlayerSlot);

            OptionalInt reverseGui = ItemSlotMapping.toGuiSlot(expectedPlayerSlot);
            assertTrue(reverseGui.isPresent());
            assertEquals(guiSlot, reverseGui.getAsInt());

            assertEquals(Optional.of(SlotCategory.HOTBAR), ItemSlotMapping.getCategoryForGuiSlot(guiSlot));
            assertEquals(Optional.of(SlotCategory.HOTBAR), ItemSlotMapping.getCategoryForPlayerSlot(expectedPlayerSlot));
            assertTrue(ItemSlotMapping.isHotbarSlot(expectedPlayerSlot));
            assertFalse(ItemSlotMapping.isMainStorageSlot(expectedPlayerSlot));
            assertFalse(ItemSlotMapping.isArmorSlot(expectedPlayerSlot));
            assertFalse(ItemSlotMapping.isOffhandSlot(expectedPlayerSlot));
        }
    }

    @Test
    @DisplayName("GUI 45 maps to logical player slot 40 (offhand)")
    void testOffhandMapping() {
        OptionalInt playerSlot = ItemSlotMapping.toPlayerSlot(45);
        assertTrue(playerSlot.isPresent());
        assertEquals(40, playerSlot.getAsInt());

        OptionalInt guiSlot = ItemSlotMapping.toGuiSlot(40);
        assertTrue(guiSlot.isPresent());
        assertEquals(45, guiSlot.getAsInt());

        assertEquals(Optional.of(SlotCategory.OFFHAND), ItemSlotMapping.getCategoryForGuiSlot(45));
        assertEquals(Optional.of(SlotCategory.OFFHAND), ItemSlotMapping.getCategoryForPlayerSlot(40));
        assertTrue(ItemSlotMapping.isOffhandSlot(40));
        assertFalse(ItemSlotMapping.isArmorSlot(40));
    }

    @Test
    @DisplayName("GUI 46..49 maps to logical player slots 36..39 (armor: boots, leggings, chestplate, helmet)")
    void testArmorMapping() {
        // Boots: GUI 46 -> 36
        OptionalInt bootsSlot = ItemSlotMapping.toPlayerSlot(46);
        assertTrue(bootsSlot.isPresent());
        assertEquals(36, bootsSlot.getAsInt());
        assertEquals(46, ItemSlotMapping.toGuiSlot(36).getAsInt());

        // Leggings: GUI 47 -> 37
        OptionalInt leggingsSlot = ItemSlotMapping.toPlayerSlot(47);
        assertTrue(leggingsSlot.isPresent());
        assertEquals(37, leggingsSlot.getAsInt());
        assertEquals(47, ItemSlotMapping.toGuiSlot(37).getAsInt());

        // Chestplate: GUI 48 -> 38
        OptionalInt chestSlot = ItemSlotMapping.toPlayerSlot(48);
        assertTrue(chestSlot.isPresent());
        assertEquals(38, chestSlot.getAsInt());
        assertEquals(48, ItemSlotMapping.toGuiSlot(38).getAsInt());

        // Helmet: GUI 49 -> 39
        OptionalInt helmSlot = ItemSlotMapping.toPlayerSlot(49);
        assertTrue(helmSlot.isPresent());
        assertEquals(39, helmSlot.getAsInt());
        assertEquals(49, ItemSlotMapping.toGuiSlot(39).getAsInt());

        for (int pSlot = 36; pSlot <= 39; pSlot++) {
            assertTrue(ItemSlotMapping.isArmorSlot(pSlot));
            assertEquals(Optional.of(SlotCategory.ARMOR), ItemSlotMapping.getCategoryForPlayerSlot(pSlot));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 36, 37, 38, 39, 40, 41, 42, 43, 44, 50, 51, 52, 53, 54, 100})
    @DisplayName("Unmapped GUI slots return empty OptionalInt")
    void testUnmappedGuiSlots(int invalidGuiSlot) {
        assertFalse(ItemSlotMapping.isValidGuiSlot(invalidGuiSlot));
        assertTrue(ItemSlotMapping.toPlayerSlot(invalidGuiSlot).isEmpty());
        assertTrue(ItemSlotMapping.getCategoryForGuiSlot(invalidGuiSlot).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 41, 42, 100})
    @DisplayName("Invalid player slots return empty OptionalInt")
    void testInvalidPlayerSlots(int invalidPlayerSlot) {
        assertFalse(ItemSlotMapping.isValidPlayerSlot(invalidPlayerSlot));
        assertTrue(ItemSlotMapping.toGuiSlot(invalidPlayerSlot).isEmpty());
        assertTrue(ItemSlotMapping.getCategoryForPlayerSlot(invalidPlayerSlot).isEmpty());
    }

    @Test
    @DisplayName("All 41 player slots (0..40) are uniquely bijected with 41 GUI slots")
    void testBijectionCompleteness() {
        Set<Integer> playerSlots = new HashSet<>();
        Set<Integer> guiSlots = new HashSet<>();

        for (int gui = 0; gui < 54; gui++) {
            OptionalInt p = ItemSlotMapping.toPlayerSlot(gui);
            if (p.isPresent()) {
                assertTrue(playerSlots.add(p.getAsInt()), "Duplicate player slot: " + p.getAsInt());
                assertTrue(guiSlots.add(gui), "Duplicate GUI slot: " + gui);
            }
        }

        assertEquals(41, playerSlots.size(), "Must map exactly 41 player slots");
        assertEquals(41, guiSlots.size(), "Must map exactly 41 GUI slots");

        for (int slot = 0; slot < 41; slot++) {
            assertTrue(playerSlots.contains(slot), "Missing player slot: " + slot);
        }
    }
}
