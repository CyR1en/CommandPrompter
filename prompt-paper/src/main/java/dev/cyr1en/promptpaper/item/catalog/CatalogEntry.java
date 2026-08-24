package dev.cyr1en.promptpaper.item.catalog;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/**
 * An immutable entry in an item catalog representing a valid Bukkit item {@link Material}.
 *
 * @param material the resolved Bukkit {@link Material} (guaranteed to satisfy {@link Material#isItem()})
 * @param key the canonical {@link NamespacedKey} for the material
 * @param canonicalKey the string form of the namespaced key (e.g., {@code "minecraft:diamond_sword"})
 */
public record CatalogEntry(Material material, NamespacedKey key, String canonicalKey) {

    public CatalogEntry {
        Objects.requireNonNull(material, "material must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(canonicalKey, "canonicalKey must not be null");
        if (!material.isItem() || material.isAir()) {
            throw new IllegalArgumentException(
                    "Material '" + material + "' is not a valid item (isItem() == false or isAir() == true)");
        }
    }

    public static CatalogEntry of(Material material) {
        Objects.requireNonNull(material, "material must not be null");
        NamespacedKey key = material.getKey();
        return new CatalogEntry(material, key, key.toString());
    }

    public static CatalogEntry of(Material material, NamespacedKey key) {
        Objects.requireNonNull(material, "material must not be null");
        Objects.requireNonNull(key, "key must not be null");
        return new CatalogEntry(material, key, key.toString());
    }

    /**
     * Resolves a raw material name or namespaced key string to a {@link CatalogEntry}.
     *
     * @param rawKey the raw string (e.g., {@code "diamond_sword"}, {@code "minecraft:stone"}, {@code "IRON_INGOT"})
     * @return the resolved {@link CatalogEntry}
     * @throws ItemCatalogException if the string cannot be resolved or does not represent a valid item
     */
    public static CatalogEntry resolve(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new ItemCatalogException("Material key cannot be null or blank");
        }
        String normalized = rawKey.trim().toLowerCase(Locale.ROOT);
        Material mat = Material.matchMaterial(normalized);
        if (mat == null && !normalized.contains(":")) {
            mat = Material.matchMaterial("minecraft:" + normalized);
        }
        if (mat == null && normalized.startsWith("minecraft:")) {
            mat = Material.matchMaterial(normalized.substring("minecraft:".length()));
        }
        if (mat == null) {
            throw new ItemCatalogException("Unknown item material: '" + rawKey + "'");
        }
        if (!mat.isItem() || mat.isAir()) {
            throw new ItemCatalogException(
                    "Material '" + rawKey + "' (" + mat.name() + ") is not a valid item");
        }
        NamespacedKey key = mat.getKey();
        return new CatalogEntry(mat, key, key.toString());
    }

    public NamespacedKey namespacedKey() {
        return key;
    }

    public Material getMaterial() {
        return material;
    }

    public NamespacedKey getKey() {
        return key;
    }

    public String getCanonicalKey() {
        return canonicalKey;
    }
}
