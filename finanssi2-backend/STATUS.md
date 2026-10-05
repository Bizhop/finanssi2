# Backend status

Updated 2026-10-02 after implementing single-player debug mode. Gameplay and private debug controls are implemented;
live frontend integration review and the follow-ups below remain. See [frontend status](../finanssi2-web/STATUS.md) for UI gaps.

## Completed features

| Area | Implemented |
|---|---|
| Game data | Startup loading and validation of 46 squares, 7 business groups, 20 title deeds, 21 shares, 21 Finance News cards and 41 Stock Tips |
| Authentication and chat | Firebase authentication for REST and STOMP, persisted chat and live messages |
| Lobby | Create, list, join, leave, creator settings and start; 2–6 players, starting cash, tie re-rolls and shuffled decks |
| Turns and movement | Command validation, allowed command types, dice, bank stops, cars and turn order |
| Money and loans | Transfers with reasons, loan limits, interest, bank rewards and queued payment obligations, including outside the payer's turn |
| Properties and shares | Purchases, automatic rent, complete groups, mortgages, redemption and selling back |
| Construction | Buildings and industrial plants, deed values by building state and building permit exceptions |
| Special squares | Move squares, jail, skipped turns, exemption after jail, bank/player dividends and share crash |
| Bonds | Purchases, sealed auctions, small/grand draws and winning bonds returned to the bank |
| Finance News | All 21 cards, immediate effects and ongoing modifiers; Stock Tip draws, permit/certificate exceptions and meeting restrictions are integrated |
| Stock Tips | All 41 transcribed cards, public held cards, choices, asset transfers/swaps, compulsory auctions and deck returns |
| Shareholders' meetings | Takeover validation, success rolls, seller payments, fee distribution and transfer of mortgages |
| Game end | Cash-and-group win, last-player win, full bankruptcy liquidation, resignation, creator closure and final standings |
| Persistence and live updates | PostgreSQL JSONB game state, atomic event history, optimistic locking and lobby/game broadcasts |
| Frontend API and dev support | Static board/card data, actor-aware game views and private allowlisted debug games with command dice and next-card controls |

## Implementation decisions

- Code lives under `fi.bizhop.finanssi2.game`: `data` loads immutable assets, `engine` applies rules without Spring or persistence,
  `service` loads/saves/broadcasts, and `web` exposes REST models. Card effects use stable ids in code; the JSON holds card text.
- The canonical data is in `src/main/resources/gamedata/`. Transcriptions are in `../input/data/`; the rules, board photo and
  card photos remain in `../input/`. The deeds and cards were transcribed from the physical set on 2026-09-30.
- JSON retains the transcription format, including chapter `type` and `font-style`. Unknown fields and inconsistent asset
  counts, groups, values or relationships fail startup. There are 19 group shares and two fund shares. The physical set lacks
  one of the 42 Stock Tips listed in the rules; use the 41 available cards.
- Commands validate before changing state, then return typed events. REST receives commands and STOMP broadcasts events and
  the saved version. Invalid rules/concurrent saves return `409`; disallowed actors return `403`.
- PostgreSQL stores game metadata in a versioned row and the complete mutable `GameState` in JSONB. Each command updates that row
  and appends its typed JSONB events in one transaction. Chat messages use a relational table with generated, fixed-width decimal
  string ids for descending cursor pagination. JPA entities define the schema; Hibernate updates it at startup. An incompatible
  update can use an empty database because persisted data is currently disposable.
- Dice are injected (`SecureRandom` normally, scripted in tests). Debug overrides belong to one command, fall back to random
  rolls when exhausted and discard unused values. The old dev dice route/profile wiring is removed. Deck order stays hidden.
- Player ids are Firebase uids; name/photo are copied on joining. Pieces are the lowest free numbers 0–5. A creator leaving the
  lobby passes ownership to the earliest remaining player; the last departure deletes the lobby. Lobby changes also enter the log.
