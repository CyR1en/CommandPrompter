# 01 — Install

This page covers installing CommandPrompter on a Paper server, verifying the install, and where to find the generated configuration files.

## Requirements

| Requirement | Version |
|---|---|
| Server software | PaperMC (or a PaperMC fork) |
| Java runtime | The build toolchain is JDK 25. Run the resulting JAR on the JDK version required by your Paper version. |
| Plugin loader | Paper's built-in plugin loader. No other loader required. |
| Folia | Supported. The plugin declares `folia-supported: true` and uses Folia's global region scheduler for all task scheduling. |

The plugin targets Paper API version `26.1.2` (per `paper-plugin.yml`).

## Install steps

1. Download the latest release JAR. Releases are published on GitHub for this repository.
2. Stop your server if it is running.
3. Copy the JAR to your server's `plugins/` directory.
4. Start the server.
5. On first start, CommandPrompter creates a `plugins/CommandPrompterPaper/` directory and writes the default configuration files.

The generated files on first run:

- `plugins/CommandPrompterPaper/config.yml` — top-level plugin settings (timeouts, permissions, debug, permission attachments) **and** the user-facing `Messages:` block. One file, two record-backed sections.
- `plugins/CommandPrompterPaper/prompt-config.yml` — UI layout, screen mappings, and prompt tag defaults.

See [04-config-reference.md](04-config-reference.md) for the full list of options.

## Verifying the install

After start, check the server log for a line like:

```
[CommandPrompterPaper] CommandPrompterPaper v3.0.0 enabled.
```

If the line is missing or replaced with an error, see [13-troubleshooting.md](13-troubleshooting.md).

You can also confirm the install by running the version command in-game or from the server console:

```
/commandprompter version
```

This requires the `promptpaper.version` permission, which operators have by default. See [03-commands.md](03-commands.md) for the full command list and [09-permissions.md](09-permissions.md) for the full permission table.

## What gets enabled on first start

The first start also initializes the bundled plugin integrations (hooks). Each hook is opt-in by way of the target plugin being installed on your server: if a hook's target plugin is not present, the hook stays inactive. See [08-hooks.md](08-hooks.md) for the full list and what each one does.

The plugin reports anonymous usage statistics to bStats (plugin id `5359`) by default. bStats is enabled at startup; see the bStats configuration page if you need to opt out.

## Updating

1. Stop the server.
2. Replace the existing JAR in `plugins/` with the new one.
3. Start the server. New configuration keys are added to the YAML files with their default values; existing keys are preserved.

You do not need to delete `plugins/CommandPrompterPaper/` between upgrades.

## Uninstalling

1. Stop the server.
2. Delete `CommandPrompterPaper-*.jar` from `plugins/`.
3. Optionally delete the `plugins/CommandPrompterPaper/` directory if you want to remove all configuration and message data.

## Next steps

- New to prompts? Read [02-quickstart.md](02-quickstart.md) for a five-minute first-prompt walkthrough.
- Looking for a specific command? See [03-commands.md](03-commands.md).
- Looking for a specific config option? See [04-config-reference.md](04-config-reference.md).
