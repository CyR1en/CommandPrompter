# 14. What's new in v3

CommandPrompter v3 is a full rewrite of the plugin against a modern Paper plugin layout. This page is a release note — it covers the v3 layout, the visible changes in configuration and commands, and the new capabilities the v3 plugin ships with. It is not a migration guide from earlier versions; if you are upgrading an existing server, see [01-install.md](01-install.md) for setup and [04-config-reference.md](04-config-reference.md) for the configuration surface.

If you want a feature tour, start with [02-quickstart.md](02-quickstart.md). If something is misbehaving, jump to [13-troubleshooting.md](13-troubleshooting.md).

## The shape of v3

v3 is built as a Gradle multi-module project with four subprojects:

| Subproject    | Role                                                          |
| ------------- | ------------------------------------------------------------- |
| `prompt-core`   | Pure library — parsers, sessions, data types, no Paper deps |
| `prompt-paper`  | The Paper plugin itself (the deployable artifact)           |
| `prompt-ui-api` | UI abstraction interfaces (`InputScreen`, `ScreenProvider`) |
| `prompt-ui-26.1` | Concrete UI implementation for Minecraft 1.21.4 via NMS    |

The deployment artifact is a single shadow JAR built by `:prompt-paper:shadowJar` that bundles `prompt-core` and `prompt-ui-26.1`. The UI subproject is packaged in via `zipTree` rather than as a dependency JAR, which keeps the NMS classes off the plugin's API classpath.

The screen provider is loaded at runtime through Java's `ServiceLoader` SPI (`META-INF/services/dev.cyr1en.promptui.ScreenProvider`). Future Minecraft versions can ship their own `prompt-ui-<version>` subproject and be bundled in the same way.

## Paper plugin conventions

v3 follows the modern Paper plugin layout, declared in `paper-plugin.yml`:

- **`bootstrapper:`** `dev.cyr1en.promptpaper.CommandPrompterBootstrap` — the bootstrapper is what lets the plugin manage its own early lifecycle.
- **`api-version:`** pinned to the target Paper API version.
- **`folia-supported: true`** — v3 is officially Folia-compatible. All task scheduling goes through a `Scheduler` abstraction (`dev.cyr1en.promptpaper.util.Scheduler`) whose `PaperScheduler` implementation uses `Bukkit.getGlobalRegionScheduler()` and `Bukkit.getAsyncScheduler()` so the plugin runs unmodified on both Paper and Folia.
- **`dependencies.server:`** — all nine optional integrations (CarbonChat, SuperVanish, PremiumVanish, VanishNoPacket, PlaceholderAPI, LuckPerms, Towny, HuskTowns, WorldGuard) are declared as `load: AFTER, required: false`. They are discovered at runtime via `Bukkit.getPluginManager().isPluginEnabled(...)`.

v2's legacy `plugin.yml` is gone; the modern `paper-plugin.yml` is the only descriptor.

## Two configuration files

v3 splits the configuration into two files:

- **`config.yml`** — CommandPrompterConfig (top-level behavior: `Prompt-Prefix`, `Prompt-Timeout`, `Cancel-Keyword`, `Enable-Permission`, `Debug-Mode`, `Fancy-Logger`, command interception, ignored commands, permission attachments) and MessageConfig (the message strings sent to players and the console).
- **`prompt-config.yml`** — PromptConfig (screen mappings, all UI sub-configs, and `Input-Validation` aliases).

This split was made because the second file is edited rarely (you change UI defaults, validators, and screen mappings only when adding or tuning prompts), while the first file is edited often (prefix, timeouts, ignored commands). Keeping them separate reduces the risk of overwriting one set of changes when the other is reloaded. The reference for both files is [04-config-reference.md](04-config-reference.md).

## Annotated config

Configuration classes in v3 are records whose fields are decorated with annotations:

- `@ConfigPath("file.yml")` — which file the record is loaded from
- `@NodeName("Section.Key")` — the YAML path the field reads from
- `@NodeDefault("value")` — the default if the key is absent from the file
- `@NodeComment({...})` — comments written into the generated file
- `@IntegerConstraint(min = ..., max = ...)` — runtime bounds
- `@Match(regex = "...")` — string pattern check

The loader (`PaperConfigLoader`) parses the YAML, validates the field, and falls back to the default on any failure. A reload reads both files and re-resolves every section, so `/commandprompter reload` picks up edits without a server restart. See [03-commands.md](03-commands.md) for the reload command.

