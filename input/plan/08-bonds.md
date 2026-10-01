# 08 Bonds

Twelve bonds, numbered 1–12, 500 each. A player holds any number; bonds cannot be traded between players.

## Buying

- Landing on square 45 or 46: the player may buy one bond from the bank. Offer it as a pending decision (`BuyBond(number)` or
  `Pass`), since the draw on 45 waits for it.
- Square 38: the bank sells one bond to the highest bidder. Every player with cash gets a sealed bid decision (R8); the winner pays
  the bid and picks the bond number. No bids, no sale.

## Draws

- Small draw on every landing on square 45, after the purchase: prizes 50 000 / 25 000 / 15 000.
- Grand draw when Finance News "Suuri obligaatioarvonta" is drawn (step 09 triggers it; implement and test it here): the drawing
  player may buy one bond, then each other player in turn order may buy one, then prizes 100 000 / 50 000 / 25 000.
- Draw: roll both dice. Largest prize to the bond numbered the sum, second to the higher die, smallest to the lower die. On a double,
  the smallest prize goes to the die value minus one, and a double 1 gives it to bond 12. Examples from the rules: 4+4 → 8, 4, 3;
  1+1 → 2, 1, 12.
- A prize goes only to a bond owned by a player; a winning bond returns to the bank.
- Square 46 with "Hyvät ajat" in effect applies to all players (step 09); keep the square's bond purchase callable per player.
- Stock Tips reuse these parts (step 10): "Uusi obligaatiolaina" gives a free bond of the drawer's choice (from the left neighbour
  when the bank has none), "Obligaatiovoitto!" pays 25 000 on bond 1 and returns it, and "Pakkomyynti" auctions an asset with the
  square 38 bid mechanism. Keep bond ownership changes and the bidding in one place each.

## Implementation notes

Starting points in `game.engine` (steps 03–07):

- **Bond state**: a `BondState(number, owner)` list on `GameState`, created in `GameSetup.initAssets` next to properties and shares
  (`BOND_COUNT`, `BOND_PRICE`, `SMALL_DRAW_PRIZES`, `GRAND_DRAW_PRIZES` are in `GameConstants`). Add it to `EngineTests.Snapshot` so
  `assertRejected` covers it, and to the `GameMongoTests` round trip. Keep ownership changes in one helper (a `Bonds` class or
  methods on `Ownership`) for the Stock Tips in step 10.
- **Square handlers**: square 45 is `SMALL_BOND_DRAW` (with `bondPurchase: true`), 46 is `BOND_PURCHASE_AND_DIVIDEND`
  (`bondPurchaseAndDividend` pays the dividend, then emits a `NotImplemented` placeholder for the purchase: replace that), 38 is
  `BOND_AUCTION`. Register handlers in the `GameEngine` constructor.
- **Decisions**: `PendingDecision` is a sealed interface with only `RaiseFunds` so far; `GameEngine.decisionCommands` switches over
  it, and `validate` casts the first decision to `RaiseFunds` for `Pay` and `DeclareBankruptcy` (check the type instead). The bond
  offer needs something to run when it resolves (the small or grand draw), so give the decision a field for what follows, rather
  than running the draw in the handler. New commands go in `GameCommand`, `TIMING` (as `DECISION`) and `candidateCommands`
  (one `BuyBond` per number; for a bid with an amount, add one representative value so `allowedCommands` lists it, see I15).
- **Order with payments**: a bond offer and a `RaiseFunds` can be pending at once; `Payments.charge` only waits for the player's
  pending `RaiseFunds` (I19), so other decision types don't hold payments back.
- **Sealed bids** (R8): the decision queue lets one player act at a time, so bidding goes in turn order from the current player; keep
  the bids out of API responses (`@JsonIgnore`, as the decks, I6) until the last bid is in, then emit them in one event.
- **Money**: `Payments.toBank` for purchases and winning bids, `fromBank` for prizes; new `MoneyReason`s `BOND_PURCHASE`,
  `BOND_PRIZE`. Prizes and the bond price go through `Rules`, so step 09 can change them.
- **Bankruptcy**: bonds have no sale value, so a bankrupt player's bonds go back to the bank without compensation (R45), listed in
  `AssetsReturned` with the properties and shares. Selling the other assets first is step 12.

## Tests

- Buying on 45 and 46, passing, no bonds left
- Draw number rules for normal rolls, doubles and double 1; prizes for unowned bonds; winning bonds returned
- Grand draw purchase order starting from the drawing player
- Auction: highest bid, tie by turn order, no bids

## Done when

Bonds can be bought and won in both draws, and square 38 auctions one bond.
