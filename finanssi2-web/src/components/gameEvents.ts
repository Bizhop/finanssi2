import type { GameBoardData } from "./gameApi.ts"

export type GameLogEntry = { id: string; seq: number; time: number; type: string; event: Record<string, unknown> }

/** What event descriptions need to turn ids into names */
export type EventContext = {
    playerName: (playerId: string) => string
    board: GameBoardData | null
}

const money = (value: unknown) => `€${Number(value ?? 0).toLocaleString()}`
const diceText = (dice: unknown) => {
    const values = Array.isArray(dice) ? dice.map(Number) : []
    return values.length > 1 ? `${values.join(" + ")} = ${values.reduce((sum, value) => sum + value, 0)}` : values.join("")
}

const reasonLabels: Record<string, string> = {
    CAR_PURCHASE: "car purchase",
    CAR_SALE: "car sale",
    LOAN: "loan",
    LOAN_REPAYMENT: "loan repayment",
    LOAN_INTEREST: "loan interest",
    BANK_ENTRANCE_REWARD: "bank entrance reward",
    PROPERTY_PURCHASE: "property purchase",
    SHARE_PURCHASE: "share purchase",
    RENT: "rent",
    MORTGAGE: "mortgage",
    REDEMPTION: "redemption",
    PROPERTY_SALE: "property sale",
    SHARE_SALE: "share sale",
    CONSTRUCTION: "construction",
    BANK_DIVIDEND: "bank dividend",
    PLAYER_DIVIDEND: "player dividend",
    SHARE_CRASH: "share crash",
    BANKRUPTCY: "bankruptcy",
    BOND_PURCHASE: "bond purchase",
    BOND_PRIZE: "bond prize",
    FINANCE_NEWS: "Finance News",
    STOCK_TIP: "Stock Tip",
    ASSET_AUCTION: "asset auction",
    SHAREHOLDERS_MEETING: "shareholders' meeting",
}

export const squareName = (board: GameBoardData | null, square: unknown) => {
    const name = board?.squares.find((item) => item.square === Number(square))?.name
    return name ? `${name} (${square})` : `square ${square}`
}

/** Share ids look like `OS-<GROUP>-<n>`; fund shares are `OS-RAHASTO-<n>` */
export const shareName = (board: GameBoardData | null, id: unknown) => {
    const share = String(id)
    const match = /^OS-(.+)-(\d+)$/.exec(share)
    if (!match) return share
    const [, group, number] = match
    if (group === "RAHASTO") return `fund share ${number}`
    return `${board?.groups.find((item) => item.id === group)?.name ?? group} share ${number}`
}

/** Asset ids from Stock Tips and auctions: `P:<square>` or `S:<share>` */
export const assetName = (board: GameBoardData | null, asset: unknown) => {
    const [kind, value] = String(asset).split(":")
    if (kind === "P") return squareName(board, value)
    if (kind === "S") return shareName(board, value)
    return String(asset)
}

export const cardTitle = (board: GameBoardData | null, id: unknown) => {
    const card = [...(board?.financeNews ?? []), ...(board?.stockTips ?? [])].find((item) => item.id === id)
    const header = card?.chapters.find((chapter) => chapter.type === "header")?.text ?? card?.chapters[0]?.text
    return header ? `“${header}”` : "a card"
}

const list = (values: unknown, format: (value: unknown) => string) => Array.isArray(values) && values.length > 0 ? values.map(format).join(", ") : "none"

