package dev.cyr1en.promptcore.i18n;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Abstract base for the plugin's internationalization service.
 *
 * <p>Provides a three-level fallback chain for loading translated messages:
 *
 * <ol>
 *   <li>{@code <baseDir>/locales/messages_<locale>.properties} — user overrides on disk
 *   <li>{@code jar://messages_<locale>.properties} — bundled locale translation
 *   <li>{@code jar://messages_en_US.properties} — ultimate fallback
 * </ol>
 *
 * <p>Subclasses supply the concrete output type {@code T} (e.g. {@code Component}) and the context
 * type {@code C} (e.g. {@code Player}). The formatting pipeline is:
 *
 * <ol>
 *   <li>{@link #preFormat(String, Object)} — optional context-aware pre-processing (e.g. PAPI)
 *   <li>{@link #applyPlaceholders(String, Placeholder[])} — {@code %key%} substitution
 *   <li>{@link #postFormat(String, Object)} — final parsing into type {@code T} (e.g. MiniMessage)
 * </ol>
 *
 * @param <T> the output type (e.g. {@code net.kyori.adventure.text.Component})
 * @param <C> the context type (e.g. {@code org.bukkit.entity.Player}); may be {@code null}
 */
public abstract class AbstractI18n<T, C> {

  private static final String LOCALES_DIR = "locales";

  protected final String locale;
  protected final File baseDir;
  protected final ClassLoader pluginClassLoader;
  protected final Properties merged;
  protected final Logger logger;

  /**
   * Constructs the i18n service and immediately loads the merged property set for the given locale.
   *
   * @param locale the locale string (e.g. {@code "en_US"})
   * @param baseDir the plugin data folder (used to locate the user-override locales directory)
   * @param pluginClassLoader the class loader used to read bundled {@code .properties} resources
   * @param logger a logger for diagnostic/warning output
   */
  protected AbstractI18n(
      String locale, File baseDir, ClassLoader pluginClassLoader, Logger logger) {
    this.locale = locale;
    this.baseDir = baseDir;
    this.pluginClassLoader = pluginClassLoader;
    this.merged = new Properties();
    this.logger = logger;
    load();
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  public String getLocale() {
    return locale;
  }

  /**
   * Returns a formatted message for the given key with optional context and placeholders.
   *
   * <p>The pipeline is: raw text → {@link #preFormat} → {@link #applyPlaceholders} → {@link
   * #postFormat}.
   *
   * @param key the message key (e.g. {@code "command.version"})
   * @param context the context object passed to {@link #preFormat} and {@link #postFormat}; may be
   *     {@code null}
   * @param placeholders zero or more {@link Placeholder} instances for {@code %key%} substitution
   * @return the formatted result, or a fallback value if the key is missing
   */
  public T get(String key, C context, Placeholder... placeholders) {
    var raw = merged.getProperty(key);
    if (raw == null) {
      return missing(key);
    }
    var preFormatted = preFormat(raw, context);
    var substituted = applyPlaceholders(preFormatted, placeholders);
    return postFormat(substituted, context);
  }

  /**
   * Convenience overload — equivalent to {@link #get(String, Object, Placeholder[])} with a {@code
   * null} context.
   *
   * @param key the message key
   * @param placeholders zero or more {@link Placeholder} instances
   * @return the formatted result
   */
  public T get(String key, Placeholder... placeholders) {
    return get(key, null, placeholders);
  }

  /**
   * Reloads the merged properties from disk, picking up any user changes in the locales directory
   * without a full server restart.
   */
  public void reload() {
    merged.clear();
    load();
  }

  // ---------------------------------------------------------------------------
  // Extension points for subclasses
  // ---------------------------------------------------------------------------

  /**
   * Optional pre-processing step invoked <em>before</em> {@code %key%} substitution.
   *
   * <p>The default implementation returns the text unchanged. Subclasses can override this to, for
   * example, expand PAPI placeholders via {@code PlaceholderAPI.setPlaceholders(context, text)}.
   *
   * @param text the raw message string retrieved from the properties
   * @param context the context object (e.g. a {@code Player}); may be {@code null}
   * @return the pre-formatted string, ready for placeholder substitution
   */
  protected String preFormat(String text, C context) {
    return text;
  }

  /**
   * Final formatting step invoked <em>after</em> {@code %key%} substitution.
   *
   * <p>Subclasses must implement this to parse the fully-substituted string into the output type
   * {@code T} (e.g. via MiniMessage).
   *
   * @param text the fully substituted message string
   * @param context the context object; may be {@code null}
   * @return the parsed output object
   */
  protected abstract T postFormat(String text, C context);

  /**
   * Returns the fallback value used when a key is not found in any properties level.
   *
   * <p>The default implementation delegates to {@link #postFormat} with a synthetic {@code <missing
   * translation: key>} string. Subclasses may override for different behaviour.
   *
   * @param key the missing message key
   * @return a fallback value of type {@code T}
   */
  protected T missing(String key) {
    return postFormat("<missing translation: " + key + ">", null);
  }

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  /**
   * Replaces all {@code %key%} tokens in {@code text} with the corresponding {@link Placeholder}
   * value.
   */
  protected String applyPlaceholders(String text, Placeholder[] placeholders) {
    if (placeholders == null || placeholders.length == 0) return text;
    for (var p : placeholders) {
      text = text.replace("%" + p.key() + "%", p.value());
    }
    return text;
  }

  /**
   * Populates {@link #merged} using the three-level fallback chain. Later levels take priority, so
   * user-override properties are loaded last and overwrite JAR-provided defaults.
   */
  private void load() {
    // Level 3: ultimate JAR fallback — en_US
    loadFromJar("messages_en_US.properties");

    // Level 2: locale-specific JAR bundle (no-op if locale == en_US or file missing)
    if (!"en_US".equals(locale)) {
      loadFromJar("messages_" + locale + ".properties");
    }

    // Level 1: user-override file on disk (highest priority)
    loadFromDisk(locale);
  }

  private void loadFromJar(String resourceName) {
    try (InputStream in = pluginClassLoader.getResourceAsStream(resourceName)) {
      if (in == null) return;
      try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        merged.load(reader);
      }
    } catch (IOException e) {
      if (logger != null) {
        logger.warning("[I18n] Failed to load bundled " + resourceName + ": " + e.getMessage());
      }
    }
  }

  private void loadFromDisk(String locale) {
    var localesDir = new File(baseDir, LOCALES_DIR);
    var overrideFile = new File(localesDir, "messages_" + locale + ".properties");
    if (!overrideFile.exists()) return;
    try (var reader =
        new InputStreamReader(new FileInputStream(overrideFile), StandardCharsets.UTF_8)) {
      merged.load(reader);
    } catch (IOException e) {
      if (logger != null) {
        logger.warning(
            "[I18n] Failed to load override " + overrideFile.getName() + ": " + e.getMessage());
      }
    }
  }
}
