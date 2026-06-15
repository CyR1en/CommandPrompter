# 03 — Commands

CommandPrompter registers a small set of top-level commands. All commands are implemented as Paper Brigadier commands.

## `/commandprompter`

Composite root command. Has no permission gate on the root itself; each subcommand enforces its own permission.

| Subcommand | Permission | Purpose |
|---|---|---|
| `/commandprompter reload` | `promptpaper.reload` | Reload `config.yml` and `prompt-config.yml` from disk. |
| `/commandprompter cancel` | `promptpaper.cancel` | Cancel the executing player's own active prompt session. |
| `/commandprompter version` | `promptpaper.version` | Print the running plugin version. |

Alias: `/cmdp`. Run `/commandprompter` with no arguments to see the help text.

### `/commandprompter reload`

Reloads every configuration record from disk. Before reloading, the plugin cancels every active session on every online player — this prevents stale sessions from holding references to old config values.

```
/commandprompter reload
```

On success:

```
Configuration reloaded.
```

On failure, the sender gets a red error message containing the underlying exception message. The plugin does not crash on reload failure; the previous config stays in memory.

**Note**: reloading does not re-init hooks. If you add or remove an integration plugin and want CommandPrompter to pick it up, fully restart the server.

### `/commandprompter cancel`

Cancels the executing player's own active prompt session. Console senders get an error; players without an active session get a polite notice.

```
/commandprompter cancel
```

For a player with an active session:

```
Prompt cancelled.
```

For a player without an active session:

```
You have no active prompt.
```

For a console sender:

```
Only players can cancel prompts.
```

### `/commandprompter version`

Sends the running plugin version to the sender.

```
/commandprompter version
```

Output (the version is from the plugin metadata; the value below is illustrative):

```
CommandPrompterPaper v3.0.0
```

## `/consoledelegate`

Runs a prompted command on behalf of a target player, but the command is dispatched as the **console**, not as the target player. Useful for admin scripts that need to execute a command in the target's context (chat messages, world state) but should bypass player permissions.

**Permission**: `promptpaper.consoledelegate`
**Sender**: Console only.

Signature:

```
/consoledelegate <target> <command...>
```

| Argument | Type | Description |
|---|---|---|
| `target` | Player selector | The player who will be prompted. Accepts plain names, `@a`, `@p`, etc. |
| `command...` | Greedy string | The command string. May contain prompt tags. |

Alias: `/cd`.

### Placeholder

`%target_player%` in the command string is replaced with the target player's name before the session starts. This lets you write delegated commands that reference the target without hardcoding the name.

### Example

```
/consoledelegate Steve /warn %target_player% <a:Why?>
```

Console types this. The plugin prompts Steve with "Why?", Steve answers, and the resulting command is dispatched as the console: `/warn Steve <Steve's answer>`.

## `/playerdelegate`

Runs a prompted command on behalf of a target player, with a temporary permission attachment that grants the perms listed in the `Permission-Attachment` section of `config.yml`. The command is dispatched **as the target player** with extra permissions.

**Permission**: `promptpaper.playerdelegate`
**Sender**: Console only.

Signature:

```
/playerdelegate <target> <permissionKey> <command...>
```

| Argument | Type | Description |
|---|---|---|
| `target` | Player selector | The player who will be prompted. |
| `permissionKey` | Word | A key from `Permission-Attachment.Permissions.*` in `config.yml`. Tab-completed. |
| `command...` | Greedy string | The command string. May contain prompt tags. |

Alias: `/pd`.

### Built-in example: GAMEMODE

`config.yml` ships with one built-in permission key: `GAMEMODE`, which grants `bukkit.command.gamemode` and a couple of Essentials equivalents. Use it to let a player switch gamemode via the prompted command.

```
/playerdelegate Steve GAMEMODE /gamemode <a:Mode?>
```

When the player runs this through your admin tooling, the plugin prompts Steve with "Mode?" (an anvil), and once Steve submits, the command `/gamemode <Steve's answer>` is dispatched as Steve with `bukkit.command.gamemode` attached for the duration of the command.

### Adding custom keys

Add new permission groups under `Permission-Attachment.Permissions` in `config.yml`. Each top-level key is a new permission group:

```yaml
Permission-Attachment:
  Permissions:
    GAMEMODE:
      - bukkit.command.gamemode
      - essentials.gamemode.survival
    FLY:
      - essentials.fly
```

After `/commandprompter reload`, `/playerdelegate Steve FLY /fly` becomes available.

### Unknown key

If the `permissionKey` doesn't match a configured group, the sender gets a red error and no session starts:

```
Unknown permission key: FOO
```

See [04-config-reference.md](04-config-reference.md#permission-attachment) for the full config schema, and [10-delegation.md](10-delegation.md) for a deeper walkthrough of the delegation commands.

## Permission summary

| Command | Permission | Sender type |
|---|---|---|
| `/commandprompter` | _(none — subcommands gated)_ | Any |
| `/commandprompter reload` | `promptpaper.reload` | Any |
| `/commandprompter cancel` | `promptpaper.cancel` | Any (player action) |
| `/commandprompter version` | `promptpaper.version` | Any |
| `/consoledelegate` | `promptpaper.consoledelegate` | Console only |
| `/playerdelegate` | `promptpaper.playerdelegate` | Console only |

The full permission table, including the per-prompt `promptpaper.use` gate, is in [09-permissions.md](09-permissions.md).
