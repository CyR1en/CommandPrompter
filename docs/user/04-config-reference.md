# 04 — Config reference

This page is the exhaustive reference for every config option in `config.yml` and `prompt-config.yml`. Options are grouped by the file and section they live in.

If you are not looking for a specific option, the how-to guides may be friendlier:

- [05-prompt-syntax.md](05-prompt-syntax.md) — prompt tags and flags
- [06-screens.md](06-screens.md) — screen-type selection and per-screen config
- [07-validators.md](07-validators.md) — input validators
- [08-hooks.md](08-hooks.md) — plugin integrations
- [09-permissions.md](09-permissions.md) — full permission table

The first run creates both files at `plugins/CommandPrompterPaper/`. After editing, run `/commandprompter reload` to apply changes without restarting the server. See [03-commands.md](03-commands.md#commandprompter-reload).

Format conventions used in this page:

- All keys are quoted exactly as they appear in the generated YAML.
- `Default` is the value the plugin writes on first start. If you delete a key, the plugin re-adds the default on next load.
- All values that contain user-facing text support MiniMessage formatting. See [13-troubleshooting.md](13-troubleshooting.md) for the color-code escape rules.
- `IntegerConstraint` and `Match` annotations on the field source are noted in the description when they constrain the value.

## `config.yml` — top-level settings

This section maps to the `CommandPrompterConfig` Java record. Most keys are scalars (strings, booleans, integers, or string lists).

### `Prompt-Prefix`

| | |
|---|---|
| **Key** | `Prompt-Prefix` |
| **Type** | String (MiniMessage) |
| **Default** | `<gradient:gold:yellow>[Prompter]</gradient> ` |

Plugin prefix prepended to plugin-emitted messages. Trailing space included.

### `Prompt-Timeout`

| | |
|---|---|
| **Key** | `Prompt-Timeout` |
| **Type** | Integer (seconds) |
| **Default** | `300` |

How long, in seconds, an unanswered prompt waits before the plugin auto-cancels the session. The player receives the `Messages.Prompt-Timed-Out` message on auto-cancel. Set to `0` (or any non-positive) to disable the timeout entirely.

### `Cancel-Keyword`

| | |
|---|---|
| **Key** | `Cancel-Keyword` |
| **Type** | String |
| **Default** | `cancel` |

The word a player types in chat (when a chat prompt is active) to cancel the prompt. Match is exact and case-insensitive.

### `Enable-Permission`

| | |
|---|---|
| **Key** | `Enable-Permission` |
| **Type** | Boolean |
| **Default** | `false` |

When `true`, players must have the `promptpaper.use` permission to use any prompt feature. When `false`, every player can use prompts. See [09-permissions.md](09-permissions.md).

### `Debug-Mode`

| | |
|---|---|
| **Key** | `Debug-Mode` |
| **Type** | Boolean |
| **Default** | `false` |

When `true`, the plugin logs verbose debug breadcrumbs to the server console: parser steps, session transitions, hook activations, screen routing, dispatch paths. Toggle off in production. See [13-troubleshooting.md](13-troubleshooting.md) for the recommended workflow.

### `Fancy-Logger`

| | |
|---|---|
| **Key** | `Fancy-Logger` |
| **Type** | Boolean |
| **Default** | `true` |

When `true`, log lines use ANSI color escapes. Disable on terminals that don't render ANSI.

### `Show-Complete-Command`

| | |
|---|---|
| **Key** | `Show-Complete-Command` |
| **Type** | Boolean |
| **Default** | `true` |

When `true`, the plugin sends the fully-assembled command (after all prompts are answered) to the player before dispatching. Set to `false` to keep the resolved command private.

### `Show-Prompt-Cancelled`

| | |
|---|---|
| **Key** | `Show-Prompt-Cancelled` |
| **Type** | Boolean |
| **Default** | `true` |

When `true`, the player receives a confirmation message when their prompt is cancelled (manually, on timeout, or by exit). See the `Messages` section for the wording.

### `Argument-Regex`

| | |
|---|---|
| **Key** | `Argument-Regex` |
| **Type** | String (regex) |
| **Default** | `<.*?>` |

Regex used to find prompt tags in the command string. The default matches angle-bracketed text. The plugin only intercepts the parts of the command that match this pattern, so changing it is the way to switch the tag syntax (e.g. `{.*?}` for braces).

The first and last characters of the pattern are the delimiters; the body is a non-greedy match. The plugin's parser also accepts a single-character escape (`\` by default) to allow literal delimiters.

### `Ignored-Commands`

| | |
|---|---|
| **Key** | `Ignored-Commands` |
| **Type** | String list |
| **Default** | `sampleCommand, sampleCommand2` |

Commands (without the leading `/`) that the plugin will never intercept. Use this to opt a vanilla or third-party command out of prompt handling — for example, if `tp` is in this list, the prompt engine will not look inside `/tp ...` even if it contains a tag-like argument.

### `Allowed-Commands-In-Prompt`

| | |
|---|---|
| **Key** | `Allowed-Commands-In-Prompt` |
| **Type** | String list |
| **Default** | `sampleCommand, sampleCommand2` |

Commands (without the leading `/`) that a player may run while a prompt session is active. Other commands are blocked by the prompt session.

### `Command-Tab-Complete`

| | |
|---|---|
| **Key** | `Command-Tab-Complete` |
| **Type** | Boolean |
| **Default** | `true` |

When `true`, the plugin installs its own tab completer for prompt-bearing commands. Disable if you have a tab-completion plugin that conflicts.

### `Permission-Attachment`

| | |
|---|---|
| **Key** | `Permission-Attachment` |
| **Type** | Section (see below) |

Configuration for the temporary permission attachment used by `/playerdelegate`. See [03-commands.md](03-commands.md#playerdelegate) and [10-delegation.md](10-delegation.md) for usage.

```yaml
Permission-Attachment:
  ticks: 1
  Permissions:
    GAMEMODE:
      - bukkit.command.gamemode
      - essentials.gamemode.survival
      - essentials.gamemode.creative
    # add more groups here
```

| Sub-key | Type | Default | Description |
|---|---|---|---|
| `Permission-Attachment.ticks` | Integer | `1` | Reserved for future use. The plugin currently attaches the permission for the duration of a single command. |
| `Permission-Attachment.Permissions.<KEY>` | String list | _(none)_ | A named group of permissions. The `<KEY>` becomes a tab-completion option for `/playerdelegate <key>`. The list contains Bukkit permission strings to attach. The plugin ships with a `GAMEMODE` key as an example. |

See [10-delegation.md](10-delegation.md) for the full delegation workflow and how to add new keys.

## `config.yml` — `Messages:` section

This section maps to the `MessageConfig` Java record. All fields support MiniMessage formatting.

| Key | Type | Default | Description |
|---|---|---|---|
| `Messages.Prompt-Cancelled` | String (MiniMessage) | `<yellow>Prompt cancelled.</yellow>` | Sent to the player when their session is cancelled (manually, by exit, on timeout — controlled by `Show-Prompt-Cancelled`). |
| `Messages.Prompt-Timed-Out` | String (MiniMessage) | `<red>Prompt timed out.</red>` | Sent to the player when the session times out. |
| `Messages.Invalid-Integer` | String (MiniMessage) | `<red>Please enter a valid integer.</red>` | Sent when an answer is required to be an integer (the `-int` flag) and is not. |
| `Messages.Invalid-String` | String (MiniMessage) | `<red>Input cannot be empty.</red>` | Sent when an answer is required to be a non-empty string (the `-str` flag) and is blank. |

These four messages can be overridden per-language by editing the `Messages:` block in `config.yml`.

## `prompt-config.yml` — `PlayerUI` section

This section maps to the `PromptConfig` record, fields prefixed with `PlayerUI.*`. The `PlayerUI` section configures the appearance and behavior of the player-head selection screen (`<p:Filter:Display>` prompts).

| Key | Type | Default | Description |
|---|---|---|---|
| `PlayerUI.Skull-Name-Format` | String | `&6%s` | Format string for the display name on player heads. `%s` is the player name. |
| `PlayerUI.Skull-Custom-Model-Data` | Integer | `0` | Custom model data applied to player head items, for resource-pack variants. |
| `PlayerUI.Size` | Integer | `54` | Total chest-GUI size for the player UI. Must be a multiple of 9 between 18 and 54. |
| `PlayerUI.Cache-Size` | Integer | `256` | How many player heads the cache holds in memory. |
| `PlayerUI.Cache-Delay` | Integer | `1` | Ticks to wait after a player joins before their head is cached. `IntegerConstraint(min=0, max=2400)`. |
| `PlayerUI.Sorted` | Boolean | `false` | If `true`, the head list is sorted alphabetically by player name. |
| `PlayerUI.Empty-Message` | String | `&cNo players found!` | Message shown when no heads match the current filter. |
| `PlayerUI.Filter-Format.World` | String | `&6&#x1F4CD; %s` | Format string for the "World" filter button. `%s` is the current world. |
| `PlayerUI.Filter-Format.Radial` | String | `&cᯤ %s` | Format string for the "Radial" filter button. `%s` is the radius. |

The `PlayerUI.Previous`, `PlayerUI.Next`, `PlayerUI.Cancel`, and `PlayerUI.Search` sub-sections each describe a control item (a button in the GUI) and share the same shape:

| Sub-key | Type | Default | Description |
|---|---|---|---|
| `.Item` | String | _(varies)_ | Material name (e.g. `Feather`, `Barrier`, `Name_Tag`). |
| `.Custom-Model-Data` | Integer | `0` | Custom model data for resource-pack variants. |
| `.Column` | Integer | _(varies)_ | The column (0–8) of the chest GUI where the control sits. |
| `.Text` | String | _(varies)_ | Display name of the control item. |

Per-control defaults:

| Control | Item | Column | Text |
|---|---|---|---|
| `Previous` | `Feather` | `3` | `&7◀◀ Previous` |
| `Next` | `Feather` | `7` | `Next ▶▶` |
| `Cancel` | `Barrier` | `5` | `&7Cancel ✘` |
| `Search` | `Name_Tag` | `9` _(invalid; should be 0–8; see below)_ | `&6Search ⌕` |

> **Note**: the default `PlayerUI.Search.Column` of `9` is outside the valid 0–8 column range. If you place the search control, override this to a valid column. The constraint is not enforced by the plugin; the GUI will simply place the item at the invalid slot.

`PlayerUI.Search.AnvilItem` configures the temporary anvil that appears when the player clicks Search:

| Sub-key | Type | Default | Description |
|---|---|---|---|
| `.AnvilItem.Title` | String | `&6&lPlayer Search` | Title of the search anvil. |
| `.AnvilItem.Material` | String | `PAPER` | The item placed in the anvil's input slot. |
| `.AnvilItem.CustomModelData` | Integer | `0` | Custom model data for the search item. |
| `.AnvilItem.Text` | String | `&6Enter Player Name` | Display name of the search item. |

## `prompt-config.yml` — `AnvilGUI` section

Configures the Anvil GUI prompt screen (`<a:Display>` prompts).

| Key | Type | Default | Description |
|---|---|---|---|
| `AnvilGUI.Enable-Title` | Boolean | `true` | When `true`, the first line of the prompt's display text is used as the anvil's title. |
| `AnvilGUI.Custom-Title` | String | _(empty)_ | When non-empty and `Enable-Title` is `true`, this string is used as the anvil's title instead of the first line of the display text. |
| `AnvilGUI.Prompt-Message` | String | _(empty)_ | Optional message displayed in the anvil. |
| `AnvilGUI.Enable-Cancel-Item` | Boolean | `false` | When `true`, a cancel item is placed in the right input slot. |
| `AnvilGUI.Item.Material` | String | `Paper` | Material of the left-slot item. |
| `AnvilGUI.Item.HideTooltips` | Boolean | `false` | Hide tooltips on the left item (1.21.2+). |
| `AnvilGUI.Item.Custom-Model-Data` | Integer | `0` | Custom model data on the left item. |
| `AnvilGUI.Item.Enchanted` | Boolean | `false` | Apply the enchantment glint to the left item. |
| `AnvilGUI.ResultItem.Material` | String | `Paper` | Material of the result item. |
| `AnvilGUI.ResultItem.HideTooltips` | Boolean | `false` | Hide tooltips on the result item. |
| `AnvilGUI.ResultItem.Custom-Model-Data` | Integer | `0` | Custom model data on the result item. |
| `AnvilGUI.ResultItem.Enchanted` | Boolean | `false` | Enchantment glint on the result item. |
| `AnvilGUI.CancelItem.Material` | String | `Barrier` | Material of the cancel item (only used when `Enable-Cancel-Item` is `true`). |
| `AnvilGUI.CancelItem.HideTooltips` | Boolean | `false` | Hide tooltips on the cancel item. |
| `AnvilGUI.CancelItem.Custom-Model-Data` | Integer | `0` | Custom model data on the cancel item. |
| `AnvilGUI.CancelItem.Enchanted` | Boolean | `false` | Enchantment glint on the cancel item. |
| `AnvilGUI.CancelItem.HoverText` | String | `&cClick to Cancel` | Hover text on the cancel item. |

## `prompt-config.yml` — `TextPrompt` section

Configures the chat text prompt screen (default for tags with no key).

| Key | Type | Default | Description |
|---|---|---|---|
| `TextPrompt.Clickable-Cancel` | Boolean | `true` | When `true`, the plugin sends a clickable cancel message after the prompt text. |
| `TextPrompt.Cancel-Message` | String | `&7[&c&l✘&7]` | The clickable text the player can click to cancel. |
| `TextPrompt.Cancel-Hover-Message` | String | `&7Click here to cancel command completion` | Hover text shown on the clickable cancel. |
| `TextPrompt.Response-Listener-Priority` | String (enum) | `DEFAULT` | Priority for the chat listener. One of `DEFAULT`, `LOW`, `LOWEST`, `NORMAL`, `HIGH`, `HIGHEST`. |

## `prompt-config.yml` — `SignUI` section

Configures the sign prompt screen (`<s:Display>` prompts).

| Key | Type | Default | Constraint | Description |
|---|---|---|---|---|
| `SignUI.Input-Field-Location` | String (enum) | `bottom` | One of `top`, `top-aggregate`, `bottom`, `bottom-aggregate` | Which line of the sign is read as the answer. `top` and `bottom` read a single line; the `-aggregate` variants concatenate multiple non-empty lines. |
| `SignUI.Material` | String | `OAK_SIGN` | `@Match(regex = "(.*SIGN.*)")` | The sign material. Must be a material whose name contains `SIGN`. |

## `prompt-config.yml` — `Input-Validation` section

Configures input validators. Each top-level entry under `Input-Validation` is a named validation group. The plugin ships with two sample groups, `Integer-Sample` and `Alpha-Sample`. You can add more with arbitrary names; the alias you choose is referenced from prompt tags via `-iv:<alias>`. See [07-validators.md](07-validators.md).

```yaml
Input-Validation:
  Integer-Sample:
    Alias: is
    Regex: ^\d+
    Err-Message: '&cPlease enter a valid integer!'
  Alpha-Sample:
    Alias: ss
    Regex: '[A-Za-z ]+'
    Err-Message: '&cInput must only consist letters of the alphabet!'
```

| Sub-key | Type | Default | Description |
|---|---|---|---|
| `Input-Validation.<NAME>.Alias` | String | _(varies)_ | The name used in the prompt tag's `-iv:<alias>` flag. |
| `Input-Validation.<NAME>.Regex` | String | _(varies)_ | A regex that the answer must match. If empty or omitted, no regex check runs. |
| `Input-Validation.<NAME>.Err-Message` | String | _(varies)_ | Message sent to the player if the regex does not match. |
| `Input-Validation.<NAME>.JS-Expression` | String | _(none)_ | Optional. A JavaScript expression that returns a truthy value when the answer is valid. See [07-validators.md](07-validators.md). |
| `Input-Validation.<NAME>.Online-Player` | Boolean | `false` | Optional. If `true`, the answer must be the name of an online player. |

## `prompt-config.yml` — `DialogUI` section

Configures the dialog prompt screen (`<d:filter:Display>` prompts and compound `<d:...> && <d:...>` dialogs).

| Key | Type | Default | Constraint | Description |
|---|---|---|---|---|
| `DialogUI.Title` | String | `Prompt` | _(none)_ | Title of the dialog window. |
| `DialogUI.Confirm-Button.Label` | String (MiniMessage) | `<green>Confirm</green>` | _(none)_ | Label of the submit button. |
| `DialogUI.Confirm-Button.Tooltip` | String | `Confirm this action` | _(none)_ | Tooltip shown on hover of the submit button. |
| `DialogUI.Cancel-Button.Label` | String (MiniMessage) | `<red>Cancel</red>` | _(none)_ | Label of the cancel button. |
| `DialogUI.Cancel-Button.Tooltip` | String | `Cancel this action` | _(none)_ | Tooltip on the cancel button. |
| `DialogUI.Defaults.Text.MaxLength` | Integer | `256` | `IntegerConstraint(min=1, max=8192)` | Maximum characters for a text-input dialog. |
| `DialogUI.Defaults.Text.Multiline` | Boolean | `false` | _(none)_ | Allow multi-line text input. |
| `DialogUI.Defaults.Text.MultilineMaxLines` | Integer | `4` | `IntegerConstraint(min=1, max=16)` | Maximum lines for multi-line text input. |
| `DialogUI.Defaults.Choice.Default-Options` | String (comma list) | _(empty)_ | _(none)_ | Default options for the `<d:choice[...]>` form when no explicit list is given in the tag. Comma-separated. |
| `DialogUI.Defaults.Number.Min` | Float | `0` | _(none)_ | Default minimum for `<d:num[...]>` dialogs. |
| `DialogUI.Defaults.Number.Max` | Float | `100` | _(none)_ | Default maximum. |
| `DialogUI.Defaults.Number.Step` | Float | `1` | _(none)_ | Default step. |
| `DialogUI.Defaults.Tab.MaxButtons` | Integer | `5` | `IntegerConstraint(min=1, max=256)` | Threshold for `<d:tab:Display>`: when the completion count is at or below this number, the dialog shows a button grid (one per completion); above this, it falls back to text input. |

See [12-dialogs-compound.md](12-dialogs-compound.md) for the dialog tag syntax and the `&&` compound form.

## `prompt-config.yml` — `screen-mappings` section

Maps a prompt tag's key (the part before the first colon) to a screen type. The default mapping, used when the section is absent or empty, is:

```yaml
screen-mappings:
  "": CHAT
  a: ANVIL
  s: SIGN
  d: DIALOG
  p: PLAYER
```

Override the section to remap keys:

```yaml
screen-mappings:
  "": DIALOG       # use dialogs as the default
  a: SIGN          # remap 'a' to use the sign
  mykey: PLAYER    # add a new key
```

| Value | Screen |
|---|---|
| `CHAT` | Chat-based text prompt. |
| `ANVIL` | Anvil GUI. Requires the `prompt-ui-26.1` subproject to be packaged (it is by default in the shadow JAR). |
| `SIGN` | Sign GUI. Requires `prompt-ui-26.1`. |
| `DIALOG` | Native dialog. |
| `PLAYER` | Paginated player-head GUI. |

Unknown values are silently skipped by the loader. See [06-screens.md](06-screens.md) for the per-screen tradeoffs and recommendations.

## Reloading

After any of the above edits, run `/commandprompter reload`. See [03-commands.md](03-commands.md#commandprompter-reload). The reload cancels all active sessions first, so coordinate with players before reloading on a live server.
