# Frontend status

Updated 2026-10-01 from the implementation and the former frontend plan. The lobby and game room are implemented, with the
functional gaps and deferred reviews below. See [backend status](../finanssi2-backend/STATUS.md) for gameplay and rule decisions.

## Completed features

- Firebase/Google sign-in, authenticated REST calls and chat. STOMP connects with a fresh Firebase token on every attempt,
  reconnects automatically and recreates subscriptions.
- Games page at `/games`: list, create, join, leave, start, creator settings, loading/errors and refresh on `/topic/games`.
- Game room at `/games/:id`: load state and static board/card data, subscribe to `/topic/games/{id}`, reload state after updates,
  merge events by sequence and fetch missing events after gaps/reconnects.
- Board display with all 46 squares, player tokens, property owners, buildings and mortgages. Player panels show cash, loans,
  cars, position and eliminated status; asset lists show properties, shares, bonds and held Stock Tips.
- Controls for turns, cars, loans, assets, construction, shareholders' meetings, resignation and creator closure. Pending-decision
  controls cover raising funds, bond offers, bond/asset bids, Finance News direction and Stock Tip options.
- Active Finance News, current-player held card text and Stock Tip choice text render card chapters with headings/italics.
  Finished games show the winner or closure and standings sorted by net worth.
- Separate Vite dev pages for chat and mocked running/finished game rooms, without Google sign-in or a backend.

## Decisions

- Keep transport/shared API types in `src/components/gameApi.ts`, the lobby in `Games.tsx`, the room in `GameRoom.tsx` and STOMP
  lifecycle/hooks in `StompContext.tsx`. The frontend uses React, Material UI, Deno and Vite.
- Send game commands over REST. Broadcasts contain events and a version, so the room fetches current state on every update;
  event sequence gaps trigger an incremental history fetch. Backend `allowedCommands` controls action types, and the backend
  remains responsible for validating their parameters.
- The settings dialog marks unlimited bank loans as recommended, while backend-created games default to the official six-loan
  bank limit. Opening settings uses the game's stored values; creation does not automatically apply the house rule. Compulsory
  sale bids default to no minimum, with half the nominal price available as a house rule.
- Visual redesign and live multiplayer review were explicitly deferred by the developer. The current board is a six-column
  grid; `../input/board.png` is the physical-board reference for later visual work.
- Dev pages use a separate Vite root on port 3001. `/game-room.html` previews a running game; `/game-room.html?finished` previews
  standings. Mocks are display fixtures: their command endpoint logs requests without applying state transitions, and they do
  not exercise real rules or live subscriptions.
- Production builds remain disabled in `deno.json` until a production Firebase environment/configuration exists.

## Todos

New work: [single-player debug mode plan](../input/plan/single-player-debug.md), for one authorized account controlling all seats.

### Functional gaps visible in the current code

- [ ] Add a picker for buying bank-owned properties. The current button only targets a property on the player's current square,
  while the rules permit buying from square 11 or 35–46; players need to select the available inventory.
- [ ] Use the deed's mortgage value for the property's current building state. Both ordinary and raise-funds controls currently
  filter out all built properties, hiding legal mortgages and potentially blocking payment decisions.
- [ ] Align parameterized controls with valid options and current prices. Share buttons show base prices despite Finance News
  modifiers, and some asset/meeting choices can be invalid even when their command type is allowed. Consider concrete options
  in the backend response instead of duplicating rule calculations in the UI.
- [ ] Explain obligations and choices: show payment amount/creditor, asset auction details/minimum bid and meaningful labels for
  the backend's Stock Tip option strings. Show costs/proceeds for purchases, redemption, construction and meetings.
- [ ] Render useful event details. The recent log currently shows only the last 12 event types, so dice, movement, transfers,
  card draws, auction outcomes and reasons need readable descriptions. Show immediate drawn Stock Tips as well as held/choice
  cards; the current card display does not cover every draw.
- [ ] Prevent duplicate submissions consistently. Simple actions/choice dialogs use `commandBusy`, but several asset and
  pending-decision buttons remain enabled while a command is in flight.
- [ ] Clear room-specific state/history when navigating between game ids and review overlapping refresh requests so stale
  events or responses cannot appear in another room.

### Deferred review and follow-up

- [ ] Run frontend typechecking, lint and formatting checks in a Deno environment. The former plan recorded typechecking and
  dev-page rendering as unverified because Deno was unavailable there; this documentation cleanup did not run those checks.
- [ ] Review live multiplayer with at least two Google-authenticated players: lobby/settings/start, all pending decisions,
  out-of-turn payments, held-card effects, auctions, game end and reconnect/missed-event recovery.
- [ ] Review mocked pages and responsive layouts, then do the deferred visual redesign. Mock command responses cannot validate
  gameplay transitions; add representative decision fixtures if needed for UI work.
- [ ] Configure production Firebase/environment values and re-enable the production build task.

Local setup and Deno tasks are in the [root README](../README.md). Start the preview server with `deno task dev:pages` from this
folder. These notes describe source review and inherited verification status, not a newly tested release.
