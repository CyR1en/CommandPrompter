# 06 — Screens

A **screen** is the actual UI the player sees when answering a prompt. CommandPrompter ships five screen types, and `prompt-config.yml` decides which one a given prompt key uses.

## How routing works

When a `<key:filter:display>` tag is parsed, the `ScreenRouter` looks up `key` in the `screen-mappings` section of `prompt-config.yml`. The mapped `ScreenType` becomes the screen that opens for the player.

The default mapping (defined in `prompt-config.yml`) is:

| Prompt key | Screen type |
|---|---|
| _(empty)_ | `CHAT` |
| `a` | `ANVIL` |
| `s` | `SIGN` |
| `d` | `DIALOG` |
| `p` | `PLAYER` |

You can add or change keys. See [04-config-reference.md](04-config-reference.md#screen-mappings).

## Screen types

### CHAT

Sends the prompt text into chat and listens for the player's next message. Works everywhere, no GUI required.

- **Open**: chat message, optionally with a clickable cancel link (`prompt-config.yml` `send-cancel-text`).
- **Answer**: the raw text of the player's next chat message.
- **Cancel keyword**: not checked here — the player types `/cmdp cancel` (or the clickable link), not the cancel keyword.

Use CHAT when you don't have a GUI provider loaded, or when the prompt is a free-form message the player can write in one line.

### ANVIL

Opens an anvil GUI with the display text as the item label. The player types a value and clicks the result item.

- **Open**: anvil GUI; the input slot is shown with a label, and the cancel slot (configurable) lets the player abort without typing.
- **Answer**: the text in the rename field, with color codes stripped.
- **Cancel keyword**: the stripped answer is compared against `config.yml` `Cancel-Keyword` using `equalsIgnoreCase`. A match is treated as cancellation, not as an answer.
- **Fallback**: if no NMS screen provider is available (e.g. an unsupported server version), falls back to a chat prompt for that player.

The anvil provider, items, titles, and cancel button are configured under `AnvilUI` in `prompt-config.yml`. See [04-config-reference.md](04-config-reference.md#anvilui).

### SIGN

Opens a sign editor with the display text distributed across the sign's four lines.

- **Single-line mode** (the default): one or more display lines, the player overwrites them, and the non-empty lines that don't match the displayed prompt text are joined into a single space-separated answer.
- **Multi-arg mode** (any display line ends in `:`): the player writes `<label>: <value>` per line, and only the `<value>` half is kept. This is how you can collect several inputs on one sign.
- **Line breaks**: use `{br}` in the display text to insert a literal newline.
- **Input-field location**: configured under `SignUI.Input-Field-Location`. `top`, `bottom-aggregate`, `top-aggregate`, and `bottom` (default) all distribute the prompt across the four sign slots differently.
- **Cancel keyword**: stripped joined answer is compared against `Cancel-Keyword` (case-insensitive). A match cancels.
- **Fallback**: chat, same as anvil.

Example sign with line breaks and a multi-arg layout:

```
/ban <s:Name:{br}Reason:{br}Duration:>
```

The player sees:

```
Line 1: Name:
Line 2: Reason:
Line 3: Duration:
Line 4: (empty input slot)
```

If the player writes `Name: Steve`, `Reason: griefing`, `Duration: 7d`, the answer is the three values joined by spaces: `Steve griefing 7d`.

### DIALOG

Opens a Paper dialog (the modern in-game dialog system). Renders one input per `key[:filter]:display` row, with a confirm and cancel action button.

- **Open**: a single Paper dialog with the title from `DialogUI.title` and one input row per tag.
- **Compound tags** (`<d:filter1:disp1 && d:filter2:disp2>`): one dialog with N input rows. Sub-answers are joined with spaces at assembly time. `d:tab` is **not** allowed in a compound block — the parser throws `IllegalArgumentException` for that combination.
- **Answer encoding**: N row answers are joined into a single string for the screen result. See [05-prompt-syntax.md](05-prompt-syntax.md#compound-dialog-tags) for the rules.
- **Sanitization**: with `-ds` (`sanitize=false`), MiniMessage tags in the answer survive and are converted to legacy `§X` codes for downstream consumers like `/say`.

#### Dialog input kinds

The filter slot of a `<d:...>` tag selects the input kind. `DialogInputKind.parse` is case-insensitive and unknown keywords fall back to a text input.

| Filter | Kind | Notes |
|---|---|---|
| `text` (or empty) | `TEXT` | One-line text input. Optional `[min,max]` bracket sets `maxLength` from `max`. |
| `num` (alias `number`, `numberrange`) | `NUMBER` | Range slider. Bracket is `[min,max]` or `[min,max,step,initial]`. Falls back to `DialogUI.Defaults.Number`. |
| `choice` | `CHOICE` | Dropdown. Bracket is a comma-separated list of options; empty falls back to `DialogUI.Defaults.Choice.Default-Options`. |
| `tab` | `TAB` | Tab-completion picker. Bracket is an optional `[N]` threshold; if completions are `<= N` it renders as a multi-action button list, otherwise as a text-input fallback. See [05-prompt-syntax.md](05-prompt-syntax.md#compound-dialog-tags) and the `Tab` block of `DialogUI.Defaults` in `prompt-config.yml`. |
| `title` | `TITLE` | A static header text block. **Only valid inside compound dialog tags**. Aborts the prompt if used in a single-row tag. |

Examples:

```
/test <d:Confirm?>                                <-- text input
/test <d:text:Title>                               <-- explicit text
/test <d:text[0,32]:Short title>                  <-- text, max length 32
/test <d:bool:Confirm?>                            <-- (legacy kind; falls through to text)
/test <d:num[0,100]:Volume>                        <-- number slider 0..100
/test <d:num[0,100,5,42]:Volume>                   <-- number slider with step 5, initial 42
/test <d:choice[a,b,c]:Pick one>                   <-- dropdown with three options
/setup <d:choice[set,add]:Sub && d:num[0,24]:Value>  <-- compound dialog, two rows
/setup <d:title:Settings && d:num[0,24]:Value>     <-- compound dialog with a title header
/cmd <d:tab:Player?>                               <-- tab-completion picker
/cmd <d:tab[5]:Player?>                            <-- tab picker with explicit 5-button threshold
```

### PLAYER

Opens a paginated chest GUI of player heads. Click a head to use that player name as the answer; click the search item to filter the visible heads by chat input; click the previous/next items to paginate; click the cancel item to abort.

- **Open**: chest GUI whose row count comes from `PlayerUI.size` (must be a multiple of 9; the last row is reserved for navigation controls).
- **Answer**: the clicked player's username.
- **Cancel**: closing the inventory or clicking the cancel item cancels the prompt.
- **Filter**: optional filter slot on the tag (`<p:w:Who?>` for "world", `<p:r50:Who?>` for "50-block radius", `<p:s:Who?>` for "everyone but me", or no filter for all visible online players). The full filter set is configured under `PlayerUI.Filters` in `prompt-config.yml`. Vanished players are excluded unless the matching hook allows them.
- **Sort**: when `PlayerUI.sorted` is true, heads are sorted alphabetically by display name.

The navigation and search controls, plus item model data, are configured under `PlayerUI` in `prompt-config.yml`. See [04-config-reference.md](04-config-reference.md#playerui).

## When the GUI fails

ANVIL, SIGN, and PLAYER screens are implemented as wrappers around a NMS `ScreenProvider` loaded via `ServiceLoader` from the bundled `prompt-ui-26.1` subproject. If the loader can't produce a working screen — for example, the server is on an unsupported version, or the provider throws during construction — the screen falls back to chat for that prompt. The fallback is per-screen and per-player, so one missing provider does not affect the others.

If `Debug-Mode` is on in `config.yml`, the fallback is logged at debug level:

```
All anvil providers failed, falling back to chat
```

## Quick reference

| Key | Default screen | Fallback | Best for |
|---|---|---|---|
| _(empty)_ | CHAT | _(none)_ | Free-form one-liners, no GUI dependency |
| `a` | ANVIL | CHAT | Single-value input with strong visual affordance |
| `s` | SIGN | CHAT | Multi-arg on one screen, signed contract feel |
| `d` | DIALOG | _(none, requires Paper 1.21.4+)_ | Structured input with text/number/choice/tab kinds |
| `p` | PLAYER | CHAT | Picking from online players |

## Next steps

- Configuring screens, items, and titles? See [04-config-reference.md](04-config-reference.md).
- Validating what the player typed? See [07-validators.md](07-validators.md).
- Need an integration plugin (vanish, town boundaries, permissions)? See [08-hooks.md](08-hooks.md).
