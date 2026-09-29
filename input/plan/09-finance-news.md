# 09 Finance News

The Finance News deck and its 21 cards (real transcriptions). Most cards change rules while they are in effect, so this step is
where the `Rules` component earns its keep.

## Deck

- Drawn on landing on square 5 or 31, or when a Stock Tip says so. Deck order is fixed at game start (step 02).
- A card that says "Voimassa toistaiseksi" (in effect until further notice) becomes the **active card** and stays until the next
  Finance News card is drawn. Then it goes to the bottom of the deck. Other cards are carried out and go straight to the bottom.
- Open point: does drawing a card without a lasting effect (e.g. "Inflaatio kiihtyy") also end the active card? The rules say
  "until a new Finance News card is drawn", so yes. Added as R15 in [open-questions.md](open-questions.md).
- The card's instruction applies to all players unless stated otherwise.

## Effect model

- `NewsCardEffect` per card id, in code: an optional immediate action (runs once when drawn) and an optional ongoing `RuleModifier`
  (default methods return the plain rule, a card overrides only what it changes). `Rules` asks the active card's modifier.
- A card may hold several articles (FL-01 has one ongoing and one immediate article).
- Immediate actions that make several players pay use the step 04 obligation mechanism, one `RaiseFunds` decision at a time in turn
  order starting from the drawing player.

## Cards

Ids follow the order of `input/finanssilehdet_21kpl.json`.

| Id | Card | Effect | Kind |
|---|---|---|---|
| FL-01 | Kiristyneet luottomarkkinat + Autoveron korotus | No new loans; interest doubled, also on square 43. Car owners pay 5 000 now | ongoing + immediate |
| FL-02, FL-03 | Suuri obligaatioarvonta | Grand bond draw (step 08) | immediate |
| FL-04 | Muuttuvat markkinat | Drawer chooses 3 steps forward or back (a decision); others, except players in jail, move 1 forward. Landing on a Finance News square draws no card (R11) | immediate |
| FL-05 | Heikentyneet taloudelliset näkymät | All rents halved, except Parkkitalo | ongoing |
| FL-06 | Huonot ajat | Everyone rolls two dice and moves by the lower; bank entrance reward by the lower die; bank buys shares back at half the purchase price; no dividends | ongoing |
| FL-07 | Inflaatio kiihtyy | Everyone pays half their cash, rounded up to 500 (R18) | immediate |
| FL-08 | Pörssikurssit laskussa | Bank sells and buys back shares at half value; no dividends | ongoing |
| FL-09 | Osakkeiden ostosulku | Bank sells no shares; buys back at half the purchase price | ongoing |
| FL-10 | Korjaustöitä pankin pääkonttorissa | A player landing on or standing on square 34 moves to square 1 and draws a Stock Tip, with no reward. Players inside the bank are not affected | ongoing (with an immediate part for players already on 34) |
| FL-11 | Tonttien hinnat nousevat | Unbuilt properties sold and bought back at +50% | ongoing |
| FL-12 | Rakennuskielto | No building, except with a building permit (Stock Tip) | ongoing |
| FL-13 | Korotettu omaisuusvero | Everyone pays 10% of cash up to 100 000 and 25% of cash above it, rounded up to 500 | immediate |
| FL-14 | Korkeasuhdanne rakennusalalla | Building prices and the prices of the six industrial properties doubled | ongoing |
| FL-15 | Tiukentuva lainsäädäntö uhkaa | No shareholders' meetings (step 11) | ongoing |
| FL-16 | Hyvät ajat | Movement rolls doubled (R7); bank rewards and dividends doubled; square 46 applies to all players in turn order from the player on it | ongoing |
| FL-17 | Bensiinin ja öljyn tuontikielto | Car owners roll one die, except the owner of Bensiiniyhtiö; no rent on Parkkitalo | ongoing |
| FL-18 | Ennätysvuosi pörssissä | Bank pays every shareholder the dividend of their shares (B2) | immediate |
| FL-19 | Uusi energiavero | Every player with built properties (mortgaged too) moves to square 1 and pays 30 000 per plant and 20 000 per other building. A player who passes square 34 on the way rolls for the reward. Square 1 effects per R5 | immediate |
| FL-20 | Pörssikurssit nousussa | Bank sells and buys back shares at double value; bank dividends doubled | ongoing |
| FL-21 | Tonttien ostokielto | Bank sells no properties, except to holders of a purchase certificate (Stock Tip) | ongoing |

Ambiguities go to open-questions.md: R15, R17 and R18 came from this table. FL-14 is assumed not to affect rent.

## Tests

- Deck: drawing, active card replaced by the next draw, card order preserved
- One test class per card, using the "given table, when landing on 5, then events" style from step 03
- Interaction checks where two rules touch: FL-06 with a car, FL-17 for the Bensiiniyhtiö owner, FL-16 on square 46

## Done when

All 21 cards work and every ongoing effect is applied through `Rules`.
