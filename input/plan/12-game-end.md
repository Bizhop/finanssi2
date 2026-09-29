# 12 Game end

Winning, finishing bankruptcy handling, and closing a game.

## Win condition

- A player wins as soon as they hold at least 1 000 000 in cash and own at least two complete business groups. Check after every
  command and every resolved decision, for all players (cash can arrive outside one's own turn, e.g. rent). If more than one player
  qualifies at once, the current player wins, otherwise the first in turn order after them.
- The last player left after the others go bankrupt wins.
- The game moves to `FINISHED` with the winner and final standings (cash, net worth from purchase prices, groups owned). No further
  commands are accepted.

## Bankruptcy, complete

Collect what steps 04–10 added into one procedure and test it as a whole: cash to the creditor (R13), loans cancelled, car,
properties (unmortgaged, buildings removed), shares and bonds back to the bank, held Stock Tips back to the deck, pending decisions
for the player dropped, player skipped in turn order.

## Leaving a running game

- `Resign`: the player leaves as if bankrupt, with the bank as creditor.
- The creator can end a game that no longer makes progress; it becomes `FINISHED` without a winner.
- Timeouts for players who stop responding (R14) are left for after the frontend works.

## Tests

- Win on exactly 1 000 000 with two groups; 1 000 000 with one group does not win; two groups reached by a shareholders' meeting
  while cash is already enough
- Win reached outside the player's turn
- Last player standing
- Full bankruptcy procedure; resign

## Done when

Games end with a winner, and a finished game rejects commands.
