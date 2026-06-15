# 09 — Permissions

CommandPrompter uses two layers of permission checking. Per-command permissions gate who can run a CommandPrompter command. The `promptpaper.use` permission, when enabled in config, gates who can be the target of a prompt (i.e. who can be intercepted and prompted when they type a command containing prompt tags).

## Per-command permissions

Every CommandPrompter command extends `PromptCommand`. Each concrete command passes a permission string and (optionally) a sender-type filter to the base class constructor. The `allowed(sender)` helper combines them into a single predicate that Brigadier wires into `.requires(...)` on the literal node.

Seven permissions are declared in `paper-plugin.yml`. `promptpaper.admin` is an umbrella for admin tooling that no command checks directly; the other six are each wired to a specific command:

| Permission | Default owner | Purpose |
|---|---|---|
| `promptpaper.admin` | op | Master permission. Intended as the umbrella for admin tooling; not assigned to any command directly. |
| `promptpaper.reload` | op | Required by `/commandprompter reload`. |
| `promptpaper.cancel` | op | Required by `/commandprompter cancel`. |
| `promptpaper.version` | op | Required by `/commandprompter version`. |
| `promptpaper.use` | op | Required to *be prompted* when `Enable-Permission` is true in `config.yml`. See [Per-prompt gate](#per-prompt-gate). |
| `promptpaper.consoledelegate` | op | Required by `/consoledelegate`. |
| `promptpaper.playerdelegate` | op | Required by `/playerdelegate`. |

A permission being in `paper-plugin.yml` declares its existence for tools like LuckPerms. No explicit `default:` is set for any permission; operators have every permission by default (via the Paper/Bukkit op mechanic), and non-op players have none.

## Command → permission map

The constructor argument on each command class is the source of truth:

| Command | Class | Permission | Sender filter |
|---|---|---|---|
| `/commandprompter` | `PromptRootCommand` | _(none — subcommands gated)_ | Any |
| `/commandprompter reload` | `ReloadCommand` | `promptpaper.reload` | Any |
| `/commandprompter cancel` | `CancelCommand` | `promptpaper.cancel` | Any |
| `/commandprompter version` | `VersionCommand` | `promptpaper.version` | Any |
| `/consoledelegate` | `ConsoleDelegateCommand` | `promptpaper.consoledelegate` | `ConsoleCommandSender` only |
| `/playerdelegate` | `PlayerDelegateCommand` | `promptpaper.playerdelegate` | `ConsoleCommandSender` only |

Two checks are applied for every command invocation:

1. **Permission check** — `sender.hasPermission(permission)`. If the command has no permission string, the check is skipped.
2. **Sender-type check** — `senderFilter.isInstance(sender)`. If the command was constructed with `null` as the sender filter, the check is skipped.

`/consoledelegate` and `/playerdelegate` are gated to console senders by passing `ConsoleCommandSender.class` as the sender filter. A player who runs them gets the standard "not allowed" response from Brigadier.

The `/commandprompter` root command has no permission or sender filter, so the bare literal is always reachable. Each subcommand applies its own gate. This lets a non-admin player run `/commandprompter cancel` if they have `promptpaper.cancel`, even if they don't have the umbrella `promptpaper.admin`.

For command signatures, examples, and expected output, see [03-commands.md](03-commands.md).

## Per-prompt gate

Setting `Enable-Permission: true` in `config.yml` switches on the `promptpaper.use` gate. When enabled, `PromptEngine.intercept` checks the player for `promptpaper.use` before starting a session:

```
if (config.enablePermission() && !player.hasPermission("promptpaper.use")) {
    return Optional.empty();
}
```

If the player lacks the permission, the command is **not** intercepted. The command falls through to the normal Bukkit dispatcher and the player runs the underlying command as if no prompts existed. No message is sent to the player — the absence of a prompt is the signal.

This is the right gate to set on a multi-server network where you want most servers to have prompts disabled for regular players, but you still want admins to be able to configure the prompts centrally.

The default is `Enable-Permission: false`, which makes every player promptable.

## Permission attachments

`/playerdelegate` adds a Bukkit `PermissionAttachment` to the target player for the duration of one dispatched command. The attachment grants the permissions listed in the named group under `Permission-Attachment.Permissions` in `config.yml`.

The default config ships one group:

```yaml
Permission-Attachment:
  Permissions:
    GAMEMODE:
      - bukkit.command.gamemode
      - essentials.gamemode.survival
      - essentials.gamemode.creative
```

The keys under `Permission-Attachment.Permissions` become the tab-completion suggestions for the `<permissionKey>` argument of `/playerdelegate`. Adding a new key (e.g. `FLY`) makes the key available immediately after `/commandprompter reload`.

The attachment's lifetime is controlled by `Permission-Attachment.ticks` in `config.yml` (default `1` tick). After that many ticks, the attachment is removed.

See [03-commands.md](03-commands.md#playerdelegate) and [10-delegation.md](10-delegation.md) for the full delegation walkthrough.

## Notes for permission plugins

- CommandPrompter declares its permissions in `paper-plugin.yml` and does not register any in code. The defaults shown in this page are what Bukkit / Paper show to op-vs-non-op; they are not enforced server-side.
- If you use LuckPerms, the hook (see [08-hooks.md](08-hooks.md#luckperms)) gives you extra `lpo` and `lpg<group>;` filters for PLAYER screens. It does **not** register or modify any of the permissions in this page.
- `promptpaper.use` does not gate specific commands or prompts; it gates the prompt interception pipeline as a whole. There is no per-prompt or per-tag permission system.

## Quick reference

| Permission | Gate | Default |
|---|---|---|
| `promptpaper.admin` | Umbrella, unused as a literal gate | op |
| `promptpaper.reload` | `/commandprompter reload` | op |
| `promptpaper.cancel` | `/commandprompter cancel` | op |
| `promptpaper.version` | `/commandprompter version` | op |
| `promptpaper.use` | Being prompted (only when `Enable-Permission: true`) | op |
| `promptpaper.consoledelegate` | `/consoledelegate` | op |
| `promptpaper.playerdelegate` | `/playerdelegate` | op |

## Next steps

- `/consoledelegate` and `/playerdelegate` deep dive? See [10-delegation.md](10-delegation.md).
- Toggling prompts globally? Set `Enable-Permission` in `config.yml` — see [04-config-reference.md](04-config-reference.md#enable-permission).
- A different prompt-related permission system? CommandPrompter does not ship one; if you need per-tag gating, gate the source command instead.
