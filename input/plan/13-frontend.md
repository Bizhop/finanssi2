# 13 Frontend

Status: implementation complete; review remains. The games lobby supports listing, creating, joining, leaving, configuring and
starting games. The game room loads live state and static board data, renders the board and player assets, supports the backend's
game commands and pending decisions, and displays card text and final standings. The user has deferred visual work until later.

## Substeps

| # | Result | Implementation |
|---|---|---|
| 13.1 | Authenticated games lobby with settings and live lobby refresh | Implemented |
| 13.2 | Game room with state reloads, event log and reconnect refresh | Implemented |
| 13.3 | Board, players, assets, bonds and held cards | Implemented |
| 13.4 | Command buttons and dialogs for pending decisions and parameterized actions | Implemented |
| 13.5 | Finance News and Stock Tips display | Implemented |
| 13.6 | Dev page with mocked game states | Implemented |

## Review status

- Visual redesign is deferred by the user; the current UI is functional but needs substantial visual work.
- The dev page at `/game-room.html` previews mocked running and finished states without Google sign-in or a backend. Its mock
  command endpoint does not simulate state transitions.
- Live multiplayer play and pending-decision flows still need manual review against the backend. This requires at least two
  Google-authenticated players; the user plans to try that later.
- Backend `compileJava` and `bootJar` succeeded here. The user confirmed that a local IntelliJ `clean build` succeeds after clearing
  a stale compiled `GameRandomConfig.class`.
- Frontend typechecking and dev-page rendering have not been verified in this environment because Deno is unavailable.

Keep transport and shared API types in `src/components/gameApi.ts`, the games list in `Games.tsx`, and the live room in `GameRoom.tsx`.

1. Games page: list, create, join, start (step 02 API, `/topic/games`); the creator picks the game settings (steps 04 and 10), with
   the house rule "unlimited bank loans" marked as recommended
2. Game view skeleton: load the game, subscribe to `/topic/games/{id}`, reload on version gaps, event log
3. Board: 46 squares from `GameData` (served by a new `GET /api/game-data`), pieces, owners, buildings, mortgages; `input/board.png`
   as the visual reference
4. Player panels: cash, car, loans, assets, bonds, held cards
5. Actions: buttons driven by `allowedCommands`, dialogs for pending decisions (raise funds, bond purchase, auction bid,
   shareholders' meeting, "Muuttuvat markkinat" direction)
6. Cards: Finance News and Stock Tips rendered from their `chapters` (header, italic), active Finance News card on display
7. Dev page with mocked game states for UI work, following `finanssi2-web/dev-pages/`

Frontend work can start in parallel after step 03 if needed: step 02 and step 03 give enough to render a lobby, a board and
moving pieces.
