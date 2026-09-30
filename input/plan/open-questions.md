# Open questions and rule interpretations

Keep this file current: when a question is answered, move it to "Decided" with the answer and the step it affects.

## Needs data from the physical game

All transcribed (D1–D6 under "Decided").

## Rule text vs board

All decided (B1–B3 under "Decided").

## Later consideration

| # | Topic | Notes |
|---|---|---|
| L2 | House rules for improved loans | Loan risk is small next to the fast returns from early purchases. Possible settings: higher interest, interest on every lap, a repayment deadline. Add as `GameSettings` fields next to the loan limit |

## Rules that need an interpretation for digital play

All decided (R1–R44 under "Decided").

## Implementation decisions to review

Made while implementing; change them if they don't fit.

| # | Decision | Step |
|---|---|---|
| I1 | Step 01 first loaded only the board, groups and Finance News; title deeds, shares and Stock Tips followed once transcribed, with record fields shaped by the cards | 01 |
| I2 | Finance News text moved as is; superseded by the transcription from the physical cards, which step 01 puts in the resources | 01 |
| I3 | Data files keep the transcription format (`"type": "FINANSSILEHTI"`, chapter `type`/`font-style`), so new transcriptions can be copied in unchanged. Unknown fields fail startup | 01 |
| I4 | Players live in `GameState` (not directly on `Game`), so the engine gets everything in one object; `GameState` exists from creation | 02 |
| I5 | Pieces are numbers 0–5, the lowest free one at join. The creator leaving the lobby passes the game to the earliest remaining player | 02 |
| I6 | Deck order is stored but left out of API responses (`@JsonIgnore`), so clients cannot see upcoming cards | 02 |
| I7 | Lobby events (`PlayerJoined`, `PlayerLeft`) go into the game log along with the start events | 02 |
| I8 | `allowedCommands` checks each parameterless command's full validation, not only its timing (no `SellCar` without a car, no `BuyCar` without the cash). Commands with parameters (step 05 on) need their own check | 03 |
| I9 | `GET /api/games/{id}` returns `{game, allowedCommands}` instead of the bare game | 03 |
| I10 | The pending decision queue came with step 04 and the per-turn purchase flag (`boughtThisTurn`, reset when the turn passes) with step 05 | 03, 05 |
| I11 | Dev dice need the `dev` profile (`--spring.profiles.active=dev`); values are queued per game and random rolls follow once they run out | 03 |
| I12 | "Not on square 43" read literally: no `TakeLoan` while the piece is on 43, including the start of the next turn before rolling | 04 |
| I13 | Car purchase and sale now log `MoneyTransferred` like every other change in cash; `CarBought`/`CarSold` no longer carry the price | 04 |
| I14 | A bankrupt player whose turn it was passes the turn on at once (`TurnStarted` for the next player, no `TurnEnded`) | 04 |
| I15 | `allowedCommands` now checks every command with every parameter value (each property and share) but still returns only the command types. The frontend will likely want the concrete options (which squares can be bought or mortgaged); extend the response then | 05 |
| I16 | Selling back is two commands, `SellBackProperty(square)` and `SellBackShare(share)`, instead of one with either parameter | 05 |
| I17 | The "can still raise funds" check counts, for each unmortgaged property, the larger of its mortgage and buy-back value, and every share's buy-back value. Mortgaged properties add nothing, since redeeming is not allowed while raising funds | 05 |

## Decided

