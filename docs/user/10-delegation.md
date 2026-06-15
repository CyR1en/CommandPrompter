# 10 — Delegation

CommandPrompter ships two top-level commands for running a prompted command **on behalf of** a target player, in a sender context other than the target's own. `/consoledelegate` runs the resulting command as the **server console**, bypassing every player permission. `/playerdelegate` runs it **as the target player** with a temporary `PermissionAttachment` granting the permissions of a named group from `config.yml`.

This page goes deep on when to use which command, how the prompt-then-dispatch flow works, and how the permission attachment lifecycle is managed. For the basic reference (signatures, tab-completion, built-in `GAMEMODE` example), see [03-commands.md](03-commands.md#consoledelegate) and [03-commands.md](03-commands.md#playerdelegate).

## When to use each

| Need | Command | Reason |
|---|---|---|
| Run a command that should not depend on the target's perms (admin moderation, scripted announcements, anything that must work on offline-equivalent state) | `/consoledelegate` | Console is the only sender that always has every Bukkit permission. |
| Run a command **as the target** but need them to have permissions they do not normally have (gamemode, fly, tpa) | `/playerdelegate` | A short-lived `PermissionAttachment` is added to the target just for the dispatch. |
| Run a command that needs to **prompt the target** (turn `/warn Steve <a:Why?>` into an interactive session with Steve answering "Why?") | Either | Both commands prompt the target. They differ only in the dispatcher used to run the final command. |
| Run a command that should appear to come from the target in chat (other plugins read `PlayerCommandPreprocessEvent`) | `/playerdelegate` | Bukkit's dispatcher will attribute the command to the target. |

## `/consoledelegate` mechanics

Source: `ConsoleDelegateCommand.java`. Senders are restricted to `ConsoleCommandSender` via the `senderFilter` argument in the `PromptCommand` base constructor.

The executor:

1. Resolves `<target>` via the `ArgumentTypes.player()` selector. Plain names and selectors (`@a`, `@p`, `@r`, `[team=red]`) all work; the resolver returns the first match. If multiple match, only the first is used.
2. Strips a leading `/` from `<command...>` so the session log and any dispatch path see the same string.
3. Substitutes every occurrence of `%target_player%` with the target's name.
4. Logs `sender used /consoledelegate -> target: command` and a debug line with the target's UUID and `mode=CONSOLE`.
5. Calls `ScreenManager.startDelegatedSession(target, command, DispatchMode.CONSOLE, null)`.

`startDelegatedSession` parses the command line. Two paths:

- **No prompt tags in the command** — the command is dispatched immediately as the console. No session is started; nothing is shown to the target.
- **Prompt tags in the command** — a normal session is started on the target's behalf, but the dispatch mode is recorded as `CONSOLE`. When the target submits every prompt, the assembled command is dispatched by `Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ...)`, not by `player.performCommand(...)`.

The console dispatcher path runs the command synchronously on the server's main thread via the `Scheduler` (Bukkit scheduler). The target's client never sees a `/` prefix because the command is rewritten into the post-prompt `assembledCommand` (which has no leading slash), then `Bukkit.dispatchCommand(consoleSender, toExecute)` runs it.

### The `%target_player%` placeholder

`%target_player%` is replaced **once, before parsing**. It is not a session-time placeholder and is not affected by PlaceholderAPI. To insert a PlaceholderAPI value into a prompt, use `<a:%player_name%>` or pass it through the validator/JSE pipeline (see [07-validators.md](07-validators.md)).

The substitution is `String.replace("%target_player%", target.getName())`. It is case-sensitive, scoped to the substring `%target_player%`, and happens after the leading `/` is stripped. Putting `%target_player%` outside the visible command text (e.g. inside a comment) still works, but the replacement target is the literal token, so adjacent characters are not affected.

### Example

Console side:

```
/consoledelegate Steve /warn %target_player% <a:Why?>
```

After parsing, the target (`Steve`) sees an anvil asking "Why?". On submit with `because he was mean`, the console dispatcher runs:

```
warn Steve because he was mean
```

The `warn` plugin receives `Steve` as the target and `because he was mean` as the reason. Steve's client never sends that final command — it is dispatched by the server.

## `/playerdelegate` mechanics

Source: `PlayerDelegateCommand.java`. Senders are restricted to `ConsoleCommandSender`.

The executor:

1. Resolves `<target>` via `ArgumentTypes.player()`, same as `/consoledelegate`.
2. Strips a leading `/` and substitutes `%target_player%` (same logic as console).
3. Tab-completes `<permissionKey>` from every key under `Permission-Attachment.Permissions.*` in `config.yml`, plus a synthetic `NONE` entry. See [Permission-Attachment config](#permission-attachment-config) below.
4. Looks up the permissions for the key via `CommandPrompterConfig.getPermissionAttachment(permKey)`. If the lookup returns zero permissions, the sender gets a red error (`<red>Unknown permission key: <key></red>`) and no session starts. This treats missing keys and keys-with-empty-lists identically.
5. Logs the use (`permKey=GAMEMODE perms=3 mode=ATTACHMENT` etc.) and calls `ScreenManager.startDelegatedSession(target, command, DispatchMode.ATTACHMENT, permKey)`.

The dispatch path is **as the target player**, with a temporary `PermissionAttachment`:

1. The plugin calls `player.addAttachment(plugin)` and stores the attachment reference.
2. For every permission in the key's list, it calls `attachment.setPermission(perm, true)`.
3. It calls `attachment.getPermissible().recalculatePermissions()` so the change is visible immediately.
4. It dispatches the assembled command via `Bukkit.dispatchCommand(player, toExecute)`.
5. In a `finally` block, it calls `player.removeAttachment(attachment)`.

The `Permission-Attachment.ticks` setting in `config.yml` (default `1`) is the **lifetime of the attachment after removal logic starts**, not a delay before removal. In v3 the attachment is removed in a `finally` block on the same scheduler tick as the dispatch, so the value is effectively a no-op for the current dispatcher. The field is kept for forward compatibility — see [13-troubleshooting.md](13-troubleshooting.md) for the rationale and the in-flight ticket.

### `NONE` key

`CommandPrompterConfig.getPermissionKeys()` always returns a synthetic `NONE` key. The dispatcher in `ScreenManager.dispatchWithAttachment` falls back to **console dispatch** if the resolved permission list is empty. Selecting `NONE` at the command line is the documented way to run a prompted command "as the target with no extra permissions" — equivalent to the player typing it themselves, except that the prompt step still runs.

### Example

Console side:

```
/playerdelegate Steve GAMEMODE /gamemode <a:Mode?>
```

After the prompt resolves to `creative`, the plugin attaches `bukkit.command.gamemode`, `essentials.gamemode.survival`, `essentials.gamemode.creative` to Steve, runs `/gamemode creative` as Steve, and immediately removes the attachment. Other plugins that see `PlayerCommandPreprocessEvent` will see Steve as the sender.

## Permission-Attachment config

The schema is part of `config.yml` (see [04-config-reference.md](04-config-reference.md#permission-attachment)). The two relevant keys are:

```yaml
Permission-Attachment:
  ticks: 1
  Permissions:
    GAMEMODE:
      - bukkit.command.gamemode
      - essentials.gamemode.survival
      - essentials.gamemode.creative
    FLY:
      - essentials.fly
```

- `ticks` — declared but not currently consumed in v3's synchronous dispatcher. Default `1`. Keep it set.
- `Permissions` — a map of group key to permission list. The key is the `<permissionKey>` argument; the list is what gets attached. Keys are case-sensitive (YAML preserves case). The plugin does not validate the permission nodes — invalid nodes simply have no effect.

Tab-completion of `<permissionKey>` reads every key under this section on every invocation, so adding a new key and running `/commandprompter reload` makes it immediately available.

The default config ships with `GAMEMODE` only. To add groups, edit `config.yml`, add the key, and run `/commandprompter reload`.

## When prompts are skipped

If the command line after `%target_player%` substitution contains no prompt tags (i.e. it does not match the `Argument-Regex` from `config.yml`, default `<.*?>`), the screen manager short-circuits the prompt step and dispatches immediately. This is true for both delegation commands. The dispatch mode (`CONSOLE` or `ATTACHMENT`) is still honored.

This makes the delegation commands useful even when you do not need prompting. For example, `/consoledelegate @a /say hello` shows "hello" to every online player without ever opening a screen.

## Errors and edge cases

| Situation | Result |
|---|---|
| Sender is a player, not the console | Standard "you do not have permission" response from Brigadier. |
| `<target>` does not match any online player | Brigadier reports the selector resolution failure before any plugin code runs. |
| `<permissionKey>` is missing or empty in config | Red `<red>Unknown permission key: <key></red>` to the sender; no session starts. |
| `Permission-Attachment.Permissions` section is missing entirely | `getPermissionKeys()` returns only `NONE`. Only `NONE` is tab-completable. |
| Target disconnects mid-prompt | The session is cancelled by the standard `PlayerQuitEvent` path; the attachment (if any) is removed in the same tick because the player object is invalid. No command is dispatched. |
| The assembled command itself is invalid (no such command, missing args) | The dispatcher surfaces the error to the target (`<red>Command failed: <msg></red>`). The plugin does not validate the assembled command itself. |

## Next steps

- Need post-commands (run something on prompt completion or cancellation)? See [11-post-commands.md](11-post-commands.md).
- Compound dialogs (one tag, many answers)? See [12-dialogs-compound.md](12-dialogs-compound.md).
- Full permission table? See [09-permissions.md](09-permissions.md).
