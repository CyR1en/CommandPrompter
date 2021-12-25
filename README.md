## ![cp](https://www.spigotmc.org/data/resource_icons/47/47772.jpg?1506763424) CommandPrompter [![Actions Status](https://github.com/CyR1en/CommandPrompter/workflows/Java%20CI/badge.svg)](https://github.com/CyR1en/CommandPrompter/actions)

_Making commands more interactive._ 
### What is this plugin for?
CommandPrompter is for plugins such as Chest Commands. Where players could click an Item in the GUI and execute a command that have been pre-defined in the config as the player that clicked the item. The downside of Chest Command is that you already have to provide a command argument for an IconCommand(The item). Now with CommandPrompter, you can configure that pre-defined command for an Item to have an argument prompt.

### How do I use this plugin?
If a player execute a command with arguments that are surrounded by "<" and ">". Those arguments are considered prompts. So after a player executes a command with prompts. CommandPrompter will send those prompts back to the player without the "<>" one by one and wait for the player's response. CommandPrompter will keep sending prompts until all the arguments have been replaced with the player's response.

#### example:
```
Player executes: /gamemode <What game mode do you want to switch to?>
CommandPrompter sends: What game mode do you want to switch to?
Player sends: creative
```
All prompts have been answered, CommandPrompter now makes the player execute the command "/gamemode creative".

So on your Chest Command. Just configure an item to have a command that have argument prompts (surrounded by <>) and that command will be dispatched by the player when the Item gets clicked.

### Installation
Just drag and drop the jar file to your plugins folder. No need to configure anything, no permissions, and no commands. But there is a config file if you do want to configure it. Should work like a charm.

### Attribution
[KujouMolean](https://github.com/KujouMolean): Player GUI implementation.
#### Disclaimer:
The idea for this plugin came from the user named Tommiek on Bukkit forums. Someone already wrote a similar plugin according to the thread, but the project page for that plugin can no longer be found. So I tried to recreate the plugin. The link for that thread is here.

_Updates will be released._
