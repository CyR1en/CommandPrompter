# 07 — Validators

A **validator** checks the player's answer before the prompt accepts it. If the validator returns `false`, the player sees the configured error message and the prompt stays open.

Validators are referenced from prompts via the `-iv:<alias>` flag. See [05-prompt-syntax.md](05-prompt-syntax.md#flag-tokens).

## How validators are configured

Validator aliases are defined in `prompt-config.yml` under `Input-Validation`. Each entry is a named section with an `Alias` field (the string you pass to `-iv:`), a `Regex` field, and an `Err-Message` field. The default config ships two examples:

```yaml
Input-Validation:
  Integer-Sample:
    Alias: 'is'           # the alias you pass to -iv:is
    Regex: '^\d+'
    Err-Message: "&cPlease enter a valid integer!"
  Alpha-Sample:
    Alias: 'ss'           # the alias you pass to -iv:ss
    Regex: '[A-Za-z ]+'
    Err-Message: "&cInput must only consist letters of the alphabet!"
```

The section name (`Integer-Sample`, `Alpha-Sample`) is arbitrary. The `Alias` field value is what you reference in a tag:

```
/set <a:Amount? -iv:is>
/set <a:Code?   -iv:ss>
```

You can add as many aliases as you like. The same `Alias`, `Regex`, and `Err-Message` fields apply to every entry you define. A `JS-Expression` field and an `Online-Player: true` field are also supported (see built-in validators below).

## Built-in validators

The runtime ships four validator implementations. Three are config-backed (`RegexValidator`, `OnlinePlayerValidator`, `JSExprValidator`); the fourth is a no-op placeholder.

### RegexValidator

Backed by a `Pattern` compiled from the alias's `Regex` field. The full input must match the regex (uses `Matcher.matches`, not `find`). The pattern is anchored implicitly — `^[0-9]+$` and `[0-9]+` are equivalent.

Implements `CompoundableValidator`; the default composition type is `AND`. See [Composition](#composition-andor) below.

### OnlinePlayerValidator

Returns `true` if `Bukkit.getPlayer(input)` is non-null and the player is online. Useful for prompts that need a real player name (e.g. a target of `/tp`, `/msg`).

Implements `CompoundableValidator`; default type `AND`.

### JSExprValidator

Evaluates a JavaScript expression through Nashorn. The expression is configured per-alias in `Input-Validation` and may reference:

| Binding | Description |
|---|---|
| `%prompt_input%` | The current input string (replaced before evaluation). |
| `BukkitServer` | `Bukkit.getServer()`, so the expression can call any server API. |
| `BukkitPlayer` | The player whose input is being validated. Bound at evaluation time, only when the input player is non-null. |

PlaceholderAPI placeholders are resolved before evaluation, when the Papi hook is active. See [08-hooks.md](08-hooks.md).

Implements `CompoundableValidator`; default type `AND`. The expression must evaluate to a JavaScript `true` boolean. Anything else (a number, a string, `null`, an exception) is treated as `false`.

### NoopValidator

A no-op that always returns `true`. Used internally as a placeholder; you generally don't reference it from config.

## Composition (AND/OR)

Multiple validators can be combined into a single `-iv:<alias>` slot. The composition is configured in the `Input-Validation` YAML itself: any alias may list more than one `Regex` (or other field) — when a validator implements `CompoundableValidator`, you can change its composition type.

When a `CompoundedValidator` runs, it partitions its members into two groups:

- **AND group** — every validator in this group must pass.
- **OR group** — at least one validator in this group must pass.

Both groups must succeed for the compound to accept the input. Validators that are not `CompoundableValidator` (e.g. a future built-in) default to AND membership.

`JSExprValidator`, `OnlinePlayerValidator`, and `RegexValidator` all default to `Type.AND` (`DEFAULT_TYPE = Type.AND`).

## How a failed validation behaves

When validation fails:

1. The validator's `messageOnFail` is sent to the player.
2. The screen stays open. The player is expected to re-enter.

The exact screen mechanics are screen-specific:

- **Chat**: the prompt re-prints and the next chat input is captured again.
- **Anvil**: the answer is rejected; the GUI stays open with the same label.
- **Sign**: the answer is rejected; the sign editor stays open.
- **Dialog**: validation runs at the `confirm` action. If it fails, the dialog stays open with the same inputs and a message is appended to the chat.
- **Player UI**: validation isn't applied to head clicks (the click is itself the answer).

## Type flag interactions

`-int` and `-str` are not validators — they are answer-type constraints on the tag itself (see [05-prompt-syntax.md](05-prompt-syntax.md#flag-tokens)). They run before any `-iv:` validator:

| Flag | Effect on parsing |
|---|---|
| `-int` | The answer must parse as an `Integer`. A failed parse cancels the prompt. |
| `-str` | The answer must be a non-empty string. Empty / whitespace answers are rejected. |

You can combine `-int` with a regex validator:

```
/set <a:Amount? -int -iv:is>
```

The `-int` flag guarantees the answer is parseable as an integer; the `-iv:is` validator then runs a regex against it (e.g. a range like `^[0-9]{1,4}$`). They are complementary, not redundant.

## Adding a new validator

To add a custom validator, add a new entry under `Input-Validation` with an `Alias`, `Regex`, and `Err-Message`:

```yaml
Input-Validation:
  AmountRange:
    Alias: 'range'
    Regex: '^[0-9]{1,4}$'
    Err-Message: "<red>Enter a number from 0 to 9999.</red>"
```

Reference it with `-iv:range` in any tag.

```yaml
# /set <a:Amount? -iv:range>
```

The same alias is available to every prompt that names it. To rename or remove an alias, edit the YAML and run `/commandprompter reload`.

## Next steps

- A specific use case? Try the `d:num[...]` filter on a dialog tag for inline numeric input — see [06-screens.md](06-screens.md#dialog-input-kinds).
- Wiring answers into other plugins? See [08-hooks.md](08-hooks.md).
