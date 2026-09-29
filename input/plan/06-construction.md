# 06 Construction

Building on owned properties. Industrial plants (black) go on squares 26, 27, 29, 30, 32 and 33; buildings (red) on the other
properties. Values come from the mock title deeds until D1 and D4 are answered.

## Scope

- `Build(squares)`: before rolling, while standing on square 17 or 40, on any number of the player's own properties in one command
  (they need not be in the same group). Each costs its building price from the title deed; the whole command fails if cash does not
  cover it all.
- Stock Tip "Rakennuslupa" (building permit) allows building anywhere; step 10 adds it through `Rules.canBuild(player)`, which in
  this step checks only the square.
- One building per property (R10); Parkkitalo cannot be built on while D4 is open. Buildings are permanent: no selling or removing,
  no refund.
- Mortgaged properties: the rules do not forbid building on them. Allow it unless the title deeds say otherwise; rent stays zero
  while mortgaged.
- Rent uses the title deed's built rent when a building exists, doubled with a complete group.
- The value of a building counts in the shareholders' meeting sum (step 11) and in the energy tax (step 09), so keep the building
  kind (plant or building) queryable.
- Bankruptcy removes the buildings (the property returns to the bank unbuilt).
- Building prices go through `Rules.buildingPrice(property)` (Finance News "Korkeasuhdanne rakennusalalla" doubles them;
  "Rakennuskielto" forbids building except with a permit).

## Tests

- Building on 17 and 40; elsewhere; after rolling; on someone else's property; twice on the same property; not enough cash for all
- Built rent with and without a complete group
- Buildings removed on bankruptcy

## Done when

Players can build on 17 and 40 and built properties charge the built rent.
