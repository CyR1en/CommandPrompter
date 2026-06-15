# 05 — Prompt Syntax

This page is the authoritative reference for the tag syntax that appears inside prompt and post-command command strings. If you only need to write a basic prompt, see [02-quickstart.md](02-quickstart.md) first.

## Overview

A "command string" is the text that gets dispatched once the player has answered every prompt. Two kinds of tags may appear inside that string:

| Tag kind | Delimiter | Purpose |
|---|---|---|
| **Prompt tag** | `<...>` | One prompt for the player. Replaced by the player's answer when the session finishes. |
| **Post-command meta (PCM)** | `<!...>` or `<!!...>` | A follow-up command dispatched after the session completes (or cancels). |

Both tag kinds are extracted by the same parser. The character `<` is the opening delimiter, `>` is the closing delimiter, and `\` is the escape character.

## Prompt tag shape

```
<[key][:filter][:display] [flags]>
```

Every segment is optional. The parser splits the inside of a tag on the first two colons, so the form mirrors Player UI's two-colon shape.

| Segment | Required? | Meaning |
|---|---|---|
| `key` | No | A short key that selects the screen type. The default screen-mappings are: `a` → anvil, `s` → sign, `d` → dialog, `p` → player (head) selector. An empty key falls back to chat. See [04-config-reference.md](04-config-reference.md) and [06-screens.md](06-screens.md). |
| `filter` | No | A second colon-separated slot. For dialog tags, this is the input kind (`text`, `bool`, `num`, `tab`) with optional constraint block. For player tags, this is the rank range (e.g. `r100`). For other tags it is reserved. |
| `display` | No | The text shown to the player (anvil label, sign text, dialog label). Stripped of all flag tokens. |

### Basic examples

```
/kick <>                              <-- empty key = chat prompt, empty display
/kick <Why?>                          <-- chat prompt with display text "Why?"
/ban <a:Why?>                         <-- anvil prompt, label "Why?"
/msg <p:Who?>                         <-- player-selector prompt, label "Who?"
/warn <s:Reason? -str>                <-- sign prompt, label "Reason?", must be a non-empty string
/test <d:Confirm?>                    <-- dialog prompt, text input, label "Confirm?"
```

### Flag tokens

Flags are recognized anywhere in the display-text segment and stripped before the player sees it. Combine them freely.

| Flag | Effect |
|---|---|
| `-ds` | Disable answer sanitization. By default color codes and symbol-only answers are stripped. |
| `-iv:<alias>` | Apply the input validator registered under `<alias>`. See [07-validators.md](07-validators.md). |
| `-int` | The answer must parse as an integer. |
| `-str` | The answer must be a non-empty string. |

Example combining flags:

```
/ban <a:Enter name -str -iv:required -ds>
```

This produces an anvil prompt labeled `Enter name`, the answer must be a non-empty string, and color codes are not stripped.

### Escaping delimiters

To include a literal `<` or `>` in display text without opening a new tag, prefix it with `\`. The parser strips the escape and the delimiter passes through.

```
say "Use \<this\> for help"
```

Renders as `Use <this> for help` with no tag detected.

### Compound dialog tags

A dialog tag may contain multiple input rows separated by `&&`. The whole block is one dialog screen; each sub-tag is a row. Sub-tags use the same `key[:filter]:display` shape, and the block-level flags are shared.

```
/setup <d:choice[set,add]:Sub && d:num[0,24]:Value>
```

`d:tab` is rejected inside a compound block — a single dialog cannot show both a per-button selector and a text field, so the parser throws `IllegalArgumentException` at parse time.

## PCM (post-command meta) shape

A PCM is dispatched after the session ends. Two variants:

| Variant | Runs on |
|---|---|
| `<!cmd>` | Successful completion (all prompts answered). |
| `<!!cmd>` | Cancellation (player typed the cancel keyword, /commandprompter cancel, or timeout). |

Both variants support the same optional modifiers:

```
<[!|!!][:[delay-ticks]] [command text with {N} refs] [@console|@player]>
```

| Modifier | Position | Effect |
|---|---|---|
| `!` or `!!` | Opening | On-complete or on-cancel trigger. |
| `:N` | Right after the `!`/`!!` | Wait N ticks before dispatching. `0` (the default) means immediate. |
| `@console` | Anywhere in the body | Dispatch the command as the server console, bypassing player permissions. |
| `@player` | Anywhere in the body | Dispatch as the prompting player. |
| _(no target token)_ | — | Default: dispatch through the same executor as the original command (passthrough). |
| `{N}` | Anywhere in the body | Substitute the Nth prompt's answer (0-indexed). |

### PCM examples

```
/kick <> <! ban {0}>
/kick <> <!! msg {0}>
/tempban <a:Why?> <!:20 log {0} @console>
/warn <> <!!:50 notify {0} @console>
/calc <a:first> <a:second> <! do {0} {1}>
```

`delayTicks` is in Minecraft ticks (20 ticks ≈ 1 second). The on-cancel variant `!!` is independent of the delay: `<!!:50 ...>` waits 50 ticks and then runs the command on cancel.

### Dispatch targets

`@console` and `@player` are tokens parsed from the body and removed before the command is dispatched. The PCM record stores a `DispatchTarget` enum value:

| Enum | Meaning |
|---|---|
| `PASSTHROUGH` | Use the executor of the original command (the default). |
| `CONSOLE` | Dispatch as the server console. |
| `PLAYER` | Dispatch as the prompting player. |

## Mixing prompts and PCMs

Prompts and PCMs may appear in any order in the command string. The parser collects each into its own list, and the order within each list reflects the order the tags appeared in the source.

```
/ban <a:Why -str> <! tempban {0} 7d>
```

One prompt (an anvil) plus one on-complete PCM. On answer, the player's reply is substituted into `tempban {0} 7d` and dispatched.

## Where this fits in the pipeline

1. The raw command string is fed to `CommandLineParser.parse(...)`.
2. The parser returns a `ParsedCommand` with the original template plus an ordered list of `PromptTag` and `PostCommandMeta` records.
3. The session collects answers into a list.
4. At completion, `ParsedCommand.buildPartialCommand` replaces the first N answered tags, strips un-answered tags, and removes PCMs — producing a partial command suitable for Brigadier parsing.
5. PCMs run with their recorded `dispatchTarget` and `delayTicks`.

For the runtime behavior of prompts (cancellation, timeouts, session state), see [13-troubleshooting.md](13-troubleshooting.md). For the available input kinds and their constraint blocks, see [06-screens.md](06-screens.md).

## Quick reference card

```
Prompt tag:   <[key][:filter][:display] [-ds] [-iv:<alias>] [-int|-str]>
PCM:          <[!|!!][:N] [command] [with {0}, {1}, ...] [@console|@player]>
Compound:     <d:filter1:disp1 && d:filter2:disp2 [block flags]>
Escape:       \<  \>
Answer ref:   {0}, {1}, {2}, ...
```

## Next steps

- Want to see every flag in context? See [02-quickstart.md](02-quickstart.md).
- Need to validate answers? See [07-validators.md](07-validators.md).
- Picking a screen type? See [06-screens.md](06-screens.md).
