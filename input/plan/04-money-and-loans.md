# 04 Money and loans

Money transfers, bank loans, and what happens when a player owes more than they have. Properties are not in the game yet, so
raising funds covers loans and selling the car only; step 05 adds mortgaging and selling back.

## Payments

- One `Payments` component for all transfers: player → bank, bank → player, player → player. Each emits a `MoneyTransferred`
  event with the reason (rent, interest, reward, ...), so the log explains every change in cash.
- The bank has unlimited money (R9 in [open-questions.md](open-questions.md)). The rules don't say so; it is an implementation
  detail, not a setting, and the UI shows no bank balance.
- **Payment obligation.** When a player must pay more than their cash, the payment is not made yet: a `RaiseFunds` pending decision
  is queued for that player with the amount and the creditor. While it is open the player may use every fund-raising command (even
  outside their own turn, R2), then `Pay` or `DeclareBankruptcy`. `Pay` fails while cash is short. Other play waits until the
  decision is resolved.
- Optional payments (buying anything) never create an obligation; they fail when cash is short.

## Loans

- `TakeLoan`: any time during own turn or during a `RaiseFunds` decision, not on square 43. 50 000 each, at most 3 per player.
- Bank loan limit, a game setting chosen in the lobby:
  - **Official** (default): 6 loans in total across the game, as there are 6 loan certificates.
  - **House rule "unlimited bank loans"**: no total limit, still 3 per player. The official limit favours the first players, who
    can take all 6 loans in the first round; the frontend recommends this option.
- `RepayLoan`: any time during own turn, pays 50 000.
- Square 1 (landing): 5 000 interest per loan held.
- Square 43: the player must repay one loan if they have any, plus 5 000 interest on it. Uses the obligation mechanism when cash is
  short, but taking a new loan is not allowed there.
- Loan limits and interest go through `Rules`, since Finance News "Kiristyneet luottomarkkinat" changes them (step 09).

## Game settings

- `GameSettings` on the game, set by the creator in the lobby (`PUT /api/games/{id}/settings`, lobby only) and fixed at start. The
  first setting is the loan limit; later house rules go here too.
- Settings reach the engine through `Rules`, so callers don't check them.

## Square 34 reward

- On landing on square 34 the engine rolls both dice for every player (car or not) and the bank pays 5 000 per pip. The roll is an
  event. The amount goes through `Rules.bankEntranceReward(roll)` for Finance News to change later.

## Bankruptcy (basic)

- `DeclareBankruptcy` is allowed only when the player cannot cover the obligation even after all possible loans and sales. The engine
  checks this itself: compare what the player could still raise with what they owe, so a player cannot go bankrupt to dodge a
  payment.
- The creditor gets the player's cash (R13), loans are cancelled (under the official limit they go back to the 6-loan pool), the car returns to the bank. The
  player is marked out and skipped in turn order. Step 05 onward extends this to properties and shares, step 12 finishes the game
  when one player is left.

## Tests

- Interest on square 1 with 0–3 loans; loan limits per player and in total, with both loan limit settings; no loan on square 43
- Square 43 with and without loans, with and without enough cash
- Square 34 reward with scripted dice, for players with and without a car
- Obligation: raising funds with a loan then paying; trying to pay short; bankruptcy refused while funds can still be raised;
  bankruptcy accepted and the player skipped afterwards

## Done when

Loans, interest, the bank reward and payment obligations work, and a player can go bankrupt.