- All table information is public, including held Stock Tips. Upcoming deck order and sealed bids stay hidden. There is one
  game topic, `/topic/games/{id}`, plus `/topic/games` for lobby changes.
- Pending decisions queue the addressed player's legal actions and block ordinary play. Later payments to a player already
  raising funds queue even if cash covers them; each requires its own `Pay`. Bankruptcy drops that player's other payments.
- `GET /api/games/{id}` returns `{game, allowedCommands, actingPlayer}`. The list contains command types, validated using candidate parameters;
  it does not enumerate all legal assets, fees or amounts. Stock Tip choices carry their options in the pending decision.
- `Rules` centralizes values and modifiers; `Payments` records cash changes, including car purchases/sales. `Bonds` centralizes
  purchases, auctions, draws and returns. `BondContinuation` is a plain enum, serialized by name.
- Money uses integer currency units (€ in the transcriptions, marks in the original game), in multiples of 500. The bank's cash
  is unlimited. Cars, properties, shares and bonds are limited; building pieces are not counted because the set has enough.
- Rejected-command tests use immutable typed snapshots of all state fields, preserving player and deck order; a coverage check
  requires new state fields to be included. Existing tests cover data, engine, service, controller, PostgreSQL and authentication.
  PostgreSQL persistence tests use a host database (`databaseTest`) or Testcontainers (`containerTest`) for manual runs.

## Private single-player debug mode

- Six planned implementation steps were completed in separate commits on 2026-10-02. The implementation plan was retired;
  these status files are the handoff. One verified account manually controls 2–6 seats (default two); no automatic opponents.
- Configure exact comma-separated addresses in `finanssi2.debug.allowed-emails`; the committed default is empty. Access requires
  Firebase's verified email claim, comparing trimmed addresses case-insensitively with `Locale.ROOT`. No addresses or patterns
  are embedded in code. `GET /api/me/capabilities` exposes only `debugMode`.
- Game mode is immutable `NORMAL`/`DEBUG`. The creator uid owns a debug game even after their seat is eliminated. Additional
  seats have stable server-generated ids and need no accounts.
- `POST /api/debug/games` accepts `playerCount` and ordinary `settings`. Existing settings/start routes work for the owner.
  Debug joins/leaves and ordinary commands are denied. Lists, reads, history and literal game-topic subscriptions require current
  access plus ownership. Debug payloads never enter the public lobby topic; client SENDs to game topics are rejected.
- `POST /api/debug/games/{id}/commands` accepts `actor`, `expectedVersion`, `command` and optional `dice` (at most 32 values, 1–6).
  Actor and version are checked before execution. The effective seat is `state.actor()`, including out-of-turn decisions; closure
  still uses authenticated creator authority. Normal commands reject unexpected actor/dice fields. Conflicts return 409.
- `PUT /api/debug/games/{id}/next-card` accepts `deck` (`FINANCE_NEWS`/`STOCK_TIP`), `card` and `expectedVersion`. It moves one
  available card to the front, preserves other order/membership and records `DebugDeckChanged` in the same transaction.
  Held cards, unknown ids/decks, pending decisions and nonrunning games are rejected; effects change only on an ordinary draw.
- `DELETE /api/debug/games/{id}` returns empty 204, removes state/history and emits a private deletion notification. Versioned
  removal rejects stale saves. Archival racing with a deletion can leave unreachable log entries; they are not cleaned up.
- Automated checks cover empty/unverified/unlisted identities, verified token mapping, six-seat creation,
  private lifecycle, owner elimination, stale actors/versions, dice isolation, Pay, all bond/asset bidders, grand-draw offers,
  normal command isolation, card invariants/draws, HTTP conflicts/204 and websocket owner enforcement.

Outstanding developer-run acceptance:

- [ ] Configure a verified Google account and play through the real frontend/backend: create/configure/start/reload a 2–6-seat
  debug game, act for every seat/decision, select both card decks, close after owner elimination and delete.
