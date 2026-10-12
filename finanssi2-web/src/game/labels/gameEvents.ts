import type { GameBoardData } from "../../api/gameApi.ts"
import { t } from "../../i18n/index.ts"
import type { MessageKey } from "../../i18n/fi.ts"
import { formatEuro } from "../../ui/format.ts"

export type GameLogEntry = { id: string; seq: number; time: number; type: string; event: Record<string, unknown> }

/** What event descriptions need to turn ids into names */
export type EventContext = {
    playerName: (playerId: string) => string
    board: GameBoardData | null
}

const money = (value: unknown) => formatEuro(Number(value ?? 0))
const diceText = (dice: unknown) => {
    const values = Array.isArray(dice) ? dice.map(Number) : []
    return values.length > 1 ? `${values.join(" + ")} = ${values.reduce((sum, value) => sum + value, 0)}` : values.join("")
}

/** The label of a money transfer's reason; a reason this client does not know is shown as sent */
const reasonLabel = (reason: unknown) => {
    const key = `event.reason.${String(reason)}` as MessageKey
    return t(key, { defaultValue: String(reason) })
}

export const squareName = (board: GameBoardData | null, square: unknown) => {
    const name = board?.squares.find((item) => item.square === Number(square))?.name
    return name ? `${name} (${square})` : t("event.square", { square: String(square) })
}

/** Share ids look like `OS-<GROUP>-<n>`; fund shares are `OS-RAHASTO-<n>` */
export const shareName = (board: GameBoardData | null, id: unknown) => {
    const share = String(id)
    const match = /^OS-(.+)-(\d+)$/.exec(share)
    if (!match) return share
    const [, group, number] = match
    if (group === "RAHASTO") return t("event.fundShare", { number })
    return t("event.groupShare", { group: board?.groups.find((item) => item.id === group)?.name ?? group, number })
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
    return header ? `“${header}”` : t("event.aCard")
}

const list = (values: unknown, format: (value: unknown) => string) =>
    Array.isArray(values) && values.length > 0 ? values.map(format).join(", ") : t("event.none")

