# 08 — Hooks

A **hook** is an integration with an external plugin. Each hook is opt-in: it only loads if the target plugin is installed and enabled. If a hook's target plugin is missing, the hook stays inactive and CommandPrompter logs a debug line.

## How hooks are discovered

`HookContainer.initHooks()` is called once during plugin enable. It iterates over a fixed list of hook classes and, for each:

1. Reads the `@TargetPlugin` annotation to learn the target plugin name.
2. Checks `Bukkit.getPluginManager().isPluginEnabled(target)`. If false, the hook is skipped.
3. If true, instantiates the hook via its single-arg `CommandPrompter` constructor.
4. Registers the instance as a Bukkit `Listener` if the hook implements `Listener` (e.g. for vanish state-change events).
5. Calls `onEnable()`.

Successful hooks are logged at info level:

```
 ✓ PremiumVanishHook hooked
 ✓ PapiHook hooked
```

Skipped hooks are logged only at debug level. To see the skipped-hook log lines, set `Debug-Mode: true` in `config.yml` and watch the server log on startup.

## The shipped hooks

Nine hooks ship in `prompt-paper`. Each is annotated with `@TargetPlugin(pluginName = "...")` and only loads when that plugin is present.

### Vanish hooks (SuperVanish family)

| Hook | Targets | Behavior |
|---|---|---|
| `SuperVanishHook` | `SuperVanish` | Uses `VanishAPI.isInvisible(player)`. Listens for `PlayerVanishStateChangeEvent`. |
| `PremiumVanishHook` | `PremiumVanish` | Extends `SuperVanishHook` — PremiumVanish shares the same API. |
| `VanishNoPacketHook` | `VanishNoPacket` | Uses `VanishPlugin.getManager().isVanished(player)` and respects `vanish.hooks.dynmap.alwayshidden` and `vanish.join-vanished` permissions. Listens for `VanishStatusChangeEvent`. |

All three extend `VanishHook` and provide a `boolean isInvisible(Player)` method. The base class falls back to reading the `vanished` Bukkit metadata value when a vanish plugin doesn't override.

When a player's visibility state changes, the hook invalidates or refreshes that player's head in the cache. This is what keeps the PLAYER screen accurate when a player vanishes or un-vanishes mid-session.

Only one of the three vanish hooks is needed on any given server.

### Chat listener hook (CarbonChat)

`CarbonChatHook` replaces the default Bukkit `AsyncChatEvent` listener with a CarbonChat-native subscription at priority `-100`. When a player has an active chat screen, the hook:

1. Cancels the CarbonChat event.
2. Clears the recipient list so other plugins do not see the message.
3. Hands the plain-text message to `ScreenManager.handleChatInput`.

`ChatListenerHook.subscribe` returns `false` when CarbonChat is not yet ready; in that case the default Bukkit listener is used as a fallback.

### PlaceholderAPI hook

`PapiHook` calls `PlaceholderAPI.setPlaceholders(player, text)` and is used by:

- The `JSExprValidator`, which resolves `%placeholder%` in JavaScript validation expressions before evaluating them.
- The `%target_player%` placeholder in `/consoledelegate` and `/playerdelegate` commands.

If PlaceholderAPI is not installed, expressions that contain `%placeholder%` tokens are evaluated without resolution (the literal token remains in the string).

### Player-list filter hooks (PLAYER screen)

These hooks register `CacheFilter` instances with `HeadCache`, exposing extra filter slots for the `<p:...:display>` player prompt. See [06-screens.md](06-screens.md#player) for the screen; see below for the filter syntax added by each hook.

#### Towny

`TownyHook` registers two filters:

| Filter | Filter regex | Resolves to |
|---|---|---|
| `tt` | `tt` | Players in the same town as the prompted player. |
| `tn` | `tn` | Players in the same nation as the prompted player. |

Example:

```
/msg <p:tt:Same-town member?>
```

#### LuckPerms

`LuckPermsHook` registers two filters:

| Filter | Filter regex | Resolves to |
|---|---|---|
| `lpo` | `lpo` | Players whose primary LuckPerms group matches the prompted player's primary group. |
| `lpg<group>;` | `lpg(\S+);` | Players whose primary LuckPerms group matches `<group>`. The `;` is a required terminator (the regex stops at non-`\S`). |

Examples:

```
/msg <p:lpo:Same-rank member?>
/msg <p:lpgadmin;:Admins>
```

#### HuskTowns

`HuskTownsHook` registers one filter:

| Filter | Filter regex | Resolves to |
|---|---|---|
| `ht` | `ht` | All members of the prompted player's current town. |

#### WorldGuard

`WorldGuardHook` registers three filters:

| Filter | Filter regex | Resolves to |
|---|---|---|
| `wgr` | `wgr` | Players in any of the WorldGuard regions applicable to the prompted player's current location. |
| `wgrm<id>;` | `wgrm(\S+);` | Members of the WorldGuard region with id `<id>`. |
| `wgro<id>;` | `wgro(\S+);` | Owners of the WorldGuard region with id `<id>`. |

The `;` terminator on the parameterized filters is required, because the region id can contain characters that would otherwise be ambiguous.

#### Built-in filters (no hook required)

These are always available because they are part of the core `HeadCache`:

| Filter | Resolves to |
|---|---|
| _(no filter)_ | All online, non-vanished players. |
| `w` | Players in the prompted player's current world. |
| `r<N>` | Players within `N` blocks of the prompted player. |
| `s` | All online players except the prompted player. |

## Soft-dependency declarations

The `paper-plugin.yml` declares each of the hook target plugins as a `load: AFTER, required: false` soft-dependency:

```yaml
dependencies:
  server:
    CarbonChat:     { load: AFTER, required: false }
    SuperVanish:    { load: AFTER, required: false }
    PremiumVanish:  { load: AFTER, required: false }
    VanishNoPacket: { load: AFTER, required: false }
    PlaceholderAPI: { load: AFTER, required: false }
    LuckPerms:      { load: AFTER, required: false }
    Towny:          { load: AFTER, required: false }
    HuskTowns:      { load: AFTER, required: false }
    WorldGuard:     { load: AFTER, required: false }
```

`AFTER` means CommandPrompter starts after the target plugin enables, so the hook can find the target plugin's services when it checks `isPluginEnabled`. `required: false` means the target plugin is optional — CommandPrompter still works if any of them is missing.

Adding or removing a target plugin requires a full server restart; `/commandprompter reload` does not re-init hooks.

## Disabling a hook

Hooks are not individually toggleable in the config. To disable a hook, uninstall or disable its target plugin. The hook will be skipped silently on the next startup.

## Next steps

- Looking up a specific filter syntax? See [06-screens.md](06-screens.md#player) for the PLAYER screen.
- The LuckPerms filter keys also affect the `%target_player%` placeholder? No — they only affect head-cache filtering. See [03-commands.md](03-commands.md) for the delegate commands.
- The `JSExprValidator` uses `PapiHook` for placeholder resolution. See [07-validators.md](07-validators.md).
