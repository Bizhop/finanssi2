# 06 Construction

Building on owned properties. Industrial plants (black, "Teollisuus" on the deed) go on squares 26, 27, 29, 30, 32 and 33;
buildings (red) on the other properties except Pysäköintitalo, which cannot be built on. The piece counts match: 6 + 2 spare plants,
13 + 2 spare buildings.

## Scope

- `Build(squares)`: before rolling, while standing on square 17 or 40, on any number of the player's own properties in one command
  (they need not be in the same group). Each costs the building price on its title deed; the whole command fails if cash does not
  cover it all.
- The two Stock Tip "Rakennuslupa" cards (building permits, step 10) change where and when building is allowed; step 10 adds them
  through `Rules.canBuild(player, square)`, which in this step checks only the square and the phase.
- One building per property. Buildings are not sold or removed by choice and the building price is never refunded on its own. A
  built property's deed values (rent, mortgage, buy-back) include the building. Two exceptions remove buildings: the Stock Tip
  "Tulipalo" (two buildings burn and go back to the bank) and bankruptcy (R10).
- No building on a mortgaged property: it must be redeemed first (R22). The deed's mortgage and redemption values depend on whether
  the property is built, and some properties can be mortgaged only in one state.
- Rent uses the title deed's built rent when a building exists, doubled with a complete group.
- The kind of building (plant or building) must be queryable: the energy tax (FL-19), the Stock Tip "Kiinteistöjen
  korjauskustannuksia" and the shareholders' meeting sum (step 11) depend on it.
- Bankruptcy removes the buildings (the property returns to the bank unbuilt).
- Building prices go through `Rules.buildingPrice(property)` (Finance News "Korkeasuhdanne rakennusalalla" doubles them;
  "Rakennuskielto" forbids building except with a permit).

## Tests

- Building on 17 and 40; elsewhere; after rolling; on someone else's property; on Pysäköintitalo; on a mortgaged property; twice on
  the same property; not enough cash for all
- Built rent with and without a complete group; Hotelli, which charges rent only when built
- Buildings removed on bankruptcy

## Done when

Players can build on 17 and 40 and built properties charge the built rent.
