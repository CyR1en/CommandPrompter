# Custom Screen Registration & Developer Extensibility Guide

CommandPrompter allows third-party plugin developers to register custom interactive prompt screens at runtime using the `CommandPrompterAPI`. This allows seamless integration for custom GUI engines, item frameworks, player selectors, or confirmation workflows directly in command prompts (e.g. `<ecoitem:Pick weapon -glow>`, `<region:Select region>`).

---

## 1. Adding the Dependency

Add `prompt-ui-api` to your build configuration as a `compileOnly` dependency.

> **Important**: Do **NOT** shade or relocate `dev.cyr1en.promptui.*` classes into your plugin jar. CommandPrompter provides these classes at runtime.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")

    // Project reference when building as part of the CommandPrompter multi-module project:
    compileOnly(project(":prompt-ui-api"))

    // External repository example (when published to a repository):
    // compileOnly("dev.cyr1en.commandprompter:prompt-ui-api:<version>")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    maven 'https://repo.papermc.io/repository/maven-public/'
}

dependencies {
    compileOnly 'io.papermc.paper:paper-api:26.1.2.build.74-stable'

    // Project reference when building as part of the CommandPrompter multi-module project:
    compileOnly project(':prompt-ui-api')

    // External repository example (when published to a repository):
    // compileOnly 'dev.cyr1en.commandprompter:prompt-ui-api:<version>'
}
```

### Maven (`pom.xml`)

```xml
<dependencies>
    <!-- Example dependency definition for prompt-ui-api -->
    <dependency>
        <groupId>dev.cyr1en.commandprompter</groupId>
        <artifactId>prompt-ui-api</artifactId>
        <version>${commandprompter.version}</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

---

## 2. Plugin Descriptor Setup

Declare `CommandPrompterPaper` in your plugin descriptor so that your plugin loads after CommandPrompter is initialized.

### `plugin.yml` (Bukkit / Spigot / Legacy Paper)

```yaml
name: MyCustomPlugin
version: 1.0.0
main: com.example.plugin.MyCustomPlugin
depend: [CommandPrompterPaper]
# Or for optional integration:
# softdepend: [CommandPrompterPaper]
```

### `paper-plugin.yml` (Modern Paper)

In Paper plugin metadata, setting `load: BEFORE` on the dependency specifies that `CommandPrompterPaper` must load before your provider plugin:

```yaml
name: MyCustomPlugin
version: 1.0.0
main: com.example.plugin.MyCustomPlugin
dependencies:
  server:
    CommandPrompterPaper:
      load: BEFORE
      required: true # Set to false if CommandPrompter is an optional soft-dependency
```

---

## 3. Retrieving `CommandPrompterAPI` & Registering Screens

CommandPrompter registers `CommandPrompterAPI` in Bukkit's `ServicesManager` upon enablement. Retrieve it safely in `onEnable()`:

```java
package com.example.plugin;

import dev.cyr1en.promptui.api.CommandPrompterAPI;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class MyCustomPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        RegisteredServiceProvider<CommandPrompterAPI> rsp =
                getServer().getServicesManager().getRegistration(CommandPrompterAPI.class);

        if (rsp != null) {
            CommandPrompterAPI api = rsp.getProvider();
            api.registerScreen(this, "ecoitem", (player, context) -> new EcoItemInputScreen(this, player, context));
            getLogger().info("Registered <ecoitem:...> screen handler with CommandPrompter.");
        } else {
            getLogger().warning("CommandPrompter not found; custom prompt screens will not be registered.");
        }
    }
}
```

---

## 4. Implementing `InputScreen`

Providers implement the `dev.cyr1en.promptui.InputScreen` interface.

### Key Implementation Invariants

1. **Exactly-Once Delivery**: The result callback registered via `onResult` should be invoked at most once per session. Use `AtomicBoolean` to prevent duplicate submissions.
2. **Non-Blocking Factory**: `PromptScreenFactory.createScreen(...)` executes synchronously on the target player's entity scheduler. Never perform blocking I/O, database queries, or synchronous network requests inside the factory.
3. **Safe Opening & Failures**: If opening the UI fails asynchronously (e.g. inventory creation error), invoke the `onOpenFailure` callback to allow CommandPrompter to abort cleanly.
4. **Platform Close vs Delegate Close**: CommandPrompter makes a best-effort `Player.closeInventory()` attempt for online players whose entity scheduler accepts work. It does NOT guarantee closure of Paper dialogs, signs, protocol UIs, arbitrary non-inventory UI, offline players, or retired schedulers. Providers own non-inventory/critical external cleanup and must not depend on `InputScreen.close()` during disable. Third-party delegate `close()` is **not** called during provider disable or host shutdown to prevent classloader errors.

### Minimal `InputScreen` Example

