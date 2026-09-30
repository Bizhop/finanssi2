# 01 Game data

Load all static game assets into the backend as immutable data and check them for consistency. Nothing else in the game reads
JSON directly.

## Scope

- Move the data files into `finanssi2-backend/src/main/resources/gamedata/`, which becomes the canonical copy (`input/` keeps the
  raw sources and photos):
  - `pelilauta.json` (from `input/data/`)
  - `hallintatodistukset.json`, `osakkeet.json`, `porssivihjeet.json` (from the `MOCK_` files; the file names lose the prefix
    because the `mock` flag lives on each entry)
  - `finanssilehdet.json` from `input/finanssilehdet_21kpl.json`, with an `id` added to each card (`FL-01` … `FL-21`) and the
    leading BOM removed
- Records in `game.data`:
  - `Square` (number, name, type, head office flag, type-specific fields: target, percent, group, price, industrial)
  - `SquareType` enum: `BANK_EXIT`, `PROPERTY`, `FINANCE_NEWS`, `STOCK_TIP`, `BRANCH_OFFICE`, `BANK_DIVIDEND`, `CONSTRUCTION`, `JAIL`,
    `BANK_ENTRANCE`, `MOVE_TO`, `SHARE_CRASH`, `GO_TO_JAIL_CHANCE`, `BOND_AUCTION`, `PLAYER_DIVIDEND`, `REPAY_LOAN`,
    `SMALL_BOND_DRAW`, `BOND_PURCHASE_AND_DIVIDEND`
  - `BusinessGroup` (id, name, colour, property squares)
  - `TitleDeed`, `Share`, `Card` (id, deck, chapters) and `CardChapter` (text, header/italic)
  - each asset record carries `mock` so the rest of the code and the UI can show what is still invented
- `GameData` Spring bean that loads everything at startup and gives lookups: square by number, deed by square, shares by group,
  group of a square, cards by deck.
- `GameConstants` (plain constants, not data files): 2–6 players, starting cash 75 000, car 50 000 / sold back 25 000, loan 50 000,
  interest 5 000, max 3 loans per player and 6 in total (official rule; step 04 adds a setting to lift the total), bank entrance reward 5 000 per pip, bond price 500 and numbers 1–12,
  small draw prizes 50 000 / 25 000 / 15 000, grand draw 100 000 / 50 000 / 25 000, mortgage redemption +10%, brokerage fee
  20 000–120 000 in steps of 10 000 with 30 000 to the bank, win at 1 000 000 cash with 2 complete groups.
- Log a warning at startup with the number of mock entries per asset type.

## Validation (fails startup, covered by tests)

- Squares 1–46, each exactly once
- Every property square has a title deed and belongs to exactly one group; every group lists only property squares
- Each group has as many shares as properties (relax this if D3 in [open-questions.md](open-questions.md) turns out otherwise)
- `MOVE_TO` targets exist; industrial flags are exactly squares 26, 27, 29, 30, 32, 33
- 21 Finance News cards and 42 Stock Tip cards, unique ids
- Money values are positive multiples of 500

## Tests

- Loading the real resource files succeeds and the counts match
- Each validation rule rejects a deliberately broken fixture

## Done when

`GameData` loads at startup with the mock warning, and the validation tests pass.

## Notes for later steps

- Card effects are not data: the files hold the text, and steps 09–10 map card ids to effects in code. Real transcriptions can
  then replace the mock text without touching the engine as long as ids stay stable.
- The Finance News file has small typos ("jka", "kaksinkertaiset.", "hinnan"); fix them when moving the file if the user agrees.
- Card 1 of the Finance News file holds two articles (an ongoing "Kiristyneet luottomarkkinat" and an immediate "Autoveron
  korotus"). Effects must allow several per card.
