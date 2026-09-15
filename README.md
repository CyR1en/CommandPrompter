![](https://cyr1en.gitbook.io/commandprompter/~gitbook/image?url=https%3A%2F%2F2106637744-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Fspaces%252FDaCb7lTXGFkT8XKPSg62%252Fuploads%252Fwk7HgDvFXOlkedwSBG01%252Fimage.png%3Falt%3Dmedia%26token%3De2e0cdeb-8096-4f8c-b71f-8f24d2e97c79&width=768&dpr=2&quality=100&sign=19482eee&sv=2)  

<p align="center">
  <a href="https://github.com/CyR1en/CommandPrompter/actions/workflows/gradle.yml"><img src="https://img.shields.io/github/actions/workflow/status/cyr1en/commandprompter/gradle.yml?style=for-the-badge&logo=githubactions&logoColor=a6da95"></a>
  <a href="https://cyr1en.gitbook.io/commandprompter/"><img src="https://img.shields.io/badge/GitBook-docs-brightgreen?logo=gitbook&style=for-the-badge&color=7dc4e4"></a>
  <a href="https://modrinth.com/plugin/commandprompter"><img src="https://img.shields.io/modrinth/v/1Ne5mutD?style=for-the-badge&logo=modrinth&logoColor=cad3f5&labelColor=363a4f&color=%23a6da95"></a>
  <a href="./LICENSE"><img src="https://img.shields.io/github/license/cyr1en/CommandPrompter?colorA=363a4f&colorB=91d7e3&style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNTYgMjU2Ij4KPHBhdGggZD0iTTIxNiwzMlYxOTJhOCw4LDAsMCwxLTgsOEg3MmExNiwxNiwwLDAsMC0xNiwxNkgxOTJhOCw4LDAsMCwxLDAsMTZINDhhOCw4LDAsMCwxLTgtOFY1NkEzMiwzMiwwLDAsMSw3MiwyNEgyMDhBOCw4LDAsMCwxLDIxNiwzMloiIHN0eWxlPSJmaWxsOiAjQ0FEM0Y1OyIvPgo8L3N2Zz4=&logoColor=cad3f5"></a>
  <a href="https://discord.com/invite/qHM8kE4XHj"><img src="https://img.shields.io/discord/936346802402238514?style=for-the-badge&color=b7bdf8&labelColor=363a4f&logo=discord&logoColor=cad3f5"></a>
  <a href="https://ko-fi.com/cyr1en"><img src="https://img.shields.io/badge/Kofi-Support_Development-f5a97f?style=for-the-badge&logo=Kofi&logoColor=cad3f5&labelColor=363a4f"></a>
</p>

CommandPrompter is a powerful tool that enhances the command prompting experience and menu creation in your Minecraft server. With its customizable configuration options, CommandPrompter allows you to tailor the command prompting process to suit your server's specific requirements.

Features:
* **Easy to use** - designed to be used within minutes after installation.
* **Multiple prompts** - allows you to choose different [prompts](https://cyr1en.gitbook.io/commandprompter/prompts/) for your menus.
* **Multi language support** - supports utf-8 characters to support a wide range of languages.
* **Input validations** - never worry about miss inputs anymore.
* **Console delegate** - a robust way to prompt a player via console.
* **Post command** - expands your command by incorporation post commands.

## Managing prompt presets

Create a structured JSON preset from a complete inline prompt:

```text
/cmdp preset add reason <Please enter a reason:>
/cmdp preset add rename <a:Enter a new name -ds>
/cmdp preset update reason <Why are you reporting this player?>
/cmdp preset remove rename
```

Use the saved preset as `<@reason>` in a command. Add and update translate one inline prompt
(including compound dialogs) into its corresponding JSON definition in
`plugins/CommandPrompterPaper/presets.json`. Configured prompt delimiters and screen mappings apply.
Placeholders remain unexpanded until the preset is displayed. Custom screen providers without
an equivalent built-in JSON type cannot be saved through this command.

Add requires an unused ID. Update replaces the complete definition of an existing prompt preset,
including its type; remove deletes it. Other preset categories cannot be edited with this command.
Changes take effect immediately and survive restarts. Active sessions retain their original definitions.
Update and remove offer existing prompt IDs through TAB completion. Players and console can use the
command with `promptpaper.preset` (operator by default), also included in `promptpaper.admin`.

Execution options such as validators, answer type constraints, timeouts, and `-breakIf` are saved
in the preset's optional `behavior` object. Dialog inputs retain their structured constraints,
layout rows become the dialog title/body, and tab completion uses `dialog_type.actions_source`
with its `max_buttons` threshold. Edit `presets.json` directly for further customization and
run `/cmdp reload` to load those manual changes.

## Migrating V2 prompts

Use `/cmdp migrate` to convert prompts embedded in another plugin's configuration to V3 syntax:

```text
/cmdp migrate --dry-run DeluxeMenus/menus/shop.yml
/cmdp migrate DeluxeMenus/menus/shop.yml
```

Paths are relative to the server's `plugins/` directory. Press TAB to complete folders and files,
including nested paths. Paths containing spaces work with or without surrounding double quotes.
The command and its file suggestions require `promptpaper.migrate` (operator by default), also
included in `promptpaper.admin`.

The dry run shows the number of changed prompts and up to 20 before/after examples. Migration
creates an exact backup under
`plugins/CommandPrompterPaper/migration-backups/<timestamp-id>/<original-relative-path>` before
replacing the file. If nothing needs migration, no backup or file write occurs. Backups are never
overwritten. Each migration prints a command to restore its backup:

```text
/cmdp migrate undo <timestamp-id>/DeluxeMenus/menus/shop.yml
```

For `undo`, paths are relative to `CommandPrompterPaper/migration-backups/`. TAB browses these
backups; the same `promptpaper.migrate` permission applies. Undo restores the selected snapshot
to its original file and first backs up the current contents, including any edits made since
migration. It retains both backups and prints a command that can restore the saved current
contents. Repeating an undo when the file already matches makes no changes or new backup.
The destination file must still exist. Undo uses the same path restrictions, concurrency check,
and atomic replacement as migration. Reload the owning plugin after restoring.

Examples:

| V2 | V3 |
| --- | --- |
| `<-a Enter a name>` | `<a:Enter a name>` |
| `<-s Enter a name>` | `<s:Enter a name>` |
| `<-p:w Select a player>` | `<p:w:Select a player>` |
| `<-exa say p:0>` | `<! say {0}>` |
| `<-exac:20\|c say Cancelled>` | `<!!:20 say Cancelled @console>` |

Supported flags and sign `{br}` lines are preserved. Labels such as `Name:` use an empty V3
filter (`<s::Name:>`) so that they remain display text, including in unquoted YAML values.
The tool uses the active CommandPrompter prompt and template delimiters; set these to match the
old configuration before migrating if your server uses custom delimiters.

Migration operates on UTF-8 text without reserializing the host plugin's configuration. It also
recognizes XML-escaped and JSON Unicode-escaped delimiters. It preserves surrounding comments,
indentation, quoting, Unicode, and line endings. Binary, backup, hidden, and temporary files are
excluded, and the file size limit is 8 MiB. Autocomplete returns up to 100 matches from the first
4,096 entries in the selected directory.

Ambiguous tags, unknown custom prompt types or filters, unsupported escaping, and failed V3
validation are reported with line numbers; any such issue leaves the entire file unchanged.
Plain chat prompts that still have the same syntax and existing V3 prompts are left intact.
Unknown colon-prefixed tags require manual review because they could be old chat text or custom
V3 prompt types. Migration does not translate plugin configuration schemas.

Pause other writers to the selected file while migrating. The command checks for changes before
replacement, rejects simultaneous migrations of the same file, and requires an atomic file
replacement, but cannot coordinate saves made by other plugins. Reload the plugin that owns the
configuration after migration. All results, including no-change and error messages, use the
configured locale and can be customized through the existing `locales/` overrides.

## Building

CommandPrompter uses Gradle as a project manager. You can build CommandPrompter for yourself by following the instructions below:

#### Requirements
* JDK 25
* Git

#### Compiling from source
```sh
git clone https://github.com/CyR1en/CommandPrompter.git
cd CommandPrompter/
./gradlew clean build
```

## Special Thanks To:
<div align="Left">
  <a href="https://www.gitbook.com/">
    <img width="230" src="https://i.imgur.com/SIPKmzS.png">
  </a>

  <p>This project owes a huge thanks to GitBook's fantastic <a href="https://docs.gitbook.com/account-management/plans/apply-for-the-non-profit-open-source-plan">Open Source License</a> and their amazing platform for creating beautiful and accessible documentation. Their dedication to open source and ease-of-use has been invaluable to this project's success!</p>

  <a href="https://lithiumhosting.com/">
    <img width="230" src="https://lithiumhosting.com/lithiumv8/images/svg/logo_horizontal_light.svg" />
  </a>

  <p>Lithium Hosting's invaluable support by providing a server to facilitate development. Their dedication to open source and the developer community has been instrumental in making this project possible.</p>
</div>

## License
CommandPrompter is licensed under the permissive [MIT license](LICENSE).
