# 12. Dialogs & Compound Prompt Tags

Dialogs are the most expressive screen CommandPrompter offers. They can collect a single typed answer, a number from a range, a selection from a fixed list, a tab-completion-driven choice — or, combined with compound prompt tags, several answers in a single screen at once.

This page covers the dialog screen and the compound prompt tag that drives it. For how dialogs are *selected* see [06-screens.md](06-screens.md); for the `<key[:filter][:display]>` tag shape itself see [05-prompt-syntax.md](05-prompt-syntax.md).

## What this page covers

- The dialog screen and its five input kinds: `text`, `number`, `choice`, `tab`, `title`
- Compound prompt tags — the `&&` syntax that turns one tag into a multi-row dialog
- Flag placement — how flags apply to single-row and compound tags (always block-level)
- How answers from multiple rows are packed into a single command argument

## When you reach for a dialog

A dialog is the right screen when you want structured input — numbers in a range, a selection from a fixed list, a completion-driven multi-choice, or multiple values at once. For open-ended text where the player just types a string, an anvil or sign is usually friendlier (see [06-screens.md](06-screens.md)).

## Dialog input kinds

Each row of a dialog resolves to one of five kinds. The kind is derived from the row's `:filter` (the part between the first and second `:` in a tag — see [05-prompt-syntax.md](05-prompt-syntax.md)):

| Filter prefix | Kind   | What the player sees                            |
| ------------- | ------ | ----------------------------------------------- |
| `text` or `""` | TEXT  | Plain text input (the default)                  |
| `num` / `number` / `numberrange` | NUMBER | Numeric input with optional min/max/step/initial |
| `choice`       | CHOICE | A single-select dropdown, one entry per option  |
| `tab`          | TAB    | A multi-action button grid driven by tab-completion |
| `title`        | TITLE  | A static header text block (only valid in compound dialogs) |

Unknown prefixes (including `bool`) fall back to `text`. The kind keywords are case-insensitive — `NUM`, `Num`, and `num` all resolve to NUMBER.

## The five kinds in detail

### `text` — the default

A free-form text field. The defaults are:

```yaml
# in prompt-config.yml
DialogUI:
  Defaults:
    Text:
      MaxLength: 256
      Multiline: false
      MultilineMaxLines: 4
```

A row can override the maximum length:

```yaml
<d:text[0,80]:Enter a short label>
```

The bracket syntax is `text[min,max]` — only the `max` value is enforced as the maximum character count, clamped to the range `[1, 8192]`. The `min` value is accepted but unused for text inputs. If no bracket content is given, the value from `DialogUI.Defaults.Text.MaxLength` is used.

When `Multiline` is `true`, the player can press Enter to add a line break in the text. `MultilineMaxLines` caps the total lines accepted. The value sent to the command is the raw text the player typed (modulo sanitization).

### `number` — bounded numeric input

A number input with optional bounds, step, and initial value:

```yaml
# in prompt-config.yml
DialogUI:
  Defaults:
    Number:
      Min: 0
      Max: 100
      Step: 1
      Initial: null   # null → midpoint of (min + max) / 2
```

A row can override any of these:

```yaml
<d:num[1,10]:Pick a count>              # min=1, max=10, step=1 (default), initial=5.5 (midpoint)
<d:num[0,360,15]:Pick a rotation>       # min=0, max=360, step=15, initial=180 (midpoint)
<d:num[0,100,5,42]:Amount>              # min=0, max=100, step=5, initial=42
```

The bracket syntax is `num[min,max,step,initial]` — all four parameters are optional. Omitted parameters fall back to the config defaults. When a per-tag range is supplied but no per-tag initial, the initial is re-resolved to the midpoint of the per-tag range (not the config default midpoint). This prevents Paper's `NumberRangeDialogInput` from rejecting an initial value that falls outside the per-tag range.

Defensive clamping rules:

- If `min >= max`, `max` is set to `min + 1`.
- If `step <= 0`, `step` is treated as `1`.
- If `initial` is outside `[min, max]`, it is clamped to the nearest bound.

