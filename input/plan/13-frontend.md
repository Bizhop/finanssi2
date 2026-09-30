# 13 Frontend (outline)

Detail this step once the backend steps are done, and split it into several files like the backend steps. The backend exposes
everything needed: game state, events, `allowedCommands` per user and the per-game topic.

Likely steps, in order:

1. Games page: list, create, join, start (step 02 API, `/topic/games`); the creator picks the game settings (step 04), with the
   house rule "unlimited bank loans" marked as recommended
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
