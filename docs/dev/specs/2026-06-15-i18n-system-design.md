# CommandPrompter v3 Internationalization (i18n) Design

## 1. Overview
The goal of this system is to provide a robust, safe, and straightforward internationalization (i18n) solution for CommandPrompter v3. It operates on a shared generic core module (`prompt-core`) and implements platform-specific formatting in `prompt-paper`. It relies on standard Java `.properties` files, supports MiniMessage and PlaceholderAPI, and guarantees fallback safety to prevent runtime exceptions.

## 2. Core Architecture & Storage
- **Module Separation**: 
  - `prompt-core`: Houses `AbstractI18n<T, C>` and `Placeholder` record. Manages loading `ResourceBundle`s and custom variable replacement. Requires the platform to provide `File baseDir` and `ClassLoader pluginClassLoader` for reading localized assets.
  - `prompt-paper`: Houses `PaperI18n extends AbstractI18n<Component, Player>` which implements formatting via PlaceholderAPI and MiniMessage.
- **Format**: Standard Java `.properties` files.
- **Syntax**: Placeholders utilize `%placeholder%` syntax (e.g. `%count%`). MiniMessage tags retain their standard `<tag>` syntax.
- **Server-Wide Language**: Driven by a single `locale` key in `config.yml` (e.g., `locale: "en_US"`).
- **Directory Structure**:
  - Internal defaults are stored in the plugin JAR (`/messages_en_US.properties`, `/messages_es_ES.properties`, etc.).
  - User-overriden properties are stored in `plugins/CommandPrompter/locales/`.
- **Loading & Fallback Chain**:
  When a string is requested, the system searches in the following order:
  1. `plugins/CommandPrompter/locales/messages_<locale>.properties` (User overrides)
  2. `jar://messages_<locale>.properties` (Plugin provided translation)
  3. `jar://messages_en_US.properties` (Ultimate fallback)
- **Encoding**: Java 25 handles UTF-8 `.properties` natively. No legacy ISO-8859-1 conversion is needed.

## 3. Developer API (DX)
- **Access Pattern**: String-based keys via an injected `I18n` service.
- **Method Signatures**: 
  - `T get(String key, C context, Placeholder... placeholders)`
  - `T get(String key, Placeholder... placeholders)` (null context)
- **Formatting Flow**:
  1. `PaperI18n` (if context is non-null and PlaceholderAPI is installed) replaces PAPI variables.
  2. `AbstractI18n` replaces custom `%placeholder%` variables with their values.
  3. `PaperI18n` parses the string into a MiniMessage `Component`.
- **Usage Example**:
  ```java
  Component msg = i18n.get("dialog.too_many_options", player, Placeholder.of("count", "5"));
  player.sendMessage(msg);
  ```

## 4. Safety & Robustness
- **Missing Keys**: If a key is completely absent from all levels of the fallback chain, the system will NOT throw a `MissingResourceException`. Instead, it will return a generic fallback string: `<missing translation: [key]>`.
- **PAPI Injection Security**: PAPI formatting executes *strictly before* custom `%placeholder%` substitutions. This ensures that user-injected text in custom variables cannot inadvertently trigger PAPI evaluations.
- **PAPI Soft Dependency**: Safely checked via Bukkit's PluginManager to avoid `NoClassDefFoundError` when PlaceholderAPI is missing.
- **Reloading**: The `I18n` service exposes a `reload()` method, allowing server administrators to refresh the properties files from disk without restarting the server.
