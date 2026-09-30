# 07 Special squares and jail

Every square with a fixed effect that is not about bonds or cards. After this step only Finance News, Stock Tip and bond squares
remain `NotImplemented`.

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

## Tests

- Each move square, including the landing effect at the target
- Jail durations for each die value; skipped turns; rent collected while in jail
- Square 36 for rolls 1–6; the exception after jail and its reset on square 1
- Dividends on each square, including 39 and 42 paying only the matching share class; square 41 with the rules examples;
  square 35 with and without complete groups

## Done when

All non-card, non-bond squares have their effect.