```java
package com.example.plugin;

import dev.cyr1en.promptui.InputScreen;
import dev.cyr1en.promptui.ScreenResult;
import dev.cyr1en.promptui.api.ScreenContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class EcoItemInputScreen implements InputScreen, Listener {

    private final Plugin plugin;
    private final Player player;
    private final ScreenContext context;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private volatile boolean programmaticClose = false;

    private Consumer<ScreenResult> resultCallback;
    private Consumer<Throwable> failureCallback;
    private Inventory inventory;
    private boolean open = false;

    public EcoItemInputScreen(Plugin plugin, Player player, ScreenContext context) {
        this.plugin = plugin;
        this.player = player;
        this.context = context;
    }

    @Override
    public void open() {
        try {
            // Read custom flags passed in prompt tag
            String rarity = context.flagOrDefault("rarity", "common");
            boolean glow = context.booleanFlag("glow");

            inventory = Bukkit.createInventory(null, 9, Component.text(context.displayText()));

            ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("Item (" + rarity + ")", NamedTextColor.AQUA));
                item.setItemMeta(meta);
            }
            inventory.setItem(4, item);

            Bukkit.getPluginManager().registerEvents(this, plugin);
            player.openInventory(inventory);
            this.open = true;
        } catch (Throwable t) {
            this.open = false;
            if (failureCallback != null) {
                failureCallback.accept(t);
            }
        }
    }

    @Override
    public void close() {
        if (!open) return;
        this.open = false;
        this.programmaticClose = true;
        HandlerList.unregisterAll(this);
        player.closeInventory();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void onResult(Consumer<ScreenResult> callback) {
        this.resultCallback = callback;
    }

    @Override
    public void onOpenFailure(Consumer<Throwable> callback) {
        this.failureCallback = callback;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!open || !event.getWhoClicked().equals(player)) return;
        if (!event.getInventory().equals(inventory)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && clicked.getType() != Material.AIR) {
            if (completed.compareAndSet(false, true)) {
                close();
                if (resultCallback != null) {
                    resultCallback.accept(ScreenResult.answer("minecraft:diamond_sword"));
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!open || !event.getPlayer().equals(player)) return;
        if (!event.getInventory().equals(inventory)) return;

        if (programmaticClose) {
            return;
        }

        if (completed.compareAndSet(false, true)) {
            this.open = false;
            HandlerList.unregisterAll(this);
            if (resultCallback != null) {
                resultCallback.accept(ScreenResult.guiExit());
            }
        }
    }
}
```

---

## 5. Tag Syntax & Custom Flag Parsing

Users and server administrators can use your registered key in any CommandPrompter command string:

```text
/giveitem <ecoitem:Pick weapon -glow -rarity:legendary -desc:"Super sword">
```

### Tag Components

- **Key**: `ecoitem` (registered with lowercase-only grammar `^[a-z][a-z0-9_]{0,31}$`; resolved case-insensitively at command execution).
- **Display Text**: `"Pick weapon"` (flags are stripped before display).
- **Custom Flags**: Arbitrary trailing flags accessible via `ScreenContext`:
  - Bare flag: `-glow` &rarr; `context.booleanFlag("glow") == true`
  - Key-value: `-rarity:legendary` &rarr; `context.flag("rarity").get() == "legendary"`
  - Quoted string: `-desc:"Super sword"` &rarr; `context.flag("desc").get() == "Super sword"`

### `ScreenContext` Methods

| Method | Return Type | Description |
| --- | --- | --- |
| `context.key()` | `String` | Canonical lowercase key (e.g. `"ecoitem"`). |
| `context.displayText()` | `String` | Display prompt string with flags stripped. |
| `context.flags()` | `Map<String, String>` | Immutable map of parsed custom flags. |
| `context.flag(name)` | `Optional<String>` | Case-insensitive flag lookup. |
| `context.booleanFlag(name)` | `boolean` | `true` if flag is present and equals `"true"`. |
| `context.hasFlag(name)` | `boolean` | `true` if flag exists in context. |
| `context.flagOrDefault(name, default)` | `String` | Returns flag value or default fallback. |
| `context.sanitize()` | `boolean` | `true` unless `-ds` flag was supplied in tag. |

### Key & Flag Naming Restrictions

- **Key Format**: Registration strictly requires lowercase grammar matching `^[a-z][a-z0-9_]{0,31}$` (starts with lowercase letter, lowercase alphanumeric or underscore, maximum 32 characters).
- **Reserved Keys**: Third-party plugins cannot register built-in keys:
  `""` (chat), `a`, `anvil`, `s`, `sign`, `p`, `player`, `d`, `dialog`, `c`, `confirm`, `confirmation`, `i`, `item`, or `@` (presets).
- **Flag Limits**: Maximum 16 custom flags per tag; maximum 32 characters per flag name; maximum 512 characters per flag value; maximum 1024 characters total across all flag values.

