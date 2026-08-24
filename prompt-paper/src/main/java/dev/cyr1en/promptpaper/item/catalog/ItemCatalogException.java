package dev.cyr1en.promptpaper.item.catalog;

/**
 * Thrown when an error occurs during parsing, validation, or loading of {@code item-catalogs.yml}.
 */
public class ItemCatalogException extends RuntimeException {

    public ItemCatalogException(String message) {
        super(message);
    }

    public ItemCatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
