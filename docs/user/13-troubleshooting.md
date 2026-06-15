# 13. Troubleshooting

This page covers the issues admins hit most often when running CommandPrompter in production, where to look in the source to confirm a diagnosis, and how to fix the configuration that caused it. Every fix here is a config change; nothing requires recompiling the plugin.

For the underlying config options see [04-config-reference.md](04-config-reference.md). For the prompt tag and screen systems see [05-prompt-syntax.md](05-prompt-syntax.md) and [06-screens.md](06-screens.md).

## Turning on debug logging

The fastest way to diagnose most problems is to enable CommandPrompter's debug logger:

```yaml
# in config.yml
Debug-Mode: true
```

With `Debug-Mode: true`, the plugin logs every command intercept, hook init, prompt submission, and PCM dispatch. Debug lines are tagged with the calling class name (e.g. `[PromptEngine]`, `[ScreenManager]`) so you can filter them. The plugin uses a dedicated `PluginLogger` with a `<PluginPrefix>-Debug` gradient prefix; lines are emitted at `INFO` level on the server logger (not a separate file), so they show up alongside other plugin output.

`Debug-Mode` and `Fancy-Logger` are both reload-aware — `/commandprompter reload` picks up changes immediately.

## Common symptoms and fixes

### Prompts are intercepted but the dialog/anvil/sign never appears

**Symptom.** The player types a command with a prompt tag (e.g. `/give <d:Why?> stone`), the command is cancelled (the original `/give` does not run), but no screen opens.

**Likely cause.** The dialog/anvil/sign NMS provider is missing or the screen key maps to a screen that fell back to chat. Run with `Debug-Mode: true` and look for one of:

- `[DialogPromptScreen] - No NMS provider; falling back to chat` — the dialog NMS implementation failed to load; the prompt becomes a chat prompt. This means the bundled `prompt-ui-26.1` artifact is not on the classpath or the version is mismatched.
- `[AnvilPromptScreen] - No NMS provider; falling back to chat` — same fallback for the anvil screen.
- `[SignPromptScreen] - No NMS provider; falling back to chat` — same for the sign screen.

**Fix.** Make sure you are running on the matching Paper version. The shadow JAR bundles the right NMS artifact for its target version — if you are on a different version, the NMS screen provider will refuse to load and the plugin falls back to chat.

### Dialog shows "No options available" or "Too many options (N)"

**Symptom.** A `<d:tab:Display>` row shows a yellow notice instead of a button grid.

