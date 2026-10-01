# Gameplay implementation plan: overview

The plan builds the game rules into the backend one feature at a time. Each step builds on the previous ones and ends with passing
tests. The frontend follows once the backend can run a game (step 13).

Any later step may be rewritten when an earlier step turns up a limitation or a better approach. Record decisions and rule
interpretations in [open-questions.md](open-questions.md) so they stay in one place.

## Steps

| # | Step | Result |
|---|---|---|
| 01 | [Game data](01-game-data.md) | Board, groups, title deeds, shares, cards and rule constants loaded and validated |
| 02 | [Game lobby](02-game-lobby.md) | Create, join and start a game; state saved and broadcast |
| 03 | [Turn engine and movement](03-turn-engine-and-movement.md) | Command/event engine, dice, movement, car, turn order |
| 04 | [Money and loans](04-money-and-loans.md) | Payments, loans, interest, square 34 reward, raising funds, basic bankruptcy |
| 05 | [Properties and shares](05-properties-and-shares.md) | Buy, rent, double rent, mortgage, sell back |
| 06 | [Construction](06-construction.md) | Buildings and industrial plants, built rent |
| 07 | [Special squares and jail](07-special-squares-and-jail.md) | Every fixed-effect square on the board |
| 08 | [Bonds](08-bonds.md) | Bond purchase, small and grand draws, auction |
| 09 | [Finance News](09-finance-news.md) | News deck and effects implemented as far as possible before Stock Tips and shareholders' meetings; remaining integrations belong to steps 10–11 |
| 10 | [Stock Tips](10-stock-tips.md) | Stock Tip deck, held cards, all card effects |
| 11 | [Shareholders' meeting](11-shareholders-meeting.md) | Business group takeover |
| 12 | [Game end](12-game-end.md) | Win condition, full bankruptcy, finished games |
| 13 | [Frontend](13-frontend.md) | Outline only; detailed when the backend is in place |

After step 03 the game is playable end to end in tests: squares without an implementation only log that the player landed there.
Each later step replaces some of those placeholders.

## Progress

| Step | Status |
|---|---|
| 01–07 | Done; each step is its own commit (`backend: plan step NN, ...`) |
| 08 | Done |
| 09 | Done as far as possible; remaining integrations deferred to steps 10–11 |
| 10–13 | Planned |

How to continue a step: read the step file and [open-questions.md](open-questions.md) (decisions R*, B*, D*, I*), implement in
`game.engine` with tests in the same style as the existing ones (`TestGame`, `EngineTests`, `ScriptedDice`), run
`bash -l -c "./gradlew test --console=plain"` in `finanssi2-backend`, record new implementation choices as I-entries for review,
then mark the step done here. Commit only when the developer asks.

## Assets

Source material is in `input/`: the rules, the board photo and the 2026-09-30 photos of the cards. Data transcribed from them is in
`input/data/`; the backend's `src/main/resources/gamedata/` holds the canonical copies that the game loads.

| Asset | Count | Status | File |
|---|---|---|---|
| Board squares | 46 | Transcribed, corrected from the board photo and title deeds | `input/finanssi_pelilauta.txt` (cp1252), `input/data/pelilauta.json` |
| Finance News | 21 | Transcribed from the cards | `input/data/finanssilehdet.json` (supersedes `input/finanssilehdet_21kpl.json`) |
| Title deeds | 20 | Transcribed, front and back | `input/data/hallintatodistukset.json` |
| Shares | 21 | Transcribed: 19 group shares and 2 fund shares | `input/data/osakkeet.json` |
| Stock Tips | 41 (42 per contents list; one is missing from the set, D6) | Transcribed | `input/data/porssivihjeet.json` |
| Bonds | 12 | Numbers 1–12 and price 500 ("Palautetaan voiton jälkeen": returned after a win); no file needed | step 01 rule constants |
| Car and loan certificates | 6 + 6 | Only counts matter; the certificate texts match the rules; no file needed | step 01 rule constants |

## Architecture

New code goes under `fi.bizhop.finanssi2.game`:

- `game.data`: static assets (board, deeds, shares, cards) loaded from JSON at startup, immutable records
- `game.engine`: the rules. Plain Java with no Spring or MongoDB, so it can be tested fast and in isolation
- `game.service`: loads a game, runs a command through the engine, saves and broadcasts the result
- `game.web`: REST controller and request/response models

Decisions that apply across all steps:

- **Commands in, events out.** A player action is a command (`Roll`, `BuyProperty`, `EndTurn`, ...). The engine checks it against
  the state, changes the state and returns events (`DiceRolled`, `RentPaid`, ...). A rejected command changes nothing and returns an
  error that the API reports as `409 Conflict` with a reason.
- **State is one MongoDB document per game**, with `@Version` optimistic locking so two concurrent commands cannot both apply. The
  engine works on the loaded copy; a failed command is simply not saved. New events are saved atomically with state in a hidden
  `unarchivedEvents` field, then copied to the separate game log. Stable event ids (`gameId:seq`) make partial log writes safe to
  retry. Event reads retry archival and merge the stored batch with the log without duplicates; later changes also retry archival
  and discard only batches successfully archived before that state save. This works with standalone MongoDB and needs no transaction
  or extra game version increment. Archival failure does not reject an already saved command.
- **Randomness is injected.** Dice go through a `Dice` interface: `SecureRandom` in production, scripted values in tests. Decks are
  shuffled once at game start and stored in the state in draw order. Every roll is recorded in an event.
- **Pending decisions.** Some rules need input from a player other than the one whose turn it is (bond purchases in turn order,
  auction bids, raising funds for a tax). The state holds a queue of pending decisions; while it is not empty only the addressed
  player may act, and only with the commands that decision allows.
- **Bonds.** `Bonds` owns purchases, auctions, draws and returns to the bank. Prices and prize lists go through `Rules`. Offer
  continuations use `BondContinuation`; the API keeps the existing numeric codes and old numeric MongoDB values still load.
- **Rule values go through one place.** Dice count, rent, prices, dividends and loan limits are computed by a `Rules` component, not
  inline. Steps 03–08 implement the plain rules; steps 09–10 make it take the active Finance News card and held Stock Tips into
  account without touching the callers.
- **Money** is an `int` in the currency units of the transcriptions (€; the 1986 game used marks). All amounts are multiples of 500.
- **Players** are identified by Firebase uid (`User.uid()`), with name and photo copied from `User` when joining.
- **All game information is public**, as it is on the physical table, so one topic per game (`/topic/games/{id}`) is enough.
  Commands go in over REST, following the chat's pattern (REST POST in, STOMP broadcast out).
- **Tests.** The engine is covered by unit tests with scripted dice; rejected commands compare typed snapshots of every state field,
  including player order and the deck. Services and controllers follow `ChatServiceTest` and `ChatControllerTest`. Every step ends
  with `bash -l -c "./gradlew test --console=plain"` passing (see `finanssi2-backend/AGENTS.md`).
