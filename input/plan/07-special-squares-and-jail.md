# 07 Special squares and jail

Every square with a fixed effect that is not about bonds or cards. After this step only Finance News, Stock Tip and bond squares
remain `NotImplemented` (square 46 keeps its bond purchase for step 08 but pays its dividend here).

## Move squares

- Square 2 → 21, square 37 → 46, square 44 → 30 (backwards, out of the bank). The target takes effect as a landing (R3); squares
  passed on the way do not, and mandatory stops do not apply (R4).
- Put the move-to-square logic in one place; Finance News and Stock Tips reuse it.

## Jail

- Square 24: roll one die; 1–2 miss one turn, 3–4 two turns, 5–6 three turns. Missed turns are skipped automatically (R6).
- Square 36 (B1: the rules text calls it 37): roll one die; 3 or more, nothing happens; 1–2, move to 24 and roll for missed turns as
  above.
- Exception: a player who has left jail and not landed on square 1 since is not affected by square 36. Track this with a flag set on
  leaving jail and cleared on square 1.
- A player in jail still receives rent, dividends and bond prizes.

## Dividends

- Every share prints its dividend ("Osinko", its price × its dividend percent). Bank dividend squares pay that printed dividend:
  - 16 and 28 ("osinkoa kaikille osakkeillesi") and 46 ("Osinkojen jako"): on all the player's shares, fund shares included (B2).
  - 39 ("osinkoa 40 %:n osakkeillesi") and 42 ("30 %:n osakkeillesi"): only on the player's shares whose dividend percent is 40 or
    30 (B3). The percentages identify shares; they are not a percentage of share capital.
- Square 41: the player pays each other player 20% of that player's share capital in the groups where the paying player owns at
  least one property (fund shares are in no group, so never count). Use the two examples in the rules as test cases (B gets
  40 000, C gets 45 000). A shortfall is a payment obligation.
- All dividend amounts go through `Rules` ("Huonot ajat" stops dividends, "Hyvät ajat" doubles them, and more, in step 09).

## Share crash

- Square 35: pay the bank 10% of share capital, not counting shares in complete business groups the player owns. Fund shares
  count.

## Implementation notes

Starting points in `game.engine` (all built in steps 03–06):

- **Square effects** are `SquareHandler`s registered in the `GameEngine` constructor (`squareHandlers.put(SquareType.X, ...)`);
  `land(state, player, dice)` emits `LandedOn` and runs the handler. Types without a handler emit `NotImplemented`. The handlers to
  add here: `MOVE_TO`, `JAIL`, `GO_TO_JAIL_CHANCE`, `BANK_DIVIDEND`, `PLAYER_DIVIDEND`, `SHARE_CRASH` and the dividend part of
  `BOND_PURCHASE_AND_DIVIDEND` (keep a `NotImplemented` event for its bond purchase until step 08).
- **Square data**: `Square.target()` (move squares), `Square.shareClass()` (40 on 39, 30 on 42, null on 16, 28 and 46),
  `Square.percent()` (10 on 35, 20 on 41).
- **Moving**: add `moveTo(state, player, target, dice)` that emits `PieceMoved(from, to)` and calls `land`. A move square's target
  is never another move square, so the recursion ends.
- **Money** goes through `Payments`: `fromBank` for dividends; `charge(state, player, creditor, charges)` for payments that may
  exceed cash (share crash, square 41). New `MoneyReason`s: `BANK_DIVIDEND`, `PLAYER_DIVIDEND`, `SHARE_CRASH`.
- **Square 41 has several creditors.** Charge them one by one in turn order from the payer. `Payments.charge` pays at once when
  cash covers the amount; change it to queue a `RaiseFunds` whenever the player already has one pending, so payments stay in order
  (otherwise a later, smaller one could be paid before an earlier one that is waiting). On bankruptcy, drop the player's other
  pending decisions: their creditors get nothing (R13 gives the cash to the creditor of the decision being resolved).
- **Share capital**: `Ownership.sharesOf`, `shareCapital`, `shareCapitalOutsideCompleteGroups` (square 35), `propertiesOf`
  (groups where the payer owns a property, ignoring Pysäköintitalo, which has no group). All amounts come out as multiples of 500
  with the real data (share prices are multiples of 25 000), so no rounding is needed.
- **Rules**: add `bankDividend(state, share)` (printed dividend) and the 10% / 20% calculations, so step 09 can stop or double them.
- **Jail state** on `PlayerState`: `missedTurns` (turns still to skip) and a flag for "left jail and not on square 1 since" (e.g.
  `jailExemption`). Skipping happens in `GameEngine.passTurn`: while the next player has `missedTurns > 0`, decrement it, emit a
  `TurnSkipped(player, remaining)` event, and move to the following player. Set the exemption flag when a player's last missed turn
  is skipped; clear it in the square 1 handler (`bankExit`). Players who are `out` are already skipped by `nextPlayer`.
- **Events** to add: `JailRoll(player, die, missedTurns)`, `GoToJailRoll(player, die, jailed)`, `TurnSkipped`, and a
  `DividendPaid(player, square, shares, amount)` or similar, so the log says which shares paid.

Rules example for square 41 (rules-en.md, "Dividends between players"): A owns properties on 3 (Käsiteollisuus), 18
(Finanssiyhtymä), 21 and 22 (Tekniikka). B holds OS-KASITEOLLISUUS-1 and -2 (2 × 50 000) and OS-TEKNIIKKA-3 (100 000), plus a share
in a group where A has nothing (e.g. OS-KEMIA-1) → B gets 20% of 200 000 = 40 000. C holds OS-FINANSSIYHTYMA-1 and -2 (2 × 75 000)
and OS-TEKNIIKKA-1 (75 000) → C gets 20% of 225 000 = 45 000.

## Tests

- Each move square, including the landing effect at the target (e.g. 2 → 21 charges rent on an owned Elektroniikkayhtiö)
- Jail durations for each die value; skipped turns, including two players in jail at once; rent collected while in jail
- Square 36 for rolls 1–6; the exception after jail and its reset on square 1
- Dividends on each square, including 39 and 42 paying only the matching share class; square 41 with the rules examples, and with
  cash for the first creditor but not the second; square 35 with and without complete groups

Test helpers in `src/test/.../game/engine`: `TestGame` (state builder: `players`, `at`, `cash`, `car`, `loans`, `owns`,
`ownsShares`, `ownsGroup`, `mortgaged`, `built`, `afterRoll`, ...), `EngineTests` (`roll` with scripted dice, `send`,
`assertRejected` which also checks the state is unchanged), `ScriptedDice`. Add `TestGame` methods for the jail state as needed.

## Done when

All non-card, non-bond squares have their effect.
