# 15. Migrating from v2 to v3

Welcome to CommandPrompter v3! If you are coming from v2, this guide will help you understand what has changed and how to adapt your existing server setup.

CommandPrompter v3 is a complete rewrite. While the core idea remains exactly the same—turning parts of a command into interactive UI prompts—the underlying engine has been modernized specifically for Paper servers.

This guide is written for server administrators and focuses on what you need to change.

## 1. Server Requirements

**v2:** Ran on Spigot and Paper across many older Minecraft versions.
**v3:** Is a **native Paper plugin**. Currently, it **strictly requires Minecraft 1.21.4**. 
Because it uses version-specific NMS (Nether Minecraft Server) code for its UI screens, it will not run on versions below 1.21.4. It will also not work on versions newer than 1.21.4 until a matching NMS UI provider update is shipped for that specific future version. It also officially supports **Folia**.

## 2. The New "Dialog" Prompt (`<d:...>`)

v2 relied heavily on Anvils (`<a>`), Chat (`<c>`), and Signs (`<s>`) to get input from players. 

v3 introduces a much better native Paper feature: **Dialogs (`<d:...>`)**.

Dialogs are the modern, native way to collect input in Minecraft. They support:
*   Plain text inputs.
*   Number sliders with minimums and maximums (e.g., `<d:num[1,10]:How many?>`).
*   Dropdown choice menus (e.g., `<d:choice[Apple,Banana]:Pick one>`).
*   Tab-completion button grids (`<d:tab:Player>`).

**Migration Action:** We highly recommend switching your old Anvil (`<a:...>`) prompts to Dialog (`<d:...>`) prompts wherever possible. They are much less buggy than Anvils and provide a cleaner experience for players.

## 3. Compound Dialogs (Multiple inputs at once!)

In v2, if you needed 3 arguments, the player had to go through 3 separate Anvil screens, one after the other. 

In v3, Dialogs support **Compound Tags** using `&&`. This allows you to ask for multiple pieces of information on a *single* screen.

*   **Example:** `/give <d:num[1,64]:Amount && d:text:Reason>` will open one single window with a slider for the amount and a text box for the reason.

## 4. Plugin Integrations (Hooks)

v2 required you to manually toggle certain hooks. 
v3 automatically detects your plugins. If you have PremiumVanish, LuckPerms, PlaceholderAPI, or Towny installed, CommandPrompter v3 will find them and use them automatically. No extra configuration is needed to turn them on.

## 5. Updating Prompt Syntax

In v2, if you wanted to specify which screen to open, you used **Prompt Arguments** inside the tag (like `<-a>` for Anvil or `<-s>` for Sign). For example, an Anvil prompt looked like `<-a What is the reason?>`.

In v3, the tag syntax has been completely redesigned. Prompt Arguments for screen types no longer exist. Instead, the syntax is strictly structured as `<key:filter:display>`:

*   **`key`**: A single letter prefix that tells the plugin *which screen* to open (`a` for Anvil, `s` for Sign, `d` for Dialog, `p` for Player picker). If you leave it empty, it defaults to **CHAT**.
*   **`filter`**: (Optional) Used mostly by Dialogs or Player pickers to specify the input type or range (e.g., `num`, `choice`, `tab`, `r50`).
*   **`display`**: The text the player actually sees.

**Migration Action:** 
You must update your old tags to the new prefix syntax.

*   **Old Anvil:** `<-a Reason>` ➔ **New Anvil:** `<a:Reason>`
*   **Old Sign:** `<-s Reason>` ➔ **New Sign:** `<s:Reason>`
*   **Old Player UI:** `<-p Player>` ➔ **New Player UI:** `<p:Player>`

*(Note: In v3, if you leave a tag as just `<Reason>` with no key, it will default to a **Chat prompt**!)*

## 6. Commands and Permissions

The base plugin commands and all internal permissions have been renamed to match the modern Paper standard.

*   **Commands:** The main command `/commandprompter` is still available, but you can use the shorter aliases `/prompt` or `/cmdp`.
*   **Permissions:** The `commandprompter.*` permission space is entirely replaced by `promptpaper.*`.
    *   `commandprompter.use` ➔ `promptpaper.use`
    *   `commandprompter.reload` ➔ `promptpaper.reload`
    *   `commandprompter.cancel` ➔ `promptpaper.cancel`

v3 also introduces new commands and permissions for delegation (`/consoledelegate` and `/playerdelegate`), which require `promptpaper.delegate.console` and `promptpaper.delegate.player`.

**Migration Action:** Update your permissions plugin (e.g., LuckPerms) to grant the new `promptpaper.*` permissions to your players/staff, and remove the obsolete `commandprompter.*` nodes.

## Summary Checklist for Upgrading

1.  **Backup** your old CommandPrompter folder (just in case you need to reference your old prompts or messages).
2.  **Delete** the old CommandPrompter `.jar` and folder.
3.  **Install** the v3 `.jar` and start your server to generate the new `config.yml` and `prompt-config.yml`.
4.  **Transfer** your old settings (like timeouts, prefixes, and custom messages) into the new `config.yml`.
5.  **Review** your existing commands. Your old `<a:Prompt>` tags will still work, but consider upgrading them to `<d:Prompt>` to take advantage of the new Paper Dialog UI.

Need more help? Check out the [Troubleshooting](13-troubleshooting.md) guide or read through the [Quickstart](02-quickstart.md) to see the new v3 features in action!