| # | Question | Decision | Affects |
|---|---|---|---|
| R9 | Bank's money | Unlimited, as a hidden implementation detail (the rules don't say). Cars and assets stay limited | 04 |
| R1 | Owner forfeits rent if they forget to ask before the next roll | Rent is collected automatically | 05 |
| R2 | Payments outside one's own turn that exceed cash (e.g. Finance News "Uusi energiavero") | The player gets a raise-funds decision out of turn: they may mortgage, sell back and take loans, then pay or go bankrupt | 04, 09 |
| R3 | Do squares reached by "move to" (2→21, 37→46, 44→30, card moves) take effect? | Yes, the target square takes effect as a normal landing. Squares passed on the way do not | 07 |
| R4 | Do mandatory stops (34, 1) apply to backward moves and to card moves? | Only to forward dice moves | 03, 09 |
| R5 | Moved to square 1 by a card (e.g. "Uusi energiavero"): pay loan interest and draw a Stock Tip? | Yes, same as landing | 04, 09 |
| R6 | Missed turns in jail | The turn is skipped automatically with an event; the player cannot act during it. Rent, bond draws and dividends continue as normal | 07 |
| R7 | "Hyvät ajat": "all dice rolls are doubled" | Doubles movement rolls only. The card lists the doubled bank reward and dividends separately. Jail, shareholders' meeting and bond draw rolls stay normal | 09 |
| R8 | Square 38 auction format | Sealed bids from every player (0 = pass), highest wins, tie goes to the earliest in turn order from the current player; the winner pays their bid | 08 |
| R10 | Buildings per property | One; not removed or sold by choice. Stock Tip "Tulipalo" and bankruptcy remove them; a built property is sold back with its building at the deed's built value | 06, 10 |
| R11 | "Muuttuvat markkinat": do other players' one-step moves take effect? | Yes, except that a Finance News square draws no card | 09 |
| R12 | Starting on square 1 | No interest or Stock Tip at game start | 02 |
| R13 | A player with no legal way to pay goes bankrupt; who receives what the player could pay? | The creditor (player or bank) gets the cash the player had; the rest of the debt is written off | 04, 12 |
| R14 | Inactive players (a player who stops responding) | Out of scope until the frontend works; later a timeout per decision | 12 |
| R15 | Does drawing a Finance News card without a lasting effect end the active card? | Yes: any new draw ends it ("until a new Finance News card is drawn") | 09 |
| R16 | Shareholders' meeting: prices used for the takeover sum while a price-changing card is active; mortgaged properties in a takeover | Base prices; mortgaged properties transfer with the mortgage | 11 |
| R17 | "Dividends doubled" (FL-16, FL-20): does it cover square 41 dividends between players? | FL-16 yes ("Osingot jaetaan kaksinkertaisina"), FL-20 no (it says the bank's dividends) | 09 |
| R18 | FL-07 "half of cash": rounding | Round the payment up to 500, like FL-13 | 09 |
| R19 | Starting order when players tie | Tied players re-roll | 02 |
| D1 | Title deed values | Transcribed 2026-09-30, front and back: `input/data/hallintatodistukset.json` | 01, 05, 06 |
| D2 | Share values and dividends | Transcribed: each share prints its price, dividend percent, dividend and buy-back value (`input/data/osakkeet.json`) | 01, 05, 07 |
| D3 | 21 shares for 20 properties | 19 group shares (as many as properties per group) plus 2 fund shares in no group | 01, 05 |
| D4 | Parkkitalo (square 8) | "Pysäköintitalo", "P-talo" on the board: no group, not buildable, cannot be mortgaged; parking fee 10 000 from car owners only; bought back for 25 000 | 01, 05, 06 |
| D5 | Stock Tip cards | 41 transcribed (`input/data/porssivihjeet.json`); the 42nd is missing from the set (D6) | 10 |
| B2 | Dividend amount on squares 16, 28 and 46 | The dividend printed on each share, fund shares included | 07, 09 |
| D6 | 42 Stock Tips in the rules, 41 in the set | A card is missing from the physical set. The game uses the 41 transcribed cards | 01, 10 |
| B3 | Squares 39 and 42, "Pankki jakaa osinkoa 40 %:n / 30 %:n osakkeillesi" | 40% and 30% identify shares: the bank pays the printed dividend of the player's shares whose dividend percent is 40 (square 39) or 30 (square 42). Not a percentage of share capital | 01, 07 |
| R20 | "Left" (vasemmanpuoleinen) and "right" (oikeanpuoleinen) neighbour on Stock Tips | Left is the next active player in turn order, right the previous one | 10 |
| R22 | Building on a mortgaged property | Not allowed: the property must be redeemed before building | 06 |
| R23 | "Puoleen hintaan" / "kaksinkertaisella hinnalla" for shares (FL-06, FL-08, FL-09, FL-20) | Selling price = share price × factor; buy-back = printed buy-back value × factor, rounded up to 500 (half of 37 500 is 18 750 → 19 000). FL-06 and FL-09 change only the buy-back | 05, 09 |
| R26 | The two "Rakennuslupa" cards | PV-01: build any time in own turn (before or after rolling), also under FL-12, but only on 17 or 40. PV-07: one property, anywhere, any time in own turn, also under FL-12. Each returns to the deck after its use | 06, 10 |
| R30 | "Epäselvyyksiä liiketoimissa": jail or bail | A decision. Jail (chosen, or forced when cash does not cover the 30 000 bail): move to 24 and roll for missed turns as on the square, nothing else. Bail: pay 30 000; next turn the player gets an extra two-dice roll before the movement roll, and a double returns the bail | 10 |
| B1 | "Go directly to jail" is square 36 on the board; both rule texts say 37 (37 is "go to square 46") | Board is right: 36. The square 37 rules (die roll, exception after jail) apply to square 36 | 07 |
| R21 | A dash on a title deed (e.g. Hotelli unbuilt rent, Liikekeskus built mortgage) | Not available in that state: no rent, cannot be mortgaged, or not bought back | 05 |
| R24 | FL-11: unbuilt properties "50% higher" | Selling price and buy-back value of unbuilt properties × 1.5 (all such values are multiples of 5 000, so no rounding); deeds that are never bought back unbuilt stay so | 05, 09 |
| R25 | "Ostotodistus": buy "although a sales stop is in effect" | Exempts one purchase from FL-09 and FL-21 only; the square, before-roll and one-per-turn rules still apply | 05, 10 |
| R27 | "Tulipalo" with fewer than two buildings; insurance roll | The player chooses which buildings burn; with one, that one; with none, nothing happens. The insurance roll (10 000 per pip) only when at least one burned | 10 |
| R28 | Card moves to a square (Kolari, Korjauskuluja, Kokous) | Forward along the board to the target, which takes effect as a landing (R3). Passing 34 gives no reward (as the cards say) and passing 1 has no effect (R4) | 07, 10 |
| R29 | "Kuljetuslakko": two missed "throwing turns" | The next two turns are skipped like jail turns (R6); on the turn after them the player draws a Finance News card before rolling, and the card goes back to the deck | 10 |
| R31 | "Epävakaat tonttimarkkinat": "nimellisestä ostoarvosta" | The player chooses the property; the bank pays the deed price, plus the building price if built. A mortgaged property: the bank keeps the redemption price from the payment | 10 |
| R33 | "Pörssikauppaa": "if you want" but "the swap must be done" | Optional when drawn and cannot be kept for later; the other player's consent is not needed; neither share may be in a complete group | 10 |
| R34 | PV-21 pays the right neighbour "osinkoa" without an amount | The printed dividend of the neighbour's shares in groups where the drawer owns a property | 10 |
| R35 | "Osinkoa kaikista osakkeistasi" (PV-22, PV-23), FL-18 "asianmukaiset osingot" | The printed dividend of each share, fund shares included (as B2) | 07, 09, 10 |
| R36 | Fund shares ("Rahasto-osake") | Ordinary shares outside every group: bought and sold back like other shares and count as share capital (square 35, bank dividends), but never make a group complete and are never part of a shareholders' meeting | 05, 07 |
| R37 | "Virheinvestointi": shares "become worthless" | The shares with that dividend percent go back to the bank without compensation and can be bought again at the normal price | 10 |
| R38 | "Veronpalautus": 20% of cash | Rounded up to 500, like the other percentage payments (R18) | 10 |
| R39 | "Obligaatiovoitto!" when the drawer holds bond 1 | The bank pays the drawer 25 000; in every paid case bond 1 returns to the bank | 08, 10 |
| R40 | PV-26 "saat siirtyä … jos maksat 25 000" | Optional (a decision): pay and move to 34 with the reward roll, or do nothing | 10 |
| R41 | Held "Kokous" to square 17 (PV-25): when can it be played | On any own turn before rolling; square 17 then takes effect (construction), and the turn ends without a movement roll | 06, 10 |
| R42 | PV-17 "mene jollekin tontille": which squares | Any property square, sold or unsold, including Pysäköintitalo | 10 |
| R43 | A card whose effect cannot apply (no car, no buildings, no eligible asset, bank bond pool empty and neighbour has none) | Nothing happens; the card goes to the bottom of the deck | 10 |
| R44 | "Ei koske … yritysryhmissä" on Stock Tips | Means complete groups (rules text): assets in a complete group the affected player owns are exempt; for swaps and transfers, both sides' | 10 |
| R32 | "Pakkomyynti" auction | The player chooses the asset; the other players bid as on square 38 (R8); no bids, no sale. Minimum bid is a game setting (L3): none by default (the rules), or the house rule "half the nominal price" | 04, 08, 10 |
| L1 | Total bank loan limit | Game setting: official 6 loans in total (default), or house rule "unlimited bank loans" (still 3 per player), which the frontend recommends. Under the official limit the first players get the loans, and with them the money for early purchases, whose returns outweigh the small interest | 02, 04, 13 |
| L3 | House rule: minimum bid in the "Pakkomyynti" auction | Game setting next to the loan limit. Official (default): no minimum. House rule: bids start at half the nominal price (deed price plus building price if built, or the share price) | 04, 10 |