/** One readable line for a game log entry */
export const describeEvent = (entry: GameLogEntry, { playerName, board }: EventContext): string => {
    const e = entry.event
    const p = (key = "player") => e[key] == null ? t("event.bank") : playerName(String(e[key]))
    const bids = (value: unknown) =>
        Array.isArray(value) && value.length > 0
            ? t("event.bids", {
                bids: value.map((bid) =>
                    t("event.bid", { player: playerName(String(bid.player)), amount: bid.amount ? money(bid.amount) : t("event.bidPass") })
                ).join(", "),
            })
            : ""
    const shares = (value: unknown) => list(value, (share) => shareName(board, share))
    const square = (key = "square") => squareName(board, e[key])
    const share = (key = "share") => shareName(board, e[key])
    const player = p()
    switch (entry.type) {
        case "DebugDeckChanged":
            return t("event.DebugDeckChanged", {
                deck: t(e.deck === "FINANCE_NEWS" ? "event.deck.FINANCE_NEWS" : "event.deck.STOCK_TIP"),
                card: cardTitle(board, e.card),
            })
        case "PlayerJoined":
            return t("event.PlayerJoined", { player: playerName(String(e.player)) })
        case "PlayerLeft":
            return t("event.PlayerLeft", { player })
        case "StartingRoll":
            return t("event.StartingRoll", { player, dice: diceText(e.dice), round: String(e.round) })
        case "GameStarted":
            return t("event.GameStarted", { cash: money(e.startingCash), order: list(e.turnOrder, (playerId) => playerName(String(playerId))) })
        case "SettingsChanged":
            return t("event.SettingsChanged")
        case "TurnStarted":
            return t("event.TurnStarted", { player })
        case "TurnEnded":
            return t("event.TurnEnded", { player })
        case "TurnSkipped":
            return t("event.TurnSkipped", { player, remaining: String(e.remaining) })
        case "DiceRolled":
            return t("event.DiceRolled", { player, dice: diceText(e.dice) })
        case "PieceMoved":
            return t("event.PieceMoved", { player, from: square("from"), to: square("to") })
        case "LandedOn":
            return t("event.LandedOn", { player, square: square() })
        case "NotImplemented":
            // The backend emits this for squares that only enable actions before the next roll, which is not a missing rule
            if (e.squareType === "BRANCH_OFFICE") return t("event.NotImplemented.BRANCH_OFFICE", { square: square() })
            if (e.squareType === "CONSTRUCTION") return t("event.NotImplemented.CONSTRUCTION", { square: square() })
            return t("event.NotImplemented", { square: square(), type: String(e.squareType) })
        case "FinanceNewsDrawn":
            return e.replaced
                ? t("event.FinanceNewsDrawnReplacing", { player, card: cardTitle(board, e.card), replaced: cardTitle(board, e.replaced) })
                : t("event.FinanceNewsDrawn", { player, card: cardTitle(board, e.card) })
        case "StockTipDrawn":
            return t(e.held ? "event.StockTipDrawnHeld" : "event.StockTipDrawn", { player, card: cardTitle(board, e.card) })
        case "StockTipUsed":
            return t("event.StockTipUsed", { player, card: cardTitle(board, e.card) })
        case "HeldStockTipsReturned":
            return t("event.HeldStockTipsReturned", { player, cards: list(e.cards, (card) => cardTitle(board, card)) })
        case "MoneyTransferred":
            return t("event.MoneyTransferred", { from: p("from"), to: p("to"), amount: money(e.amount), reason: reasonLabel(e.reason) })
        case "PaymentDue":
            return t("event.PaymentDue", { player, amount: money(e.amount), creditor: p("creditor") })
        case "RentCharged":
            return t(e.doubled ? "event.RentChargedDoubled" : "event.RentCharged", { player, amount: money(e.amount), owner: p("owner"), square: square() })
        case "CarBought":
            return t("event.CarBought", { player })
        case "CarSold":
            return t("event.CarSold", { player })
        case "CarLost":
            return t("event.CarLost", { player })
        case "LoanTaken":
            return t("event.LoanTaken", { player, loans: String(e.loans) })
        case "LoanRepaid":
            return t("event.LoanRepaid", { player, loans: String(e.loans) })
        case "BankEntranceRoll":
            return t("event.BankEntranceRoll", { player, dice: diceText(e.dice) })
        case "PropertyBought":
            return t("event.PropertyBought", { player, square: square() })
        case "ShareBought":
            return t("event.ShareBought", { player, share: share() })
        case "PropertyMortgaged":
            return t("event.PropertyMortgaged", { player, square: square() })
        case "PropertyRedeemed":
            return t("event.PropertyRedeemed", { player, square: square() })
        case "PropertySoldBack":
            return t("event.PropertySoldBack", { player, square: square() })
        case "ShareSoldBack":
            return t("event.ShareSoldBack", { player, share: share() })
        case "PropertyBuilt":
            return t(e.industrial ? "event.PropertyBuiltIndustrial" : "event.PropertyBuilt", { player, square: square() })
        case "BuildingsBurned":
            return t("event.BuildingsBurned", { player, squares: list(e.squares, (item) => squareName(board, item)) })
        case "JailRoll":
            return t("event.JailRoll", { player, die: String(e.die), count: Number(e.missedTurns) })
        case "GoToJailRoll":
            return t(e.jailed ? "event.GoToJailRoll.jailed" : "event.GoToJailRoll.free", { player, die: String(e.die) })
        case "JailExempt":
            return t("event.JailExempt", { player })
        case "BailRoll":
            return t(e.bailReturned ? "event.BailRollReturned" : "event.BailRoll", { player, dice: diceText(e.dice) })
        case "BankDividend":
            return t("event.BankDividend", { player, amount: money(e.amount), square: square(), shares: shares(e.shares) })
        case "PlayerDividendCharged":
            return t("event.PlayerDividendCharged", { player, shareholder: p("shareholder"), amount: money(e.amount), shares: shares(e.shares) })
        case "StockTipDividendCharged":
            return t("event.StockTipDividendCharged", { player, shareholder: p("shareholder"), amount: money(e.amount), shares: shares(e.shares) })
        case "BondBought":
            return t("event.BondBought", { player, number: String(e.number) })
        case "BondGranted":
            return t("event.BondGranted", { player, number: String(e.number) })
        case "BondTransferred":
            return t("event.BondTransferred", { number: String(e.number), from: p("from"), to: p("to") })
        case "BondDrawn":
            return t("event.BondDrawn", {
                number: String(e.number),
                prize: money(e.prize),
                winner: e.winner ? playerName(String(e.winner)) : t("event.notOwned"),
            })
        case "BondOneWon":
            return e.previousOwner
                ? t("event.BondOneWonOwner", { amount: money(e.amount), owner: p("previousOwner") })
                : t("event.BondOneWon", { amount: money(e.amount) })
        case "BondAuctionCompleted":
            return e.winner
                ? t("event.BondAuctionCompleted", { winner: p("winner"), number: String(e.number), amount: money(e.amount), bids: bids(e.bids) })
                : t("event.BondAuctionNoBids", { number: String(e.number), bids: bids(e.bids) })
        case "AssetAuctionStarted":
            return t("event.AssetAuctionStarted", {
                seller: p("seller"),
                asset: assetName(board, e.asset),
                minimumBid: money(e.minimumBid),
                bidders: list(e.bidders, (playerId) => playerName(String(playerId))),
            })
        case "AssetAuctionCompleted":
            return e.winner
                ? t("event.AssetAuctionCompleted", {
                    winner: p("winner"),
                    asset: assetName(board, e.asset),
                    seller: p("seller"),
                    amount: money(e.amount),
                    bids: bids(e.bids),
                })
                : t("event.AssetAuctionNoSale", { asset: assetName(board, e.asset), bids: bids(e.bids) })
        case "AssetTransferred":
            return t("event.AssetTransferred", { asset: assetName(board, e.asset), from: p("from"), to: p("to") })
        case "SharesSwapped":
            return t("event.SharesSwapped", { player, given: share("given"), received: share("received"), receivedFrom: p("receivedFrom") })
        case "ShareIssued":
            return t("event.ShareIssued", { player, share: share() })
        case "ShareTaken":
            return t("event.ShareTaken", { player, share: share(), from: p("from") })
        case "ShareLost":
            return t("event.ShareLost", { player, share: share() })
        case "ShareholdersMeetingResolved":
            return t("event.ShareholdersMeetingResolved", {
                player,
                group: board?.groups.find((group) => group.id === e.group)?.name ?? String(e.group),
                fee: money(e.brokerageFee),
                takeover: money(e.takeoverSum),
                rolled: Array.isArray(e.dice) && e.dice.length ? t("event.rolled", { dice: diceText(e.dice) }) : "",
                result: t(e.success ? "event.succeeded" : "event.failed"),
            })
        case "PlayerBankrupt":
            return t("event.PlayerBankrupt", { player, creditor: p("creditor") })
        case "PlayerResigned":
            return t("event.PlayerResigned", { player })
        case "AssetsReturned":
            return t("event.AssetsReturned", {
                player,
                properties: list(e.properties, (item) => squareName(board, item)),
                shares: shares(e.shares),
                bonds: list(e.bonds, String),
            })
        case "GameEnded":
            return e.winner ? t("event.GameEndedWinner", { winner: p("winner") }) : t("event.GameEnded")
        default:
            return entry.type
    }
}