- [ ] Use two tabs for conflicting actions and deletion notifications; verify reconnects, allowlist removal and normal games.
- [ ] Run `containerTest` against PostgreSQL in Docker.
- On 2026-10-02 the available host endpoint `host.docker.internal:8080/api/hello` returned 500. No deployed allowlist or
  Google-authenticated browser session was supplied, so live play was not claimed.

## Rules and interpretations

These retain the decisions from the former open-questions file. The physical rules are in
[Finnish](../input/rules/rules-fi.md) and [English](../input/rules/rules-en.md).

### Movement, turns and jail

- Starting on square 1 triggers neither interest nor a Stock Tip. Starting-order ties re-roll among the tied players; play then
  proceeds in join order from the starter.
- Mandatory stops on 34 and 1 apply only to forward dice movement. Moving to a square triggers its landing effect, without
  effects on intervening squares. Card moves to square 1 normally charge interest and draw a Stock Tip unless the card forbids it.
- The board resolves the rules' numbering error: square 36 is the chance of jail; square 37 moves to 46. Square 36 sends a jailed
  player to 24 for the ordinary jail roll. An exempt player neither moves nor rolls.
- Landing in jail resolves normally and allows after-roll actions before `EndTurn`. Missed turns begin on the next turn and are
  skipped automatically with events. Rent, dividends and bond prizes continue. If all active players are jailed, skip until
  someone can play. Leaving jail grants exemption from square 36 until landing on square 1.
- Left neighbour means the next active player in turn order; right means the previous one.
- A bankrupt current player passes the turn immediately, with `TurnStarted` for the next player and no `TurnEnded`.

### Money, assets and construction

- Rent is collected automatically. A complete business group requires all its properties and shares and doubles rent.
  Fund shares belong to no group: they count as share capital and receive ordinary bank dividends, but never complete a group
  or participate in a shareholders' meeting.
- A dash on a deed means unavailable for that building state: no rent, mortgage or buy-back as applicable. Square 8 is
  Pysäköintitalo: no group, building or mortgage; 10 000 parking fee from car owners only; 25 000 buy-back.
- Property/share purchases use separate commands, as do their sales back to the bank. Buying is before rolling, on 11 or 35–46,
  once per turn. Construction does not consume the purchase allowance; each building has its own payment and event.
- One building per property. Mortgaged properties must be redeemed before building. Buildings cannot be voluntarily removed
  or sold separately; a sale back includes the building at the deed's built value. Fire and bankruptcy remove buildings.
- No loan on square 43, including before rolling on the next turn. Mandatory payments can require raising funds outside one's
  turn. Optional purchases fail when cash is short rather than creating an obligation.
- Funds available include cash, the car's sale value, available loans, share buy-back values and the larger of mortgage/buy-back
  for each unmortgaged property. Mortgaged properties contribute nothing; redemption is unavailable while raising funds.
- Bankruptcy is legal only when those funds cannot cover the debt. The engine takes permitted loans and liquidates sellable
  assets automatically, using the higher property mortgage/buy-back value. The creditor receives all resulting cash; the rest
  of the debt is written off. Remaining assets return to the bank without compensation, buildings/mortgages are cleared,
  loans cancelled and held cards returned to the deck. Resignation uses bankruptcy liquidation with the bank as creditor.
- Squares 16, 28 and 46 pay each share's printed dividend, including fund shares. The 40% on square 39 and 30% on square 42
  identify dividend classes, rather than percentages of share capital. Square 41 counts groups where the payer owns any property,
  mortgaged or not, and records each nonzero creditor's shares. Zero dividends/crash charges add no event beyond landing.
- Square 38 auctions use sealed bids: 0 passes, highest wins and pays its bid, ties follow turn order from the current player.
  Compulsory asset sales use the same tie rule. Bids stay hidden until all eligible players respond; no bids mean no sale.
- Square 45's draw waits for its bond offer; square 46 pays dividends before offering a bond. Draw events record every winning
  number/prize, even unowned bonds. Owned winners return to the bank. On doubles the smallest prize uses die minus one, or 12
  for double 1.