---

## 6. Lifecycle, Concurrency & Guarantees

### API Lifecycle at a Glance

```
Provider onEnable() ──► ServicesManager.getRegistration(CommandPrompterAPI.class)
                    ──► api.registerScreen(plugin, "mykey", factory)
                                     │
Player runs command ──► CommandPrompter parses <mykey:Prompt -flags>
                    ──► PromptScreenFactory.createScreen(player, context) [on entity scheduler]
                    ──► screen.onResult(callback)
                    ──► screen.open() [on entity scheduler]
                                     │
Player interacts    ──► screen delivers ScreenResult to callback (any thread)
                    ──► CommandPrompter hops result to player scheduler
                    ──► Verifies generation tokens & enforces exactly-once completion
                    ──► Advances prompt or executes assembled command
                                     │
Provider onDisable()──► CommandPrompter detects PluginDisableEvent
                    ──► Atomically unregisters and detaches provider tokens
                    ──► Best-effort platform close (Player#closeInventory) on entity scheduler
                    ──► Cancels active sessions with CancelReason.MANUAL
```

### What CommandPrompter Guarantees vs Provider Responsibilities

| Subsystem / Phase | CommandPrompter Guarantee | Provider Responsibility |
| --- | --- | --- |
| **Factory Invocation** | Invoked synchronously on target player's entity scheduler. | Must remain non-blocking; do not execute synchronous network or database I/O. |
| **Result Threading** | Result callbacks may be invoked from any thread context; CommandPrompter reschedules onto the player's scheduler. | Guard callbacks to deliver at most once per session. |
| **Token Verification** | Stale callbacks from previous prompt steps or cancelled sessions are discarded. | Unregister listeners when UI closes. |
| **Error Boundaries** | Exceptions in `createScreen` or `open()` are caught and safely abort the session with `CancelReason.ERROR`. | Handle internal UI initialization errors gracefully when possible. |
| **Provider Teardown** | When provider plugin disables or explicitly unregisters, CommandPrompter detaches all registrations, unlinks active handles, cancels active sessions with `CancelReason.MANUAL`, and makes a best-effort `Player.closeInventory()` attempt for online players whose entity scheduler accepts work. It does NOT guarantee closure of Paper dialogs, signs, protocol UIs, arbitrary non-inventory UI, offline players, or retired schedulers. | **Important:** Providers own non-inventory/critical external cleanup and must NOT rely on `InputScreen.close()` during disable, as CommandPrompter avoids calling third-party plugin methods to prevent classloader errors. |

### Manual Unregistration
You can manually unregister screens at any time:
```java
api.unregisterScreens(this);
```

---

## 7. Fail-Closed Security & Error Boundaries

CommandPrompter enforces a strict **fail-closed** policy:
- **Unregistered Keys**: If a command uses `<unknown:Text>` and no provider is registered for `unknown`, session creation is aborted and an administrative error is logged. The command will **never** silently fall back to chat.
- **Malformed Flags**: Unbalanced quotes (`-desc:"unclosed`) or duplicate flags (`-rarity:rare -rarity:epic`) cause parse failure and session abort.
- **Provider Exceptions**: Any uncaught exception thrown in `createScreen()`, `open()`, or during event handling is caught by CommandPrompter's error boundary, safely cancelling the session with `CancelReason.ERROR` without crashing the server.

---

## 8. Troubleshooting

| Symptom | Cause | Solution |
| --- | --- | --- |
| `IllegalArgumentException: Screen key '...' must be lowercase` | Key does not match `^[a-z][a-z0-9_]{0,31}$` or contains uppercase letters/symbols. | Use a valid lowercase identifier (e.g. `"ecoitem"`, `"my_prompt"`). |
| `IllegalArgumentException: Key '...' is reserved` | Key collides with a built-in prompt type (e.g. `"anvil"`, `"dialog"`, `"confirm"`). | Choose a distinct custom key prefix. |
| `IllegalArgumentException: Key already registered` | Another plugin has already registered the same key. | Use a unique namespace or coordinate with the other plugin. |
| `ServicesManager.getRegistration(CommandPrompterAPI.class)` returns `null` | Plugin loaded before CommandPrompter. | Add `depend: [CommandPrompterPaper]` in `plugin.yml` or `dependencies.server.CommandPrompterPaper: { load: BEFORE }` in `paper-plugin.yml`. |
| Prompt tag aborted with fail-closed warning | Key not registered when command was executed, or duplicate/malformed flags used. | Ensure provider is registered before command execution and verify flag syntax. |
| `ClassCastException` or class loading errors | API classes shaded into provider jar. | Set `compileOnly` on `prompt-ui-api` in `build.gradle.kts` / `pom.xml`. |
