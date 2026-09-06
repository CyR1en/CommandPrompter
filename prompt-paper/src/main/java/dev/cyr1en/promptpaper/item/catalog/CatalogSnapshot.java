package dev.cyr1en.promptpaper.item.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An immutable snapshot of item catalog definitions loaded from {@code item-catalogs.yml}.
 *
 * <p>Preserves category and entry ordering defined in the source configuration. Snapshots are
 * deeply immutable and safe to retain across asynchronous workflows and in-flight GUI screens
 * without risking concurrent mutation or reload inconsistencies.
 */
public final class CatalogSnapshot {

  private static final CatalogSnapshot EMPTY = new CatalogSnapshot(Map.of());

  private final Map<String, List<CatalogEntry>> categories;
  private final List<String> categoryNames;

  /**
   * Constructs an immutable snapshot containing defensive copies of the given categories.
   *
   * @param categories mapping of category names to their catalog entry lists
   */
  public CatalogSnapshot(Map<String, List<CatalogEntry>> categories) {
    Objects.requireNonNull(categories, "categories must not be null");
    Map<String, List<CatalogEntry>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<CatalogEntry>> entry : categories.entrySet()) {
      Objects.requireNonNull(entry.getKey(), "category key must not be null");
      Objects.requireNonNull(entry.getValue(), "category entries must not be null");
      copy.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    this.categories = Collections.unmodifiableMap(copy);
    this.categoryNames = List.copyOf(copy.keySet());
  }

  /** Returns an empty catalog snapshot. */
  public static CatalogSnapshot empty() {
    return EMPTY;
  }

  /**
   * Retrieves the list of catalog entries for a category, if present.
   *
   * @param category the category name
   * @return an {@link Optional} containing an unmodifiable list of entries, or empty if not found
   */
  public Optional<List<CatalogEntry>> getCategory(String category) {
    if (category == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(categories.get(category));
  }

  /**
   * Retrieves the list of catalog entries for a category, returning an empty list if not found.
   *
   * @param category the category name
   * @return an unmodifiable list of entries, or an empty list
   */
  public List<CatalogEntry> getEntries(String category) {
    if (category == null) {
      return List.of();
    }
    List<CatalogEntry> list = categories.get(category);
    return list != null ? list : List.of();
  }

  /**
   * Returns the entries for the required {@code "all"} category.
   *
   * @return an unmodifiable list of entries in the {@code "all"} category
   */
  public List<CatalogEntry> all() {
    return getEntries("all");
  }

  /**
   * Checks whether a category exists in this snapshot.
   *
   * @param category the category name
   * @return {@code true} if the category is present, {@code false} otherwise
   */
  public boolean hasCategory(String category) {
    return category != null && categories.containsKey(category);
  }

  /**
   * Returns the category names in their preserved YAML declaration order.
   *
   * @return an unmodifiable list of category names
   */
  public List<String> getCategoryNames() {
    return categoryNames;
  }

  /** Alias for {@link #getCategoryNames()}. */
  public List<String> categories() {
    return categoryNames;
  }

  /**
   * Returns a set view of category names in preserved order.
   *
   * @return an unmodifiable set of category names
   */
  public Set<String> categoryKeySet() {
    return categories.keySet();
  }

  /** Returns the number of categories in this snapshot. */
  public int categoryCount() {
    return categories.size();
  }

  /**
   * Returns the number of entries in a specific category.
   *
   * @param category the category name
   * @return the number of entries, or 0 if the category does not exist
   */
  public int entryCount(String category) {
    if (category == null) {
      return 0;
    }
    List<CatalogEntry> list = categories.get(category);
    return list != null ? list.size() : 0;
  }

  /** Returns the total count of all entries across all categories. */
  public int totalEntryCount() {
    return categories.values().stream().mapToInt(List::size).sum();
  }

  /** Returns the underlying unmodifiable map of category names to entry lists. */
  public Map<String, List<CatalogEntry>> asMap() {
    return categories;
  }

  /**
   * Returns a zero-based slice (page) of entries for a category.
   *
   * @param category the category name
   * @param page zero-based page index
   * @param pageSize maximum number of items per page (must be > 0)
   * @return an unmodifiable sublist of entries for the given page, or empty if out of range
   * @throws IllegalArgumentException if {@code page < 0} or {@code pageSize <= 0}
   */
  public List<CatalogEntry> getPage(String category, int page, int pageSize) {
    if (page < 0) {
      throw new IllegalArgumentException("Page index must not be negative: " + page);
    }
    if (pageSize <= 0) {
      throw new IllegalArgumentException("Page size must be positive: " + pageSize);
    }
    List<CatalogEntry> entries = categories.get(category);
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }
    int fromIndex = page * pageSize;
    if (fromIndex >= entries.size()) {
      return List.of();
    }
    int toIndex = Math.min(entries.size(), fromIndex + pageSize);
    return entries.subList(fromIndex, toIndex);
  }

  /**
   * Computes the total number of pages for a category given a page size.
   *
   * @param category the category name
   * @param pageSize maximum number of items per page (must be > 0)
   * @return the total number of pages (0 if category is missing or empty)
   * @throws IllegalArgumentException if {@code pageSize <= 0}
   */
  public int getPageCount(String category, int pageSize) {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("Page size must be positive: " + pageSize);
    }
    List<CatalogEntry> entries = categories.get(category);
    if (entries == null || entries.isEmpty()) {
      return 0;
    }
    return (entries.size() + pageSize - 1) / pageSize;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CatalogSnapshot that)) return false;
    return categories.equals(that.categories);
  }

  @Override
  public int hashCode() {
    return categories.hashCode();
  }

  @Override
  public String toString() {
    return "CatalogSnapshot{categories=" + categories.keySet() + "}";
  }
}