/** One readable line for a game log entry */
export const describeEvent = (entry: GameLogEntry, { playerName, board }: EventContext): string => {
    const e = entry.event
    const p = (key = "player") => e[key] == null ? "the bank" : playerName(String(e[key]))
    const bids = (value: unknown) =>
        Array.isArray(value) && value.length > 0
            ? ` Bids: ${value.map((bid) => `${playerName(String(bid.player))} ${bid.amount ? money(bid.amount) : "pass"}`).join(", ")}.`
            : ""
    switch (entry.type) {
        case "DebugDeckChanged":
            return `Debug: next ${e.deck === "FINANCE_NEWS" ? "Finance News" : "Stock Tip"} set to ${cardTitle(board, e.card)}`
        case "PlayerJoined":
            return `${playerName(String(e.player))} joined`
        case "PlayerLeft":
            return `${p()} left`
        case "StartingRoll":
            return `${p()} rolled ${diceText(e.dice)} for starting order (round ${e.round})`
        case "GameStarted":
            return `Game started with ${money(e.startingCash)} each. Turn order: ${list(e.turnOrder, (playerId) => playerName(String(playerId)))}`
        case "SettingsChanged":
            return "Settings changed"
        case "TurnStarted":
            return `${p()}'s turn`
        case "TurnEnded":
            return `${p()} ended the turn`
        case "TurnSkipped":
            return `${p()} misses a turn (${e.remaining} more to skip)`
        case "DiceRolled":
            return `${p()} rolled ${diceText(e.dice)}`
        case "PieceMoved":
            return `${p()} moved from ${squareName(board, e.from)} to ${squareName(board, e.to)}`
        case "LandedOn":
            return `${p()} landed on ${squareName(board, e.square)}`
        case "NotImplemented":
            // The backend emits this for squares that only enable actions before the next roll, which is not a missing rule
            if (e.squareType === "BRANCH_OFFICE") return `${squareName(board, e.square)}: no landing effect; buying is allowed here before the next roll`
            if (e.squareType === "CONSTRUCTION") return `${squareName(board, e.square)}: no landing effect; building is allowed here before the next roll`
            return `Not implemented: ${squareName(board, e.square)} (${e.squareType}) has no effect yet`
        case "FinanceNewsDrawn":
            return `${p()} drew Finance News ${cardTitle(board, e.card)}${e.replaced ? `, replacing ${cardTitle(board, e.replaced)}` : ""}`
        case "StockTipDrawn":
            return `${p()} drew Stock Tip ${cardTitle(board, e.card)}${e.held ? " and keeps it" : ""}`
        case "StockTipUsed":
            return `${p()} used Stock Tip ${cardTitle(board, e.card)}`
        case "HeldStockTipsReturned":
            return `${p()} returned held Stock Tips: ${list(e.cards, (card) => cardTitle(board, card))}`
        case "MoneyTransferred":
            return `${p("from")} paid ${money(e.amount)} to ${p("to")} (${reasonLabels[String(e.reason)] ?? e.reason})`
        case "PaymentDue":
            return `${p()} owes ${money(e.amount)} to ${p("creditor")} and must raise funds`
        case "RentCharged":
            return `${p()} owes ${money(e.amount)} rent to ${p("owner")} for ${squareName(board, e.square)}${e.doubled ? " (doubled, complete group)" : ""}`
        case "CarBought":
            return `${p()} bought a car`
        case "CarSold":
            return `${p()} sold the car`
        case "CarLost":
            return `${p()} lost the car`
        case "LoanTaken":
            return `${p()} took a loan (now ${e.loans})`
        case "LoanRepaid":
            return `${p()} repaid a loan (now ${e.loans})`
        case "BankEntranceRoll":
            return `${p()} rolled ${diceText(e.dice)} for the bank entrance reward`
        case "PropertyBought":
            return `${p()} bought ${squareName(board, e.square)}`
        case "ShareBought":
            return `${p()} bought ${shareName(board, e.share)}`
        case "PropertyMortgaged":
            return `${p()} mortgaged ${squareName(board, e.square)}`
        case "PropertyRedeemed":
            return `${p()} redeemed ${squareName(board, e.square)}`
        case "PropertySoldBack":
            return `${p()} sold ${squareName(board, e.square)} back to the bank`
        case "ShareSoldBack":
            return `${p()} sold ${shareName(board, e.share)} back to the bank`
        case "PropertyBuilt":
            return `${p()} built ${e.industrial ? "an industrial plant" : "a building"} on ${squareName(board, e.square)}`
        case "BuildingsBurned":
            return `${p()} lost buildings to fire: ${list(e.squares, (square) => squareName(board, square))}`
        case "JailRoll":
            return `${p()} rolled ${e.die} in jail and misses ${e.missedTurns} turn${e.missedTurns === 1 ? "" : "s"}`
        case "GoToJailRoll":
            return `${p()} rolled ${e.die} on the chance of jail${e.jailed ? " and goes to jail" : " and stays free"}`
        case "JailExempt":
            return `${p()} is exempt from the chance of jail`
        case "BailRoll":
            return `${p()} rolled ${diceText(e.dice)} for bail${e.bailReturned ? "; bail returned" : ""}`
        case "BankDividend":
            return `${p()} receives ${money(e.amount)} bank dividend on ${squareName(board, e.square)} for ${list(e.shares, (s) => shareName(board, s))}`
        case "PlayerDividendCharged":
            return `${p()} owes ${p("shareholder")} ${money(e.amount)} dividend for ${list(e.shares, (s) => shareName(board, s))}`
        case "StockTipDividendCharged":
            return `${p()} owes ${p("shareholder")} ${money(e.amount)} dividend for ${list(e.shares, (s) => shareName(board, s))}`
        case "BondBought":
            return `${p()} bought bond ${e.number}`
        case "BondGranted":
            return `${p()} received bond ${e.number}`
        case "BondTransferred":
            return `Bond ${e.number} moved from ${p("from")} to ${p("to")}`
        case "BondDrawn":
            return `Bond ${e.number} drawn for ${money(e.prize)}: ${e.winner ? playerName(String(e.winner)) : "not owned"}`
        case "BondOneWon":
            return `Bond 1 won ${money(e.amount)}${e.previousOwner ? ` (owner ${p("previousOwner")})` : ""}`
        case "BondAuctionCompleted":
            return e.winner
                ? `${p("winner")} won bond ${e.number} at auction for ${money(e.amount)}.${bids(e.bids)}`
                : `No bids for bond ${e.number}.${bids(e.bids)}`
        case "AssetAuctionStarted":
            return `${p("seller")} must auction ${assetName(board, e.asset)}, minimum bid ${money(e.minimumBid)}; bidders: ${
                list(e.bidders, (playerId) => playerName(String(playerId)))
            }`
        case "AssetAuctionCompleted":
            return e.winner
                ? `${p("winner")} bought ${assetName(board, e.asset)} from ${p("seller")} at auction for ${money(e.amount)}.${bids(e.bids)}`
                : `No sale of ${assetName(board, e.asset)}.${bids(e.bids)}`
        case "AssetTransferred":
            return `${assetName(board, e.asset)} moved from ${p("from")} to ${p("to")}`
        case "SharesSwapped":
            return `${p()} swapped ${shareName(board, e.given)} for ${p("receivedFrom")}'s ${shareName(board, e.received)}`
        case "ShareIssued":
            return `${p()} received ${shareName(board, e.share)}`
        case "ShareTaken":
            return `${p()} took ${shareName(board, e.share)} from ${p("from")}`
        case "ShareLost":
            return `${p()} lost ${shareName(board, e.share)} to the bank`
        case "ShareholdersMeetingResolved":
            return `${p()} called a shareholders' meeting for ${board?.groups.find((group) => group.id === e.group)?.name ?? e.group}: fee ${
                money(e.brokerageFee)
            }, takeover ${money(e.takeoverSum)}${Array.isArray(e.dice) && e.dice.length ? `, rolled ${diceText(e.dice)}` : ""} — ${
                e.success ? "succeeded" : "failed"
            }`
        case "PlayerBankrupt":
            return `${p()} went bankrupt to ${p("creditor")}`
        case "PlayerResigned":
            return `${p()} resigned`
        case "AssetsReturned":
            return `${p()}'s assets returned to the bank: properties ${list(e.properties, (s) => squareName(board, s))}; shares ${
                list(e.shares, (s) => shareName(board, s))
            }; bonds ${list(e.bonds, String)}`
        case "GameEnded":
            return e.winner ? `Game over: ${p("winner")} wins` : "Game closed without a winner"
        default:
            return entry.type
    }
}