## Commands

v3 separates the three top-level commands:

- **`/commandprompter`** with subcommands `reload`, `cancel`, `version` — meta-commands (see [03-commands.md](03-commands.md)).
- **`/consoledelegate <target> <command...>`** — dispatch the rest of the line as if the target player were the console. See [10-delegation.md](10-delegation.md).
- **`/playerdelegate <target> <permissionKey> <command...>`** — same idea, but with a temporary permission attachment configured under `Permission-Attachment.Permissions.<key>`. See [10-delegation.md](10-delegation.md).

All three are permission-gated. The permission set is in [09-permissions.md](09-permissions.md).

## Prompt syntax

The single-tag form `<key[:filter][:display]>` is unchanged. v3 adds a compound form that turns one tag into a multi-row dialog:

```yaml
/transfer <d:d:num[1,1000,1]:Amount && d:tab:Target && d:text[120]:Reason> confirm
```

The full syntax, escape rules, and tag-level flags are in [05-prompt-syntax.md](05-prompt-syntax.md). The dialog screen and compound-tag semantics are in [12-dialogs-compound.md](12-dialogs-compound.md).

### What's new in the syntax

- **Compound prompt tags** — the `&&` syntax lets one tag drive a multi-row dialog. v3 strips block-level flags (`-ds`, `-iv:alias`, `-int`, `-str`) before splitting on `&&`, so flags written inside a sub-tag are hoisted to the block level and apply to every sub-answer.
- **The `d:tab` kind** — a single-row dialog kind that turns tab-completion results into a multi-action button grid, with a text-input fallback when completions are absent or exceed `DialogUI.Defaults.Tab.MaxButtons`.
- **The `d:num[...]`, `d:choice[...]`, `d:text[...]` kinds** — bounded numeric input, button grid, and length-bounded text input. The bracket-depth rule means `&&` inside `[...]` is part of the filter content, not a row separator.
- **RS/US answer encoding** — multi-row dialog answers are joined with the unit separator (`\u001F`) and wrapped with the record separator (`\u001E`) so a single command argument can carry several values.

## Post-commands and answer references

v3 supports post-commands that run after a prompt session completes (or is cancelled). The marker `<!cmd>` runs on completion, `<!!cmd>` on cancellation. The `:N` syntax schedules the post-command `N` ticks later. The `@console` / `@player` prefix sets the executor. The `{N}` references inside the body resolve against the session's answer list in order.

The full reference is in [11-post-commands.md](11-post-commands.md).

## Hooks

v3 hard-codes nine integration hooks in this order, each gated on the corresponding plugin being present:

`PremiumVanish`, `SuperVanish`, `CarbonChat`, `VanishNoPacket`, `PlaceholderAPI`, `Towny`, `LuckPerms`, `HuskTowns`, `WorldGuard`.

The hooks contribute four kinds of behavior: vanish detection (so heads of vanished players don't show in the player-picker UI), chat listening (CarbonChat, with a fallback to CommandPrompter's own listener), placeholder resolution (PlaceholderAPI for `JS-Expression` validators and delegate commands), and filter completion (LuckPerms, Towny, HuskTowns, WorldGuard register tab-completion filters in the player-picker UI). The full per-hook reference is in [08-hooks.md](08-hooks.md).

## Operational basics

- **Folia-supported** — declared in `paper-plugin.yml`. Use the scheduler abstraction if you write custom integrations.
- **Java runtime** — the plugin runs on JDK 17 or higher. The build toolchain is JDK 25. See [01-install.md](01-install.md).
- **bStats** — metrics are enabled (id 5359). The plugin reports server version, player count, and core feature usage. You can opt out globally via `plugins/bStats/config.yml`.
- **ServiceLoader** — the UI implementation is discovered through `META-INF/services/dev.cyr1en.promptui.ScreenProvider`, not by class name. If you ship a custom `prompt-ui-<version>` subproject for a future Minecraft release, register your screen provider through the same mechanism.

## Where to go next

- [01-install.md](01-install.md) — install and first-run checks
- [02-quickstart.md](02-quickstart.md) — your first prompt tag
- [04-config-reference.md](04-config-reference.md) — every config key, default, and example
- [13-troubleshooting.md](13-troubleshooting.md) — common failure modes and how to read the logs
