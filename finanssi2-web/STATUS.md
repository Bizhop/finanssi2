# Frontend status

Updated 2026-10-02 after implementing private debug games and the prerequisite asset controls. The functional gaps and deferred live reviews below remain. See
[backend status](../finanssi2-backend/STATUS.md) for gameplay and rule decisions.

Per-game chat UI implementation is in progress (2026-10-05): game rooms use their own chat endpoint/topic, viewers without a seat
get a read-only composer state, and chat plus event log render in independent collapsible panels. Frontend typechecking passes;
responsive review, mock fixtures, and chat lifecycle coverage remain.

## Completed features

- Firebase/Google sign-in, authenticated REST calls and chat. STOMP connects with a fresh Firebase token on every attempt, reconnects automatically and
  recreates subscriptions.
- Games page at `/games`: list, create, join, leave, start, creator settings, loading/errors and refresh on `/topic/games`.
- Game room at `/games/:id`: load state and static board/card data, subscribe to `/topic/games/{id}`, reload state after updates, merge events by sequence and
  fetch missing events after gaps/reconnects.
- Board display with all 46 squares, player tokens, property owners, buildings and mortgages. Player panels show cash, loans, cars, position and eliminated
  status; asset lists show properties, shares, bonds and held Stock Tips.
- Controls for turns, cars, loans, assets, construction, shareholders' meetings, resignation and creator closure. Pending-decision controls cover raising funds,
  bond offers, bond/asset bids, Finance News direction and Stock Tip options.
- Active Finance News, current-player held card text and Stock Tip choice text render card chapters with headings/italics. Finished games show the winner or
  closure and standings sorted by net worth.
- Separate Vite dev pages for chat and mocked running/finished game rooms, without Google sign-in or a backend.

## Decisions

- Keep transport/shared API types in `src/components/gameApi.ts`, the lobby in `Games.tsx`, the room in `GameRoom.tsx` and STOMP lifecycle/hooks in
  `StompContext.tsx`. The frontend uses React, Material UI, Deno and Vite.
- Send game commands over REST. Broadcasts contain events and a version, so the room fetches current state on every update; event sequence gaps trigger an
  incremental history fetch. Backend `allowedCommands` controls action types, and the backend remains responsible for validating their parameters.
- Rule settings tag the developer's house rules as recommended and the printed rules as original. Recommended options are the defaults, in the backend and
  in the debug-game dialog: unlimited bank loans, a forced-sale minimum bid of half the nominal price and shareholders' meetings only once the whole group is
  bought from the bank. Original rules stay available for legacy play. Opening settings uses the game's stored values.
- Live multiplayer review was explicitly deferred by the developer. The board (`GameBoard.tsx`) draws a perspective-corrected scan of the physical board
  (`src/assets/board.webp`) with transparent squares positioned from measured divider lines (`boardLayout.ts`). Ownership is a border in the owner's colour
  (dashed when mortgaged), and the Finance News and Stock Tip decks sit on the marked places with the active Finance News and the Stock Tip drawn this turn face-up beside them; card faces are
  rendered from card data rather than photographed.
- Dev pages use a separate Vite root on port 3001. `/game-room.html` previews a running game; `/game-room.html?finished` previews standings. Mocks are display
  fixtures: their command endpoint logs requests without applying state transitions, and they do not exercise real rules or live subscriptions.
- Production builds remain disabled in `deno.json` until a production Firebase environment/configuration exists.

## Private debug games

- Capabilities load after sign-in, clear on account changes and refresh on reconnect. STOMP reconnects on account changes. Eligible users can create 2–6-seat
  debug games with settings; lists and rooms label the mode. Creators delete debug games (any status) from the game list.
- Rooms distinguish the authenticated account from `actingPlayer`, display “Controlling: …” and follow every turn/decision. Asset ownership, held cards and
  out-of-turn controls use the effective seat; creator closure still uses the account.
- Debug commands submit the displayed actor/version and optional command-only dice. Inputs clear after submission; 409 reloads without replay. Controls are
  disabled while requests run. Finance News/Stock Tip selectors exclude held cards.
- Creators end running normal games and delete debug games from the game list, after confirmation; deletion handles empty 204. Private deletion notifications leave other rooms; 403/capability removal clears access and
  leaves the room. Owned lists refresh on reconnect because debug lifecycle stays off the public topic.
- API transport accepts all empty successful bodies and preserves error status. Two Deno tests cover 200/204 and single-request 403/409 failures. Typechecking
  corrected inherited MUI 9 system props to `sx` and select configuration to `slotProps`.
- `dev-pages/game-room.html?debug` previews controls; fixtures remain visual mocks without real transitions.
- [ ] Complete live debug acceptance with an allowlisted Google account, including auctions/out-of-turn decisions, mortgage and inventory controls, stale
      actions in two tabs, deletion and reconnect/capability removal. See the backend status for setup and the host-service limitation observed during
      implementation.

## Todos

Private single-player debug mode is implemented. Live Google-authenticated acceptance remains outstanding.

### Functional gaps visible in the current code

- [x] Add a picker for buying bank-owned properties from available inventory.
- [x] Use built/unbuilt deed mortgage values in ordinary and raise-funds controls.
- [ ] Align parameterized controls with valid options and current prices. Share buttons show base prices despite Finance News modifiers, and some asset/meeting
      choices can be invalid even when their command type is allowed. Consider concrete options in the backend response instead of duplicating rule calculations
      in the UI.
- [ ] Explain obligations and choices: show payment amount/creditor, asset auction details/minimum bid and meaningful labels for the backend's Stock Tip option
      strings. Show costs/proceeds for purchases, redemption, construction and meetings.
- [x] Render useful event details. `gameEvents.ts` describes every backend event type (dice, squares, money and reasons, cards, auctions with bids,
      unimplemented squares); the room's event log shows the latest 200, newest first, with drawn card text inline. A sidebar card shows the last drawn Stock
      Tip, immediate or held. Unknown event types fall back to their type name. Card text remains Finnish.
- [x] Disable room controls while requests run and guard submissions synchronously against duplicate clicks.
- [x] Remount rooms on game/account changes and reject stale state/history responses; newest state refresh wins.

### Deferred review and follow-up

- [x] Run frontend typecheck, lint, formatting and transport tests using Deno on 2026-10-02 (via `npx deno`).
- [ ] Review live multiplayer with at least two Google-authenticated players: lobby/settings/start, all pending decisions, out-of-turn payments, held-card
      effects, auctions, game end and reconnect/missed-event recovery.
- [ ] Review mocked pages and responsive layouts, then do the deferred visual redesign. Mock command responses cannot validate gameplay transitions; add
      representative decision fixtures if needed for UI work.
- [ ] Configure production Firebase/environment values and re-enable the production build task.

Local setup and Deno tasks are in the [root README](../README.md). Start the preview server with `deno task dev:pages` from this folder. Automated checks
passed; live play and responsive visual review are still unverified.
