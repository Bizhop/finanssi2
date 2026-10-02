# Single-player debug mode

Status: implementation in progress. Created 2026-10-01.

## Resume after a cleared session

1. Read [root AGENTS.md](../../AGENTS.md), [backend AGENTS.md](../../finanssi2-backend/AGENTS.md),
   [backend status](../../finanssi2-backend/STATUS.md), [frontend status](../../finanssi2-web/STATUS.md) and this plan.
   The former numbered gameplay plans have been replaced by the status files; they are not implementation prerequisites.
2. Inspect `git status` and the implementation before editing. At this handoff only documentation has changed: no debug mode
   code/configuration, accounts or tests have been added. Start with the first unchecked task under "Implementation order".
3. Use the design below as the implementation baseline. Manual control of all seats is the working assumption recorded in this
   plan; the user has not separately specified automatic opponents. The actual email addresses remain deployment input:
   implement and verify with test fixtures, and leave the deployed allowlist empty until the developer configures it.
4. After each task, update its checkbox and add a dated progress note with changed files, checks run/results and remaining work.
   Preserve partially completed work explicitly so another session can resume without reconstructing the conversation.
5. Follow the repository commit rules. The request to commit this documentation does not authorize commits for later
   implementation work. For local services and credentials, follow the root README and backend AGENTS.md.

### Implementation entry points

Paths below are relative to the repository root; backend Java paths start with
`finanssi2-backend/src/main/java/fi/bizhop/finanssi2/`.

| Work | Existing files to read/change |
|---|---|
| Verified identity and access | `security/User.java`, `security/FirebaseTokenFilter.java`, `security/FirebaseAuthenticationToken.java`; `finanssi2-backend/src/main/resources/application.properties` |
| Game mode and queries | `game/db/Game.java`, `GameRepository.java`; preserve defaults for existing MongoDB documents |
| Creation, actors and saves | `game/service/GameService.java`, `GameConfig.java`, `DiceSource.java`; `game/engine/GameSetup.java`, `GameState.java`, `GameEngine.java` |
| HTTP views and debug routes | `game/web/GameController.java`, `GameView.java`, `DevGameController.java`; new capability/debug controllers and request records |
| Private subscriptions | `web/config/StompAuthenticationInterceptor.java`, `WebSocketConfig.java`; `game/service/GameUpdate.java`, `LobbyChange.java` |
| Deck changes and old dice cleanup | `game/engine/GameEvent.java`, `GameState.java`; `game/service/DevDiceSource.java` |
| Frontend identity/transport | `finanssi2-web/src/components/gameApi.ts`, `CurrentUserContext.tsx`, `StompContext.tsx` |
| Frontend controls | `finanssi2-web/src/components/Games.tsx`, `GameRoom.tsx`; preview fixtures in `finanssi2-web/dev-pages/game-room.tsx` |
| Existing test patterns | `finanssi2-backend/src/test/java/fi/bizhop/finanssi2/game/{service,web,db,engine}/`; websocket tests in `web/config/`, test identity mapping in `web/SecurityConfig.java` |

## Goal and scope

An authorized user can create and play a real game alone, manually controlling every seat, including decisions made outside
the current player's turn. Access is restricted to a fixed backend list of account emails.

The proposed meaning of single-player is one human controlling 2–6 seats (default two). This keeps existing game setup, neighbour
effects, auctions and last-player wins usable. Automatic opponents are a separate possible feature. Debug games use the real
engine, persistence and event stream; the existing mocked dev page remains useful for visual previews.

The first version includes creation, settings/start, control of each turn/decision, optional dice values for a command, selection
of the next card and closing/deleting one's debug game. General state editing, scenario imports and undo are later extensions.

## Current implementation to build on

- `security.User` already carries the email from a verified Firebase ID token. It does not yet carry the `email_verified` claim.
- `GameSetup` requires 2–6 seats, and `GameState.actor()` identifies the first pending decision's player or the current player.
- `GameService.command` currently passes the authenticated uid to the engine. `allowedCommands` and several room controls also
  assume that the authenticated user is the player taking an action.
- `/api/games` lists every lobby plus games the user belongs to. Lobby broadcasts contain the game, and STOMP currently checks
  authentication without checking permission to subscribe to a specific game.
- `DevGameController` offers queued dice only under the `dev` profile. It currently has no email allowlist, game ownership check
  or restriction to debug games; it must not become an alternate route around the new controls.

## Access and game identity

- Configure a fixed set through `finanssi2.debug.allowed-emails`; default to an empty set, which disables access. Keep the actual
  accounts in backend deployment/local configuration. They have not been supplied yet; no account is authorized by this plan.
