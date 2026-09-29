# 03 Turn engine and movement

The core loop: whose turn it is, what they may do, rolling, moving and ending the turn. Every later step adds commands, events and
square handlers to this framework.

## Engine

- `GameEngine.handle(state, playerUid, command, dice) -> Outcome(events)`: validates, mutates the given state and returns events,
  or throws `RuleViolation(reason)` without partial changes. Keep validation first and mutation after it, so a failed command
  leaves the state untouched without needing a rollback.
- `GameCommand` sealed interface (Jackson polymorphic by `type`); this step adds `Roll`, `EndTurn`, `BuyCar`, `SellCar`.
- `GameEvent` sealed interface; this step adds `TurnStarted`, `DiceRolled`, `PieceMoved`, `LandedOn`, `CarBought`, `CarSold`,
  `TurnEnded`, `NotImplemented` (landing on a square whose handler does not exist yet).
- Turn state in `GameState`: current player, `phase` (`BEFORE_ROLL`, `AFTER_ROLL`), per-turn flags (e.g. bought something this
  turn, used for step 05), and the pending decision queue (empty in this step; see the overview).
- Every command declares when it is allowed: before roll only, after roll only, or any time during own turn. Put this in one
  table in the engine rather than in each handler; the rules text uses exactly these three categories.
- `GameEngine.allowedCommands(state, playerUid)` lists the command types the player may send right now, from the same table.
  `GET /api/games/{id}` returns it for the requesting user, so the frontend can show only valid actions without duplicating rules.
- `SquareHandler` per `SquareType`, called on landing. This step implements only no-op handlers for `BANK_EXIT` and
  `BANK_ENTRANCE`; the rest emit `NotImplemented`.

## Movement rules

- Without a car: one die. With a car: two dice. Inside the bank (a player starting the move on squares 34–46): one die.
- The board is one loop 1 → 46 → 1. A forward move stops on square 34 and on square 1 even if the roll is larger.
- A roll is `Rules.movementDice(state, player)`; later steps (Finance News) change it only there.
- After the move, the landing handler runs, then the phase is `AFTER_ROLL`. `EndTurn` passes the turn to the next active player.

## Car

- `BuyCar`: before rolling, costs 50 000, one car per player, 6 cars in total (certificate count). Fails without enough cash
  (raising funds for it comes with step 04 loans).
- `SellCar`: any time during own turn, bank pays 25 000.

## Testing support

- `ScriptedDice` for tests: a queue of values that fails the test when it runs out.
- A test builder for game states (players, positions, cash, later assets), so rule tests read as "given this table, when this
  command, then these events".
- Dev profile only: `POST /api/games/{id}/dev/dice` queues dice values for the next rolls, for manual testing with the frontend.

## API

- `POST /api/games/{id}/commands` with a command body; returns the events. `409` with a reason on a rule violation, `403` when it is
  not the player's turn or decision.

## Tests

- One die, two dice, one die inside the bank
- Stops on 34 and 1 when the roll would pass them; wrap from 46 to 1
- Rolling twice, ending turn before rolling, acting out of turn
- Car purchase before/after rolling, second car, not enough cash, selling
- Turn order passes over players who are out (prepares step 12)

## Done when

Players can take turns, move around the board and buy and sell cars through the API, with every action in the event log.
