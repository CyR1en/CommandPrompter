package dev.cyr1en.promptpaper.item.snapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Pure utility class defining slot mappings between GUI container slots and logical player
 * inventory slots.
 *
 * <p>Mapping specifications:
 *
 * <ul>
 *   <li>GUI {@code 0..26} &rarr; Player logical {@code 9..35} (main inventory rows 1-3)
 *   <li>GUI {@code 27..35} &rarr; Player logical {@code 0..8} (hotbar)
 *   <li>GUI {@code 45} &rarr; Player logical {@code 40} (offhand)
 *   <li>GUI {@code 46} &rarr; Player logical {@code 36} (boots)
 *   <li>GUI {@code 47} &rarr; Player logical {@code 37} (leggings)
 *   <li>GUI {@code 48} &rarr; Player logical {@code 38} (chestplate)
 *   <li>GUI {@code 49} &rarr; Player logical {@code 39} (helmet)
 * </ul>
 */
public final class ItemSlotMapping {

  // GUI Slot Ranges & Constants
  public static final int GUI_MAIN_START = 0;
  public static final int GUI_MAIN_END = 26;

  public static final int GUI_HOTBAR_START = 27;
  public static final int GUI_HOTBAR_END = 35;

  public static final int GUI_OFFHAND = 45;
  public static final int GUI_BOOTS = 46;
  public static final int GUI_LEGGINGS = 47;
  public static final int GUI_CHESTPLATE = 48;
  public static final int GUI_HELMET = 49;

  // Player Slot Ranges & Constants
  public static final int PLAYER_HOTBAR_START = 0;
  public static final int PLAYER_HOTBAR_END = 8;

  public static final int PLAYER_MAIN_START = 9;
  public static final int PLAYER_MAIN_END = 35;

  public static final int PLAYER_BOOTS = 36;
  public static final int PLAYER_LEGGINGS = 37;
  public static final int PLAYER_CHESTPLATE = 38;
  public static final int PLAYER_HELMET = 39;
  public static final int PLAYER_OFFHAND = 40;

  public static final int PLAYER_ARMOR_START = 36;
  public static final int PLAYER_ARMOR_END = 39;

  public static final int TOTAL_PLAYER_SLOTS = 41; // 0..40

  public enum SlotCategory {
    MAIN_STORAGE,
    HOTBAR,
    ARMOR,
    OFFHAND
  }

  private static final Map<Integer, Integer> GUI_TO_PLAYER;
  private static final Map<Integer, Integer> PLAYER_TO_GUI;

  static {
    Map<Integer, Integer> g2p = new LinkedHashMap<>(TOTAL_PLAYER_SLOTS);

    // GUI 0..26 -> Player 9..35
    for (int gui = GUI_MAIN_START; gui <= GUI_MAIN_END; gui++) {
      int player = gui + 9;
      g2p.put(gui, player);
    }

    // GUI 27..35 -> Player 0..8
    for (int gui = GUI_HOTBAR_START; gui <= GUI_HOTBAR_END; gui++) {
      int player = gui - 27;
      g2p.put(gui, player);
    }

    // Offhand: GUI 45 -> Player 40
    g2p.put(GUI_OFFHAND, PLAYER_OFFHAND);

    // Boots: GUI 46 -> Player 36
    g2p.put(GUI_BOOTS, PLAYER_BOOTS);

    // Leggings: GUI 47 -> Player 37
    g2p.put(GUI_LEGGINGS, PLAYER_LEGGINGS);

    // Chestplate: GUI 48 -> Player 38
    g2p.put(GUI_CHESTPLATE, PLAYER_CHESTPLATE);

    // Helmet: GUI 49 -> Player 39
    g2p.put(GUI_HELMET, PLAYER_HELMET);

    Map<Integer, Integer> p2g = new LinkedHashMap<>(g2p.size());
    g2p.forEach((gui, player) -> p2g.put(player, gui));

    GUI_TO_PLAYER = Collections.unmodifiableMap(g2p);
    PLAYER_TO_GUI = Collections.unmodifiableMap(p2g);
  }

  private ItemSlotMapping() {}