- Require a verified email claim from the authenticated Firebase token. Extend the server's user model/token mapping to retain
  this claim, and use the same identity checks for REST and STOMP. Never trust an email supplied in a request body.
- Compare trimmed emails case-insensitively using `Locale.ROOT`. Use exact addresses, without domains, patterns or provider-specific
  alias rewriting. Centralize this in a `DebugAccess` service. Recheck every debug request; configuration changes take effect
  after backend restart and websocket reconnect.
- Expose `GET /api/me/capabilities` returning `{debugMode: boolean}`. The frontend uses this to show debug controls. The capability
  endpoint returns no allowlist, and hiding controls is supplementary to backend enforcement.
- Add immutable game mode `NORMAL` or `DEBUG` on the game document, defaulting old documents without the field to `NORMAL`.
  Use the creator uid as debug owner. Normal games cannot be converted, and debug owner authority survives elimination of their
  player seat. Mode belongs to the game/session metadata, separate from gameplay house rules.
- Seat one uses the owner's uid/name; additional seats use generated ids such as `debug:{gameId}:seat:2` and names such as
  "Debug player 2". They require no Firebase accounts. Generate ids on the server and keep them stable through reloads.
- A debug game belongs to its owner alone. Even another allowlisted user cannot operate or read it. Reject ordinary joins/leaves;
  use debug controls for its lifecycle. Require current allowlist membership as well as ownership for reads and mutations.
- Filter debug games from other users' lists and from public lobby broadcasts. Check direct game/event reads and STOMP subscriptions
  to debug game topics. Reject client `SEND` frames to game broadcast topics so REST remains the source of game updates.
  Check the game on subscription, rather than trusting a client-supplied mode flag.

## Proposed API and command handling

| Endpoint | Purpose |
|---|---|
| `GET /api/me/capabilities` | Discover whether the signed-in account may use debug mode |
| `POST /api/debug/games` | Create a private lobby with `playerCount` (2–6, default 2) and ordinary game settings |
| `GET /api/games/{id}` | Return the existing view plus `actingPlayer`; allowed commands apply to that seat in debug mode |
| `PUT /api/games/{id}/settings`, `POST /api/games/{id}/start` | Reuse lobby operations with debug owner/access checks |
| `POST /api/debug/games/{id}/commands` | Execute `{actor, expectedVersion, command, dice?}` for the current actor |
| `PUT /api/debug/games/{id}/next-card` | Put the specified card first in its deck, with `expectedVersion` |
| `DELETE /api/debug/games/{id}` | Delete an owned debug game and its history; publish a deletion notification to its topic |

Commands and dice:

- Resolve the effective actor on the server with `state.actor()`. Compute `allowedCommands` for that actor and call the existing
  `GameEngine.handle` with their uid. Keep the engine's turn, ownership, timing and payment validation.
- The command request's actor must match the actor of the loaded state, and `expectedVersion` must match the game version.
  Reject stale views with `409` before execution. MongoDB optimistic locking still protects the final save. This prevents a
  command intended for one seat being applied to a different seat after another tab advances the game.
- Handle creator-only `EndGame` through the existing service path, using the authenticated owner rather than the effective seat.
  Ordinary `/commands` must reject debug games and never accept an acting uid for a normal game.
- Optional `dice` contains a bounded list of values 1–6 for this command only. Consume them in the engine's actual roll order,
  then use random dice when exhausted. Report the rolls through existing events; discard unused values when the request ends.
  Keep overrides local to execution, so a rejected command or failed save leaves no persistent dice queue to consume.
- Expose the dice input for movement and other commands that roll. Starting-order rolls can remain random in the first version;
  the default two-seat game must be usable immediately through ordinary start.
- Replace the old `/dev/dice` helper with the authorized debug command facility, updating its tests and removing unused dev dice
  wiring. Debug access is controlled by the email list and game mode, so enabling a Spring profile cannot grant access.

Card control and persistence:

- The next-card endpoint accepts a deck and a known card id. Move that id to the front of the existing deck, preserving the order
  of the other cards. Reject held Stock Tips, which are absent from the draw deck. Do not execute the card or change the current
  active Finance News effect until ordinary gameplay draws it.
- Allow deck changes only in running games with no pending decision. Preserve every card exactly once across deck and held cards.
  Return useful validation errors and reject stale versions, unknown ids/decks and finished games.
- Save creation, gameplay and deck changes through the existing atomic state/event archival path. Add an explicit debug deck-change
  event for the history. Broadcast after successful saves, using the private game topic without the public lobby topic.
