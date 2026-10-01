# 11 Shareholders' meeting

Status: done. The command validates timing, location, fee, ownership and funds; resolution records the roll, pays sellers and the
bank, transfers assets with mortgages intact, and completes groups for doubled rent.

Taking over a business group from the other players. Completes the main route to owning complete groups.

## Command

`CallShareholdersMeeting(group, brokerageFee)`:

- Before rolling, while standing inside the bank (squares 35–46).
- The player owns at least one property or share in the group, and other players own at least one.
- Not allowed while Finance News FL-15 is active.
- `brokerageFee` is a multiple of 10 000 between 20 000 and 120 000.
- **Takeover sum**: the purchase prices of everything the other players own in the group, properties plus buildings plus shares
  (the share price, not its lower buy-back value).
  Use the base prices, not prices changed by an active Finance News card (R16).
- The player's cash must cover the takeover sum plus the fee. Mortgages, sales and loans done earlier in the same turn are how
  they get there; nothing special is needed for that.

## Resolution

- Fee 120 000: success without rolling.
- Otherwise roll two dice (not doubled under FL-16, R7). Success if the total is at most fee / 10 000.
- **Success**: each other player's properties, buildings and shares in the group move to the caller, who pays each seller the
  purchase price of what they sold. Of the fee, 30 000 goes to the bank and the rest is split equally among the sellers, rounded to
  the nearest 500; the bank absorbs any rounding difference.
- **Failure**: the whole fee goes to the bank; nothing changes hands.
- Mortgaged properties: transfer with the mortgage in place (R16).
- Assets still owned by the bank are not part of the meeting; the player buys them in the normal way.

## Tests

- The rules example (Liikekeskus Oy): sum 140 000, fee 80 000; roll 8 succeeds (B gets 90 000 + 25 000, C gets 50 000 + 25 000),
  roll 9 fails and the bank gets 80 000. The transcribed cards match it: B's built Kirjakauppa is 20 000 + 20 000 and the
  Liikekeskus shares are 50 000, 50 000 and 75 000, so the test doubles as a data check.
- Fee 120 000 without rolling; fee bounds and steps; outside the bank; after rolling; not enough cash; FL-15 active
- Three sellers with a rounding difference

## Done when

A player can take over a group through a shareholders' meeting, and double rent follows from the complete group.
