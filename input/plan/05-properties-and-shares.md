# 05 Properties and shares

Buying, owning and selling properties and shares, rent, and complete business groups. Works on mock title deeds and shares until
D1–D4 in [open-questions.md](open-questions.md) are transcribed.

## Ownership

- State: each property has owner (or bank), mortgaged flag and (from step 06) building flag; each share has owner (or bank).
- `Ownership` helper: assets of a player, owner of a square, `ownsCompleteGroup(player, group)` (all properties and all shares of the
  group), share capital of a player (sum of share values), share capital excluding complete groups.

## Buying

- `BuyProperty(square)` and `BuyShare(shareId)`: before rolling, only while standing on square 11 or 35–46, and at most one property
  or share per turn. The asset must be the bank's. Price through `Rules` (Finance News changes prices and can stop sales).
- The rule "if a player has bought on their turn, they may buy on their next turn too" needs no code: it only restates that the limit
  is per turn.

## Rent

- Landing on a property owned by another player charges rent automatically (R1). No rent on a mortgaged property, an unsold one or
  one's own.
- Rent is doubled when the owner has the complete business group.
- `Rules.rent(property)` is the only rent calculation; step 06 adds built rent and step 09 the Finance News changes (halved rent, no
  parking fee).
- Rent is a payment obligation (step 04) when the payer is short.

## Mortgage and selling back

- `Mortgage(square)`: any time during own turn or a `RaiseFunds` decision; bank pays the mortgage value.
- `Redeem(square)`: before rolling only; costs the mortgage value + 10%, rounded up to 500 (check the rounding once the real
  mortgage values are known).
- `SellBack(square | shareId)`: any time during own turn or a `RaiseFunds` decision; bank pays the buy-back value. A mortgaged
  property must be redeemed first. Selling shares back is not restricted by complete groups. Buy-back value through `Rules`
  (Finance News "Huonot ajat" etc. halve share buy-back).
- Update the "can still raise funds" check of step 04 to include mortgaging and selling back.

## Bankruptcy

- A bankrupt player's properties and shares return to the bank, unmortgaged.

## Tests

- Buying: on 11, on 35–46, elsewhere; after rolling; second purchase in a turn; asset already owned; not enough cash
- Rent: unowned, own, other player's, mortgaged, complete group doubles, rent that triggers raise-funds
- Mortgage, redeem (before/after roll), sell back, sell back mortgaged
- Complete group needs all shares as well as all properties
- Bankruptcy returns assets

## Done when

Players can buy, collect rent on, mortgage and sell properties and shares, and complete groups double the rent.