### Finance News

- Every new draw ends the previous ongoing effect, even when the new card has only immediate effects. The drawn card goes to
  the deck's bottom; the active ongoing card is stored by id. `FinanceNewsDrawn` precedes immediate effects. FL-01 has both
  ongoing credit restrictions and an immediate car tax.
- FL-04 resolves the drawer's direction before their three-step move. Other players, except those in jail, move one step and
  trigger ordinary landings, except that Finance News squares do not draw another card.
- FL-06 uses the lower of two dice for movement and the bank reward. FL-16 doubles movement, bank rewards and dividends;
  jail, shareholders' meeting and bond rolls stay ordinary. Under FL-16, square 46 pays doubled bank dividends to all active
  players in turn order, but offers a bond only to the player who landed there.
- FL-16 doubles player dividends as well as bank dividends; FL-20 doubles only bank dividends. FL-06/FL-08 stop dividends.
- Share price changes apply to the printed sale and buy-back values separately, rounded up to 500 when halved (37 500 → 19 000).
  FL-06/FL-09 change only buy-back values. FL-11 raises unbuilt property sale/buy-back values by 50%; unavailable buy-back stays
  unavailable. FL-14 changes building/industrial property prices, without changing rent.
- Percentage cash payments (FL-07/FL-13) round up to 500. FL-19 moves players with buildings directly to 1, ignoring intervening
  effects, granting the bank reward if the forward route crosses 34, then applying square 1 and the building tax.
- FL-10's move to 1 draws a Stock Tip. Purchase certificates bypass FL-09/FL-21; building permits bypass FL-12. FL-15 prevents
  shareholders' meetings.

### Stock Tips

- Immediate cards go to the deck's bottom; held cards stay public and outside the deck until used or bankruptcy. Cards with
  no applicable effect do nothing and return to the bottom. Complete-group exemptions protect the affected owner's complete
  groups; transfers and swaps check both sides.
- PV-01 permits building before/after rolling on 17 or 40, even under FL-12. PV-07 permits one property anywhere before/after
  rolling, also under FL-12. Each permit returns after use. Purchase certificates exempt one purchase from a sales stop;
  location, before-roll timing and the one-purchase limit still apply.
- Fire lets the player choose up to two existing buildings; insurance pays 10 000 per pip only if a building burned.
- Card moves normally go forward to the target, triggering its landing, without a reward for passing 34 or effects on passing 1.
  PV-17 can choose any property, including Pysäköintitalo. PV-25 can be used before rolling on any own turn to move to 17,
  allow construction and end the turn without movement dice. PV-26 optionally pays 25 000 to move to 34 and receive its reward.
- Transport strike skips two turns, then draws Finance News before the next movement roll and returns the held card.
- PV-30 offers jail or 30 000 bail; insufficient cash forces jail. Jail moves to 24 and rolls missed turns without other effects.
  Bail gets an extra two-dice roll before the next movement roll, refunded on doubles.
- PV-31 sells the chosen eligible property at its base deed price plus building price, subtracting redemption for a mortgage.
  PV-38 is an immediate optional equal-price share swap, requiring no consent and excluding complete groups on both sides.
- PV-21 pays the right neighbour the printed dividends of shares in groups where the drawer owns a property. PV-22 pays only
  when the drawer is on square 1; PV-22/PV-23 and FL-18 use printed dividends, including fund shares. Shares made worthless
  return to the bank and can be bought again.
- Tax refunds round 20% of cash up to 500. PV-16 pays 25 000 from the bank when the drawer owns bond 1, otherwise from the drawer
  to its owner; no payment when unowned. Any paid bond 1 returns to the bank.
- Compulsory-sale choices/options live in pending decisions and bids stay sealed until all bidders respond. With two players,
  PV-36 does nothing. The minimum bid is a lobby setting.

### Shareholders' meetings and game end

