# CommandPrompter end-user documentation

**Status**: Approved (brainstorming complete, 2026-06-14)
**Date**: 2026-06-14
**Repo branch**: `version-3` (current work branch for v3 rewrite)

## 1. Purpose

CommandPrompter v3 is a Paper plugin that turns Minecraft command inputs into interactive prompt sessions. The plugin was rewritten in a v2->v3 modular split (four Gradle subprojects) and has an external GitBook at <https://cyr1en.gitbook.io/commandprompter/>. There is currently **no in-repo documentation** for end users. This spec defines a `docs/user/` doc set in the repository, treated as the authoritative source independent of the GitBook.

## 2. Audience and constraints

- **Primary audience**: server administrators installing and configuring CommandPrompter on a Minecraft server.
- **Tone**: second-person, present tense, end-user-facing. No marketing fluff. No emoji.
- **Style**: reference + how-to mix, moderate verbosity — every option/flag gets a default, description, and example; every command gets a signature, permission, and example invocation.
- **Repo location**: `docs/user/`. The empty `docs/dev/` directory is preserved with a `README.md` noting it is reserved for future contributor docs.
- **GitBook relationship**: independent. This doc set is the single source of truth. The GitBook may mirror this content later, but is not required to.
- **Out of scope**: developer/contributor docs, hook-author SPI docs, doc i18n.

## 3. File inventory and source-of-truth mapping

Each file is named with a two-digit prefix so reading order is obvious in any directory listing.

| File | Topic | Source of truth in repo |
|---|---|---|
| `docs/user/README.md` | Table of contents and short overview | n/a |
| `docs/user/01-install.md` | Install + first-run | `prompt-paper/src/main/resources/paper-plugin.yml`, AGENTS.md gotchas |
| `docs/user/02-quickstart.md` | Five-minute first-prompt walkthrough | `prompt-core/src/main/java/dev/cyr1en/promptcore/parser/CommandLineParser.java`, `PromptTag.java` |
| `docs/user/03-commands.md` | `/commandprompter`, `/consoledelegate`, `/playerdelegate` reference | `prompt-paper/src/main/java/dev/cyr1en/promptpaper/command/*Command.java` |
| `docs/user/04-config-reference.md` | Exhaustive `config.yml` + `Messages` + `prompt-config.yml` options | `CommandPrompterConfig.java`, `MessageConfig.java`, `PromptConfig.java`, `ConfigurationManager.java` |
| `docs/user/05-prompt-syntax.md` | Tag grammar, flags, answer refs, post-commands | `CommandLineParser.java`, `PromptTag.java`, `PostCommandMeta.java` |
| `docs/user/06-screens.md` | CHAT / ANVIL / SIGN / DIALOG / PLAYER screen types + `screen-mappings` | `ScreenType.java`, `ScreenRouter.java`, `screen/*Screen.java` |
| `docs/user/07-validators.md` | Built-in and custom input validators | `validation/*Validator.java`, `ValidationConfig.java` |
| `docs/user/08-hooks.md` | Per-hook behavior and prerequisites | `hook/HookContainer.java`, `hook/hooks/*Hook.java`, `hook/annotations/TargetPlugin.java` |
| `docs/user/09-permissions.md` | Full permission table | `paper-plugin.yml`, `PromptCommand` subclasses |
| `docs/user/10-delegation.md` | `/consoledelegate` and `/playerdelegate` deep-dive | `ConsoleDelegateCommand.java`, `PlayerDelegateCommand.java` |
| `docs/user/11-post-commands.md` | On-complete and on-cancel PCMs, delay, target | `PostCommandMeta.java`, `PromptEngine.java#dispatchPCMs` |
| `docs/user/12-dialogs-compound.md` | Compound `&&` dialog tags, answer encoding | `DialogPromptScreen.java`, `AnswerEncoding.java`, `DialogInputKind.java` |
| `docs/user/13-troubleshooting.md` | Common issues and `Debug-Mode` usage | Combined knowledge from `PluginLogger.java`, `HookContainer.java`, all error paths |
| `docs/user/14-migration-from-v2.md` | v2->v3 breaking changes | Facts provided by the user (deferred to drafting stage) |

Plus a single `docs/dev/README.md` reserving the empty `docs/dev/` for future contributor docs.

## 4. Style rules

1. **One file = one topic.** No mega-files.
2. **Numbered prefixes** (`01-`, `02-`, ...) make reading order obvious in directory listings.
3. **All code, config keys, and command names in backticks.**
4. **Default values quoted exactly** as the plugin emits them in the generated YAML.
5. **YAML examples are full and runnable**, not fragments.
6. **No Java source in user docs** unless it is the only way to explain behavior (e.g. lifecycle or scheduler).
7. **Cross-references use explicit relative links** (`See [Permissions](09-permissions.md).`).
8. **Each command documented** includes: full signature, permission, example invocation, expected output.
9. **Each config option documented** includes: YAML key path, default value, description, one example.
10. **Lower-numbered docs assume nothing** from higher-numbered ones. Cross-reference goes one direction.

## 5. Drafting process

1. Draft in tier order (1 -> 2 -> 3 -> 4) so cross-references resolve.
2. Before drafting each doc, re-read the corresponding source files fresh to confirm defaults, signatures, and example values match the code.
3. Within each tier, draft files in numeric order; a tier can be paused if a downstream doc in the same tier is not ready to be referenced.
4. After Tier 4 is drafted, write `docs/user/README.md` last (so it links to all 14 files).
5. After every doc, run `./gradlew :prompt-paper:shadowJar` to confirm docs have not accidentally affected the build (sanity check, not a verification of doc accuracy).
6. After all docs are drafted, do a final consistency pass: cross-references resolve, all example commands actually work, defaults match the generated YAML.

## 6. Review cadence

- User reviews and signs off after **each tier** is fully drafted. No commit between tiers without sign-off.
- Within a tier, the user can ask for changes to any individual file before the next file in the tier is started.

## 7. Verification

The user-facing accuracy of each doc is verified by reading the source file(s) it documents. The doc is not considered complete until:

- Every config key mentioned in the doc exists in the corresponding Java config record.
- Every default value quoted in the doc matches the `@NodeDefault` annotation.
- Every command signature in the doc matches the `build()` method in the corresponding `*Command.java`.
- Every permission in the doc exists in `paper-plugin.yml` or in the `permission` field of a `PromptCommand` subclass.
- Every cross-reference link points to a file that exists (or will exist by the time the link is followed).

Verification is the author's responsibility per the brainstorming skill, not a separate QA step.

## 8. Out of scope

- Developer / contributor documentation.
- Hook-author / ScreenProvider SPI documentation (third-party extension surface).
- Internationalization of the docs.
- Migration doc content: the v2->v3 breaking changes will be supplied by the user when the doc is drafted. The user will provide facts; the spec author will write them.
