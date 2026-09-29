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

## Tests

- Buying on 45 and 46, passing, no bonds left
- Draw number rules for normal rolls, doubles and double 1; prizes for unowned bonds; winning bonds returned
- Grand draw purchase order starting from the drawing player
- Auction: highest bid, tie by turn order, no bids

## Done when

Bonds can be bought and won in both draws, and square 38 auctions one bond.
