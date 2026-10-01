# 10 Stock Tips

Status: done. All 41 transcribed Stock Tip cards have an effect, including held cards and pending choices. The deck is shuffled at
game setup, draws on square 1 and Stock Tip squares, and returns held cards to the bottom on use or bankruptcy.

The Stock Tip deck: 41 cards transcribed from the physical game into `input/data/porssivihjeet.json` (ids PV-01 … PV-41, in photo
order). The rules list 42, but one is missing from the set (D6 in [open-questions.md](open-questions.md)), so the game uses 41. Many
cards share an effect, so the step builds reusable effect primitives and maps card ids to them.

## Deck

- Drawn on landing on squares 7, 14, 20 and 25, and on square 1 (bank exit). Also on square 1 when moved there by a card (R5) and
  under Finance News FL-10. Two cards move a player to square 1 or 20 and say not to draw ("Älä nosta uutta pörssivihjettä").
- The card is carried out immediately unless it says otherwise, then goes to the bottom of the deck.
- **Held cards** stay with the player and out of the deck until used, then go to the bottom. They are public, and return to the
  deck on bankruptcy. Held: both "Rakennuslupa" (PV-01, PV-07), both "Ostotodistus" (PV-02, PV-08), "Kokous" to square 17
  (PV-25), and "Kuljetuslakko" (PV-29) while its missed turns run.

## Cards

"Left" and "right" neighbours follow R20. "Not in groups" means assets in a complete business group the player owns are exempt
(rules text; R44).

| Id | Card | Effect |
|---|---|---|
| PV-01 | Rakennuslupa | Held. Build at any time during own turn (before or after rolling), also under FL-12, but only while on square 17 or 40 (R26) |
| PV-07 | Rakennuslupa | Held. Build on one property at any time during own turn, anywhere on the board, also under FL-12 (R26) |
| PV-02, PV-08 | Ostotodistus | Held. Buy one property or share from the bank although sales are stopped (FL-09, FL-21) (R25) |
| PV-03, PV-09 | Rahasto-osakeanti | Free fund share (20% / 25%) from the bank; if another player holds it, roll two dice and take it from them free on 7 or more |
| PV-04 | Ojaanajo | Car owners only: the car goes back to the bank without compensation |
| PV-05 | Kolari | The drawer and the left neighbour move to square 10 (R28); applies without a car too |
| PV-06 | Autoveron korotus! | Car owners pay 15 000 |
| PV-10 | Tulipalo | Two buildings of the player's choice go back to the bank; then roll one die and receive 10 000 per pip (R27) |
| PV-11 | Kiinteistöjen korjauskustannuksia | Pay 40 000 per industrial plant and 20 000 per other building |
| PV-12 | Korjauskuluja | Car owners move to square 10 (R28); others take an extra one-die movement roll |
| PV-13, PV-14, PV-15 | Osinkojen jako | As square 41: pay other players 20% of their share capital in groups where the drawer owns a property |
| PV-21 | Osinkojen jako | Pay the right neighbour the printed dividend of their shares in groups where the drawer owns a property (R34) |
| PV-22 | Osinkojen jako | If the drawer is on square 1 now, the bank pays the printed dividend of all their shares |
| PV-23 | Osakevoittoa! | The bank pays the printed dividend of all the drawer's shares |
| PV-16 | Obligaatiovoitto! | Bond 1 wins 25 000: paid by the bank to the drawer if they hold it, otherwise by the drawer to its holder; nothing if the bank holds it. The bond returns to the bank (R39) |
| PV-24 | Uusi obligaatiolaina | Free bond of the drawer's choice from the bank; if none are left, the left neighbour must give one the drawer chooses |
| PV-17 | Kokous | Move to any property square, own or another player's; no reward when passing square 34 (R28) |
| PV-18 | Kokous | Move to square 20 without drawing a Stock Tip; if already on 20, move to 34 without the reward roll |
| PV-19 | Kokous | Move to square 8 (Pysäköintitalo), no reward when passing 34; then draw a Finance News card |
| PV-20 | Onnistunut kokous | Move to square 34 and roll for the reward; then draw a Finance News card |
| PV-25 | Kokous | Held. Play it before rolling on any own turn: move to square 17 (construction) and do not roll that turn (R41) |
| PV-26 | Kokous | Optional: pay 25 000 to move to square 34 (reward roll follows) (R40) |
| PV-27, PV-28 | Kokous pankin lainaosastolla | Move to square 43 and repay a loan (square 43 rules); without loans, move to square 34 and roll for the reward |
| PV-29 | Kuljetuslakko | Miss two turns, keeping the card; before the next roll after that, draw a Finance News card (R29) |
| PV-30 | Epäselvyyksiä liiketoimissa | Choose: go to jail now (square 24 and its roll, nothing else), or pay 30 000 bail; jail is forced when cash does not cover the bail. With bail, an extra two-dice roll before the next movement roll returns the bail on a double (R30) |
| PV-31 | Epävakaat tonttimarkkinat | Roll two dice; on 7 or less sell one property back to the bank at its purchase price, also one never normally bought back; not in groups (R31) |
| PV-32, PV-33 | Epävakaa taloudellinen tilanne | Draw a Finance News card |
| PV-34 | Perintö | Receive 50 000 and move to square 1 without drawing a Stock Tip (loan interest applies, R5) |
| PV-35 | Pakkoluovutus | Give a share or property of the drawer's choice to the left neighbour, who pays the drawer 10 000; not in groups |
| PV-36 | Pakkomyynti | Sell a share or property of the drawer's choice to the highest bidder among the others; not with two players; not in groups. Minimum bid per game setting: none (default) or half the nominal price (house rule, L3) (R32) |
| PV-37 | Veronpalautus | Receive 20% of cash (R38); then draw a Finance News card |
| PV-38 | Pörssikauppaa | Optionally swap one share with another player's share of the same price; must be done now if possible, cannot be kept; not in groups (R33) |
| PV-39, PV-40 | Virheinvestointi | All the drawer's shares with a 30% (40%) dividend go back to the bank without compensation; not in groups (R37) |
| PV-41 | Virheinvestointeja | Pay the bank per share by its dividend percent: 20% and 25% 5 000, 30% and 40% 10 000, 50% 15 000; complete groups included |