The number is sent to the command as a string — `Long.toString` when the step is exactly `1.0` and the value is a whole number, otherwise `Float.toString`.

### `choice` — pick from a list

A single-select dropdown, one entry per option. The default list is empty:

```yaml
# in prompt-config.yml
DialogUI:
  Defaults:
    Choice:
      Default-Options: ""
```

`Default-Options` is a comma-separated string. The plugin splits on `,` and trims each entry. With the default empty value, a `<d:choice:Display>` row has no options to show and renders a dropdown with a single empty entry.

A row can override the options:

```yaml
<d:choice[Survival,Creative,Adventure,Spectator]:Pick a mode>
```

The plugin splits the bracket content on `,` and trims each option. The label is the same string sent to the command — the player's selected option label is the value that ends up in the command line. The first option is always pre-selected (Paper requires at least one option to carry the `initial` flag).

### `tab` — completion-driven multi-choice

A `tab` row turns the dialog into a multi-action grid where each button is a tab-completion result for the original command:

```yaml
# in prompt-config.yml
DialogUI:
  Defaults:
    Tab:
      MaxButtons: 5
```

```yaml
<d:tab:Pick a target player>
```

When the dialog opens, CommandPrompter asks the server for tab-completion results for the partial command and builds one button per completion. The dialog's behavior depends on how many completions come back:

- **One to `MaxButtons` completions** — each completion becomes a button. Clicking a button dispatches the command with that completion in place of the tag.
- **Zero completions** — the dialog falls back to a plain text input with a yellow notice: `No options available`.
- **More than `MaxButtons`** — the dialog falls back to a plain text input with a yellow notice: `Too many options (N)`.

The fallback path always shows a text input named `answer` and a confirm/cancel button pair. Cancel exits the prompt.

A per-tag override can set the button threshold:

```yaml
<d:tab[10]:Pick a target player>
```

`d:tab` is **only** valid in single-row dialog tags. It is rejected in compound prompt tags — see below.

### `title` — compound static header

A `title` row renders no interactive input — instead, it places a static, bold header block using the display text of the tag:

```yaml
/transfer <d:title:Transfer Details && d:num[1,1000]:Amount && d:text:Reason>
```

`d:title` is **only** valid inside compound dialog tags (`&&`). If used as a standalone single-row prompt, the session is immediately aborted with an invalid configuration warning. When used properly inside a compound, it acts as a visual separator or label, and does not produce an answer value in the encoded payload.

## Compound prompt tags

A compound prompt tag splits one tag into several rows separated by `&&`:

```yaml
/give <d:num[1,32,1]:Rows && d:num[1,32,1]:Cols && d:text[0,40]:Name>
```

The whole `<...>` block is one prompt tag. Inside the angle brackets, the rows are independent sub-tags with their own `key`, `filter`, and `display` text. Each sub-tag follows the same `key:filter:display` shape as a single tag. The compound key (used for screen routing) is the first sub-tag's key — in the example above, `d`.

With answers `5`, `5`, `My Item` the assembled command is `/give 5 5 My Item` — the tag and its enclosing angle brackets disappear, replaced by three space-separated words.

### What the rows look like

Each sub-tag follows the `key:filter:display` shape:

```yaml
/transfer <d:num[1,1000,1]:Amount && d:choice[Steve,Alex,Sam]:Target && d:text[0,120]:Reason>
```

| Row | Key | Filter            | Display |
| --- | --- | ----------------- | ------- |
| 1   | `d` | `num[1,1000,1]`   | `Amount` |
| 2   | `d` | `choice[Steve,Alex,Sam]` | `Target` |
| 3   | `d` | `text[0,120]`     | `Reason` |

### Flag placement

Flags (`-ds`, `-int`, `-str`, `-iv:<alias>`) always apply to the **entire** prompt tag — there is no row-level flag placement. Where you write them depends on whether the tag is single-row or compound:

**Single-row tags** — flags go **after the display text**:

```yaml
<d:num[1,1000,1]:Amount -iv:notempty>
<d:text:Greeting -ds -str>
```

