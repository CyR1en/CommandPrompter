package dev.cyr1en.promptpaper.item.catalog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parser and validator for {@code item-catalogs.yml}.
 *
 * <p>Enforces strict limits and invariants:
 *
 * <ul>
 *   <li>Maximum file size: 1 MiB (1,048,576 bytes)
 *   <li>Category regex: {@code ^[a-z0-9_.-]{1,64}$}
 *   <li>Maximum categories: 256
 *   <li>Maximum entries per category: 1024
 *   <li>Required category: {@code "all"}
 *   <li>Material resolution requiring {@link Material#isItem()} == true
 *   <li>Rejection of duplicates within categories
 *   <li>Preservation of YAML category and entry declaration order
 * </ul>
 */
public final class ItemCatalogParser {

  public static final String REQUIRED_CATEGORY = "all";
  public static final Pattern CATEGORY_PATTERN = Pattern.compile("^[a-z0-9_.-]{1,64}$");
  public static final int MAX_CATEGORIES = 256;
  public static final int MAX_ENTRIES_PER_CATEGORY = 1024;
  public static final long MAX_FILE_SIZE_BYTES = 1024 * 1024; // 1 MiB

  private ItemCatalogParser() {}

  /**
   * Parses and validates a catalog YAML file from disk.
   *
   * @param file the YAML file
   * @return the validated, immutable {@link CatalogSnapshot}
   * @throws ItemCatalogException if parsing or validation fails
   */
  public static CatalogSnapshot parse(File file) {
    Objects.requireNonNull(file, "file must not be null");
    if (!file.exists()) {
      throw new ItemCatalogException("Catalog file does not exist: " + file.getAbsolutePath());
    }
    long length = file.length();
    if (length > MAX_FILE_SIZE_BYTES) {
      throw new ItemCatalogException(
          "Catalog file exceeds maximum size of 1 MiB: "
              + length
              + " bytes (path: "
              + file.getAbsolutePath()
              + ")");
    }
    try (var input = Files.newInputStream(file.toPath())) {
      byte[] bytes = input.readNBytes((int) MAX_FILE_SIZE_BYTES + 1);
      if (bytes.length > MAX_FILE_SIZE_BYTES) {
        throw new ItemCatalogException(
            "Catalog file exceeds maximum size of 1 MiB: "
                + bytes.length
                + " bytes (path: "
                + file.getAbsolutePath()
                + ")");
      }
      String content = new String(bytes, StandardCharsets.UTF_8);
      return parse(content, file.getAbsolutePath());
    } catch (IOException e) {
      throw new ItemCatalogException(
          "Failed to read catalog file: " + file.getAbsolutePath() + ": " + e.getMessage(), e);
    }
  }

  /**
   * Parses and validates catalog YAML from an {@link InputStream}.
   *
   * @param inputStream the input stream
   * @param sourceHint human-readable source description for error reporting
   * @return the validated, immutable {@link CatalogSnapshot}
   * @throws ItemCatalogException if parsing or validation fails
   */
  public static CatalogSnapshot parse(InputStream inputStream, String sourceHint) {
    Objects.requireNonNull(inputStream, "inputStream must not be null");
    try {
      byte[] bytes = inputStream.readNBytes((int) MAX_FILE_SIZE_BYTES + 1);
      if (bytes.length > MAX_FILE_SIZE_BYTES) {
        throw new ItemCatalogException(
            "Catalog stream exceeds maximum size of 1 MiB: "
                + bytes.length
                + " bytes ("
                + sourceHint
                + ")");
      }
      String content = new String(bytes, StandardCharsets.UTF_8);
      return parse(content, sourceHint);
    } catch (IOException e) {
      throw new ItemCatalogException(
          "Failed to read catalog stream (" + sourceHint + "): " + e.getMessage(), e);
    }
  }

  /**
   * Parses and validates a catalog YAML string.
   *
   * @param yamlContent the YAML content string
   * @param sourceHint human-readable source description for error reporting
   * @return the validated, immutable {@link CatalogSnapshot}
   * @throws ItemCatalogException if parsing or validation fails
   */
  public static CatalogSnapshot parse(String yamlContent, String sourceHint) {
    Objects.requireNonNull(yamlContent, "yamlContent must not be null");
    if (yamlContent.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_SIZE_BYTES) {
      throw new ItemCatalogException(
          "Catalog content exceeds maximum size of 1 MiB (" + sourceHint + ")");
    }
    if (yamlContent.isBlank()) {
      throw new ItemCatalogException(
          "Catalog YAML is empty; missing required '"
              + REQUIRED_CATEGORY
              + "' category ("
              + sourceHint
              + ")");
    }

    LoaderOptions loaderOptions = new LoaderOptions();
    loaderOptions.setCodePointLimit((int) MAX_FILE_SIZE_BYTES);
    Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));

    Object loaded;
    try {
      loaded = yaml.load(yamlContent);
    } catch (Exception e) {
      throw new ItemCatalogException(
          "Failed to parse YAML syntax in catalog (" + sourceHint + "): " + e.getMessage(), e);
    }

    if (loaded == null) {
      throw new ItemCatalogException(
          "Catalog document is empty; missing required '"
              + REQUIRED_CATEGORY
              + "' category ("
              + sourceHint
              + ")");
    }

    if (!(loaded instanceof Map<?, ?> rootMap)) {
      throw new ItemCatalogException(
          "Catalog root must be a YAML mapping/object (" + sourceHint + ")");
    }

    Map<?, ?> categoriesMap;
    if (rootMap.containsKey("categories")) {
      Object rawCategories = rootMap.get("categories");
      if (rawCategories == null) {
        throw new ItemCatalogException(
            "The 'categories' section is empty; missing required '"
                + REQUIRED_CATEGORY
                + "' category ("
                + sourceHint
                + ")");
      }
      if (!(rawCategories instanceof Map<?, ?> rawCatMap)) {
        throw new ItemCatalogException(
            "The 'categories' section must be a YAML mapping (" + sourceHint + ")");
      }
      categoriesMap = rawCatMap;
    } else {
      categoriesMap = rootMap;
    }

    if (categoriesMap.size() > MAX_CATEGORIES) {
      throw new ItemCatalogException(
          "Category count exceeds maximum limit of "
              + MAX_CATEGORIES
              + " (found: "
              + categoriesMap.size()
              + ") in "
              + sourceHint);
    }

    if (!categoriesMap.containsKey(REQUIRED_CATEGORY)) {
      throw new ItemCatalogException(
          "Missing required '" + REQUIRED_CATEGORY + "' category in " + sourceHint);
    }

    Map<String, List<CatalogEntry>> parsedCategories = new LinkedHashMap<>();

    for (Map.Entry<?, ?> entry : categoriesMap.entrySet()) {
      Object keyObj = entry.getKey();
      if (keyObj == null) {
        throw new ItemCatalogException("Null category name encountered in " + sourceHint);
      }
      String categoryName = keyObj.toString();
      if (!CATEGORY_PATTERN.matcher(categoryName).matches()) {
        throw new ItemCatalogException(
            "Invalid category name '"
                + categoryName
                + "': must match regex "
                + CATEGORY_PATTERN.pattern()
                + " in "
                + sourceHint);
      }

      Object valueObj = entry.getValue();
      if (valueObj == null) {
        throw new ItemCatalogException(
            "Category '" + categoryName + "' entries must not be null in " + sourceHint);
      }
      if (!(valueObj instanceof List<?> entryList)) {
        throw new ItemCatalogException(
            "Category '" + categoryName + "' entries must be a list in " + sourceHint);
      }

      if (entryList.size() > MAX_ENTRIES_PER_CATEGORY) {
        throw new ItemCatalogException(
            "Category '"
                + categoryName
                + "' exceeds maximum entry limit of "
                + MAX_ENTRIES_PER_CATEGORY
                + " (found: "
                + entryList.size()
                + ") in "
                + sourceHint);
      }

      List<CatalogEntry> entries = new ArrayList<>(entryList.size());
      Set<NamespacedKey> seenKeys = new HashSet<>();

      for (int i = 0; i < entryList.size(); i++) {
        Object itemObj = entryList.get(i);
        CatalogEntry catalogEntry = parseCatalogEntry(itemObj, categoryName, i, sourceHint);
        if (!seenKeys.add(catalogEntry.key())) {
          throw new ItemCatalogException(
              "Duplicate entry '"
                  + catalogEntry.canonicalKey()
                  + "' in category '"
                  + categoryName
                  + "' (entry index: "
                  + i
                  + ") in "
                  + sourceHint);
        }
        entries.add(catalogEntry);
      }

      parsedCategories.put(categoryName, Collections.unmodifiableList(entries));
    }

    return new CatalogSnapshot(parsedCategories);
  }

  private static CatalogEntry parseCatalogEntry(
      Object itemObj, String categoryName, int index, String sourceHint) {
    if (itemObj == null) {
      throw new ItemCatalogException(
          "Null entry at index " + index + " in category '" + categoryName + "' in " + sourceHint);
    }
    String rawKey;
    if (itemObj instanceof String str) {
      rawKey = str.trim();
    } else if (itemObj instanceof Map<?, ?> map) {
      Object idVal = map.get("item");
      if (idVal == null) idVal = map.get("material");
      if (idVal == null) idVal = map.get("key");
      if (idVal == null) idVal = map.get("id");
      if (idVal == null) {
        throw new ItemCatalogException(
            "Mapping entry at index "
                + index
                + " in category '"
                + categoryName
                + "' is missing 'item' or 'material' key in "
                + sourceHint);
      }
      rawKey = idVal.toString().trim();
    } else {
      throw new ItemCatalogException(
          "Unsupported entry type at index "
              + index
              + " in category '"
              + categoryName
              + "': "
              + itemObj.getClass().getSimpleName()
              + " in "
              + sourceHint);
    }

    if (rawKey.isBlank()) {
      throw new ItemCatalogException(
          "Empty entry string at index "
              + index
              + " in category '"
              + categoryName
              + "' in "
              + sourceHint);
    }

    return resolveMaterial(rawKey, categoryName, index, sourceHint);
  }

  public static CatalogEntry resolveMaterial(
      String rawKey, String categoryName, int index, String sourceHint) {
    String normalized = rawKey.trim().toLowerCase(Locale.ROOT);
    Material mat = Material.matchMaterial(normalized);
    if (mat == null && !normalized.contains(":")) {
      mat = Material.matchMaterial("minecraft:" + normalized);
    }
    if (mat == null && normalized.startsWith("minecraft:")) {
      mat = Material.matchMaterial(normalized.substring("minecraft:".length()));
    }
    if (mat == null) {
      throw new ItemCatalogException(
          "Unknown material '"
              + rawKey
              + "' at index "
              + index
              + " in category '"
              + categoryName
              + "' in "
              + sourceHint);
    }
    if (!mat.isItem() || mat.isAir()) {
      throw new ItemCatalogException(
          "Material '"
              + rawKey
              + "' ("
              + mat.name()
              + ") at index "
              + index
              + " in category '"
              + categoryName
              + "' is not a valid item in "
              + sourceHint);
    }
    NamespacedKey key = mat.getKey();
    return new CatalogEntry(mat, key, key.toString());
  }
}
