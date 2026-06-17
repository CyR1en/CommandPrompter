# DeluxeMenus Integration Example

CommandPrompter integrates seamlessly with menu plugins like **DeluxeMenus** to add interactivity to normally static GUI buttons. By putting CommandPrompter syntax directly into the commands executed by your DeluxeMenus items, you can prompt players for input (text, numbers, player names) before executing the final command.

This guide provides a sample menu configuration showcasing the different types of prompts CommandPrompter offers.

## Sample Configuration

Create a new file in your `plugins/DeluxeMenus/gui_menus/` directory (e.g., `prompts_example.yml`) and paste the following configuration:

```yaml
menu_title: '&#FB0000C&#FB0C09o&#FC1712m&#FC231Am&#FC2E23a&#FD3A2Ca&#FD4535n&#FD513Ed&#FE5C47P&#FE6850r&#FE7359o&#FF7F62m&#FF8A6Bp&#FF9674t&#FFA17De&#FFAD86r &#FFB88FExamples'
open_command: promptmenu
size: 27

items:
  'chat_prompt':
    material: PAPER
    slot: 10
    display_name: '&eChat Prompt Example'
    lore:
      - '&7Click to broadcast a message.'
      - '&7Uses standard chat input.'
    left_click_commands:
      - '[close]'
      - '[player] say <Enter your message>'

  'anvil_prompt':
    material: ANVIL
    slot: 11
    display_name: '&aAnvil Prompt Example'
    lore:
      - '&7Click to rename your held item.'
      - '&7Uses an Anvil GUI for input.'
    left_click_commands:
      - '[close]'
      - '[player] rename <a:Enter new item name>'

  'player_prompt':
    material: PLAYER_HEAD
    slot: 12
    display_name: '&bPlayer Prompt Example'
    lore:
      - '&7Click to heal a player.'
      - '&7Prompts for an online player name.'
    left_click_commands:
      - '[close]'
      - '[console] heal <p:Select Player>'

  'dialog_number':
    material: GOLD_INGOT
    slot: 14
    display_name: '&6Dialog Prompt (Number)'
    lore:
      - '&7Click to give yourself money.'
      - '&7Uses a constrained number dialog.'
    left_click_commands:
      - '[close]'
      - '[console] eco give %player_name% <d:num[1,1000]:Amount to receive>'

  'compound_dialog':
    material: COMMAND_BLOCK
    slot: 15
    display_name: '&dCompound Dialog Example'
    lore:
      - '&7Click to configure a game setting.'
      - '&7Combines multiple inputs in one UI.'
    left_click_commands:
      - '[close]'
      - '[console] gamerule <d:choice[keepInventory,mobGriefing]:Rule> <d:bool[true]:Value>'

  'post_command_meta':
    material: REDSTONE_TORCH
    slot: 16
    display_name: '&cPost-Command Meta'
    lore:
      - '&7Click to teleport to spawn.'
      - '&7Sends a follow-up message after.'
    left_click_commands:
      - '[close]'
      - '[player] spawn <! msg %player_name% You teleported! @console>'

  'preset_prompt':
    material: EMERALD
    slot: 17
    display_name: '&aPreset Example'
    lore:
      - '&7Click to trigger a predefined JSON preset.'
      - '&7Uses <@ban_form> defined in presets.json'
    left_click_commands:
      - '[close]'
      - '[console] ban %player_name% <@ban_form> <!@log_reason>'
```

## How It Works

DeluxeMenus evaluates its commands top-to-bottom. When a user clicks an item:
1. `[close]` fires immediately, closing the inventory so the player can see the prompt.
2. The second command is dispatched to the server.
3. **CommandPrompter intercepts the command**, detects the `<...>` tags, and cancels the command execution temporarily.
4. It prompts the player using the requested method (Chat, Anvil, UI Dialog).
5. Once the player provides valid input, CommandPrompter reconstructs the command with the player's answers and runs it!

### Prompt Breakdown from the Example:
* `<Enter your message>`: A basic chat prompt.
* `<a:Enter new item name>`: The `a:` prefix tells CommandPrompter to open an Anvil GUI instead of chat.
* `<p:Select Player>`: The `p:` prefix opens a player selection list (showing online players).
* `<d:num[1,1000]:Amount to receive>`: The `d:` prefix opens the rich Dialog UI, in this case, a number selector constrained between 1 and 1000.
* `<d:choice[...]:Rule> <d:bool[...]:Value>`: Multiple dialog prompts in the same command create a multi-step sequence or compound dialog.
* `<! msg ...>`: A Post-Command Meta (PCM) that runs an extra command after the prompt succeeds.
* `<@ban_form>` and `<!@log_reason>`: Resolves a JSON-defined preset prompt and post-command from `presets.json`.

## Preset Configuration Example (`presets.json`)

To use the preset example shown above (`<@ban_form>` and `<!@log_reason>`), you must define them in CommandPrompter's `presets.json` file. Presets allow you to create reusable, complex UI configurations that keep your menu commands clean.

```json
{
  "prompts": [
    {
      "type": "dialog",
      "id": "ban_form",
      "title": "Ban Player",
      "sanitize": true,
      "rows": [
        {
          "label": "Reason",
          "input_type": "choice",
          "constraints": ["Hacking", "Spam", "Toxicity"]
        },
        {
          "label": "Duration (Days)",
          "input_type": "number",
          "constraints": [1, 365]
        }
      ]
    }
  ],
  "post_commands": [
    {
      "id": "log_reason",
      "command": "discord broadcast {player} punished {input:1} for: {input:2}",
      "execution_policy": "on_complete",
      "execute_as": "console"
    }
  ]
}
```

By defining complex dialogs in JSON, your DeluxeMenus configuration remains readable while unlocking advanced CommandPrompter UI capabilities!