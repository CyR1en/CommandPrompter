# 11 — Post-commands

A **post-command** (PCM) is a piece of command text attached to a prompt that runs **after** the session ends. PCMs come in two flavors: **on-complete** (runs when every prompt is answered) and **on-cancel** (runs when the player cancels, times out, or disconnects). Each PCM can reference any collected answer by index, run on a delay, and be dispatched as the console or the player.

Source: `PostCommandMeta.java` (the data shape), `CommandLineParser.java#parsePCM` (the grammar), `PromptEngine.java#dispatchPCMs` (the runtime), `PromptSession.java#resolvePCMReferences` (the `{N}` substitution). For the prompt syntax up to the `!`/`!!` marker, see [05-prompt-syntax.md](05-prompt-syntax.md).

## Where PCMs live in a command line

PCMs use the same `<...>` delimiters as prompt tags, but the body starts with `!` (on-complete) or `!!` (on-cancel). The parser dispatches a tag to the prompt or PCM path based on the leading `!`; anything else is a prompt tag. PCMs are extracted in source order alongside prompt tags.

## Grammar

A PCM is structured as a sequence of optional sections followed by the command body:

```
<! [:delay] [@target] {command-with-{N}-references}>
<!![:delay] [@target] {command-with-{N}-references}>
```

The parser reads the sections in this fixed order:

1. **Marker** — `!` (on-complete) or `!!` (on-cancel). Required.
2. **Delay** — `:N` where `N` is a non-negative integer in ticks. `0` (or absent) means immediate. Tick count is 20 ticks per second.
3. **Target** — `@console` or `@player`. Omit for the default, which is `PASSTHROUGH`.
4. **Command body** — the rest, with `{0}`, `{1}`, ... references substituted at dispatch time against the collected answers.

Whitespace between sections is trimmed. The command body is everything left after the marker, delay, and target are stripped. Reference extraction (`answerRef = \{(\d+)\}`) happens on the raw body; substitution happens at dispatch.

## On-complete vs on-cancel

The marker `!` runs the PCM when the **session completes normally** (all prompts answered and the assembled command is dispatched). The marker `!!` runs when the session **ends in cancellation** — that includes the player typing the cancel keyword, closing the screen, the timeout firing, the player running `/commandprompter cancel`, and the session being torn down on plugin reload.