**Cause.** The number of completions returned for the partial command is zero (no targets) or above `DialogUI.Defaults.Tab.MaxButtons` (default 5, configurable up to 256). See [12-dialogs-compound.md](12-dialogs-compound.md#tab--completion-driven-multi-choice).

**Fix.** Raise the per-row threshold with `<d:tab[N]:Display>` (e.g. `<d:tab[10]:Display>` to allow up to 10 buttons) or lower the `DialogUI.Defaults.Tab.MaxButtons` default in `prompt-config.yml`. A high `MaxButtons` is fine for a handful of completions; if the command's tab-completion returns hundreds of players on a busy server, the text-input fallback is usually the better experience.

### A dialog re-opens immediately after Confirm

**Symptom.** The player fills in the dialog, hits Confirm, and the same dialog appears again with no error message.

**Cause.** Malformed compound payload. The server decoded the payload, the row count did not match, and the dialog re-opens with a warning. Look in the log for:

```
[ScreenManager] - Malformed compound payload from dialog for <player>: <payload>
```

**Fix.** This usually means a plugin intercepted the answer and modified it before the plugin could read it. If you have a chat-preprocessor or input-disabler plugin, check its priority — it must run at `LOWEST` or earlier for CommandPrompter to see the original input.

### Commands run before the prompt is finished

**Symptom.** A player is part-way through a multi-prompt session and types `/something` — the command runs anyway, interrupting the session.

**Cause.** The command name is in the `Allowed-Commands-In-Prompt` list, or the config is empty (which denies everything except plugin commands) and the command still ran. The latter usually means a `PlayerCommandPreprocessEvent` listener at a higher priority is interfering.

**Fix.** Add the command name to `Allowed-Commands-In-Prompt` in `config.yml` if the player should be able to run it mid-session, or leave the list empty to deny all non-plugin commands while a screen is open. The plugin's own commands (`/commandprompter`, `/consoledelegate`, `/playerdelegate`) and the `ignored-commands` list always bypass the deny.

### Hooks do not activate

**Symptom.** A target plugin (PremiumVanish, Towny, etc.) is installed but CommandPrompter does not pick it up. Vanished players still show in `p:`, Towny filter does not work, PlaceholderAPI substitutions are missing.

**Cause.** The hook is silently skipped if the target plugin is not enabled when CommandPrompter enables. With `Debug-Mode: true`, look for:

```
[HookContainer] - Skipping <HookName>: <pluginName> not installed
```

This is normal if the dependency is missing. If the plugin **is** installed, check:

- **Load order.** The target plugin must load **before** CommandPrompter. `paper-plugin.yml` declares all 9 target plugins as `load: AFTER` and `required: false`. If a load-order plugin manager is reversing this, the target plugin is not enabled at the time of `initHooks()` and the hook is skipped.
- **Plugin name spelling.** The `@TargetPlugin(pluginName=...)` value must match the target plugin's exact Bukkit plugin name. A renamed target (e.g. a fork distributed under a different name) will not match.
- **Hook construction failure.** The error log shows `Failed to construct hook <HookName>: <message>`. This usually means the target plugin's API changed and the hook's reflection call broke.

**Fix.** Reload CommandPrompter **after** the target plugin is enabled. Run `/commandprompter reload` or restart the server with the target plugin in the `plugins/` folder. If the hook still fails to construct, check the server log for the exception stack trace.

### PlaceholderAPI substitutions are missing in dialogs/PCMs

**Symptom.** `%player_name%` and other PlaceholderAPI placeholders are not resolved in dialog body text, validator JS expressions, or delegate command bodies.

**Cause.** PlaceholderAPI is not installed, or the hook is not loaded. Look for the `[HookContainer]` log line for `PapiHook` (it should be `✓ PapiHook hooked`).

**Fix.** Install PlaceholderAPI, restart the server, then `/commandprompter reload`. PapiHook calls `PlaceholderAPI.setPlaceholders(player, text)` from inside `JSExprValidator` and from the delegate command dispatcher.

### Players see "You don't have permission" on a prompt command

**Symptom.** A player tries to run a command with a prompt tag and gets a permission error from the underlying command, or the prompt never starts and the command runs as if the tag is not there.

**Cause.** `Enable-Permission: true` and the player lacks `promptpaper.use`. With this config, the prompt system does not intercept the command for that player — the command runs as a vanilla command (and the prompt tag is treated as part of the command line). See [09-permissions.md](09-permissions.md) for the full permission matrix.

**Fix.** Either grant `promptpaper.use` to the player, or set `Enable-Permission: false` (the default) to let any player use the prompt system. Note: `Enable-Permission` only gates the prompt system; it does not gate the underlying command. A player with `promptpaper.use` still needs the underlying command's permission for the assembled command to dispatch.

### The command works but the assembled output looks wrong

**Symptom.** The dialog/anvil/sign collects the answer correctly, the command dispatches, but the assembled command line has stray characters, empty spaces, or the wrong number of arguments.

**Cause.** Almost always a parser-level misunderstanding:

- `{0}` references in the **main command body** are not expanded — the `{N}` syntax only works in post-commands (`<!...>` / `!!...`). See [11-post-commands.md](11-post-commands.md).
- `d:tab` inside a compound block (`<d:d:tab:X && ...>`) is rejected with `IllegalArgumentException: d:tab is not allowed in compound prompt tags`. The error surfaces as the dialog never opening. See [12-dialogs-compound.md](12-dialogs-compound.md).
- An `&` in the answer gets sanitized to a MiniMessage tag or color code by default. To preserve the literal `&`, add `-ds` (disable sanitize) to the tag.

**Fix.** Check the parser behavior in [05-prompt-syntax.md](05-prompt-syntax.md) and the compound rules in [12-dialogs-compound.md](12-dialogs-compound.md). The plugin's debug log shows the assembled command line after all answers are substituted — search for `Session complete, dispatching:` in the log.

### Log noise from prompt intercepts

**Symptom.** The debug log fills with `Command received: ...` lines for every command a player types.

**Fix.** This is the `PlayerCommandListener` debug line. It is on by design when `Debug-Mode` is true. To reduce noise:

- Set `Debug-Mode: false` in `config.yml` and reload.
- If you only need debug for one feature, leave `Debug-Mode: false` and add a targeted listener (Bukkit's `org.bukkit.plugin.PluginLogger` filters by class name, or use a log management plugin).

### Internal commands (`/cmdp`, `/cd`, `/pd`) appear in tab completion

**Symptom.** Players see CommandPrompter's internal commands in their tab-completion list, which is undesirable on a public server.

**Cause.** `Command-Tab-Complete: true` in `config.yml` (the default). The plugin hides these commands from tab-completion automatically when this is `false`.

**Fix.** Set `Command-Tab-Complete: false` and reload. The `CommandSendListener` will strip `commandprompter`, `cmdp`, `consoledelegate`, `cd`, `playerdelegate`, `pd` from the player's `PlayerCommandSendEvent`.

### Server crashes / no log output on startup

**Symptom.** The plugin enables silently but no log line appears, or the plugin's `onEnable` throws an exception.

**Cause.** Almost always a malformed config file — an unknown key, an invalid value, or a YAML syntax error. The plugin disables itself in `onEnable()` if any subsystem fails.

**Fix.** Run with `Debug-Mode: true` and look for the `Failed to enable CommandPrompterPaper:` line. The exception is logged at `SEVERE` level. Common causes:

- `Argument-Regex` is not a valid regex pattern — the plugin disables the config loader.
- `Prompt-Timeout` is not a non-negative integer.
- A required YAML section (e.g. `screen-mappings`) has a value that does not match a `ScreenType` enum name.
- A `@Match`-constrained field (e.g. `SignUI.Material`) has a value that does not match its regex (the default is `(.*SIGN.*)`).

Restore the config from the plugin's bundled default by deleting `config.yml` and `prompt-config.yml` from the plugin's data folder, then restarting. The plugin will regenerate the defaults.

### Dialog "body" is empty

**Symptom.** The dialog opens with only the input field and confirm/cancel buttons, no body text.

**Cause.** The display text (`:`) of the prompt tag is empty. Empty display text is treated as "no body" by the dialog builder — not an error.

**Fix.** Set a display text on the tag, e.g. `<d:Why?:Pick a reason>`.

## When to file an issue

If the troubleshooting steps above do not resolve the issue, gather the following before opening a bug report:

1. **Plugin version** — `version` output, e.g. `CommandPrompterPaper v3.0.0`.
2. **Server version** — Paper build number, e.g. `Paper 1.21.4 #123`.
3. **Target plugin versions** — for hook issues, the exact version of the missing/misbehaving plugin.
4. **Reproduction** — the exact command line the player ran.
5. **Debug log** — the relevant section of the log with `Debug-Mode: true`. Strip any unrelated server noise.

The plugin's source of truth is in the repo's `prompt-paper` and `prompt-core` subprojects. The plugin runs `bStats` with id `5359` — disable it via `plugins/bStats/config.yml` if you need to opt out.

## See also

- [04-config-reference.md](04-config-reference.md) — every config key, default, and constraint
- [05-prompt-syntax.md](05-prompt-syntax.md) — tag shape, escape rules, post-commands
- [06-screens.md](06-screens.md) — screen routing, fallback chain, dialog kinds
- [08-hooks.md](08-hooks.md) — supported target plugins, hook lifecycle, filter syntax
- [12-dialogs-compound.md](12-dialogs-compound.md) — dialog kinds, compound syntax, answer encoding