- Meetings are before rolling on 35–46, with owned assets and another player's assets in the chosen group. With the recommended
  `shareholdersMeeting` setting every property and share of the group must already be bought from the bank, since a meeting takes
  over all of the group; the printed rules leave this out. With the original setting bank-owned assets stay out of the takeover.
  Base property, building and share prices determine takeover cost; mortgages transfer intact.
- Brokerage fees are multiples of 10 000 from 20 000 to 120 000. At 120 000 success is automatic; otherwise two dice must total
  at most fee / 10 000. On failure the bank gets the fee. On success the bank gets up to 30 000, sellers split the remainder
  rounded to the nearest 500, and the bank absorbs rounding. A 20 000 fee goes entirely to the bank.
- Winning requires at least 1 000 000 cash and two complete groups, or being the last active player. Simultaneous qualifying
  players are ordered from the current player. Current end checks wait until pending decisions are resolved.
- Finished games reject commands. The creator can close a running game without a winner. Final net worth uses cash plus base
  purchase prices of properties, buildings, shares, bonds and a car, minus loan principal.

## Settings and todos

Single-player debug implementation is complete; developer-run live acceptance remains outstanding below.

Implemented lobby settings, fixed once the game starts:

The recommended options are the developer's house rules and the defaults (`GameSettings.DEFAULT`). The original printed rules
(`GameSettings.ORIGINAL`) stay available for legacy play, though some of them play poorly.

| Setting | Recommended (default) | Original |
|---|---|---|
| `loanLimit` | `UNLIMITED`: no bank total limit, still three per player | `OFFICIAL`: six loans total, at most three per player |
| `compulsorySaleMinimumBid` | `HALF_NOMINAL_PRICE`: half the base share price or property-plus-building price | `NONE`: no minimum |
| `shareholdersMeeting` | `ALL_ASSETS_BOUGHT`: the whole group bought from the bank | `ANY_OTHER_OWNER`: another player owns some of the group |

Settings saved before a setting existed keep what those games were played with: no minimum bid, and the whole-group meeting rule.
Engine tests run with the recommended rules unless they set `GameSettings.ORIGINAL` for printed-rule behaviour.

- [ ] Review the frontend against a live backend with at least two Google-authenticated players, especially decisions outside
  the current player's turn, auctions, card chains and reconnects. See the frontend notes for known controls that need work.
- [ ] Review later implementation choices. The old decision log explicitly recorded I1–I23 as accepted; it did not record
  acceptance for I24–I37. Those later choices cover bond queues/sealed bids/draw events and compatibility, atomic event writes,
  typed rejection snapshots, Finance News movement/dividends, Stock Tip decks/choices, the low meeting fee and net-worth formula.
- [ ] Add decision timeouts for players who stop responding, after live frontend review. Creator closure is the current fallback.
- [ ] Consider optional borrowing on bankruptcy: exclude untaken loans from solvency and leave borrowing voluntary. The current
  official rule requires every available loan before bankruptcy and passes that money to the creditor.
- [ ] Consider loan settings for higher interest, interest every lap or a repayment deadline. These are proposals, not adopted
  rules; the current low cost and early purchasing advantage motivated the unlimited-bank-loans recommendation.
- [ ] Remove or rename the remaining `NotImplemented` landing events for `BRANCH_OFFICE` and `CONSTRUCTION`. Their actions
  already work through commands; the marker misleadingly suggests missing gameplay.
- [ ] Expand focused card/end-game regression coverage when those areas change. The planned per-card and interaction checks
  were broader than the current `FinanceNewsTest`, `StockTipsTest` and `GameEndTest` classes; implementation is not evidence
  that every planned scenario has an automated test.

The last full isolated suite run was before the PostgreSQL migration, on 2026-10-02 (231 tests, including the former in-memory
MongoDB suite, HTTP endpoints and real websocket clients). On 2026-10-05, all 212 regular tests and all 13 PostgreSQL integration
checks passed against the host PostgreSQL 17 instance. Follow [AGENTS.md](AGENTS.md) for build isolation and [README](../README.md)
for local setup.
