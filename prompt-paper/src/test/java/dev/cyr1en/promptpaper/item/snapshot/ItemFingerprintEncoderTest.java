package dev.cyr1en.promptpaper.item.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import dev.cyr1en.promptpaper.MockBukkitTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ItemFingerprint and ItemFingerprintEncoder Tests")
class ItemFingerprintEncoderTest extends MockBukkitTest {

  @Test
  @DisplayName("Deterministic equality: identical items produce identical fingerprints")
  void testDeterministicEquality() {
    ItemStack item1 = new ItemStack(Material.DIAMOND_SWORD, 1);
    ItemStack item2 = new ItemStack(Material.DIAMOND_SWORD, 1);

    ItemFingerprint fp1 = ItemFingerprintEncoder.encode(item1);
    ItemFingerprint fp2 = ItemFingerprintEncoder.encode(item2);

    assertEquals(fp1, fp2);
    assertEquals(fp1.sha256(), fp2.sha256());
    assertEquals(fp1.material(), fp2.material());
    assertEquals(fp1.amount(), fp2.amount());
    assertEquals(fp1.serializedByteCount(), fp2.serializedByteCount());
    assertTrue(fp1.matches(fp2));
    assertEquals(64, fp1.sha256().length());
  }

  @Test
  @DisplayName("Amount normalization: items differing only in amount have identical SHA-256 hash")
  void testAmountNormalization() {
    ItemStack singleApple = new ItemStack(Material.APPLE, 1);
    ItemStack stackApple = new ItemStack(Material.APPLE, 64);

    ItemFingerprint fpSingle = ItemFingerprintEncoder.encode(singleApple);
    ItemFingerprint fpStack = ItemFingerprintEncoder.encode(stackApple);

    // SHA-256 must match because both are normalized to amount 1 during hash computation
    assertEquals(fpSingle.sha256(), fpStack.sha256());
    assertEquals(fpSingle.serializedByteCount(), fpStack.serializedByteCount());

    // Amounts in fingerprint record real quantity
    assertEquals(1, fpSingle.amount());
    assertEquals(64, fpStack.amount());

    // matches() considers amount, while matchesIgnoringAmount() ignores amount
    assertFalse(fpSingle.matches(fpStack));
    assertTrue(fpSingle.matchesIgnoringAmount(fpStack));
  }

  @Test
  @DisplayName("Material mutation produces different hash and material in fingerprint")
  void testMaterialMutation() {
    ItemStack diamondSword = new ItemStack(Material.DIAMOND_SWORD, 1);
    ItemStack ironSword = new ItemStack(Material.IRON_SWORD, 1);

    ItemFingerprint fpDiamond = ItemFingerprintEncoder.encode(diamondSword);
    ItemFingerprint fpIron = ItemFingerprintEncoder.encode(ironSword);

    assertNotEquals(fpDiamond.material(), fpIron.material());
    assertNotEquals(fpDiamond.sha256(), fpIron.sha256());
    assertFalse(fpDiamond.matches(fpIron));
  }

  @Test
  @DisplayName("ItemMeta mutations (custom name, lore, enchantments) alter SHA-256 hash")
  void testItemMetaMutations() {
    ItemStack plainBow = new ItemStack(Material.BOW, 1);
    ItemFingerprint fpPlain = ItemFingerprintEncoder.encode(plainBow);

    // Named Bow
    ItemStack namedBow = plainBow.clone();
    ItemMeta nameMeta = namedBow.getItemMeta();
    nameMeta.displayName(Component.text("Excalibur"));
    namedBow.setItemMeta(nameMeta);
    ItemFingerprint fpNamed = ItemFingerprintEncoder.encode(namedBow);

    assertNotEquals(
        fpPlain.sha256(), fpNamed.sha256(), "Named item must produce different hash from plain");

    // Enchanted Bow
    ItemStack enchantedBow = plainBow.clone();
    ItemMeta enchantMeta = enchantedBow.getItemMeta();
    enchantMeta.addEnchant(Enchantment.POWER, 5, true);
    enchantedBow.setItemMeta(enchantMeta);
    ItemFingerprint fpEnchanted = ItemFingerprintEncoder.encode(enchantedBow);

    assertNotEquals(
        fpPlain.sha256(), fpEnchanted.sha256(), "Enchanted item must produce different hash");
    assertNotEquals(
        fpNamed.sha256(), fpEnchanted.sha256(), "Enchanted item must differ from named item");

    // Lore Bow
    ItemStack loreBow = plainBow.clone();
    ItemMeta loreMeta = loreBow.getItemMeta();
    loreMeta.lore(List.of(Component.text("Ancient relic"), Component.text("Found in deep caves")));
    loreBow.setItemMeta(loreMeta);
    ItemFingerprint fpLore = ItemFingerprintEncoder.encode(loreBow);

    assertNotEquals(fpPlain.sha256(), fpLore.sha256(), "Lore item must produce different hash");
  }

