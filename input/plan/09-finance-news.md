# 09 Finance News

The Finance News deck and its 21 cards (real transcriptions). Most cards change rules while they are in effect, so this step is
where the `Rules` component earns its keep.

## Deck

- Drawn on landing on square 5 or 31, or when a Stock Tip says so. Deck order is fixed at game start (step 02).
- A card that says "Voimassa toistaiseksi" (in effect until further notice) becomes the **active card** and stays until the next
  Finance News card is drawn. Then it goes to the bottom of the deck. Other cards are carried out and go straight to the bottom.
- Drawing any card, also one without a lasting effect (e.g. "Inflaatiopaineita!"), ends the active card: the rules say "until a
  new Finance News card is drawn" (R15 in [open-questions.md](open-questions.md)).
- The card's instruction applies to all players unless stated otherwise.

## Effect model

- `NewsCardEffect` per card id, in code: an optional immediate action (runs once when drawn) and an optional ongoing `RuleModifier`
  (default methods return the plain rule, a card overrides only what it changes). `Rules` asks the active card's modifier.
- A card may hold several articles (FL-01 has one ongoing and one immediate article).
- Immediate actions that make several players pay use the step 04 obligation mechanism, one `RaiseFunds` decision at a time in turn
  order starting from the drawing player.

## Cards

Ids follow the order of `input/data/finanssilehdet.json`, transcribed from the physical cards (2026-09-30 photo). The earlier
`input/finanssilehdet_21kpl.json` has the same cards in the same order with different wording and titles; it is superseded.

| Id | Card | Effect | Kind |
|---|---|---|---|
| FL-01 | Kiristyneet luottomarkkinat + Autoveron korotus | No new loans; interest doubled, also on square 43. Car owners pay 5 000 now | ongoing + immediate |
| FL-02, FL-03 | Suuri obligaatioarvonta! | Grand bond draw (step 08) | immediate |
| FL-04 | Muuttuvat markkinat | Drawer chooses 3 steps forward or back (a decision); others, except players in jail, move 1 forward. Landing on a Finance News square draws no card (R11) | immediate |
| FL-05 | Huonot taloudelliset näkymät | All rents halved, built and unbuilt; only the Pysäköintitalo fee stays | ongoing |
| FL-06 | Huonot ajat | Everyone rolls two dice and moves by the lower; the square 34 reward too ("Sääntö pätee myös palkkio-tilanteissa"); bank buys shares back at half price (R23); no dividends | ongoing |
| FL-07 | Inflaatiopaineita! | Everyone pays half their cash, rounded up to 500 (R18) | immediate |
| FL-08 | Laskusuhdanne | Bank sells and buys back shares at half price (R23); no dividends | ongoing |
| FL-09 | Osakekaupat pysähtyneet! | Bank sells no shares, except to holders of "Ostotodistus" (R25); buys back at half price (R23) | ongoing |
| FL-10 | Korjaustöitä pankin pääkonttorissa | A player landing on or standing on square 34 moves to square 1 and draws a Stock Tip, with no reward. Players inside the bank (35–46) are not affected | ongoing (with an immediate part for players already on 34) |
| FL-11 | Tonttien hinnannousu! | Bank sells and buys back unbuilt properties at +50% (R24) | ongoing |
| FL-12 | Rakennuskielto! | No building, except with a "Rakennuslupa" (Stock Tip) | ongoing |
| FL-13 | Omaisuusveron korotus! | Everyone pays 10% of cash up to 100 000 and 25% of cash above it, rounded up to 500 | immediate |
| FL-14 | Korkeasuhdanne rakennusalalla | Building prices and the prices of the six industrial properties (26, 27, 29, 30, 32, 33) doubled | ongoing |
| FL-15 | Lainsäädännön tiukentaminen | No shareholders' meetings (step 11) | ongoing |
| FL-16 | Hyvät ajat! | Movement rolls doubled (R7); bank rewards and dividends doubled; square 46 applies to all players in turn order from the player on it | ongoing |
| FL-17 | Bensiinin tuontikielto | Car owners roll one die, except the owner of square 27 (Bensiiniyhtiö); no Pysäköintitalo fee | ongoing |
| FL-18 | Ennätysvuosi pörssissä! | Bank pays every shareholder the printed dividend of each share (B2) | immediate |
| FL-19 | Uusi energiavero! | Every player with built properties (mortgaged too) moves to square 1 and pays 30 000 per plant and 20 000 per other building. A player who passes square 34 on the way rolls for the reward. Square 1 effects per R5 | immediate |
| FL-20 | Noususuhdanne! | Bank sells and buys back shares at double price (R23); bank dividends doubled, other dividends (square 41) normal | ongoing |
| FL-21 | Tonttikaupat pysähtyneet! | Bank sells no properties, except to holders of "Ostotodistus" (R25) | ongoing |

Interpretations are recorded in open-questions.md (R15, R17, R18, R23, R24). FL-14 changes prices only, not rent.

## Tests

- Deck: drawing, active card replaced by the next draw, card order preserved
- One test class per card, using the "given table, when landing on 5, then events" style from step 03
- Interaction checks where two rules touch: FL-06 with a car, FL-17 for the Bensiiniyhtiö owner, FL-16 on square 46

## Done when

All 21 cards work and every ongoing effect is applied through `Rules`.
