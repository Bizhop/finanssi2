# 02 Game lobby

Players can create a game, join it and start it. No gameplay yet, but the game document, the command path and the broadcast are in
place for the following steps.

## Scope

- `Game` document in collection `games`: id, status (`LOBBY`, `RUNNING`, `FINISHED`), creator uid, created time, `@Version`,
  players, and the game state (empty until started).
- `PlayerState`: uid, name, photo URL, piece colour or number, cash, position. Later steps add car, loans, assets, jail, held cards.
- Lobby operations (`GameService` + `GameController`):
  - `POST /api/games` creates a game; the creator joins it
  - `GET /api/games` lists games in the lobby and games the user is in
  - `GET /api/games/{id}` returns the full game
  - `POST /api/games/{id}/join`, `POST /api/games/{id}/leave` (lobby only; the last player leaving deletes the game)
  - `POST /api/games/{id}/start` (creator only, 2–6 players)
- Start: every player gets 75 000 and stands on square 1. Turn order: roll two dice per player, highest starts, ties re-roll among
  the tied players, then play proceeds in join order from the starter ("clockwise"). The rolls are events. Decks are shuffled.
- `GameEvent` collection `game_events`: game id, sequence number, time, type, payload. `GET /api/games/{id}/events?after=<seq>` for
  the log.
- Broadcast on `/topic/games/{id}`: the events and the new state version after every change. Clients load the full game over REST
  when they open it or see a version gap. Generalise `MessagingService` for this instead of adding a second sender.
- Lobby changes (a game created, joined, started) are also broadcast on `/topic/games` so the Games page can refresh.

## Tests

- Service: create/join/leave/start rules (full game, joining twice, starting with one player, non-creator starting, joining a
  running game)
- Start with scripted dice, including a tie
- Optimistic locking: two saves of the same version, the second fails and is reported as `409`
- Controller: status codes and request validation

## Done when

A game can be created, joined and started through the API, and the start events are stored and broadcast.

## Notes for later steps

- Step 03 routes all in-game actions through `POST /api/games/{id}/commands` rather than one endpoint per action; lobby actions stay
  as separate endpoints.