The parser splits on colons left-to-right (`key:filter:display`), so anything after the second colon is the remainder. Flags are extracted from the remainder, stripped, and what is left becomes the display text. Writing a flag before the filter (e.g. `<d:-iv:notempty:num[1,1000,1]:Amount>`) does **not** work — the parser would treat `-iv` as the filter, which falls back to TEXT, and the validator is never extracted.

**Compound tags** — flags are extracted from the raw content **before** splitting on `&&`, so they apply to **every** sub-answer regardless of where they appear. The clearest place to write them is after the display text of the first sub-tag, consistent with single-row tags:

```yaml
/answer <d:num[1,10]:Count -iv:notempty -int && d:text[0,120]:Reason>
```

The `-iv:notempty` and `-int` flags apply to every row — both `Count` and `Reason` are validated as non-empty integers. Because flags are stripped from the raw content before the `&&` split, a flag written "inside" a sub-tag is hoisted to the block level:

```yaml
<!-- CAUTION: this does NOT make just the count an integer -->
/answer <d:num[1,10]:Count -int && d:text[0,120]:Reason>
```

The `-int` is hoisted, and the `Reason` row is *also* validated as an integer, which will fail because `Reason` is not numeric. There is no way to apply a flag to only one row of a compound tag.

### Bracket-depth rule

`&&` is only a row separator when it appears at the **top bracket level**. Inside `[…]` (filter options), `&&` is part of the filter content and does not split a row. This matters when a row's filter has a list:

```yaml
<d:choice[Tom && Jerry,Bugs && Bunny]:Pick a duo>
```

This is one row with two choices (`"Tom && Jerry"`, `"Bugs && Bunny"`), not three rows.

### What compound dialogs do not support

- **`d:tab` in any sub-tag.** If any sub-tag's filter resolves to `tab`, the parser throws `IllegalArgumentException: d:tab is not allowed in compound prompt tags` and the prompt never starts. The single-row `d:tab` form is the only way to use tab-completion — see above.
- **Different screen keys per row.** Every sub-tag in a compound block uses the same key as the first sub-tag. The `kind` is computed per-row from each sub-tag's filter; rows of different kinds can be mixed freely.
- **Empty rows.** Sub-contents that are empty after trimming are silently skipped (logged at FINE level). A compound that produces zero sub-tags is treated as a no-op; the prompt tag is dropped and no dialog opens.

### Type and validator resolution

For a single prompt tag, the `-int` / `-str` type constraint and the `-iv:<alias>` validator each apply to the single answer. For a compound prompt tag, the type and validator apply to **every** sub-answer in the compound — there is no per-row type or validator. Flags written inside a sub-tag are hoisted to the block level before splitting, so they affect all rows. A compound tag with `-int` will coerce every sub-answer to an integer; with `-iv:notempty` every sub-answer must pass the `notempty` validator.

### How many answers the dialog collects

The number of answers is the number of non-empty sub-tags after parsing. The dialog's `MaxButtons` (for `tab`) and `MaxLength` (for `text`) still apply per-row. Each row's answer is validated independently against the block-level type/validator, then submitted as a separate entry in the session's answer list.

## How answers are packed

When the player confirms a multi-row dialog, the answers are joined with the unit separator (`\u001F`, `0x1F`) and wrapped with the record separator (`\u001E`, `0x1E`):

```
\u001E answer0 \u001F answer1 \u001E
```

A single-row dialog sends the bare answer with no wrapping — `hello` instead of `\u001Ehello\u001E`. This is the same format `AnswerEncoding.decode` expects on the way in: any deviation (missing wrapping separator, wrong number of parts) is treated as a malformed payload, the dialog re-opens with a warning, and the player can retry.

When the assembled command line is built, the whole compound tag is replaced with the sub-answers space-joined in row order. So `/transfer <d:num[1,1000,1]:Amount && d:choice[Steve,Alex,Sam]:Target && d:text[0,120]:Reason>` with answers `500`, `Steve`, `some-reason` becomes `/transfer 500 Steve some-reason` — the tag and its enclosing angle brackets disappear, replaced by three space-separated words.