- Debug deletion removes state/history and returns an empty `204` response. The frontend must handle empty successful responses
  consistently. Refresh the owner's list after creation/deletion; other open rooms respond to deletion by returning to the list.
  Coordinate deletion with in-flight saves and archival retries so a deleted game cannot recreate orphaned history.

## Frontend changes

- Fetch capabilities after sign-in and clear them on sign-out/account change. Add "Create debug game" for eligible accounts,
  with seat count/settings, and label debug games in the owner's list and room.
- Separate authenticated account identity from the acting seat. Use the view's `actingPlayer` for "your decision", owned assets,
  held cards and turn controls. Continue using the authenticated account for access, tokens and creator closure. Render an explicit
  "Controlling: …" label and follow actor changes automatically; inspection of another seat must not change the command actor.
- Send debug actions to the debug command endpoint with the displayed actor/version. On `409`, reload state and require a fresh
  action; do not silently replay the command for a new seat. Disable controls while requests run.
- Add an optional dice input and next Finance News/Stock Tip selectors. Label dice as applying to the next submitted command,
  then clear the input after submission. Offer a confirmed deletion action for debug games.
- Handle capability removal/`403` by clearing debug controls and leaving an inaccessible room. Refresh owned games when the
  connection returns, because debug lifecycle updates are intentionally absent from the public lobby stream.
- Include the property inventory picker and built-state mortgage fixes recorded in [frontend status](../../finanssi2-web/STATUS.md).
  These are prerequisites for using the room to debug purchases and raise-funds decisions across seats.

## Implementation order

- [x] 1. Backend access/configuration and capabilities: verified email mapping, central checks, normal/debug metadata and legacy
  defaults. Done when capability/access tests cover configured accounts and existing documents default to normal mode.
- [ ] 2. Debug game creation/lifecycle and visibility: synthetic seats, list/read filters, subscription checks and private
  broadcasts. Done when owner-only lifecycle works and neither ordinary REST nor public topics expose another user's debug game.
- [ ] 3. Effective actor views/commands, version checks and request-scoped dice; replace the existing dev dice route. Done when
  one owner can resolve every seat's legal actions, stale requests fail and no dev-profile bypass remains.
- [ ] 4. Next-card control with deck invariants, event persistence and version checks. Done when ordinary draws use the selected
  card and held cards, finished games and pending decisions reject invalid deck changes.
- [ ] 5. Frontend capability gating, creation, acting-seat controls and the required asset control fixes. Done when real debug
  rooms show the current controlled seat and submit actor/version with each action, including out-of-turn decisions.
- [ ] 6. Focused automated checks and manual play through the real app; update status notes as features are completed. Done when
  the acceptance checks below pass, or any review requiring developer-run services is explicitly recorded as outstanding.

### Progress log

- 2026-10-01: Plan and session handoff written. All six tasks remain unstarted. Documentation links/whitespace checked;
  builds, frontend checks and live gameplay have not been run for this work. Next action: task 1.

## Acceptance checks for implementation

- Empty allowlist, missing/unverified email and a nonlisted account deny debug access. Matching a configured verified address
  allows capability discovery and creation; casing/whitespace follow the documented comparison policy.
- An allowlisted nonowner is denied, including direct REST requests and websocket subscriptions. No debug game payload reaches
  public lobby subscribers. Normal game commands cannot impersonate seats or accept dice/card controls.
- One account creates, configures, starts and reloads a 2–6-seat game. It controls every turn and every addressed decision, including
  out-of-turn payments, grand bond offers and all auction bids. Elimination of the owner's seat does not remove control.
- Stale actor/version requests fail without state/events changing. Normal validation failures and optimistic-lock conflicts do
  not leak overrides into later requests; successful debug gameplay follows the existing save/archive/broadcast behavior.
- Card selection preserves deck/held-card membership, draws the selected card through normal rules and rejects ineligible changes.
- Existing normal games, including stored documents without mode metadata, retain their setup, actors, allowed commands and wins.
- Closing finishes a debug game normally; deleting removes its state/history and open rooms handle the notification. Test deletion
  without a JSON response body and multiple open tabs issuing conflicting actions.
- Run backend checks using [backend AGENTS.md](../../finanssi2-backend/AGENTS.md), and frontend typecheck/lint/format checks.
  Manually confirm a single Google-authenticated allowlisted account can complete the flows above without mocked responses.

Once implemented, move final decisions and any remaining todos into the backend/frontend status notes and remove this plan.

- 2026-10-02: Step 1 complete: security token mapping, DebugAccess, capabilities, immutable game mode/configuration. Backend full test suite passed, including allowlist checks and MongoDB legacy mode defaults. Next: private lifecycle and subscriptions.
