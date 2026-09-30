# 01 Game data

Load all static game assets into the backend as immutable data and check them for consistency. Nothing else in the game reads
JSON directly.

## Done (first part)

The board, the groups and the Finance News cards load from `finanssi2-backend/src/main/resources/gamedata/`, the canonical copy
(`input/` keeps the raw sources and photos), with `Square`, `SquareType`, `BusinessGroup`, `Card`, `CardChapter`, `GameData` and
`GameConstants` in `game.data`. Title deeds, shares and Stock Tips waited for the transcriptions (I1).

## Remaining: title deeds, shares, Stock Tips (transcribed 2026-09-30)

- Copy `input/data/hallintatodistukset.json`, `osakkeet.json` and `porssivihjeet.json` to the resources, and replace the resource
  `finanssilehdet.json` with `input/data/finanssilehdet.json` (same ids, text from the physical cards; this also settles I2).
- Correct the board (`pelilauta.json`) from the board photo and the deeds:
  - square 8: name "Pysäköintitalo" (board "P-talo"), no group; Palveluyhtiö is squares 9 and 10 only
  - square 18: "Investointiyhtiö" (not "Sijoitusyhtiö"); square 29: "Jalostamo" (not "Öljynjalostamo")
  - squares 39 and 42: the percent identifies shares ("40 %:n osakkeillesi"), not a percentage of capital (B3); rename the field so
    the two meanings can't be mixed up, e.g. `shareClass`
  - drop the `_unverified` notes that the cards answered
- Records in `game.data`: `TitleDeed` (square, name, group or null, price, building label and price or null, rent / mortgage /
  redemption / buy-back as unbuilt–built pairs with nulls for dashes, parking fee), `Share` (id, group or null, value, dividend
  percent, dividend, buy-back). Nothing is mock any more, so no `mock` field and no startup warning about mock data.
- `GameData` lookups: deed by square, shares by group, fund shares, cards by deck (Stock Tips added).
- `GameConstants.STOCK_TIP_CARD_COUNT` is 41: one card of the 42 in the rules is missing from the physical set (D6).

## Validation (fails startup, covered by tests)

- Squares 1–46, each exactly once; `MOVE_TO` targets exist; industrial flags are exactly squares 26, 27, 29, 30, 32, 33
- Every property square has a title deed; Pysäköintitalo is the only property without a group and the only one without a building;
  every group lists only property squares
- Each group has as many shares as properties, and their prices sum to the group's printed share capital (`groupShareCapital`);
  exactly two fund shares; dividend = price × dividend percent
- Redemption = mortgage + 10% wherever a mortgage value exists; building label "Teollisuus" exactly on the industrial squares
- 21 Finance News cards and 41 Stock Tips, unique ids
- Money values are positive multiples of 500

## Tests

- Loading the real resource files succeeds and the counts match
- Each validation rule rejects a deliberately broken fixture

## Done when

All assets load at startup and the validation tests pass.

## Notes for later steps

- Card effects are not data: the files hold the text, and steps 09–10 map card ids to effects in code, so text corrections never
  touch the engine as long as ids stay stable.
- FL-01 holds two articles (an ongoing "Kiristyneet luottomarkkinat" and an immediate "Autoveron korotus"). Effects must allow
  several per card.
