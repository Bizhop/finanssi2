# 05 Properties and shares

Buying, owning and selling properties and shares, rent, and complete business groups. Values come from the transcribed title deeds
and shares (`input/data/hallintatodistukset.json`, `input/data/osakkeet.json`, loaded in step 01).

## What the cards show

- **Title deeds** give every value twice, for an unbuilt and a built property: rent, mortgage value, redemption price (back of the
  card) and buy-back value. A dash means "not available in this state", and a whole section can be missing:
  - Rent: Hotelli and the two Finanssiyhtymä properties charge no rent while unbuilt.
  - Mortgage: Liikekeskus properties can be mortgaged only unbuilt, Finanssiyhtymä only built; Pysäköintitalo and the three
    Teollisuuskonserni properties cannot be mortgaged at all ("Ei lainoitusta").
  - Buy-back: Käsiteollisuus and Tekniikka properties are never bought back ("Ei osteta takaisin"); Liikekeskus and Kemia only
    built, Finanssiyhtymä only unbuilt.
- **Pysäköintitalo** (square 8, "P-talo" on the board) belongs to no group and cannot be built on. It charges a parking fee of
  10 000, paid only by car owners, and is bought back for 25 000.
- **Shares**: 21 certificates. 19 belong to the seven groups (as many as the group has properties; Palveluyhtiö has two, for
  squares 9 and 10). Two are fund shares ("Rahasto-osake", 20% and 25%) outside any group. Each share prints its price, a dividend
  percent and the dividend amount (price × percent), and its own buy-back value, which is often below the price.

## Ownership

- State: each property has owner (or bank), mortgaged flag and (from step 06) building flag; each share has owner (or bank).
- `Ownership` helper: assets of a player, owner of a square, `ownsCompleteGroup(player, group)` (all properties and all shares of
  the group), share capital of a player (sum of share prices), share capital excluding complete groups. Fund shares count as share
  capital but never make a group complete.

## Buying

- `BuyProperty(square)` and `BuyShare(shareId)`: before rolling, only while standing on square 11 or 35–46, and at most one property
  or share per turn. The asset must be the bank's. Fund shares are bought like other shares (R36). Price through `Rules` (Finance
  News changes prices and can stop sales; the Stock Tip "Ostotodistus" lifts a sales stop, R25).
- The rule "if a player has bought on their turn, they may buy on their next turn too" needs no code: it only restates that the limit
  is per turn.

## Rent

- Landing on a property owned by another player charges rent automatically (R1). No rent on a mortgaged property, an unsold one or
  one's own, and none where the title deed shows a dash for the property's state.
- Rent is doubled when the owner has the complete business group.
- Pysäköintitalo: the parking fee is charged only from car owners; no doubling (no group).
- `Rules.rent(property, payer)` is the only rent calculation (the payer matters for the parking fee); step 06 adds built rent and
  step 09 the Finance News changes (halved rent except the parking fee, no parking fee).
- Rent is a payment obligation (step 04) when the payer is short.

## Mortgage and selling back

- `Mortgage(square)`: any time during own turn or a `RaiseFunds` decision, when the deed has a mortgage value for the property's
  current state; bank pays that value.
- `Redeem(square)`: before rolling only; costs the redemption price printed on the back of the deed (the mortgage value + 10%,
  always a multiple of 500, so no rounding is needed).
- `SellBackProperty(square)` and `SellBackShare(shareId)`: any time during own turn or a `RaiseFunds` decision, when the deed or share has a buy-back value for
  its current state; bank pays that value. A mortgaged property must be redeemed first. Selling shares back is not restricted by
  complete groups. Buy-back values through `Rules` (Finance News halves or doubles share prices, R23).
- Update the "can still raise funds" check of step 04 to include mortgaging and selling back, using the per-state values.

## Bankruptcy

- A bankrupt player's properties and shares return to the bank, unmortgaged.

## Tests

- Buying: on 11, on 35–46, elsewhere; after rolling; second purchase in a turn; asset already owned; not enough cash; a fund share
- Rent: unowned, own, other player's, mortgaged, complete group doubles, dash on the deed (Hotelli unbuilt), parking fee with and
  without a car, rent that triggers raise-funds
- Mortgage where the deed allows and where it doesn't; redeem (before/after roll) at the printed price; sell back where allowed,
  never-bought-back deeds, sell back mortgaged; share buy-back at the printed value
- Complete group needs all shares as well as all properties; fund shares never complete a group
- Bankruptcy returns assets

## Done when

Players can buy, collect rent on, mortgage and sell properties and shares, and complete groups double the rent.
