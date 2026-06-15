package dev.cyr1en.promptpaper.config;

import dev.cyr1en.promptpaper.CommandPrompter;
import dev.cyr1en.promptpaper.config.annotations.field.*;
import dev.cyr1en.promptpaper.config.annotations.type.ConfigHeader;
import dev.cyr1en.promptpaper.config.annotations.type.ConfigPath;
import dev.cyr1en.promptpaper.config.annotations.type.Configuration;
import dev.cyr1en.promptpaper.config.handlers.ConfigTypeHandlerFactory;
import com.cyr1en.kiso.mc.configuration.base.Config;
import com.cyr1en.kiso.mc.configuration.base.ConfigManager;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Reflection-based YAML configuration loader that bridges SnakeYAML and annotated Java records.
 *
 * <p>For each {@link Configuration}-annotated record class, this manager:
 * <ol>
 *   <li>Creates/updates the YAML file with {@code @NodeDefault} values and {@code @NodeComment} lines.</li>
 *   <li>Reads all {@code @ConfigNode} fields via {@link ConfigTypeHandlerFactory}.</li>
 *   <li>Validates {@code @Match} regex and {@code @IntegerConstraint} bounds.</li>
 *   <li>Instantiates the record through its canonical constructor.</li>
 * </ol>
 *
 * <p>Field ordering matters: the constructor arguments must align with the
 * declared field order in the record class.
 */
public class ConfigurationManager {

    private final ConfigManager configManager;
    private final Logger logger;
    private final Set<Class<?>> sanitizedClasses;

    /**
     * Creates a manager backed by the plugin's data folder.
     *
     * @param plugin the owning plugin (provides data folder and logger)
     */
    public ConfigurationManager(CommandPrompter plugin) {
        this.configManager = new ConfigManager(plugin);
        this.logger = plugin.getLogger();
        this.sanitizedClasses = ConcurrentHashMap.newKeySet();
    }

    /**
     * Loads (or reloads) a configuration from its YAML file and returns an immutable record instance.
     *
     * <p>The class must carry {@link Configuration} and {@link ConfigPath}. On first load,
     * any key missing from the YAML is written with its {@link NodeDefault} value.
     *
     * @param configClass the annotated record class to instantiate
     * @param <T> the config record type
     * @return a fully-populated record instance
     * @throws IllegalArgumentException if the class is missing {@link Configuration}
     * @throws RuntimeException if reflection-based instantiation fails
     */
    public <T> T getConfig(Class<T> configClass) {
        if (configClass.getAnnotation(Configuration.class) == null)
            throw new IllegalArgumentException("Class " + configClass.getSimpleName()
                    + " is missing @Configuration annotation");

        logger.fine("Loading config: " + configClass.getSimpleName());
        var config = initConfigFile(configClass);
        initializeConfig(configClass, config);

        var configValues = new ArrayList<>();
        configValues.add(config);

        for (Field field : configClass.getDeclaredFields()) {
            if (field.getAnnotation(ConfigNode.class) == null) continue;

            var nameAnnotation = field.getAnnotation(NodeName.class);
            String nodeName = nameAnnotation != null ? nameAnnotation.value() : field.getName();

            var handler = ConfigTypeHandlerFactory.getHandler(field.getType());

            if (field.isAnnotationPresent(Match.class)) {
                var matchAnnotation = field.getAnnotation(Match.class);
                var regex = matchAnnotation.regex();
                var pattern = Pattern.compile(regex);
                var res = pattern.matcher(config.getString(nodeName)).matches();
                if (!res) {
                    throw new IllegalArgumentException("Configured value for '" + nodeName
                            + "' does not match required pattern: " + regex
                            + " (got: " + config.getString(nodeName) + ")");
                }
            }

            configValues.add(handler.getValue(config, nodeName, field));
        }
        try {
            var recordConfig = configClass.getDeclaredConstructors()[0].newInstance(configValues.toArray());
            @SuppressWarnings("unchecked") var out = (T) recordConfig;
            return out;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to instantiate config: " + configClass.getSimpleName(), e);
        }
    }

    /** Convenience alias for {@link #getConfig(Class)}; re-reads the YAML from disk. */
    public <T> T reload(Class<T> configClass) {
        return getConfig(configClass);
    }

    /**
     * Writes {@code @NodeDefault} values for any config key that is currently absent.
     *
     * @param configClass the record class whose fields to scan
     * @param config the raw SnakeYAML config to populate
     */
    private void initializeConfig(Class<?> configClass, Config config) {
        for (Field field : configClass.getDeclaredFields())
            initializeField(field, config);
    }

