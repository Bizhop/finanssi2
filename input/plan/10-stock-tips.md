# 10 Stock Tips

The Stock Tip deck. The 42 cards in `MOCK_porssivihjeet.json` are invented (D5), so this step builds the mechanism around a set of
reusable effect primitives and maps the mock cards onto them. When the real cards are transcribed, replacing the data and the
id-to-effect table should be most of the work; add primitives only for cards that need them.

## Deck

- Drawn on landing on squares 7, 14, 20 and 25, and on square 1 (bank exit). Also on square 1 when moved there by a card
  (R5) and under Finance News FL-10.
- The card is carried out immediately unless it says otherwise, then goes to the bottom of the deck.
- **Held cards**: cards to be kept ("Säilytä kortti") stay with the player and out of the deck until used or returned. They are
  public. Held cards return to the deck on bankruptcy.

## Effect primitives

A first set, driven by the mock cards and the rules text:

- `ReceiveFromBank(amount)`, `PayBank(amount)`
- `PayPerBuilding(perBuilding, perPlant)`
- `ReceiveFromEachPlayer(amount)`, `PayEachPlayer(amount)`
- `MoveTo(square)`, `GoToJail` (straight to square 24 and roll for missed turns)
- `DrawFinanceNews`
- `BankDividend(percent | printed)`, `PlayerDividend(20%)` (reuse step 07)
- `ShareCapitalChange(percent, excludesCompleteGroups)`
- `CompulsorySale(cheapestProperty | chosenShare)`: "does not apply to shares in business groups" means assets in a complete group
  the player owns cannot be forced into the sale
- `CarPrize(orCash)`, `LoseCar`
- `RepayLoan`, `RollAgain`
- Held: `BuildingPermit` (build anywhere before rolling, and exempt from FL-12), `PurchaseCertificate` (exempt from FL-21),
  `JailRelease`. Their rule changes go through `Rules`, like the Finance News modifiers.

Mock-only cards that turn out not to exist can be deleted along with their primitive.

## Tests

- Deck order and held cards leaving and returning to the deck
- Each primitive, with scripted deck order
- Held permits interacting with FL-12 and FL-21

## Done when

Every card in the mock deck has an effect, and square 1 and the Stock Tip squares draw from the deck.

## When the real cards arrive

Replace the data file, rewrite the id-to-effect table, add missing primitives, delete unused ones, and update this file with the
real list.