## Game setting

- `GameSettings.compulsorySaleMinimumBid` (L3): `NONE` (default, the rules) or `HALF_NOMINAL_PRICE` (house rule: the deed price plus
  building price if built, or the share price, halved). Read through `Rules`, like the loan limit. Bids below the minimum are
  rejected; with no valid bid there is no sale.

## Effect primitives

- Money: `ReceiveFromBank(amount | percentOfCash)`, `PayBank(amount)`, `PayPerBuilding(perPlant, perBuilding)`,
  `PayPerShareClass(table)`
- Movement: `MoveTo(square, drawCard = true, reward = normal)`, `MoveToChosenProperty`, `ExtraRoll(dice = 1)`,
  `MoveWithNeighbour(square)`; all reuse the move-to logic of step 07
- Cards: `DrawFinanceNews` (after the other effects of the card)
- Dividends: `BankDividendOnAllShares` (printed), `PlayerDividend(20%)` (reuse step 07), `NeighbourDividend`
- Assets: `LoseCar`, `ReturnBuildings(count)`, `ReturnSharesOfClass(percent)`, `SellPropertyToBank(onRoll)`,
  `GiveAssetToNeighbour(payment)`, `CompulsoryAuction` (reuse the square 38 bid mechanism of step 08), `SwapShare`,
  `FundShareIssue(shareId)`, `FreeBond`, `BondOneWins`
- Decisions: `Choice` for optional or either-or cards (PV-26, PV-30, PV-38)
- Turn effects: `MissTurns(count, thenDrawFinanceNews)` (reuse the missed-turn handling of step 07), `NoRollThisTurn`,
  `BailRoll` (the extra roll at the start of the next turn, R30)
- Held: `BuildingPermit(onConstructionSquareOnly | oneProperty)`, `PurchaseCertificate`, `MoveToConstruction`. Their rule changes
  go through `Rules`, like the Finance News modifiers.

## Tests

- Deck order; held cards leaving and returning to the deck; the "do not draw" moves
- Each primitive, with scripted deck order
- Held permits interacting with FL-12 and certificates with FL-09 and FL-21
- "Not in groups" exemptions for each card that has one

## Done when

Every transcribed card has an effect, and square 1 and the Stock Tip squares draw from the deck.