    /**
     * Resolves the YAML file, sanitizes existing comment colons, and returns the Config handle.
     *
     * @param configClass the annotated class (used for {@link ConfigPath} and {@link ConfigHeader})
     * @return the SnakeYAML Config ready for reading/writing
     */
    private Config initConfigFile(Class<?> configClass) {
        var pathAnnotation = configClass.getAnnotation(ConfigPath.class);
        var filePath = pathAnnotation == null ? configClass.getSimpleName() : pathAnnotation.value();

        var headerAnnotation = configClass.getAnnotation(ConfigHeader.class);
        var header = headerAnnotation == null ? new String[]{configClass.getSimpleName(), "Configuration"} :
                wrapComments(headerAnnotation.value(), 50);
        var headerSanitized = sanitizeYamlComments(header);

        var configFile = configManager.getConfigFile(filePath);
        if (configFile != null && configFile.exists()
                && !sanitizedClasses.contains(configClass)) {
            sanitizeExistingFile(configFile);
            sanitizedClasses.add(configClass);
        }

        return configManager.getNewConfig(filePath, headerSanitized);
    }

    /**
     * Word-wraps comment lines so no line exceeds {@code maxWidth} characters.
     *
     * @param comments raw comment strings
     * @param maxWidth maximum character count per line
     * @return wrapped lines (null entries preserved)
     */
    static String[] wrapComments(String[] comments, int maxWidth) {
        var result = new ArrayList<String>();
        for (var line : comments) {
            if (line == null || line.length() <= maxWidth) {
                result.add(line);
                continue;
            }
            var words = line.split(" ");
            var sb = new StringBuilder();
            for (var word : words) {
                if (sb.length() + word.length() + 1 > maxWidth && !sb.isEmpty()) {
                    result.add(sb.toString().stripTrailing());
                    sb = new StringBuilder();
                }
                sb.append(word).append(" ");
            }
            if (!sb.isEmpty())
                result.add(sb.toString().stripTrailing());
        }
        return result.toArray(new String[0]);
    }

    /**
     * Writes a single field's default value and comment to the YAML if the key is missing.
     *
     * @param field the annotated record field
     * @param config the raw SnakeYAML config
     */
    private void initializeField(Field field, Config config) {
        if (field.getAnnotation(ConfigNode.class) == null) return;

        var nameAnnotation = field.getAnnotation(NodeName.class);
        var nodeName = nameAnnotation != null ? nameAnnotation.value() : field.getName();

        var handler = ConfigTypeHandlerFactory.getHandler(field.getType());

        var defaultAnnotation = field.getAnnotation(NodeDefault.class);
        var nodeDefault = defaultAnnotation != null ? handler.getDefault(field) : null;

        var commentAnnotation = field.getAnnotation(NodeComment.class);
        var nodeComment = commentAnnotation != null ?
                sanitizeYamlComments(wrapComments(commentAnnotation.value(), 50)) : new String[]{};

        if (nodeDefault != null && config.get(nodeName) == null) {
            handler.setValue(config, nodeName, nodeDefault, nodeComment);
            config.saveConfig();
        }
    }

    /**
     * Replaces {@code ": "} with {@code " - "} in comment strings to prevent YAML key-value ambiguity.
     *
     * @param comments raw comment lines
     * @return sanitized copies
     */
    static String[] sanitizeYamlComments(String... comments) {
        var result = new String[comments.length];
        for (int i = 0; i < comments.length; i++) {
            var c = comments[i];
            if (c == null) { result[i] = null; continue; }
            result[i] = c.replace(": ", " - ");
        }
        return result;
    }

    /**
     * Rewrites an existing YAML file in-place to fix colon-containing comment lines.
     *
     * <p>Called once per config class on the first load after the manager is created.
     *
     * @param file the YAML file to sanitize
     */
    private void sanitizeExistingFile(File file) {
        try {
            var lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            var modified = false;
            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i);
                if (line.startsWith("# ")) {
                    var sanitized = sanitizeYamlCommentLine(line);
                    if (!sanitized.equals(line)) {
                        lines.set(i, sanitized);
                        modified = true;
                    }
                }
            }
            if (modified) {
                Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
                logger.info("Sanitized YAML comments in " + file.getName());
            }
        } catch (IOException e) {
            logger.warning("Failed to sanitize " + file.getName() + ": " + e.getMessage());
        }
    }

    private static String sanitizeYamlCommentLine(String line) {
        var text = line.substring(1);
        return "#" + text.replace(": ", " - ");
    }
}
