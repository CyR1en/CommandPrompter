# Web Editor Roadmap

Implementation tracker for the CommandPrompter Web Preset Editor.

**Reference spec:** [`system-initiated-config-system-v2.html`](system-initiated-config-system-v2.html)
**Status legend:** `⬜` not started · `🚧` in progress · `✅` done · `⛔` blocked

## How to use this file

- Each milestone is a self-contained phase that produces a runnable artifact.
- Check items off as they're completed. Update the status summary at the top.
- Every item links to the spec section it implements. The spec is the source of truth for *what* to build; this file tracks *progress*.
- **Definition of done** for any item: code written + tests passing + spec section verified end-to-end. "Implemented but not tested" is not done.

## Status Summary

| Milestone | Status | Progress | Target |
|---|---|---|---|
| [M0: Spikes](#m0-spikes) | ⬜ | 0/4 | Week 1 |
| [M1: Infrastructure](#m1-infrastructure) | ⬜ | 0/5 | Week 2 |
| [M2: Vertical Slice](#m2-vertical-slice) | ⬜ | 0/6 | Week 4 |
| [M3: Session & Trust](#m3-session--trust) | ⬜ | 0/7 | Week 6 |
| [M4: Form Editor & Preview](#m4-form-editor--preview) | ⬜ | 0/8 | Week 8 |
| [M5: Apply & Risk Tiers](#m5-apply--risk-tiers) | ⬜ | 0/7 | Week 10 |
| [M6: High-Risk Confirm](#m6-high-risk-confirm) | ⬜ | 0/5 | Week 12 |
| [M7: Hardening](#m7-hardening) | ⬜ | 0/6 | Week 14 |

**Overall: 0/48 items complete (0%)**

---

## M0: Spikes

**Goal:** Validate the three architectural unknowns that could invalidate the spec. No implementation work begins until these pass.

- [ ] **S1: Web Crypto X25519/Ed25519 support check** — PoC that generates both keypairs and signs/verifies a message in Chrome 124+, Firefox 131+, Safari 17+. Tests pass on all three. *[Spec §4.3, §4.2]*
  - [ ] Chrome test
  - [ ] Firefox test
  - [ ] Safari test
  - [ ] If Safari < 17 fails: document fallback (WebAssembly crypto or RSA-OAEP) and update spec

- [ ] **S2: JDK WebSocket through reverse proxies** — Test plugin that opens a WSS connection from a Paper server through Nginx Proxy Manager to `wss://echo.websocket.org`. Verifies connection survives Folia scheduler handoff. *[Spec §11.3, §7 Phase 1]*

- [ ] **S3: Schema validator PoC** — Test that loads `presets.schema.json` with `networknt/json-schema-validator`, validates a sample `presets.json`, and confirms it catches: missing fields, wrong types, unknown prompt `type` discriminator, invalid `dialog_type.type`. *[Spec §11.3, §7 Phase 4]*

- [ ] **S4: Decision gate** — All three spikes pass. Architecture is validated. Proceed to M1. If any spike fails, update spec and re-approve before continuing.

**Dependencies:** None (this is the first phase)
**Blocks:** M1, M2, M3, M4, M5, M6, M7

---

## M1: Infrastructure

**Goal:** Set up the second repo, CI pipeline, and schema contract sync. No application code yet.

- [ ] **I1: Create `CyR1en/CommandPrompter-web` repo** — GitHub repo, MIT license, README placeholder, .gitignore
- [ ] **I2: Monorepo scaffolding** — pnpm workspace + turborepo config. Three packages: `packages/schema`, `apps/relay`, `apps/frontend`. *[Spec §11.4]*
- [ ] **I3: Pin schema from plugin release** — Download `presets.schema.json` from latest plugin release tag into `packages/schema/`. Add `json-schema-to-typescript` codegen script. Generate initial TS types. *[Spec §11.2]*
- [ ] **I4: GitHub Actions Docker build** — Workflow that builds `apps/relay` + `apps/frontend` into a single Docker image. Pushes to GitHub Container Registry. *[Spec §11.6]*
- [ ] **I5: Schema drift CI check** — GitHub Actions job in the web repo that fetches the latest plugin release, compares the `presets.schema.json` to the pinned version, and fails the build on mismatch. *[Spec §11.6]*

**Dependencies:** M0
**Blocks:** M2 (relay must exist for vertical slice)

---

## M2: Vertical Slice

**Goal:** Prove the full crypto + transport pipeline works end-to-end: plugin opens WSS, browser joins, encrypted hello, encrypted presets push, browser renders "loaded." No form UI, no apply, no risk classification.

- [ ] **V1: Plugin crypto module (minimal)** — `dev.cyr1en.promptpaper.editor.crypto`: token generation (`SecureRandom`), `K_initial` derivation (HKDF-SHA256), AES-256-GCM encrypt/decrypt, HMAC-SHA256. Unit tests with known test vectors. *[Spec §4.1, §4.2, §4.4]*

- [ ] **V2: Plugin WSS relay client (minimal)** — `dev.cyr1en.promptpaper.editor.relay`: opens `java.net.http.WebSocket` to `wss://{relay.url}/ws/{token}`, sends WebSocket protocol-level ping every 10s, handles onOpen/onClose/onError. No application frames yet. *[Spec §11.3, §5.2]*

- [ ] **V3: Relay skeleton** — `apps/relay`: Fastify + `ws` library. WSS endpoint at `/ws/{token}`, max 2 clients per channel, static file serving. No frame routing logic yet (just accepts connections). *[Spec §5.1, §5.2]*

- [ ] **V4: SPA skeleton** — `apps/frontend`: Vue 3 or React + Vite. Single page: "Loading..." → connect WSS → derive `K_initial` → decrypt presets → render "Loaded N prompts" text. No form, no preview, no apply. *[Spec §3.3, §7 Phase 2]*

- [ ] **V5: End-to-end test** — Run Paper server with the plugin, open the SPA in a browser, verify "Loaded N prompts" appears. Confirm WSS frames are encrypted (Wireshark or relay-side logging). *[Spec §2.3]*

- [ ] **V6: Hello frame + MAC** — Add `hello` frame with HMAC-SHA256(K_initial) MAC verification. Plugin rejects connections with invalid MAC. Test: bad MAC → connection closed. *[Spec §4.4, §4.6]*

**Dependencies:** M0, M1
**Blocks:** M3 (trust handshake builds on hello), M4 (form UI needs presets data)

---

## M3: Session & Trust

**Goal:** Full crypto handshake, browser-fingerprint trust, session persistence, relay restart recovery.

- [ ] **T1: Ed25519 keypair generation + signed frames** — Plugin and browser generate Ed25519 keypairs. Frame envelope: `{msg: ciphertext, sig: Ed25519.sign(privKey, msg), nonce}`. Receiver verifies sig before decrypting. *[Spec §4.3, §4.6]*

- [ ] **T2: X25519 ECDH + K_session derivation** — Both sides exchange X25519 pubkeys via hello. Derive `K_session` via HKDF-SHA256(ECDH, salt=token, info="session-transport"). Switch from `K_initial` to `K_session` for all post-hello frames. *[Spec §4.2, §4.6]*

- [ ] **T3: Counter-based nonce management** — Plugin uses even send counters, browser uses odd. 64-bit big-endian, zero-padded to 12 bytes. Out-of-order → reject + close. *[Spec §4.2 Nonce Management]*

- [ ] **T4: Trust store** — `plugins/CommandPrompterPaper/.editor-truststore.json`: `playerUUID → [SHA-256(browser Ed25519 pubkey), ...]`. First-connection prompt: `/commandprompter trusteditor <nonce>`. `trusteditor clear` command. *[Spec §4.4, §3.1]*

- [ ] **T5: Session persistence** — `.editor-sessions.json` on plugin disk. Load on startup, reopen WSS for each session. Browser reconnect → resume. Plugin re-pushes presets. *[Spec §9.3, §10.4]*

- [ ] **T6: Relay restart recovery** — Both WSS connections drop on relay restart. Plugin reopens via session persistence, browser auto-reconnects with backoff. Channel recreated. *[Spec §10.4]*

- [ ] **T7: Session resilience edge cases** — Plugin WSS drops while browser connected (browser shows "waiting for reconnect"), browser tab closed (60s grace timer), channel idle timeout. *[Spec §10.4]*

**Dependencies:** M2
**Blocks:** M4 (form UI needs working session), M5 (apply needs working session)

---

## M4: Form Editor & Preview

**Goal:** Schema-driven form for editing presets, live in-game preview, diff computation.

- [ ] **F1: Schema-driven form (chat prompts)** — Render form fields from `presets.schema.json` for `chat` prompt type: `prompt_text`, `cancel` object, `sanitize`. Debounced live preview of the chat-window mock. *[Spec §6, §6.1]*

- [ ] **F2: Anvil + sign prompt forms** — Form rendering for `anvil` and `sign` prompt types. Preview: 3-slot anvil mock, sign texture with 4 lines. Material sprite atlas. Invalid materials → `PAPER` fallback. *[Spec §6, §6.2]*

- [ ] **F3: Dialog prompt form** — Form for `dialog` type: `base.body` items, `base.inputs` rows (text/number/choice), `dialog_type` (confirmation/multi_action). Preview: Paper dialog window mock. *[Spec §6]*

- [ ] **F4: Player UI prompt form** — Form for `player_ui` type: head grid, pagination controls at configured slots. Preview: chest GUI mock. *[Spec §6]*

- [ ] **F5: Post-command editor** — Add/edit/remove `post_commands`. Resolved-command card preview with `{input:N}`/`{player}` chips, `execute_as` badge, `execution_policy` badge. Red warning badge on `execute_as: console`. *[Spec §6.1]*

- [ ] **F6: Add/remove/reorder** — Preset list with add, remove, and reorder controls. Schema validation on every change. Apply button disabled while errors present. *[Spec §7 Phase 3]*

- [ ] **F7: Diff computation** — SPA computes structured diff against original: added/removed/changed prompts and post-commands with field-level detail. Browser diff panel. *[Spec §7 Phase 3, §8.3]*

- [ ] **F8: Material & color rendering** — MiniMessage + legacy `&`-code parser for preview text. Material sprite atlas. `PAPER` fallback for invalid materials. *[Spec §6.2]*

**Dependencies:** M3
**Blocks:** M5 (apply needs form data + diff)

---

## M5: Apply & Risk Tiers

**Goal:** Low/medium-risk edits auto-apply. High-risk edits queue for in-game confirmation.

- [ ] **A1: change_request frame** — SPA sends `{type: "change_request", presets: PresetConfig}` encrypted with `K_session`. Plugin receives, decrypts, validates schema. *[Spec §4.6, §7 Phase 4]*

- [ ] **A2: Plugin-side schema validation** — Validate decrypted payload against `presets.schema.json` using `networknt/json-schema-validator`. Reject with `{state: "rejected", reason: "schema_invalid", errors: [...]}`. *[Spec §10 mitigation 6, §7 Phase 4]*

- [ ] **A3: Risk classifier** — Compute diff between decrypted payload and current in-memory `PresetRegistry`. Classify: low (display fields only), medium (structural changes), high (console `post_commands` added/modified). *[Spec §8.1]*

- [ ] **A4: Low-risk auto-apply** — Write to `presets.json`, call `PresetRegistry.reload()`. Send `change-response {state: "applied"}` to browser. No in-game prompt. *[Spec §8.1, §7 Phase 5]*

- [ ] **A5: Medium-risk auto-apply with notification** — Same as low-risk, plus in-game chat message: "Presets updated via Web Editor (3 added, 1 removed)." *[Spec §8.1]*

- [ ] **A6: change_response frame** — Plugin sends `{state: "applied"|"rejected", reason?, errors?}`. Browser updates UI. *[Spec §4.6, §7 Phase 5]*

- [ ] **A7: Post-apply state push** — After apply, plugin pushes updated presets as new encrypted frame. Browser refreshes form to show new state. Channel stays open for continued editing. *[Spec §7 Phase 5]*

**Dependencies:** M4
**Blocks:** M6 (high-risk confirm extends apply flow)

---

## M6: High-Risk Confirm

**Goal:** Console `post_commands` edits require in-game confirmation with command review.

- [ ] **H1: High-risk confirmation prompt** — Hardcoded chat/dialog primitive (NOT a preset). Shows: "Web editor wants to apply N changes, including M console commands: [Review] [Confirm] [Cancel]". 60s timeout. *[Spec §8.2]*

- [ ] **H2: Command review pagination** — [Review] paginates exact command strings in chat. Includes `{input:N}` and `{player}` placeholder highlighting. *[Spec §8.2]*

- [ ] **H3: Audience resolution** — `initiated_by` player if online on this server; otherwise any online player with `promptpaper.editor`. If no qualifying player: hold + re-deliver on `PlayerJoinEvent`. *[Spec §8.2, §10.4]*

- [ ] **H4: Permission re-check** — Re-check `promptpaper.editor` at confirm-click time (in case revoked since session creation). Record `confirmed_by` in audit log. *[Spec §8.2]*

- [ ] **H5: Supersession** — One in-flight pending apply per server. New apply replaces old (cancels old prompt, shows latest). *[Spec §8.2, §10.4]*

**Dependencies:** M5
**Blocks:** M7 (hardening tests the high-risk flow)

---

## M7: Hardening

**Goal:** Production readiness. All edge cases handled, audit trail working, deployment documented.

- [ ] **H6: Audit trail** — `.editor-audit.jsonl`: `session.create`, `trust.add`, `apply` events with `initiated_by`, `confirmed_by`, `risk_tier`, diff summary, `token_hash` (SHA-256, not raw token). *[Spec §10.3]*

- [ ] **H7: Edge case matrix** — Work through every case in spec §10.4 table. For each: implement, write test, verify. ~22 cases. *[Spec §10.4]*

- [ ] **H8: Fallback commands** — `/commandprompter export` writes `presets-export.json`. `/commandprompter import <file>` validates + classifies risk + applies. [Spec §9.2, §3.1]*

- [ ] **H9: Rate limiting** — 5 concurrent WSS per source IP, 100 messages/min per channel, 5MB max frame. Verify relay enforcement. *[Spec §5.3]*

- [ ] **H10: Deployment docs** — Nginx Proxy Manager config, Docker compose for relay, proxy-level auth setup (access list / Cloudflare Access). Self-hoster override file docs. *[Spec §11.5, §3.3]*

- [ ] **H11: User docs** — Update `docs/user/03-commands.md` with `/commandprompter editor`, `trusteditor`, `export`, `import`. Update `04-config-reference.md` with `Web-Editor.Enabled`. *[Spec §3.1, §3.2]*

**Dependencies:** M6
**Blocks:** None (final phase)

---

## Cross-Cutting Concerns

These run alongside the milestones, not as separate phases.

- [ ] **X1: Classpath resource** — `prompt-paper/src/main/resources/editor-endpoints.properties` with hardcoded `relay.url` and `editor.url`. `[Spec §3.3]`
- [ ] **X2: Config node** — `Web-Editor.Enabled` boolean in `CommandPrompterConfig.java` (default `true`). `[Spec §3.2]`
- [ ] **X3: Permission node** — `promptpaper.editor` in `paper-plugin.yml`. `[Spec §3.1]`
- [ ] **X4: Folia scheduler integration** — All disk writes, cache swaps, in-game notifications, and confirm prompts dispatched via region/global scheduler. WSS IO thread never mutates shared state. `[Spec §7 Phase 5, §10.4]`

---

## Risk Register

Known risks tracked during implementation. Update this section as new risks are discovered.

| Risk | Mitigation | Status |
|---|---|---|
| Web Crypto X25519/Ed25519 not in all target browsers | M0 spike S1. Fallback: WebAssembly crypto or RSA-OAEP. | ⬜ |
| JDK WebSocket fails through certain proxies | M0 spike S2. Fallback: OkHttp WebSocket client. | ⬜ |
| Schema validator dependency bloat | Shaded, not packaged separately. If too heavy, custom validation for common cases. | ⬜ |
| IndexedDB unavailable in some browser contexts (private mode) | Fallback to localStorage with reduced key size. | ⬜ |

---

## Out of Scope (Reminder)

From spec §12 — do NOT add to milestone scope:

- `config.yml` / `prompt-config.yml` editing
- Locale `.properties` file editing
- Cross-server confirm routing (BungeeCord/Velocity plugin messaging)
- Live Minecraft connection for preview
- Per-preset permission nodes
- Relay-level authentication
- Multi-admin concurrent editing on the same session