To consume the values in a post-command (which runs after the main command), use the `{N}` answer references described in [11-post-commands.md](11-post-commands.md). `{0}` is the first sub-answer, `{1}` the second, and so on, in the order the rows appear in the compound:

```yaml
/transfer <d:num[1,1000,1]:Amount && d:choice[Steve,Alex,Sam]:Target && d:text[0,120]:Reason><!broadcast Transfer of {0} {1} confirmed>
```

With answers `500`, `Steve`, `some-reason` the assembled command is `/transfer 500 Steve some-reason` and the post-command `/broadcast Transfer of 500 Steve confirmed` runs afterwards.

## Confirm and cancel behavior

- **Confirm** — The dialog reads each row's answer, encodes them with the format above, and dispatches the assembled command (with `{N}` references resolved for any post-commands).
- **Cancel** — The dialog closes without dispatching the command. If a post-command tagged `!!` is attached (see [11-post-commands.md](11-post-commands.md)), it runs instead. Otherwise the prompt just ends.

## Examples

A two-row dialog that asks for a count and a target:

```yaml
/xp give <d:num[1,100,1]:Levels && d:choice[Steve,Alex,Sam]:Player>
```

A choice dialog using a custom list:

```yaml
/gamemode <d:choice[Survival,Creative,Adventure,Spectator]:Pick a mode>
```

A number dialog with a custom initial value:

```yaml
/speed <d:num[0,100,5,50]:Set fly speed>
```

A tab-completion dialog with a small completion set:

```yaml
/tp <d:tab:Target player>
```

A tab-completion dialog with a custom button threshold:

```yaml
/tp <d:tab[10]:Target player>
```

A compound dialog with a block-level validator and a post-command that uses the answers:

```yaml
/transfer <d:num[1,1000,1]:Amount && d:choice[Steve,Alex,Sam]:Target && d:text[0,120]:Reason -iv:notempty > <!broadcast Transfer of {0} {1} confirmed>
```

A compound dialog with a block-level integer type constraint:

```yaml
/set <d:num[1,10]:Count && d:num[1,10]:Limit -int>
```

## Common pitfalls

- **Empty rows.** A sub-tag whose body is empty after trimming is silently skipped, not an error. If every sub-content trims to empty the whole tag is dropped.
- **Mismatched row count.** If the player's answer is malformed (the server sees the wrong number of parts after decoding), the dialog re-opens with a warning rather than failing silently. The warning is logged at WARN level.
- **`d:tab` in a compound block.** The error is immediate and visible: `IllegalArgumentException: d:tab is not allowed in compound prompt tags`. Either move the `tab` row out into its own prompt tag or remove the `&&` to make it single-row.
- **Flags are always block-level.** There is no per-row flag placement. The parser strips every flag token (`-ds`, `-int`, `-str`, `-iv:alias`) from the raw content before splitting on `&&`, so a flag written "inside" a sub-tag is hoisted to the block level and applies to **all** rows. Writing `<d:num[1,10]:Count -int && d:text[0,120]:Reason>` does not make just the count an integer — the `-int` is hoisted, and the `Reason` row is *also* validated as an integer, which fails because `Reason` is not numeric. For single-row tags, flags go after the display text: `<d:num[1,10]:Count -int>`.
- **`{N}` references in the main command body.** The main command does not have `{N}` references — the compound tag itself is replaced with the space-joined sub-answers. `{N}` references are only meaningful inside post-commands (`<!...>` / `!!...`).
- **`bool` is not a kind.** The filter `bool` (or `bool[true]`/`bool[false]`) resolves to TEXT, not a dedicated boolean input. There is no boolean dialog kind; use `choice[true,false]` instead.

## See also

- [05-prompt-syntax.md](05-prompt-syntax.md) — the `<key[:filter][:display]>` tag shape, escape rules, and tag-level flags
- [06-screens.md](06-screens.md) — how the `d` screen key maps to the dialog screen and the fallback chain
- [07-validators.md](07-validators.md) — input validators and the `-iv:<alias>` flag
- [11-post-commands.md](11-post-commands.md) — `{N}` answer references, `<!` / `!!` post-command syntax, dispatch targets