  @Test
  @DisplayName("Empty or null ItemStack encodes to ItemFingerprint.EMPTY")
  void testEmptyItems() {
    assertEquals(ItemFingerprint.EMPTY, ItemFingerprintEncoder.encode(null));
    assertEquals(ItemFingerprint.EMPTY, ItemFingerprintEncoder.encode(new ItemStack(Material.AIR)));
    assertEquals(ItemFingerprint.EMPTY, ItemFingerprintEncoder.encode(ItemStack.empty()));

    ItemStack stoneZero = new ItemStack(Material.STONE, 1);
    stoneZero.setAmount(0);
    assertEquals(ItemFingerprint.EMPTY, ItemFingerprintEncoder.encode(stoneZero));

    assertTrue(ItemFingerprint.EMPTY.isEmpty());
    assertTrue(ItemFingerprint.EMPTY.matches(ItemFingerprint.EMPTY));
  }

  @Test
  @DisplayName("ScreenAggregateAccounting accurately tracks bytes and enforces 2 MiB limit")
  void testScreenAggregateAccounting() {
    ScreenAggregateAccounting accounting = new ScreenAggregateAccounting();
    assertEquals(0, accounting.getTotalBytes());
    assertEquals(ScreenAggregateAccounting.MAX_AGGREGATE_BYTES, accounting.getRemainingBytes());

    ItemFingerprint fp = ItemFingerprintEncoder.encode(new ItemStack(Material.DIAMOND_SWORD, 1));
    accounting.add(fp);
    assertEquals(fp.serializedByteCount(), accounting.getTotalBytes());
    assertEquals(1, accounting.getItemCount());
    assertTrue(accounting.isWithinLimit());

    // Adding bytes right up to limit
    long remaining = accounting.getRemainingBytes();
    accounting.addBytes(remaining);
    assertEquals(ScreenAggregateAccounting.MAX_AGGREGATE_BYTES, accounting.getTotalBytes());
    assertEquals(0, accounting.getRemainingBytes());
    assertTrue(accounting.isWithinLimit());

    // Exceeding limit throws OversizedAggregateException
    assertThrows(OversizedAggregateException.class, () -> accounting.addBytes(1));
  }

  @Test
  @DisplayName("encodeSlots encodes map of items and enforces aggregate bound")
  void testEncodeSlots() {
    Map<Integer, ItemStack> slots = new HashMap<>();
    slots.put(0, new ItemStack(Material.DIAMOND, 64));
    slots.put(1, new ItemStack(Material.EMERALD, 32));

    Map<Integer, ItemFingerprint> encoded = ItemFingerprintEncoder.encodeSlots(slots);
    assertEquals(2, encoded.size());
    assertEquals(Material.DIAMOND, encoded.get(0).material());
    assertEquals(64, encoded.get(0).amount());
    assertEquals(Material.EMERALD, encoded.get(1).material());
    assertEquals(32, encoded.get(1).amount());
  }

  @Test
  @DisplayName("OversizedAggregateException contains accurate actual and max bytes")
  void testOversizedAggregateExceptionDetails() {
    OversizedAggregateException ex =
        assertThrows(
            OversizedAggregateException.class,
            () -> {
              ScreenAggregateAccounting acc = new ScreenAggregateAccounting();
              acc.addBytes(ScreenAggregateAccounting.MAX_AGGREGATE_BYTES + 100);
            });

    assertEquals(ScreenAggregateAccounting.MAX_AGGREGATE_BYTES + 100, ex.getActualBytes());
    assertEquals(ScreenAggregateAccounting.MAX_AGGREGATE_BYTES, ex.getMaxBytes());
  }

  @Test
  @DisplayName("OversizedItemException contains accurate actual and max bytes")
  void testOversizedItemExceptionDetails() {
    OversizedItemException ex = new OversizedItemException("Item too large", 70000, 65536);
    assertEquals(70000, ex.getActualBytes());
    assertEquals(65536, ex.getMaxBytes());
  }

  @Test
  @DisplayName("ItemFingerprint constructor validates invariants")
  void testFingerprintInvariants() {
    assertThrows(NullPointerException.class, () -> new ItemFingerprint(null, 1, "abc", 10));
    assertThrows(
        NullPointerException.class, () -> new ItemFingerprint(Material.STONE, 1, null, 10));
    assertThrows(
        IllegalArgumentException.class, () -> new ItemFingerprint(Material.STONE, -1, "abc", 10));
    assertThrows(
        IllegalArgumentException.class, () -> new ItemFingerprint(Material.STONE, 1, "abc", -1));
  }

  @Test
  @DisplayName("computeSha256Hex handles empty or null data")
  void testComputeSha256Hex() {
    assertEquals(ItemFingerprint.EMPTY_SHA256, ItemFingerprintEncoder.computeSha256Hex(null));
    assertEquals(
        ItemFingerprint.EMPTY_SHA256, ItemFingerprintEncoder.computeSha256Hex(new byte[0]));
  }
}
