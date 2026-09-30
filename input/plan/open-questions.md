# Open questions and rule interpretations

Keep this file current: when a question is answered, move it to "Decided" with the answer and the step it affects.

## Needs data from the physical game

| # | Question | Assumed for now | Affects |
|---|---|---|---|
| D1 | Title deed values (rent, built rent, building price, mortgage, buy-back) | Mock: rent 20% of price, built rent 80%, building price = price, mortgage 50%, buy-back 75% | 05, 06 |
| D2 | Share values and whether each share has a printed dividend | Mock values; dividend 10% of value | 05, 07 |
| D3 | 21 shares in the contents list vs 20 properties ("each group has as many shares as properties") | 20 shares | 01, 05 |
| D4 | Parkkitalo (square 8, white): its group, whether it can be built on, how its rent works | In Palveluyhtiö; not buildable (13 red buildings + 2 spare match the 13 other non-industrial properties); plain rent | 01, 05, 06 |
| D5 | All 42 Stock Tip cards | Mock deck | 10 |

## Rule text vs board

| # | Question | Assumed for now | Affects |
|---|---|---|---|
| B1 | "Go directly to jail" is square 36 on the board; both rule texts say 37 (37 is "go to square 46") | Board is right: 36. The square 37 rules (die roll, exception after jail) apply to square 36 | 07 |
| B2 | Dividend amount on squares 16, 28 and 46 ("determined case by case"). Finance News "Ennätysvuosi" pays "dividends according to the shares", which suggests each share lists a dividend | Pay the dividend printed on each share (mock value until D2) | 07, 09 |

## Rules that need an interpretation for digital play

| # | Question | Proposal | Affects |
|---|---|---|---|
| R1 | Owner forfeits rent if they forget to ask before the next roll | Rent is collected automatically | 05 |
| R2 | Payments outside one's own turn that exceed cash (e.g. Finance News "Uusi energiavero") | The player gets a raise-funds decision out of turn: they may mortgage, sell back and take loans, then pay or go bankrupt | 04, 09 |
| R3 | Do squares reached by "move to" (2→21, 37→46, 44→30, card moves) take effect? | Yes, the target square takes effect as a normal landing. Squares passed on the way do not | 07 |
| R4 | Do mandatory stops (34, 1) apply to backward moves and to card moves? | Only to forward dice moves | 03, 09 |
| R5 | Moved to square 1 by a card (e.g. "Uusi energiavero"): pay loan interest and draw a Stock Tip? | Yes, same as landing | 04, 09 |
| R6 | Missed turns in jail | The turn is skipped automatically with an event; the player cannot act during it. Rent, bond draws and dividends continue as normal | 07 |
| R7 | "Hyvät ajat": "all dice rolls are doubled" | Doubles movement rolls only. The card lists the doubled bank reward and dividends separately. Jail, shareholders' meeting and bond draw rolls stay normal | 09 |
| R8 | Square 38 auction format | Sealed bids from every player (0 = pass), highest wins, tie goes to the earliest in turn order from the current player; the winner pays their bid | 08 |
| R10 | Buildings per property | One; buildings cannot be removed or sold | 06 |
| R11 | "Muuttuvat markkinat": do other players' one-step moves take effect? | Yes, except that a Finance News square draws no card | 09 |
| R12 | Starting on square 1 | No interest or Stock Tip at game start | 02 |
| R13 | A player with no legal way to pay goes bankrupt; who receives what the player could pay? | The creditor (player or bank) gets the cash the player had; the rest of the debt is written off | 04, 12 |
| R14 | Inactive players (a player who stops responding) | Out of scope until the frontend works; later a timeout per decision | 12 |
| R15 | Does drawing a Finance News card without a lasting effect end the active card? | Yes: any new draw ends it ("until a new Finance News card is drawn") | 09 |
| R16 | Shareholders' meeting: prices used for the takeover sum while a price-changing card is active; mortgaged properties in a takeover | Base prices; mortgaged properties transfer with the mortgage | 11 |
| R17 | "Dividends doubled" (FL-16, FL-20): does it cover square 41 dividends between players? | FL-16 yes ("Osingot jaetaan kaksinkertaisina"), FL-20 no (it says the bank's dividends) | 09 |
| R18 | FL-07 "half of cash": rounding | Round the payment up to 500, like FL-13 | 09 |
| R19 | Starting order when players tie | Tied players re-roll | 02 |

## Implementation decisions to review

Made while implementing; change them if they don't fit.

| # | Decision | Step |
|---|---|---|
| I1 | Step 01 loads only the board, groups and Finance News. Title deeds, shares and Stock Tips (and their validations and the mock-count warning) wait for the transcriptions, since their record fields depend on what the cards show | 01 |
| I2 | Finance News text moved as is; the typos ("jka", "kaksinkertaiset.", "hinnan") are not fixed yet | 01 |
| I3 | Data files keep the transcription format (`"type": "FINANSSILEHTI"`, chapter `type`/`font-style`), so new transcriptions can be copied in unchanged. Unknown fields fail startup | 01 |
| I4 | Players live in `GameState` (not directly on `Game`), so the engine gets everything in one object; `GameState` exists from creation | 02 |
| I5 | Pieces are numbers 0–5, the lowest free one at join. The creator leaving the lobby passes the game to the earliest remaining player | 02 |
| I6 | Deck order is stored but left out of API responses (`@JsonIgnore`), so clients cannot see upcoming cards | 02 |
| I7 | Lobby events (`PlayerJoined`, `PlayerLeft`) go into the game log along with the start events | 02 |
| I8 | `allowedCommands` checks each parameterless command's full validation, not only its timing (no `SellCar` without a car, no `BuyCar` without the cash). Commands with parameters (step 05 on) need their own check | 03 |
| I9 | `GET /api/games/{id}` returns `{game, allowedCommands}` instead of the bare game | 03 |
| I10 | Per-turn flags and the pending decision queue are left for steps 04–05, where they are first used | 03 |
| I11 | Dev dice need the `dev` profile (`--spring.profiles.active=dev`); values are queued per game and random rolls follow once they run out | 03 |

## Decided

| # | Question | Decision | Affects |
|---|---|---|---|
| R9 | Bank's money | Unlimited, as a hidden implementation detail (the rules don't say). Cars and assets stay limited | 04 |
| L1 | Total bank loan limit | Game setting: official 6 loans in total (default), or house rule "unlimited bank loans" (still 3 per player), which the frontend recommends | 02, 04, 13 |
