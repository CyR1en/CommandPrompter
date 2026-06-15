# 02 — Quickstart

A five-minute walkthrough: install the plugin, then turn a vanilla command into an interactive prompt with a single line of configuration.

This page assumes you have already followed [01-install.md](01-install.md) and that CommandPrompter is running.

## What a prompt tag looks like

A prompt tag is a chunk of angle-bracketed text inside a command argument. When the player runs the command, the plugin intercepts the tag, asks the player a question, and substitutes the player's answer back into the command before dispatch.

A basic example:

```
/kick <a:Why?>
```

When a player runs `/kick Steve <a:Why?>`, the plugin:

1. Intercepts the `<a:Why?>` part.
2. Opens an Anvil GUI titled "Why?".
3. Waits for the player to type a reason and take the result item out of the anvil.
4. Replaces `<a:Why?>` with the typed reason.
5. Dispatches `/kick Steve <answer>`.

The `a` is the screen-type key. The text after the colon is the prompt's display text. The default screen-type keys are:

| Key | Screen | Use when |
|---|---|---|
| _(empty)_ | `CHAT` | Default. Reads the answer from chat. |
| `a` | `ANVIL` | The player types into an anvil's text field. |
| `s` | `SIGN` | The player types on a sign. |
| `d` | `DIALOG` | A native dialog. Supports text, number, and choice inputs. |
| `p` | `PLAYER` | The player picks a head from a paginated list of online players. |

You can change which screen each key uses with the `screen-mappings:` section in `prompt-config.yml`. See [06-screens.md](06-screens.md) for the full list and configuration.

## Try it: prompt the kick reason

1. Join your server as an operator.
2. In chat, run: `/kick YourName <a:Why?>`.
3. An anvil titled "Why?" opens. Type a reason and take the result item.
4. The plugin dispatches `/kick YourName <your reason>`. Watch the server console — you will see the resolved command and a confirmation log line.

If nothing happens, check [13-troubleshooting.md](13-troubleshooting.md).

## Add the prompt to another plugin's command

Most prompt users wrap prompts in their own commands, or in commands from other plugins. The plugin does not own the command — it just intercepts the prompt tag in the command string.

For example, to make a `/warn` command require a reason:

```
/warn Steve <a:Reason?>
```

The argument-regex default is `<.*?>`, which matches the angle-bracketed form. The plugin only intercepts arguments that match this pattern, so the rest of the command passes through unchanged. See [04-config-reference.md](04-config-reference.md#argument-regex) for the `Argument-Regex` option.

## Sanitization: the `-ds` flag

By default, the plugin strips color codes and special characters from the player's answer before substituting it back into the command. This prevents players from injecting color codes or `&` characters into arguments that don't expect them.

If you want to allow color codes in an answer (for example, the reason for a kick is shown in chat and you want it color-formatted), add `-ds` (disable sanitize) to the tag:

```
/kick <a:Why? -ds>
```

The display text is the part after the colon and before any flag.

## Multiple prompts in one command

You can chain multiple prompts in a single command. The plugin asks them in order:

```
/transfer Steve <a:DestinationServer?> <a:Reason?>
```

The player answers the destination first, then the reason.

## Cancel a prompt

A player can type the configured cancel keyword (default: `cancel`) to abort the active prompt. The session ends without dispatching the command. The cancel keyword is `cancel` by default; change it with `Cancel-Keyword` in `config.yml` (see [04-config-reference.md](04-config-reference.md#cancel-keyword)).

Players can also run `/commandprompter cancel` to abort their own active session. See [03-commands.md](03-commands.md#commandprompter-cancel).

## Reload after editing config

After changing `config.yml` or `prompt-config.yml`, run `/commandprompter reload` in-game or from the server console. The plugin cancels every active session, re-reads the YAML, and applies the new settings. See [03-commands.md](03-commands.md#commandprompter-reload).

## Next steps

- Need the full syntax (filters, flags, answer references, post-commands)? See [05-prompt-syntax.md](05-prompt-syntax.md).
- Want to restrict prompt use by permission? See [04-config-reference.md](04-config-reference.md#enable-permission) and [09-permissions.md](09-permissions.md).
- Want to validate the answer (regex, online-player check, JS expression)? See [07-validators.md](07-validators.md).