  /**
   * Converts a GUI container slot index to a logical player inventory slot index.
   *
   * @param guiSlot GUI slot index
   * @return {@link OptionalInt} containing logical player slot index if mapped, or empty
   */
  public static OptionalInt toPlayerSlot(int guiSlot) {
    Integer playerSlot = GUI_TO_PLAYER.get(guiSlot);
    return playerSlot != null ? OptionalInt.of(playerSlot) : OptionalInt.empty();
  }

  /**
   * Converts a logical player inventory slot index to a GUI container slot index.
   *
   * @param playerSlot Player inventory slot index (0..40)
   * @return {@link OptionalInt} containing GUI slot index if mapped, or empty
   */
  public static OptionalInt toGuiSlot(int playerSlot) {
    Integer guiSlot = PLAYER_TO_GUI.get(playerSlot);
    return guiSlot != null ? OptionalInt.of(guiSlot) : OptionalInt.empty();
  }

  /**
   * Checks if a GUI slot has a corresponding player inventory mapping.
   *
   * @param guiSlot GUI slot index
   * @return true if mapped
   */
  public static boolean isValidGuiSlot(int guiSlot) {
    return GUI_TO_PLAYER.containsKey(guiSlot);
  }

  /**
   * Checks if a player slot index is within the valid logical inventory range (0..40).
   *
   * @param playerSlot Player slot index
   * @return true if valid
   */
  public static boolean isValidPlayerSlot(int playerSlot) {
    return playerSlot >= 0 && playerSlot < TOTAL_PLAYER_SLOTS;
  }

  /**
   * Checks if the given player slot is an armor slot (36..39).
   *
   * @param playerSlot Player slot index
   * @return true if armor slot
   */
  public static boolean isArmorSlot(int playerSlot) {
    return playerSlot >= PLAYER_ARMOR_START && playerSlot <= PLAYER_ARMOR_END;
  }

  /**
   * Checks if the given player slot is the offhand slot (40).
   *
   * @param playerSlot Player slot index
   * @return true if offhand slot
   */
  public static boolean isOffhandSlot(int playerSlot) {
    return playerSlot == PLAYER_OFFHAND;
  }

  /**
   * Checks if the given player slot is a hotbar slot (0..8).
   *
   * @param playerSlot Player slot index
   * @return true if hotbar slot
   */
  public static boolean isHotbarSlot(int playerSlot) {
    return playerSlot >= PLAYER_HOTBAR_START && playerSlot <= PLAYER_HOTBAR_END;
  }

  /**
   * Checks if the given player slot is a main storage slot (9..35).
   *
   * @param playerSlot Player slot index
   * @return true if main storage slot
   */
  public static boolean isMainStorageSlot(int playerSlot) {
    return playerSlot >= PLAYER_MAIN_START && playerSlot <= PLAYER_MAIN_END;
  }

  /**
   * Retrieves the {@link SlotCategory} for a given player slot.
   *
   * @param playerSlot Player slot index
   * @return {@link Optional} containing category or empty if invalid
   */
  public static Optional<SlotCategory> getCategoryForPlayerSlot(int playerSlot) {
    if (isHotbarSlot(playerSlot)) {
      return Optional.of(SlotCategory.HOTBAR);
    } else if (isMainStorageSlot(playerSlot)) {
      return Optional.of(SlotCategory.MAIN_STORAGE);
    } else if (isArmorSlot(playerSlot)) {
      return Optional.of(SlotCategory.ARMOR);
    } else if (isOffhandSlot(playerSlot)) {
      return Optional.of(SlotCategory.OFFHAND);
    }
    return Optional.empty();
  }

  /**
   * Retrieves the {@link SlotCategory} for a given GUI slot.
   *
   * @param guiSlot GUI slot index
   * @return {@link Optional} containing category or empty if invalid
   */
  public static Optional<SlotCategory> getCategoryForGuiSlot(int guiSlot) {
    OptionalInt playerSlot = toPlayerSlot(guiSlot);
    return playerSlot.isPresent()
        ? getCategoryForPlayerSlot(playerSlot.getAsInt())
        : Optional.empty();
  }

  /** Returns an unmodifiable set of all valid GUI slot indices. */
  public static Set<Integer> getAllValidGuiSlots() {
    return GUI_TO_PLAYER.keySet();
  }

  /** Returns an unmodifiable set of all valid player slot indices. */
  public static Set<Integer> getAllValidPlayerSlots() {
    return PLAYER_TO_GUI.keySet();
  }
}
