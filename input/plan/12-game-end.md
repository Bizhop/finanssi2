# 12 Game end

Status: done. The engine now performs complete bankruptcy liquidation, records final standings, detects cash-and-group wins and the
last-player win, and supports resignation and creator-initiated closure.

Winning, finishing bankruptcy handling, and closing a game.

## Win condition

- A player wins as soon as they hold at least 1 000 000 in cash and own at least two complete business groups. Check after every
  command and every resolved decision, for all players (cash can arrive outside one's own turn, e.g. rent). If more than one player
  qualifies at once, the current player wins, otherwise the first in turn order after them.
- The last player left after the others go bankrupt wins.
- The game moves to `FINISHED` with the winner and final standings (cash, net worth from purchase prices, groups owned). No further
  commands are accepted.

## Bankruptcy, complete

Collect what steps 04–10 added into one procedure and test it as a whole:

- `DeclareBankruptcy` stays allowed only when even every available loan and sale cannot cover the debt (step 04,
  `GameEngine.fundsAvailable`).
- Take the available loans and sell everything sellable to the bank (R45): the car and shares (fund shares too) sold back, each
  unmortgaged property sold back or mortgaged, whichever gives more, built ones at their built value. Steps 04–07 skip this: the
  creditor gets only the cash on hand, no loans are taken, and the car, shares and properties return to the bank unsold.
- Cash to the creditor (R13), loans cancelled.
- What is left back to the bank without compensation: mortgaged properties (unmortgaged, buildings removed), properties the bank
  neither buys back nor lends on, bonds.
- Held Stock Tips back to the deck, pending decisions for the player dropped (I19), player skipped in turn order.

## Leaving a running game

- `Resign`: the player leaves as if bankrupt, with the bank as creditor.
- The creator can end a game that no longer makes progress; it becomes `FINISHED` without a winner.
- Timeouts for players who stop responding (R14) are left for after the frontend works.

## Tests

- Win on exactly 1 000 000 with two groups; 1 000 000 with one group does not win; two groups reached by a shareholders' meeting
  while cash is already enough
- Win reached outside the player's turn
- Last player standing
- Full bankruptcy procedure: the creditor gets the loan money and sale proceeds, unsellable assets return to the bank; resign

## Done when

Games end with a winner, and a finished game rejects commands.