Cancellation can happen at any point. If the player answers the first prompt and then cancels the second, the on-cancel PCMs run with whatever answers were collected so far. A `{1}` reference in an on-cancel PCM is empty (and gets stripped) if the player never reached the second prompt — see [Unresolved references](#unresolved-references).

## Reference resolution

`{N}` is a 0-based index into the collected answers, in the order the prompt tags were parsed. The first prompt in the command line is `{0}`, the second is `{1}`, and so on. Compound dialog tags count as one prompt per `&&`-separated row in the answer list — see [12-dialogs-compound.md](12-dialogs-compound.md).

The substitution happens in `PromptSession.resolvePCMReferences` (line 300) at session finish, **not** at parse time. That means the parser stores `{N}` references in the `PostCommandMeta.command()` string verbatim; they are resolved when the PCM is about to dispatch. This is also true for cancellation: if the player answers `{0}` and `{1}` and then cancels, an on-cancel PCM that references `{2}` will see `{2}` stripped with a warning logged at WARN level (`Unresolved PCM reference {2} in command: ...`).

After substitution, runs of whitespace are collapsed to single spaces and the result is trimmed.

## Delay

`!:N` schedules the PCM `N` ticks after the dispatch (or cancel) that triggered it. Positive delays use the scheduler's `runLater`; zero or absent delay runs synchronously via `runSync`. Both run on the server's main thread.

A delay of `!:0` is identical to omitting the `:N` segment. Negative values are not allowed (the parser only consumes digits; a leading `-` would not match `:(\\d+)` and would be left in the command body).

## Dispatch target

The target decides which command sender the PCM runs as:

| Marker | Behavior |
|---|---|
| `@console` | `Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ...)` |
| `@player` | `Bukkit.dispatchCommand(player, ...)` |
| _omitted_ (default `PASSTHROUGH`) | In v3 the runtime also routes this to the console sender. The two markers differ in intent only; the dispatcher is identical. |

The `@player` form makes the player the sender for plugins that read `PlayerCommandPreprocessEvent` or check `CommandSender.isOp()`. The default (no marker) is the right choice for anything that does not need to be attributed to a specific player.

## Examples

PCMs are easier to read in a real command line. Every example below is a complete command a player might type.

### Log every successful warn

```
/warn <a:Why?> <!log warn {0}>
```

On submit, the plugin runs the player's `/warn` command with the answer substituted, then runs `log warn <reason>` as the console. `{0}` is the answer to the "Why?" prompt.

### Run something on cancel only

```
/kick <a:Why?> <!!notify {0}>
```

If the player submits, nothing extra happens. If they cancel (timeout, close the screen, type the cancel keyword), `notify <target>` is dispatched as the console.

### Delay-then-broadcast

```
/give <a:Item?> <!:200 broadcast Gave {0}>
```

`{0}` is the item (the only prompt answer). Ten seconds after the session completes (200 ticks = 10s), `broadcast Gave <item>` runs as the console.

### On-cancel player-targeted cleanup

```
/ban <a:Reason?> <!!@player cleanup {0}>
```

If the player cancels, the **player** runs `cleanup <target>`. Use this when the cleanup command needs to act as that player (perms, location, etc.).

## Lifecycle

PCMs are extracted at parse time and stored in `ParsedCommand.postCmds()`. The session holds a copy. When the session finishes (either by `submitAnswer` reaching the last prompt, or by `cancel` from the engine), `resolvePCMReferences` rewrites the `{N}` references into concrete strings and the result is handed to `PromptEngine.dispatchPCMs`.

`dispatchPCMs` walks the list and either runs synchronously (delay 0) or schedules via `runLater` (delay > 0). Each PCM's runtime path goes through `executePCM`, which dispatches against the chosen sender and logs at DEBUG level.

If a PCM is empty after substitution (the original command body was whitespace, or the only content was stripped references), the PCM is skipped silently with a debug log. No error is surfaced to the player.

## Errors and edge cases

| Situation | Result |
|---|---|
| PCM has a `{N}` reference where N is larger than the number of collected answers (e.g. player cancels early) | The reference is stripped; a warning is logged at WARN level. The rest of the command still dispatches. |
| PCM is empty after parsing (just `<!>`) | Skipped silently at dispatch time. |
| Multiple PCMs in the same command | All on-complete PCMs run in source order on success; all on-cancel PCMs run in source order on cancel. They do not interleave. |
| Two PCMs with different delays | They schedule independently and can finish in either order. |
| Server reloads mid-delay | The scheduled task is dropped. The PCM does not run on the next start. There is no persistence layer. |
| Target player is offline when a delayed `@player` PCM fires | The plugin still uses the captured `Player` reference; if the player has logged out, `Bukkit.dispatchCommand(player, ...)` will fail with a `NullPointerException` because the underlying `org.bukkit.craftbukkit.entity.CraftPlayer` is invalid. Avoid long delays with `@player`. Use `@console` for anything time-sensitive. |

## What PCMs are **not**

- PCMs are not a way to validate input. That is the `-iv:` flag and the validator config — see [07-validators.md](07-validators.md).
- PCMs are not conditional. Every on-complete PCM in a command runs on every completion. Use the dispatch target to control who runs the command, not to gate execution.
- PCMs are not a substitute for plugins. The `!` marker is parsed before any plugin sees the command, so PCMs can run on behalf of any Bukkit command line that was intercepted by a prompt tag.

## Next steps

- Compound dialogs (one tag, many answers, how `{N}` is allocated)? See [12-dialogs-compound.md](12-dialogs-compound.md).
- Delegation modes for the assembled command? See [10-delegation.md](10-delegation.md).
- A reference of the prompt-tag grammar up to the `!`/`!!` boundary? See [05-prompt-syntax.md](05-prompt-syntax.md).
